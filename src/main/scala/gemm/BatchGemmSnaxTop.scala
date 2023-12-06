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
class BatchGemmSnaxTopIO(TCDMWritePorts: Int = 8) extends BatchGemmIO {
  override val data = new TCDMWritePortsDataIO(TCDMWritePorts)
  override val ctrl = new TCDMWritePortsCtrlIO()
}

// The Gemm with multiple cycle output port
// BatchGemmSnaxTop inherits BatchGemm
class BatchGemmSnaxTop(TCDMWritePorts: Int = 8) extends BatchGemm {

  override lazy val io = noPrefix {
    IO(new BatchGemmSnaxTopIO(TCDMWritePorts))
  }

  io.suggestName("io")

  override lazy val controller = Module(new BatchGemmWithStallController())

  // signals for keep sending request until q_ready
  val read_fire = WireInit(false.B)
  val keep_read = RegInit(false.B)

  // signals for indicating when to shift
  // shift after reading K matrix
  val K = RegInit(0.U(GemmConstant.sizeConfigWidth.W))
  val read_rsp_counter = RegInit(0.U((3 * GemmConstant.sizeConfigWidth).W))
  val k_read_rsp_done = WireInit(false.B)

  // number of stage to output the result
  val stages = GemmConstant.idealTCDMWritePorts / TCDMWritePorts

  // registers to store input submatrix address for keep sending request when waiting for the q_ready
  val addr_a = RegInit(0.U(GemmConstant.addrWidth.W))
  val addr_b = RegInit(0.U(GemmConstant.addrWidth.W))

  // check if the register to store current result (current computation process) is ready to decide stall read or not
  val is_ping_output_for_read = WireInit(0.B)
  val is_pong_output_for_read = WireInit(0.B)
  val is_ping_output_for_read_reg = RegInit(1.B)
  val is_pong_output_for_read_reg = RegInit(0.B)

  // double registers to store data and address when output in multi-cycle or waiting for output reg ready
  // the ping/pong double buffering strategy
  val data_c_reg_ping = RegInit(
    0.U(((stages) * TCDMWritePorts * GemmConstant.TCDMDataWidth).W)
  )
  val data_c_reg_pong = RegInit(
    0.U(((stages) * TCDMWritePorts * GemmConstant.TCDMDataWidth).W)
  )
  val addr_c_reg_ping = RegInit(0.U(GemmConstant.addrWidth.W))
  val addr_c_reg_pong = RegInit(0.U(GemmConstant.addrWidth.W))

  // ready/valid signal for each output data and address register to handshake between
  // inputting results from the GEMM and outputting to TCDM
  val output_reg_ready_ping = WireInit(1.B)
  val output_reg_ready_pong = WireInit(1.B)
  val output_reg_valid_ping = RegInit(0.B)
  val output_reg_valid_pong = RegInit(0.B)

  // indicating which output data and address register to receiving current result from GEMM
  val is_ping_output_for_write = WireInit(1.B)
  val is_pong_output_for_write = WireInit(0.B)
  val is_ping_output_for_write_reg = RegInit(1.B)
  val is_pong_output_for_write_reg = RegInit(0.B)

  // indicating which output data and address register is writing to TCDM
  val is_ping_writing = RegInit(1.B)
  val is_pong_writing = RegInit(0.B)

  // write fire signals, only assert when io.ctrl.valid and io.ctrl.ready are asserted together
  val ping_write_fire = WireInit(false.B)
  val pong_write_fire = WireInit(false.B)
  val write_fire = WireInit(false.B)

  // indicating from block gemm point of view, the output is done
  val ping_new_output_fire = WireInit(false.B)
  val pong_new_output_fire = WireInit(false.B)
  val new_output_fire = WireInit(false.B)

  // signals for indicating output progress
  val output_done = WireInit(false.B)
  val output_start = WireInit(false.B)
  val output_counter = RegInit(0.U(log2Ceil(stages).W))

  // indicating if we are output result matrix
  // this is related to the ready signal for ping/pong buffering
  val ping_output_state = RegInit(0.B)
  val pong_output_state = RegInit(0.B)

  // for generating the address in multi cycle output for submatrix c
  def addrDelta =
    TCDMWritePorts * GemmConstant.TCDMDataWidth / GemmConstant.dataWidthPerAddr

  // gemm_write_valid_from_block_gemm asserts until it get a ready signal
  // which means al least the GEMM result is written to one of the double buffering
  val gemm_write_valid_from_block_gemm =
    controller.io.gemm_write_valid_o || keep_gemm_write_valid_o

  val perf_counter = RegInit(0.U(32.W))

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

  // when get k read responds, shift the ping/pong buffer for stalling reading
  k_read_rsp_done := read_rsp_counter === K - 1.U && io.ctrl.data_valid_i === 1.B

  when(k_read_rsp_done) {
    is_ping_output_for_read := ~is_ping_output_for_read_reg
    is_pong_output_for_read := ~is_pong_output_for_read_reg
  }.otherwise {
    is_ping_output_for_read := is_ping_output_for_read_reg
    is_pong_output_for_read := is_pong_output_for_read_reg
  }
  is_ping_output_for_read_reg := is_ping_output_for_read
  is_pong_output_for_read_reg := is_pong_output_for_read

  dontTouch(controller.io.stalled_by_write)
  controller.io.stalled_by_write := ((ping_new_output_fire || !output_reg_ready_ping) && is_ping_output_for_read) || ((pong_new_output_fire || !output_reg_ready_pong) && is_pong_output_for_read)

  // shift the ping/pong buffer for writing the results form GEMM
  new_output_fire := ping_new_output_fire || pong_new_output_fire
  when(new_output_fire) {
    is_ping_output_for_write := ~is_ping_output_for_write_reg
    is_pong_output_for_write := ~is_pong_output_for_write_reg
  }.otherwise {
    is_ping_output_for_write := is_ping_output_for_write_reg
    is_pong_output_for_write := is_pong_output_for_write_reg
  }
  is_ping_output_for_write_reg := is_ping_output_for_write
  is_pong_output_for_write_reg := is_pong_output_for_write

  // storing io.data.c_o when it is valid for later output according to if it is ping or pong
  // for ping buffer
  ping_write_fire := io.ctrl.gemm_write_valid_o && io.ctrl.write_mem_ready && is_ping_writing
  ping_new_output_fire := output_reg_ready_ping && gemm_write_valid_from_block_gemm && is_ping_output_for_write_reg
  // right shift the result data to support only output the lowest part of bits every time without changing the index
  when(ping_write_fire && ping_new_output_fire) {
    data_c_reg_ping := io.data.c_o >> (TCDMWritePorts * GemmConstant.TCDMDataWidth).U
  }.elsewhen(!ping_write_fire && ping_new_output_fire) {
    data_c_reg_ping := io.data.c_o
  }.elsewhen(ping_write_fire) {
    data_c_reg_ping := data_c_reg_ping >> (TCDMWritePorts * GemmConstant.TCDMDataWidth).U
  }

  // store the address and extra data for later output
  when(ping_new_output_fire) {
    addr_c_reg_ping := block_gemm_addr_c_o
  }

  // for pong buffer
  pong_write_fire := io.ctrl.gemm_write_valid_o && io.ctrl.write_mem_ready && is_pong_writing
  pong_new_output_fire := output_reg_ready_pong && gemm_write_valid_from_block_gemm && is_pong_output_for_write_reg
  when(pong_write_fire && pong_new_output_fire) {
    data_c_reg_pong := io.data.c_o >> (TCDMWritePorts * GemmConstant.TCDMDataWidth).U
  }.elsewhen(!pong_write_fire && pong_new_output_fire) {
    data_c_reg_pong := io.data.c_o
  }.elsewhen(pong_write_fire) {
    data_c_reg_pong := data_c_reg_pong >> (TCDMWritePorts * GemmConstant.TCDMDataWidth).U
  }

  when(pong_new_output_fire) {
    addr_c_reg_pong := block_gemm_addr_c_o
  }

  // designing ping/pong output state change
  when(ping_new_output_fire) {
    ping_output_state := 1.U
  }.elsewhen(
    ping_output_state === 1.U && output_done && is_ping_writing
  ) {
    ping_output_state := 0.U
  }

  when(pong_new_output_fire) {
    pong_output_state := 1.U
  }.elsewhen(
    pong_output_state === 1.U && output_done && is_pong_writing
  ) {
    pong_output_state := 0.U
  }

  // output_counter for multi stage output for both ping and pong
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
  // output key signals for both ping and pong
  output_start := new_output_fire
  output_done := output_counter === (stages.U - 1.U) && write_fire

  // shift the ping/pong for writing to the TCDM
  when(output_done) {
    is_ping_writing := ~is_ping_writing
    is_pong_writing := ~is_pong_writing
  }.otherwise {
    is_ping_writing := is_ping_writing
    is_pong_writing := is_pong_writing
  }

  // set new_output_ready when it is not the output state
  output_reg_ready_ping := !ping_output_state
  output_reg_ready_pong := !pong_output_state
  // give ready signal to block gemm
  gemm_write_ready_o := (output_reg_ready_ping && is_ping_output_for_write_reg) || (output_reg_ready_pong && is_pong_output_for_write_reg)

  // multi-stage output data and address according to the ping/pong
  when(is_ping_writing) {
    io.data.multi_stage_c_o := Mux(
      ping_new_output_fire,
      io.data.c_o((TCDMWritePorts * GemmConstant.TCDMDataWidth) - 1, 0),
      data_c_reg_ping((TCDMWritePorts * GemmConstant.TCDMDataWidth) - 1, 0)
    )
    io.ctrl.addr_c_o := Mux(
      ping_new_output_fire,
      block_gemm_addr_c_o,
      addr_c_reg_ping + addrDelta.U * output_counter
    )
  }.otherwise {
    io.data.multi_stage_c_o := Mux(
      pong_new_output_fire,
      io.data.c_o((TCDMWritePorts * GemmConstant.TCDMDataWidth) - 1, 0),
      data_c_reg_pong((TCDMWritePorts * GemmConstant.TCDMDataWidth) - 1, 0)
    )
    io.ctrl.addr_c_o := Mux(
      pong_new_output_fire,
      block_gemm_addr_c_o,
      addr_c_reg_pong + addrDelta.U * output_counter
    )
  }

  // indicating if the data in ping/pong buffer is valid. if valid, keep writing
  when(ping_new_output_fire) {
    output_reg_valid_ping := 1.B
  }.elsewhen(output_done && is_ping_writing) {
    output_reg_valid_ping := 0.B
  }

  when(pong_new_output_fire) {
    output_reg_valid_pong := 1.B
  }.elsewhen(output_done && is_pong_writing) {
    output_reg_valid_pong := 0.B
  }

  // busy util all write finish
  io.ctrl.busy_o := controller.io.busy_o || io.ctrl.gemm_write_valid_o
  controller.io.start_do_i := io.ctrl.start_do_i && !io.ctrl.busy_o

  // performance counter for profiling
  when(io.ctrl.start_do_i && !io.ctrl.busy_o) {
    perf_counter := 0.U
  }.elsewhen(io.ctrl.busy_o =/= 0.U) {
    perf_counter := perf_counter + 1.U
  }

  // below is for if not q_ready, keep sending read/write request
  read_fire := io.ctrl.gemm_read_valid_o && io.ctrl.read_mem_ready
  when(io.ctrl.gemm_read_valid_o && !read_fire) {
    keep_read := 1.B
  }.otherwise {
    keep_read := 0.B
  }
  io.ctrl.gemm_read_valid_o := controller.io.gemm_read_valid_o || keep_read
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

  write_fire := ping_write_fire || pong_write_fire

  io.ctrl.gemm_write_valid_o := ((output_reg_valid_ping || ping_new_output_fire && is_ping_writing) || (output_reg_valid_pong || pong_new_output_fire && is_pong_writing))

  io.ctrl.perf_counter := perf_counter
}

object BatchGemmSnaxTop extends App {
  emitVerilog(
    new BatchGemmSnaxTop(GemmConstant.TCDMWritePorts),
    Array("--target-dir", "generated/gemm")
  )
}
