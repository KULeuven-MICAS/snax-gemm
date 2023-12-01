package gemm

import chisel3._
import chisel3.util._
import chisel3.experimental.{prefix, noPrefix}

// Compared with Batch Gemm, BatchGemmWithStall has an extra input port: stalled_by_write
// If stalled_by_write is asserted, the input read signal and computation is stalled.
class BatchGemmWithStallControllerIO extends BatchGemmControllerIO {
  val stalled_by_write = Input(Bool())
}

// BatchGemmWithStallController module inherit BatchGemmController
// BatchGemmController has streaming input and output without any stall, assuming enough bandwidth
// In this module, if stalled_by_write is asserted, the input read signal and computation is stalled.
class BatchGemmWithStallController extends BatchGemmController {
  override lazy val io = IO(new BatchGemmWithStallControllerIO())

  // These two signal are used for generating new read request after the stall finishes
  val read_rsp_counter = RegInit(0.U(24.W))
  val new_read = WireInit(false.B)

  when(io.data_valid_i && io.busy_o) {
    read_rsp_counter := read_rsp_counter + 1.U
  }.elsewhen(!io.busy_o) {
    read_rsp_counter := 0.U
  }

  // If read_counter <= read_rsp_counter, it means the read request number is equal or less the read responds number
  // So another read request can be sent
  new_read := read_counter <= read_rsp_counter

  io.gemm_read_valid_o := ((io.data_valid_i === 1.B || start_batch === 1.B || new_read === 1.B) && (!io.stalled_by_write) && (cstate === sREAD))

}

// The Gemm with stall signal for stalling the input and computation
// until the output is finished
// Note: this module still output the result matrix in one cycle! Only with the stall support
class BatchGemmTCDMWritePorts(TCDMWritePorts: Int = 8) extends BatchGemm {

  override lazy val controller = Module(new BatchGemmWithStallController())

  val stalled_by_write = WireInit(false.B)

  // number of stage to output the result
  val stages = GemmConstant.idealTCDMWritePorts / TCDMWritePorts

  // signals and counters for generating stall signal
  val K = RegInit(0.U(GemmConstant.sizeConfigWidth.W))
  val need_stall = stages.U > K
  // - 1.U as start_stall_counter also stalls on cycle
  val stall_cycle_num = stages.U - K - 1.U
  val stall = WireInit(false.B)
  val start_stall_counter = WireInit(false.B)
  val stall_counter = RegInit(0.U(GemmConstant.sizeConfigWidth.W))
  val read_rsp_counter = RegInit(0.U(24.W))

  val output_reg = RegInit(
    0.U(((stages - 1) * TCDMWritePorts * GemmConstant.TCDMDataWidth).W)
  )
  val output_counter = RegInit(0.U(log2Ceil(stages).W))
  val addr_c = RegInit(0.U(GemmConstant.addrWidth.W))

  K := Mux(io.ctrl.start_do_i && !controller.io.busy_o, io.ctrl.K_i, K)

  // counting the read respond signal to decide when to stall
  when(read_rsp_counter =/= K && io.ctrl.data_valid_i === 1.B) {
    read_rsp_counter := read_rsp_counter + 1.U
  }.elsewhen(read_rsp_counter === K && io.ctrl.data_valid_i === 1.B) {
    read_rsp_counter := 1.U
  }.elsewhen(read_rsp_counter === K && io.ctrl.data_valid_i === 0.B) {
    read_rsp_counter := 0.U
  }.elsewhen(!controller.io.busy_o) {
    read_rsp_counter := 0.U
  }

  // after K times read responds valid, it is the time to start stall
  start_stall_counter := read_rsp_counter === K - 1.U && io.ctrl.data_valid_i === 1.B

  // counting for stall cycle
  // only stall when stall_cycle_num =/= 0
  // stall_counter === stall_cycle_num means stall over, then clean the stall_counter
  when(stall_cycle_num =/= 0.U) {
    when(stall_counter === 0.U && start_stall_counter) {
      stall_counter := 1.U
    }.elsewhen(stall_counter =/= 0.U && stall_counter =/= stall_cycle_num) {
      stall_counter := stall_counter + 1.U
    }.elsewhen(stall_counter === stall_cycle_num) {
      stall_counter := 0.U
    }.otherwise {
      stall_counter := 0.U
    }
  }

  // stall when start_stall_counter or the stall_counter is less than the stall cycle number
  // otherwise will give read request when the data_valid_i is asserted
  stall := (start_stall_counter) || (stall_counter =/= 0.U && stall_counter <= stall_cycle_num)

  // consider if needs to stall only when need_stall which means the K is less than the stages
  stalled_by_write := need_stall && stall && controller.io.busy_o

  controller.io.stalled_by_write := stalled_by_write

}

object BatchGemmTCDMWritePorts extends App {
  emitVerilog(
    new (BatchGemmTCDMWritePorts),
    Array("--target-dir", "generated/gemm")
  )
}
