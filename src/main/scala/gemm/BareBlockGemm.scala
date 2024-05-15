package gemm

import chisel3._
import chisel3.util._

// The BareBlockGemm's control port declaration.
class BareBlockGemmCtrlIO extends Bundle {

  val M_i = (UInt(GemmConstant.sizeConfigWidth.W))
  val K_i = (UInt(GemmConstant.sizeConfigWidth.W))
  val N_i = (UInt(GemmConstant.sizeConfigWidth.W))
  val subtraction_constant_i = (UInt(GemmConstant.subtractionCfgWidth.W))

}

// The BareBlockGemm's data port declaration. Decoupled interface connected to the streamer
class BareBlockGemmDataIO extends Bundle {
  val a_i = Flipped(
    DecoupledIO(
      UInt(
        (GemmConstant.meshRow * GemmConstant.tileSize * GemmConstant.dataWidthA).W
      )
    )
  )
  val b_i = Flipped(
    DecoupledIO(
      UInt(
        (GemmConstant.tileSize * GemmConstant.meshCol * GemmConstant.dataWidthB).W
      )
    )
  )
  val c_o = DecoupledIO(
    UInt(
      (GemmConstant.meshRow * GemmConstant.meshCol * GemmConstant.dataWidthC).W
    )
  )
}

// BareBlockGemmIO declaration, including control and data as well as two extra output signal
class BareBlockGemmIO extends Bundle {

  val ctrl = Flipped(DecoupledIO(new BareBlockGemmCtrlIO()))
  val data = new BareBlockGemmDataIO()
  val busy_o = Output(Bool())
  val performance_counter = Output(UInt(32.W))

}

// BareBlockGemm module
class BareBlockGemm extends Module with RequireAsyncReset {

  val io = IO(new BareBlockGemmIO())

  val gemm_array = Module(new GemmArray())

  // Registers to store the configurations
  val M = RegInit(0.U(GemmConstant.sizeConfigWidth.W))
  val K = RegInit(0.U(GemmConstant.sizeConfigWidth.W))
  val N = RegInit(0.U(GemmConstant.sizeConfigWidth.W))

  val subtraction_a = RegInit(0.U(GemmConstant.dataWidthA.W))
  val subtraction_b = RegInit(0.U(GemmConstant.dataWidthB.W))

  // useful counters
  val accumulation_counter = RegInit(0.U((3 * GemmConstant.sizeConfigWidth).W))

  val write_valid_counter = RegInit(0.U(GemmConstant.sizeConfigWidth.W))

  val write_counter = RegInit(0.U((3 * GemmConstant.sizeConfigWidth).W))

  val performance_counter = RegInit(0.U(32.W))

  // control signals for the counter incremental
  val accumulation = WireInit(0.B)
  val input_data_valid = WireInit(0.B)

  val gemm_input_fire = WireInit(0.B)
  val gemm_output_fire = WireInit(0.B)

  // State declaration
  val sIDLE :: sBUSY :: Nil = Enum(2)
  val cstate = RegInit(sIDLE)
  val nstate = WireInit(sIDLE)

  // signals for state transition
  val config_valid = WireInit(0.B)
  val computation_finish = WireInit(0.B)

  // Changing states
  cstate := nstate

  chisel3.dontTouch(cstate)
  switch(cstate) {
    is(sIDLE) {
      when(config_valid) {
        nstate := sBUSY
      }.otherwise {
        nstate := sIDLE
      }

    }
    is(sBUSY) {
      when(computation_finish) {
        nstate := sIDLE
      }.otherwise {
        nstate := sBUSY
      }
    }
  }

  config_valid := io.ctrl.fire

  // Store the configurations when config valid
  when(config_valid && cstate === sIDLE) {
    M := io.ctrl.bits.M_i
    N := io.ctrl.bits.N_i
    K := io.ctrl.bits.K_i
    assert(
      io.ctrl.bits.M_i =/= 0.U || io.ctrl.bits.K_i =/= 0.U || io.ctrl.bits.K_i =/= 0.U,
      " M == 0 or K ==0 or N == 0, invalid configuration!"
    )
    subtraction_a := io.ctrl.bits.subtraction_constant_i(7, 0)
    subtraction_b := io.ctrl.bits.subtraction_constant_i(15, 8)
  }

  // write all the results out means the operation is done
  computation_finish := write_counter === (M * N - 1.U) && io.data.c_o.fire && cstate === sBUSY

  // write counter increment according to output data fire
  when(io.data.c_o.fire) {
    write_counter := write_counter + 1.U
  }.elsewhen(cstate === sIDLE) {
    write_counter := 0.U
  }

  // input data valid signal, when both a and b are valid, the input data is valid
  input_data_valid := io.data.a_i.valid && io.data.b_i.valid && cstate === sBUSY
  // gemm input fire signal, when both a and b are valid and gemm is ready for new input data
  gemm_input_fire := gemm_array.io.a_b_ready_o && gemm_array.io.a_b_valid_i

  // accumulation counter for generating the accumulation signal for Gemm Array
  // value change according to gemm_input_fire
  when(
    gemm_input_fire && accumulation_counter =/= K - 1.U && K =/= 1.U && cstate =/= sIDLE
  ) {
    accumulation_counter := accumulation_counter + 1.U
  }.elsewhen(
    gemm_input_fire && accumulation_counter === K - 1.U && cstate =/= sIDLE
  ) {
    accumulation_counter := 0.U
  }.elsewhen(cstate === sIDLE) {
    accumulation_counter := 0.U
  }

  // accumulation control signal
  accumulation := accumulation_counter =/= 0.U

  when(cstate === sBUSY) {
    performance_counter := performance_counter + 1.U
  }.elsewhen(config_valid) {
    performance_counter := 0.U
  }

  // gemm output fire signal, asserted when gemm is fire for outputting the result
  gemm_output_fire := gemm_array.io.c_valid_o && gemm_array.io.c_ready_i

  when(
    gemm_output_fire && write_valid_counter =/= (K - 1.U) && cstate =/= sIDLE
  ) {
    write_valid_counter := write_valid_counter + 1.U
  }.elsewhen(
    gemm_output_fire && write_valid_counter === (K - 1.U) && cstate =/= sIDLE
  ) {
    write_valid_counter := 0.U
  }.elsewhen(cstate === sIDLE) {
    write_valid_counter := 0.U
  }

  // output control signals
  io.performance_counter := performance_counter

  io.busy_o := cstate =/= sIDLE

  io.ctrl.ready := cstate === sIDLE

  // Gemm Array signal connection
  // control signals
  gemm_array.io.a_b_valid_i := input_data_valid
  gemm_array.io.accumulate_i := accumulation
  gemm_array.io.c_ready_i := io.data.c_o.ready || write_valid_counter =/= K - 1.U

  // data signals
  gemm_array.io.data.a_i := io.data.a_i.bits
  gemm_array.io.data.b_i := io.data.b_i.bits

  gemm_array.io.subtraction_a_i := subtraction_a
  gemm_array.io.subtraction_b_i := subtraction_b

  // ready for pop out the data from outside
  val output_stalled = io.data.c_o.valid && !io.data.c_o.ready
  io.data.a_i.ready := cstate === sBUSY && gemm_input_fire && !output_stalled
  io.data.b_i.ready := cstate === sBUSY && gemm_input_fire && !output_stalled

  // gemm output signals
  io.data.c_o.bits := gemm_array.io.data.c_o
  io.data.c_o.valid := (write_valid_counter === K - 1.U) && gemm_array.io.c_valid_o && cstate =/= sIDLE

}

// Scala main function for generating system verilog file for the BareBlockGemm module
object BareBlockGemm extends App {
  emitVerilog(
    new (BareBlockGemm),
    Array("--target-dir", "generated/gemm")
  )
}

// adds the GemmCsrManager for new gemm to be integrated with streamer.
// gives the new gemm the same interface to SNAX as streamer (the csrReqRspIO).
class BareBlockGemmTop() extends Module with RequireAsyncReset {
  val io = IO(new Bundle {
    val csr = new CsrReqRspIO(GemmConstant.csrAddrWidth)
    val data = new BareBlockGemmDataIO()
  })

  val GemmCsrManager = Module(
    new GemmCsrManager(GemmConstant.csrNum, GemmConstant.csrAddrWidth)
  )
  val bareBlockGemm = Module(new BareBlockGemm())

  // io.csr and GemmCsrManager input connection
  // rsp port connected directly to the outside
  GemmCsrManager.io.csr_config_in.rsp <> io.csr.rsp

  // csr req port connected with both the outside and the GEMM performacne counter write
  val csr_config_in_req_valid = WireInit(false.B)
  val csr_config_in_req_bits = Wire(new CsrReq(GemmConstant.csrAddrWidth))

  val GeMMBusy2Idle = WireInit(false.B)
  GeMMBusy2Idle := !bareBlockGemm.io.busy_o && RegNext(bareBlockGemm.io.busy_o)

  csr_config_in_req_valid := io.csr.req.valid
  when(GeMMBusy2Idle) {
    csr_config_in_req_bits.addr := GemmConstant.csrNum.U - 2.U
    csr_config_in_req_bits.data := bareBlockGemm.io.performance_counter
    csr_config_in_req_bits.write := true.B
  }.otherwise {
    csr_config_in_req_bits := io.csr.req.bits
  }

  GemmCsrManager.io.csr_config_in.req.valid := csr_config_in_req_valid
  GemmCsrManager.io.csr_config_in.req.bits := csr_config_in_req_bits
  io.csr.req.ready := GemmCsrManager.io.csr_config_in.req.ready

  GemmCsrManager.io.GeMMBusy2Idle := GeMMBusy2Idle

  // GemmCsrManager output and bare block gemm control port connection
  // control signals
  bareBlockGemm.io.ctrl.valid := GemmCsrManager.io.csr_config_out.valid
  GemmCsrManager.io.csr_config_out.ready := bareBlockGemm.io.ctrl.ready

  // the first csr contains the innermost loop bound which is K
  bareBlockGemm.io.ctrl.bits.K_i := GemmCsrManager.io.csr_config_out.bits(0)
  // the second csr contains the next inside loop bound which is N
  bareBlockGemm.io.ctrl.bits.N_i := GemmCsrManager.io.csr_config_out.bits(1)
  // the third csr contains the outermost loop bound which is M
  bareBlockGemm.io.ctrl.bits.M_i := GemmCsrManager.io.csr_config_out.bits(2)

  // the forth csr contains the subtraction_a value
  bareBlockGemm.io.ctrl.bits.subtraction_constant_i :=
    GemmCsrManager.io.csr_config_out.bits(3)

  // io.data and bare block gemm data ports connection
  io.data <> bareBlockGemm.io.data

}

object BareBlockGemmTop extends App {
  emitVerilog(
    new (BareBlockGemmTop),
    Array("--target-dir", "generated/gemm")
  )
}

object BareBlockGemmTopGen {
  def main(args: Array[String]): Unit = {
    val outPath = args.headOption.getOrElse("../../../../rtl/.")
    emitVerilog(
      new BareBlockGemmTop,
      Array("--target-dir", outPath)
    )
  }
}
