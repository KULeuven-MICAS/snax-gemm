package gemm

import chisel3._
import chisel3.util._
import chisel3.experimental.{prefix, noPrefix}

// Add the multi cycle output port
class TCDMWritePortsDataIO(TCDMWritePorts: Int = 8) extends GemmDataIO {
  val multi_stage_c_o = Output(
    UInt((TCDMWritePorts * GemmConstant.TCDMDataWidth).W)
  )
}

// Add mem ready signals
class TCDMWritePortsCtrlIO extends BatchGemmCtrlIO {
  val read_mem_ready = Input(Bool())
  val write_mem_ready = Input(Bool())
}

// Override old gemm io ports with new ports
class BatchGemmTCDMWritePortsMultiOutputIO(TCDMWritePorts: Int = 8)
    extends BatchGemmIO {
  override val data = new TCDMWritePortsDataIO(TCDMWritePorts)
  override val ctrl = new TCDMWritePortsCtrlIO()
}

// The Gemm with multiple cycle output port
// BatchGemmTCDMWritePortsMultiOutput inherits BatchGemmTCDMWritePorts
class BatchGemmTCDMWritePortsMultiOutput(TCDMWritePorts: Int = 8)
    extends BatchGemm {

  override lazy val io = noPrefix {
    IO(new BatchGemmTCDMWritePortsMultiOutputIO(TCDMWritePorts))
  }

  io.suggestName("io")

  override lazy val controller = Module(new BatchGemmWithStallController())

  // signals for keep sending request until q_ready
  val read_fire = WireInit(false.B)
  val keep_read = RegInit(false.B)
  val write_fire = WireInit(false.B)
  val keep_write = RegInit(false.B)

  // signals for indicating when to stall
  // stall after reading K matrix until writing current output is ready
  val stalled_by_write_reg = RegInit(false.B)
  val K = RegInit(0.U(GemmConstant.sizeConfigWidth.W))
  val read_rsp_counter = RegInit(0.U(24.W))
  val start_stall_counter = WireInit(false.B)

  val output_done = WireInit(false.B)
  val output_start = WireInit(false.B)

  // number of stage to output the result
  val stages = GemmConstant.idealTCDMWritePorts / TCDMWritePorts

  // signals for indicating output progress
  val output_counter = RegInit(0.U(log2Ceil(stages).W))
  // indicating if we are output result matrix
  val output_state = RegInit(0.U(1.W))

  // regs to store data and address when output in multi-cycle or waiting for q_ready
  val output_reg = RegInit(
    0.U(((stages) * TCDMWritePorts * GemmConstant.TCDMDataWidth).W)
  )
  val addr_c = RegInit(0.U(GemmConstant.addrWidth.W))

  // regs to store input matrix address fro keep sending request when waiting for the q_ready
  val addr_a = RegInit(0.U(GemmConstant.addrWidth.W))
  val addr_b = RegInit(0.U(GemmConstant.addrWidth.W))

  // for generating the address in multi cycle output
  def addrDelta =
    TCDMWritePorts * GemmConstant.TCDMDataWidth / GemmConstant.dataWidthPerAddr

  // when output_valid_multi_stages is asserted, the output data valid is asserted too
  def output_valid_multi_stages = output_state =/= 0.U

  K := Mux(io.ctrl.start_do_i && !controller.io.busy_o, io.ctrl.K_i, K)

  addr_a := Mux(controller.io.gemm_read_valid_o, controller.io.addr_a_o, addr_a)
  addr_b := Mux(controller.io.gemm_read_valid_o, controller.io.addr_b_o, addr_b)

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

  // stall after reading K matrix back
  start_stall_counter := read_rsp_counter === K - 1.U && io.ctrl.data_valid_i === 1.B

  // stall after reading K matrix, no need to stall when outputting current result matrix, because we have double buffering
  when(start_stall_counter) {
    stalled_by_write_reg := 1.B
  }.elsewhen(output_start) {
    stalled_by_write_reg := 0.B
  }

  controller.io.stalled_by_write := start_stall_counter || stalled_by_write_reg

  // store the address and extra data for later output
  when(controller.io.gemm_write_valid_o) {
    addr_c := io.ctrl.addr_c_o
  }

  when(controller.io.gemm_write_valid_o) {
    output_state := 1.U
  }.elsewhen(
    output_state === 1.U && output_done && !controller.io.gemm_write_valid_o
  ) {
    output_state := 0.U
  }

  // output_counter for multi stage output
  when(
    output_counter === 0.U && write_fire && (output_counter < (stages.U - 1.U))
  ) {
    output_counter := 1.U
  }.elsewhen(
    output_counter =/= 0.U && write_fire && (output_counter < (stages.U - 1.U))
  ) {
    output_counter := output_counter + 1.U
  }.elsewhen(output_counter =/= 0.U && output_done) {
    output_counter := 0.U
  }.elsewhen(!io.ctrl.busy_o) {
    output_counter := 0.U
  }

  dontTouch(output_done)
  output_start := controller.io.gemm_write_valid_o
  output_done := output_counter === (stages.U - 1.U) && write_fire

  // right shift the result data to support only output the lowest part of bits every time without changing the index
  // storing io.data.c_o when it is valid for later output
  when(write_fire && controller.io.gemm_write_valid_o) {
    output_reg := io.data.c_o >> (TCDMWritePorts * GemmConstant.TCDMDataWidth).U
  }.elsewhen(!write_fire && controller.io.gemm_write_valid_o) {
    output_reg := io.data.c_o
  }.elsewhen(write_fire) {
    output_reg := output_reg >> (TCDMWritePorts * GemmConstant.TCDMDataWidth).U
  }

  // multi-stage output data and address
  io.data.multi_stage_c_o := Mux(
    controller.io.gemm_write_valid_o,
    io.data.c_o((TCDMWritePorts * GemmConstant.TCDMDataWidth) - 1, 0),
    output_reg((TCDMWritePorts * GemmConstant.TCDMDataWidth) - 1, 0)
  )
  io.ctrl.addr_c_o := Mux(
    controller.io.gemm_write_valid_o,
    controller.io.addr_c_o,
    addr_c + addrDelta.U * output_counter
  )

  // not busy util all write finish
  io.ctrl.busy_o := controller.io.busy_o || io.ctrl.gemm_write_valid_o
  controller.io.start_do_i := io.ctrl.start_do_i && !io.ctrl.busy_o

  // below is for if not q_ready, keep sending read/write request
  read_fire := io.ctrl.gemm_read_valid_o && io.ctrl.read_mem_ready
  write_fire := io.ctrl.gemm_write_valid_o && io.ctrl.write_mem_ready

  when(io.ctrl.gemm_read_valid_o && !read_fire) {
    keep_read := 1.B
  }.otherwise {
    keep_read := 0.B
  }

  when(io.ctrl.gemm_write_valid_o && !write_fire) {
    keep_write := 1.B
  }.otherwise {
    keep_write := 0.B
  }

  // giving output
  io.ctrl.gemm_read_valid_o := controller.io.gemm_read_valid_o || keep_read
  io.ctrl.gemm_write_valid_o := controller.io.gemm_write_valid_o || output_valid_multi_stages || keep_write
  io.ctrl.addr_a_o := Mux(
    controller.io.gemm_read_valid_o,
    controller.io.addr_a_o,
    addr_a
  )
  io.ctrl.addr_b_o := Mux(
    controller.io.gemm_read_valid_o,
    controller.io.addr_b_o,
    addr_b
  )

}

object BatchGemmTCDMWritePortsMultiOutput extends App {
  emitVerilog(
    new (BatchGemmTCDMWritePortsMultiOutput),
    Array("--target-dir", "generated/gemm")
  )
}
