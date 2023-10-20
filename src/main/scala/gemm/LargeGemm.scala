package gemm
import chisel3._
import chisel3.util._

// The LargeGemmControllerIO's port declaration. Detailed explanation of these ports can be found in the README
class LargeGemmControllerIO extends Bundle {
  val M_in = Input(UInt(8.W))
  val K_in = Input(UInt(8.W))
  val N_in = Input(UInt(8.W))
  val start_do = Input(Bool())
  val data_valid_o = Input(Bool())
  val data_valid_i = Input(Bool())

  val addr_a_in = Input(UInt(32.W))
  val addr_b_in = Input(UInt(32.W))
  val addr_c_in = Input(UInt(32.W))

  val gemm_read_valid = Output(Bool())
  val gemm_write_valid = Output(Bool())

  val addr_a_out = Output(UInt(32.W))
  val addr_b_out = Output(UInt(32.W))
  val addr_c_out = Output(UInt(32.W))

  val gemm_busy = Output(Bool())
  val accumulate = Output(Bool())
}

// LargeGemmController module. This module takes in the configurations and gives out read/write valid signals and the right addresses of the sub-matrices.
class LargeGemmController extends Module {
  val io = IO(new LargeGemmControllerIO())

  val M = RegInit(0.U(8.W))
  val K = RegInit(0.U(8.W))
  val N = RegInit(0.U(8.W))

  // Counters for tracing the block matrix multiplication and generating the right addresses
  val M_read_counter = RegInit(0.U(8.W))
  val N_read_counter = RegInit(0.U(8.W))
  val K_read_counter = RegInit(0.U(8.W))

  val M_write_counter_next = RegInit(0.U(8.W))
  val N_write_counter_next = RegInit(0.U(8.W))
  val K_write_counter_next = RegInit(0.U(8.W))

  val M_write_counter = RegInit(0.U(8.W))
  val N_write_counter = RegInit(0.U(8.W))
  val K_write_counter = RegInit(0.U(8.W))

  val read_tcdm_done = WireInit(false.B)
  val gemm_done = WireInit(false.B)

  val write_counter = RegInit(0.U(8.W))
  val accumulate_counter = RegInit(0.U(8.W))

  // Statemeny declaration
  val sIDLE :: sREAD :: sREAD_DONE :: Nil = Enum(3)
  val cstate = RegInit(sIDLE)
  val nstate = WireInit(sIDLE)

  // Changing states
  cstate := nstate

  // Next state changes
  switch(cstate) {
    is(sIDLE) {
      when(io.start_do) {
        nstate := sREAD
      }.otherwise {
        nstate := sIDLE
      }
    }
    is(sREAD) {
      when(read_tcdm_done) {
        nstate := sREAD_DONE
      }.otherwise {
        nstate := sREAD
      }
    }
    is(sREAD_DONE) {
      when(gemm_done) {
        nstate := sIDLE
      }.otherwise {
        nstate := sREAD_DONE
      }
    }
  }

  // Store the configurations
  when(io.start_do && !io.gemm_busy) {
    M := io.M_in
    N := io.N_in
    K := io.K_in
  }.elsewhen(cstate === sIDLE) {
    M := 0.U
    N := 0.U
    K := 0.U
  }

  // K read counter
  when(
    (io.start_do === 1.B) && K_read_counter =/= (io.K_in - 1.U)
  ) {
    K_read_counter := K_read_counter + 1.U
  }.elsewhen(
    io.data_valid_i === 1.B && cstate === sREAD && K_read_counter =/= (K - 1.U)
  ) {
    K_read_counter := K_read_counter + 1.U
  }.elsewhen(
    io.data_valid_i === 1.B && K_read_counter === (K - 1.U)
  ) {
    K_read_counter := 0.U
  }

  // N read counter
  when(
    ((io.data_valid_i === 1.B && cstate === sREAD)) && K_read_counter === (K - 1.U) && N_read_counter =/= (N - 1.U) && K =/= 1.U
  ) {
    N_read_counter := N_read_counter + 1.U
  }.elsewhen(
    (io.start_do === 1.B) && K_read_counter === (io.K_in - 1.U) && N_read_counter =/= (io.N_in - 1.U) && io.K_in =/= 1.U
  ) {
    N_read_counter := N_read_counter + 1.U
  }.elsewhen(
    ((io.data_valid_i === 1.B && cstate === sREAD && K === 1.U)) && N_read_counter =/= (N - 1.U)
  ) {
    N_read_counter := N_read_counter + 1.U
  }.elsewhen(
    (io.start_do === 1.B) && io.K_in === 1.U && N_read_counter =/= (io.N_in - 1.U)
  ) {
    N_read_counter := N_read_counter + 1.U
  }.elsewhen(
    io.data_valid_i === 1.B && K_read_counter === (K - 1.U) && N_read_counter === (N - 1.U)
  ) {
    N_read_counter := 0.U
  }

  // M read counter
  when(
    ((io.start_do === 1.B) || (io.data_valid_i === 1.B && cstate === sREAD)) && K_read_counter === (K - 1.U) && N_read_counter === (N - 1.U) && M_read_counter =/= (M - 1.U) && K =/= 1.U && N =/= 1.U
  ) {
    M_read_counter := M_read_counter + 1.U
  }.elsewhen(
    ((io.start_do === 1.B && io.K_in === 1.U) || (io.data_valid_i === 1.B && cstate === sREAD && K === 1.U)) && N_read_counter === (N - 1.U) && M_read_counter =/= (M - 1.U) && N =/= 1.U
  ) {
    M_read_counter := M_read_counter + 1.U
  }.elsewhen(
    ((io.start_do === 1.B && io.N_in === 1.U) || (io.data_valid_i === 1.B && cstate === sREAD && N === 1.U)) && K_read_counter === (K - 1.U) && M_read_counter =/= (M - 1.U) && K =/= 1.U
  ) {
    M_read_counter := M_read_counter + 1.U
  }.elsewhen(
    ((io.start_do === 1.B && io.K_in === 1.U && io.N_in === 1.U) || (io.data_valid_i === 1.B && cstate === sREAD && K === 1.U && N === 1.U)) && M_read_counter =/= (M - 1.U)
  ) {
    M_read_counter := M_read_counter + 1.U
  }.elsewhen(
    io.data_valid_i === 1.B && K_read_counter === (K - 1.U) && N_read_counter === (N - 1.U) && M_read_counter === (M - 1.U)
  ) {
    M_read_counter := 0.U
  }

  // K write counter
  when(
    (io.data_valid_o === 1.B) && (cstate === sREAD || cstate === sREAD_DONE) && K_write_counter_next =/= (K - 1.U)
  ) {
    K_write_counter_next := K_write_counter_next + 1.U
  }.elsewhen(
    (io.data_valid_o === 1.B && K_write_counter_next === (K - 1.U)) || cstate === sIDLE
  ) {
    K_write_counter_next := 0.U
  }

  // N write counter
  when(
    io.data_valid_o === 1.B && K_write_counter_next === (K - 1.U) && N_write_counter_next =/= (N - 1.U) && K =/= 1.U
  ) {
    N_write_counter_next := N_write_counter_next + 1.U
  }.elsewhen(
    io.data_valid_o === 1.B && (cstate === sREAD || cstate === sREAD_DONE) && N_write_counter_next =/= (N - 1.U) && K === 1.U
  ) {
    N_write_counter_next := N_write_counter_next + 1.U
  }.elsewhen(
    io.data_valid_o === 1.B && K_write_counter_next === (K - 1.U) && N_write_counter_next === (N - 1.U)
  ) {
    N_write_counter_next := 0.U
  }

  // M write counter in the next cycle
  when(
    io.data_valid_o === 1.B && K_write_counter_next === (K - 1.U) && N_write_counter_next === (N - 1.U) && M_write_counter_next =/= (M - 1.U) && K =/= 1.U && N =/= 1.U && (cstate === sREAD || cstate === sREAD_DONE)
  ) {
    M_write_counter_next := M_write_counter_next + 1.U
  }.elsewhen(
    io.data_valid_o === 1.B && N_write_counter_next === (N - 1.U) && M_write_counter_next =/= (M - 1.U) && K === 1.U && N =/= 1.U && (cstate === sREAD || cstate === sREAD_DONE)
  ) {
    M_write_counter_next := M_write_counter_next + 1.U
  }.elsewhen(
    io.data_valid_o === 1.B && K_write_counter_next === (K - 1.U) && M_write_counter_next =/= (M - 1.U) && K =/= 1.U && N === 1.U && (cstate === sREAD || cstate === sREAD_DONE)
  ) {
    M_write_counter_next := M_write_counter_next + 1.U
  }.elsewhen(
    io.data_valid_o === 1.B && M_write_counter_next =/= (M - 1.U) && K === 1.U && N === 1.U && (cstate === sREAD || cstate === sREAD_DONE)
  ) {
    M_write_counter_next := M_write_counter_next + 1.U
  }.elsewhen(
    io.data_valid_o === 1.B && K_write_counter_next === (K - 1.U) && N_write_counter_next === (N - 1.U) && M_write_counter_next === (M - 1.U)
  ) {
    M_write_counter_next := 0.U
  }

  // write_counter for the address generation to write submatrix of C
  when(
    io.data_valid_o && write_counter =/= K && cstate =/= sIDLE
  ) {
    write_counter := write_counter + 1.U
  }.elsewhen(
    io.data_valid_o && write_counter === K && cstate =/= sIDLE
  ) {
    write_counter := 1.U
  }.elsewhen(write_counter === K || cstate === sIDLE) {
    write_counter := 0.U
  }

  // accumulate_counter for geenrating the accumulation signal for BaseGemm
  when(
    io.data_valid_i && accumulate_counter =/= K - 1.U && K =/= 1.U && cstate =/= sIDLE
  ) {
    accumulate_counter := accumulate_counter + 1.U
  }.elsewhen(
    io.data_valid_i && accumulate_counter === K - 1.U && cstate =/= sIDLE
  ) {
    accumulate_counter := 0.U
  }.elsewhen(
    write_counter === K - 1.U || cstate === sIDLE
  ) {
    accumulate_counter := 0.U
  }

  // Update the write_counters next cycle after io.data_valid_o === 1.B || gemm_done === 1.B
  M_write_counter := Mux(
    io.data_valid_o === 1.B || gemm_done === 1.B,
    M_write_counter_next,
    M_write_counter
  )
  K_write_counter := Mux(
    io.data_valid_o === 1.B || gemm_done === 1.B,
    K_write_counter_next,
    K_write_counter
  )
  N_write_counter := Mux(
    io.data_valid_o === 1.B || gemm_done === 1.B,
    N_write_counter_next,
    N_write_counter
  )

  // Intermediate or output control signals generation according to the counters
  read_tcdm_done := (M_read_counter === (M - 1.U)) && (N_read_counter === (N - 1.U)) && (K_read_counter === (K - 1.U))
  io.gemm_read_valid := (io.start_do === 1.B) || (io.data_valid_i && cstate === sREAD)
  io.gemm_write_valid := (write_counter === K) && cstate =/= sIDLE

  gemm_done := (M_write_counter === (M - 1.U)) && (N_write_counter === (N - 1.U)) && (K_write_counter === (K - 1.U))
  io.gemm_busy := (cstate =/= sIDLE)
  io.accumulate := (accumulate_counter =/= K - 1.U && io.data_valid_i === 1.B)

  // Address generation
  io.addr_a_out := io.addr_a_in + (GEMMConstant.InputMatrixBaseAddrA.U) * (M_read_counter * K + K_read_counter)
  io.addr_b_out := io.addr_b_in + (GEMMConstant.InputMatrixBaseAddrB.U) * (N_read_counter * K + K_read_counter)
  io.addr_c_out := io.addr_c_in + (GEMMConstant.OnputMatrixBaseAddrC.U) * (M_write_counter * N + N_write_counter)

}

// The LargeGemm's control port declaration. Detailed explanation of these ports can be found in the README
class LargeGemmCtrlIO extends Bundle {
  val M_in = Input(UInt(8.W))
  val K_in = Input(UInt(8.W))
  val N_in = Input(UInt(8.W))

  val start_do = Input(Bool())
  val data_valid_i = Input(Bool())

  val addr_a_in = Input(UInt(32.W))
  val addr_b_in = Input(UInt(32.W))
  val addr_c_in = Input(UInt(32.W))

  val gemm_read_valid = Output(Bool())
  val gemm_write_valid = Output(Bool())

  val addr_a_out = Output(UInt(32.W))
  val addr_b_out = Output(UInt(32.W))
  val addr_c_out = Output(UInt(32.W))

  val gemm_busy = Output(Bool())
}

// LargeGemmIO definition
class LargeGemmIO extends Bundle {
  val data = new GemmDataIO()
  val ctrl = new LargeGemmCtrlIO()
}

// LargeGemm module.
// In this module, a GemmArray is generated to do the computation and
// a controller is generated to generate the control signals for
// read/write reqeust and related address
class LargeGemm extends Module {
  val io = IO(new LargeGemmIO())

  val gemm_array = Module(new GemmArray())
  val controller = Module(new LargeGemmController())

  controller.io.M_in <> io.ctrl.M_in
  controller.io.K_in <> io.ctrl.K_in
  controller.io.N_in <> io.ctrl.N_in
  controller.io.start_do <> io.ctrl.start_do
  controller.io.data_valid_i <> io.ctrl.data_valid_i
  controller.io.addr_a_in <> io.ctrl.addr_a_in
  controller.io.addr_b_in <> io.ctrl.addr_b_in
  controller.io.addr_c_in <> io.ctrl.addr_c_in
  controller.io.gemm_read_valid <> io.ctrl.gemm_read_valid
  controller.io.gemm_write_valid <> io.ctrl.gemm_write_valid
  controller.io.addr_a_out <> io.ctrl.addr_a_out
  controller.io.addr_b_out <> io.ctrl.addr_b_out
  controller.io.addr_c_out <> io.ctrl.addr_c_out
  controller.io.gemm_busy <> io.ctrl.gemm_busy
  controller.io.data_valid_o := gemm_array.io.data_valid_o

  gemm_array.io.data.a_i <> io.data.a_i
  gemm_array.io.data.b_i <> io.data.b_i
  io.data.c_o <> RegNext(gemm_array.io.data.c_o)

  gemm_array.io.data_valid_i := io.ctrl.data_valid_i
  gemm_array.io.accumulate_i := controller.io.accumulate
}