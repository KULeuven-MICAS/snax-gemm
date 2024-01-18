package gemm

import chisel3._
import chisel3.util._

// The BareBlockGemm's control port declaration.
class BareBlockGemmCtrlIO extends Bundle {

  val M_i = (UInt(GemmConstant.sizeConfigWidth.W))
  val K_i = (UInt(GemmConstant.sizeConfigWidth.W))
  val N_i = (UInt(GemmConstant.sizeConfigWidth.W))
  val subtraction_a_i = (UInt(GemmConstant.dataWidthA.W))
  val subtraction_b_i = (UInt(GemmConstant.dataWidthB.W))

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
  val perf_counter = Output(UInt(32.W))

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

  val write_counter = RegInit(0.U((3 * GemmConstant.sizeConfigWidth).W))

  val perf_counter = RegInit(0.U(32.W))

  // control signals for the counter incremental
  val accumulation = WireInit(0.B)
  val input_data_valid = WireInit(0.B)

  // signals for state transition
  val config_valid = WireInit(0.B)
  val computation_finish = WireInit(0.B)

  // State declaration
  val sIDLE :: sBUSY :: Nil = Enum(2)
  val cstate = RegInit(sIDLE)
  val nstate = WireInit(sIDLE)

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
  when(config_valid) {
    M := io.ctrl.bits.M_i
    N := io.ctrl.bits.N_i
    K := io.ctrl.bits.K_i
    assert(
      io.ctrl.bits.M_i =/= 0.U || io.ctrl.bits.K_i =/= 0.U || io.ctrl.bits.K_i =/= 0.U,
      " M == 0 or K ==0 or N == 0, invalid configuration!"
    )
    subtraction_a := io.ctrl.bits.subtraction_a_i
    subtraction_b := io.ctrl.bits.subtraction_b_i
  }

  // write all the results out means the operation is done
  computation_finish := write_counter === (M * N * K - 1.U) && io.data.c_o.fire && cstate === sBUSY

  // write counter increment according to output data fire
  when(io.data.c_o.fire) {
    write_counter := write_counter + 1.U
  }.elsewhen(cstate === sIDLE) {
    write_counter := 0.U
  }

  input_data_valid := io.data.a_i.fire && io.data.b_i.fire && cstate === sBUSY

  // accumulation counter for generating the accumulation signal for Gemm Array
  when(
    input_data_valid && accumulation_counter =/= K - 1.U && K =/= 1.U && cstate =/= sIDLE
  ) {
    accumulation_counter := accumulation_counter + 1.U
  }.elsewhen(
    (input_data_valid) && accumulation_counter === K - 1.U && cstate =/= sIDLE
  ) {
    accumulation_counter := 0.U
  }.elsewhen(cstate === sIDLE) {
    accumulation_counter := 0.U
  }

  accumulation := accumulation_counter =/= K - 1.U

  when(cstate === sBUSY) {
    perf_counter := perf_counter + 1.U
  }.elsewhen(config_valid) {
    perf_counter := 0.U
  }

  // output control signals
  io.perf_counter := perf_counter

  io.busy_o := cstate =/= sIDLE

  io.ctrl.ready := cstate === sIDLE

  // ready for getting new input data when gemm is ready for new computation, aka, not the the outputting previous result mode
  io.data.a_i.ready := cstate === sBUSY && gemm_array.io.gemm_ready_o
  io.data.b_i.ready := cstate === sBUSY && gemm_array.io.gemm_ready_o

  // Gemm Array signal connection
  // control signals
  gemm_array.io.data_valid_i := input_data_valid
  gemm_array.io.accumulate_i := accumulation
  gemm_array.io.data_ready_o := io.data.c_o.ready

  // data signals
  gemm_array.io.data.a_i := io.data.a_i.bits
  gemm_array.io.data.b_i := io.data.b_i.bits

  gemm_array.io.subtraction_a_i := subtraction_a
  gemm_array.io.subtraction_b_i := subtraction_b

  // gemm output signals
  io.data.c_o.bits := gemm_array.io.data.c_o
  io.data.c_o.valid := gemm_array.io.data_valid_o

}

// Scala main function for generating system verilog file for the BareBlockGemm module
object BareBlockGemm extends App {
  emitVerilog(
    new (BareBlockGemm),
    Array("--target-dir", "generated/gemm")
  )
}
