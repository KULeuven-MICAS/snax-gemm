package gemm
import chisel3._
import chisel3.util._

// The BlockGemmControllerIO's port declaration. Detailed explanation of these ports can be found in the README
class BlockGemmControllerIO extends Bundle {
  val M_i = Input(UInt(GemmConstant.sizeConfigWidth.W))
  val K_i = Input(UInt(GemmConstant.sizeConfigWidth.W))
  val N_i = Input(UInt(GemmConstant.sizeConfigWidth.W))
  val subtraction_a_i = Input(UInt(GemmConstant.dataWidthA.W))
  val subtraction_b_i = Input(UInt(GemmConstant.dataWidthB.W))
  val start_do_i = Input(Bool())
  val data_valid_o = Input(Bool())
  val data_valid_i = Input(Bool())

  val ptr_addr_a_i = Input(UInt(GemmConstant.addrWidth.W))
  val ptr_addr_b_i = Input(UInt(GemmConstant.addrWidth.W))
  val ptr_addr_c_i = Input(UInt(GemmConstant.addrWidth.W))

  val gemm_read_valid_o = Output(Bool())
  val gemm_write_valid_o = Output(Bool())

  val addr_a_o = Output(UInt(GemmConstant.addrWidth.W))
  val addr_b_o = Output(UInt(GemmConstant.addrWidth.W))
  val addr_c_o = Output(UInt(GemmConstant.addrWidth.W))

  val subtraction_a_o = Output(UInt(GemmConstant.dataWidthA.W))
  val subtraction_b_o = Output(UInt(GemmConstant.dataWidthB.W))

  val busy_o = Output(Bool())
  val accumulate_i = Output(Bool())

  val perf_counter = Output(UInt(32.W))
}

// BlockGemmController module. This module takes in the configurations and gives out read/write valid signals and the right addresses of the sub-matrices.
class BlockGemmController extends Module with RequireAsyncReset {
  lazy val io = IO(new BlockGemmControllerIO())

  val start_do = RegInit(false.B)

  // Registers to store the configurations
  val M = RegInit(0.U(GemmConstant.sizeConfigWidth.W))
  val K = RegInit(0.U(GemmConstant.sizeConfigWidth.W))
  val N = RegInit(0.U(GemmConstant.sizeConfigWidth.W))
  val a = RegInit(0.U(GemmConstant.dataWidthA.W))
  val b = RegInit(0.U(GemmConstant.dataWidthB.W))

  val ptr_addr_a = RegInit(0.U(GemmConstant.addrWidth.W))
  val ptr_addr_b = RegInit(0.U(GemmConstant.addrWidth.W))
  val ptr_addr_c = RegInit(0.U(GemmConstant.addrWidth.W))

  // Counters for tracing the block matrix multiplication
  val read_counter = RegInit(0.U((3 * GemmConstant.sizeConfigWidth).W))
  val write_counter = RegInit(0.U((3 * GemmConstant.sizeConfigWidth).W))
  val perf_counter = RegInit(0.U(32.W))

  // Counters for generating the right addresses
  val M_read_counter = WireInit(0.U(GemmConstant.sizeConfigWidth.W))
  val N_read_counter = WireInit(0.U(GemmConstant.sizeConfigWidth.W))
  val K_read_counter = WireInit(0.U(GemmConstant.sizeConfigWidth.W))

  val M_write_counter = WireInit(0.U(GemmConstant.sizeConfigWidth.W))
  val N_write_counter = WireInit(0.U(GemmConstant.sizeConfigWidth.W))
  val K_write_counter = WireInit(0.U(GemmConstant.sizeConfigWidth.W))

  // Counters for write valid and accumulate signal generation
  val write_valid_counter = RegInit(0.U(GemmConstant.sizeConfigWidth.W))
  val accumulate_counter = RegInit(0.U(GemmConstant.sizeConfigWidth.W))

  val read_tcdm_done = WireInit(false.B)
  val gemm_done = WireInit(false.B)

  // State declaration
  val sIDLE :: sREAD :: sREAD_DONE :: Nil = Enum(3)
  val cstate = RegInit(sIDLE)
  val nstate = WireInit(sIDLE)

  // Changing states
  cstate := nstate

  // Next state changes according to three key signals: io.start_do_i, read_tcdm_done and gemm_done
  switch(cstate) {
    is(sIDLE) {
      when(io.start_do_i) {
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

  // Store the configurations when io.start_do_i && !io.busy_o
  when(io.start_do_i && !io.busy_o) {
    M := io.M_i
    N := io.N_i
    K := io.K_i
    a := io.subtraction_a_i
    b := io.subtraction_b_i
    ptr_addr_a := io.ptr_addr_a_i
    ptr_addr_b := io.ptr_addr_b_i
    ptr_addr_c := io.ptr_addr_c_i
    assert(
      io.M_i =/= 0.U && io.K_i =/= 0.U && io.K_i =/= 0.U,
      " M == 0 or K ==0 or N == 0, invalid configuration!"
    )
  }.elsewhen(cstate === sIDLE) {
    M := 0.U
    N := 0.U
    K := 0.U
    a := 0.U
    b := 0.U
    ptr_addr_a := 0.U
    ptr_addr_b := 0.U
    ptr_addr_c := 0.U
  }

  // Store the start_do_i signal when io.start_do_i && !io.busy_o for the first read valid
  when(io.start_do_i && !io.busy_o) {
    start_do := true.B
  }.otherwise {
    start_do := false.B
  }

  def loopCounterGen(
      workingState: Bool,
      increment: Bool,
      loopBounds: Seq[UInt]
  ): Vec[UInt] = {

    /** Generates a nested loop counter based on the provided parameters.
      */

    def temporalLoopDim = loopBounds.length
    val loop_counters = RegInit(
      VecInit(
        Seq.fill(temporalLoopDim)(
          0.U(GemmConstant.sizeConfigWidth.W)
        )
      )
    )
    val loop_counters_next = WireInit(
      VecInit(
        Seq.fill(temporalLoopDim)(
          0.U(GemmConstant.sizeConfigWidth.W)
        )
      )
    )
    val loop_counters_valid = WireInit(
      VecInit(Seq.fill(temporalLoopDim)(0.B))
    )
    val loop_counters_last = WireInit(
      VecInit(Seq.fill(temporalLoopDim)(0.B))
    )

    for (i <- 0 until temporalLoopDim) {
      // the next loop counter is the current loop counter plus 1
      loop_counters_next(i) := loop_counters(i) + 1.U
      // the loop counter reaches the last value when the
      // next loop counter equals the loop bound
      loop_counters_last(i) := loop_counters_next(i) === loopBounds(i)
    }

    loop_counters_valid(0) := increment
    for (i <- 1 until temporalLoopDim) {
      // every loop counter must be incremented when the previous loop counter
      // reaches the last value and is incremented
      loop_counters_valid(i) := loop_counters_last(
        i - 1
      ) && loop_counters_valid(i - 1)
    }

    when(workingState) {
      for (i <- 0 until temporalLoopDim) {
        when(loop_counters_valid(i)) {
          loop_counters(i) := Mux(
            loop_counters_last(i),
            0.U,
            loop_counters_next(i)
          )
        }.otherwise {
          loop_counters(i) := loop_counters(i)
        }
      }
    }.otherwise {
      for (i <- 0 until temporalLoopDim) {
        loop_counters(i) := 0.U
      }
    }
    loop_counters
  }

  // Counters for generating the right addresses for block matrix multiplication
  lazy val loopBounds = Seq(K, N, M)
  lazy val readLoopCounters =
    loopCounterGen(cstate =/= sIDLE, io.gemm_read_valid_o, loopBounds)
  K_read_counter := readLoopCounters(0)
  N_read_counter := readLoopCounters(1)
  M_read_counter := readLoopCounters(2)

  lazy val writeLoopCounters =
    loopCounterGen(cstate =/= sIDLE, io.data_valid_o, loopBounds)
  K_write_counter := writeLoopCounters(0)
  N_write_counter := writeLoopCounters(1)
  M_write_counter := writeLoopCounters(2)

  when(io.gemm_read_valid_o && io.busy_o) {
    read_counter := read_counter + 1.U
  }.elsewhen(!io.busy_o) {
    read_counter := 0.U
  }

  when(io.start_do_i && !io.busy_o) {
    perf_counter := 0.U
  }.elsewhen(io.busy_o =/= 0.U) {
    perf_counter := perf_counter + 1.U
  }

  // write_counter for the address generation to write submatrix of C
  when(
    io.data_valid_o && write_valid_counter =/= (K - 1.U) && cstate =/= sIDLE
  ) {
    write_valid_counter := write_valid_counter + 1.U
  }.elsewhen(
    io.data_valid_o && write_valid_counter === (K - 1.U) && cstate =/= sIDLE
  ) {
    write_valid_counter := 0.U
  }.elsewhen(cstate === sIDLE) {
    write_valid_counter := 0.U
  }

  // accumulate_counter for generating the accumulation signal for BaseGemm
  when(
    (io.data_valid_i === 1.B) && accumulate_counter =/= K - 1.U && K =/= 1.U && cstate =/= sIDLE
  ) {
    accumulate_counter := accumulate_counter + 1.U
  }.elsewhen(
    (io.data_valid_i === 1.B) && accumulate_counter === K - 1.U && cstate =/= sIDLE
  ) {
    accumulate_counter := 0.U
  }.elsewhen(cstate === sIDLE) {
    accumulate_counter := 0.U
  }

  // Intermediate or output control signals generation according to the counters
  read_tcdm_done := (M_read_counter === (M - 1.U)) && (N_read_counter === (N - 1.U)) && (K_read_counter === (K - 1.U)) && io.gemm_read_valid_o
  gemm_done := (M_write_counter === (M - 1.U)) && (N_write_counter === (N - 1.U)) && (K_write_counter === (K - 1.U)) && io.gemm_write_valid_o

  io.gemm_read_valid_o := (start_do === 1.B) || (io.data_valid_i && cstate === sREAD)
  io.gemm_write_valid_o := (write_valid_counter === K - 1.U) && io.data_valid_o && cstate =/= sIDLE
  io.accumulate_i := (accumulate_counter =/= 0.U && (io.data_valid_i === 1.B))

  io.busy_o := (cstate =/= sIDLE)

  // Address generation
  io.addr_a_o := ptr_addr_a + (GemmConstant.baseAddrIncrementA.U) * (M_read_counter * K + K_read_counter)
  io.addr_b_o := ptr_addr_b + (GemmConstant.baseAddrIncrementB.U) * (N_read_counter * K + K_read_counter)
  io.addr_c_o := ptr_addr_c + (GemmConstant.baseAddrIncrementC.U) * (M_write_counter * N + N_write_counter)

  io.perf_counter := perf_counter

  io.subtraction_a_o := a
  io.subtraction_b_o := b
}

// The BlockGemm's control port declaration. Detailed explanation of these ports can be found in the README
class BlockGemmCtrlIO extends Bundle {
  val M_i = Input(UInt(GemmConstant.sizeConfigWidth.W))
  val K_i = Input(UInt(GemmConstant.sizeConfigWidth.W))
  val N_i = Input(UInt(GemmConstant.sizeConfigWidth.W))
  val subtraction_a_i = Input(UInt(GemmConstant.dataWidthA.W))
  val subtraction_b_i = Input(UInt(GemmConstant.dataWidthB.W))

  val start_do_i = Input(Bool())
  val data_valid_i = Input(Bool())

  val ptr_addr_a_i = Input(UInt(GemmConstant.addrWidth.W))
  val ptr_addr_b_i = Input(UInt(GemmConstant.addrWidth.W))
  val ptr_addr_c_i = Input(UInt(GemmConstant.addrWidth.W))

  val gemm_read_valid_o = Output(Bool())
  val gemm_write_valid_o = Output(Bool())

  val addr_a_o = Output(UInt(GemmConstant.addrWidth.W))
  val addr_b_o = Output(UInt(GemmConstant.addrWidth.W))
  val addr_c_o = Output(UInt(GemmConstant.addrWidth.W))

  val busy_o = Output(Bool())

  val perf_counter = Output(UInt(32.W))
}

// BlockGemmIO definition
class BlockGemmIO extends Bundle {
  val data = new GemmDataIO()
  val ctrl = new BlockGemmCtrlIO()
}

// BlockGemm module.
// In this module, a GemmArray is generated to do the computation and
// a controller is generated to generate the control signals for
// read/write request and related address
class BlockGemm extends Module with RequireAsyncReset {
  lazy val io = IO(new BlockGemmIO())
  io.suggestName("io")

  val gemm_array = Module(new GemmArray())
  lazy val controller = Module(new BlockGemmController())

  // add ready signal, if not receive a ready signal, keep sending valid signal
  val gemm_write_ready_o = WireInit(false.B)
  val keep_gemm_write_valid_o = RegInit(false.B)
  val gemm_write_valid_o =
    (controller.io.gemm_write_valid_o || keep_gemm_write_valid_o)
  keep_gemm_write_valid_o := gemm_write_valid_o && !gemm_write_ready_o
  gemm_write_ready_o := 1.B
  dontTouch(keep_gemm_write_valid_o)

  // if not ready immediately when valid is asserted, need to save the addr_c
  // use the old addr_c when ready
  // addr_c is only right when valid is asserted from controller (one cycle)
  val addr_c_o = RegInit(0.U(GemmConstant.addrWidth.W))
  val block_gemm_addr_c_o = WireInit(0.U(GemmConstant.addrWidth.W))
  addr_c_o := Mux(
    controller.io.gemm_write_valid_o,
    controller.io.addr_c_o,
    addr_c_o
  )

  controller.io.M_i <> io.ctrl.M_i
  controller.io.K_i <> io.ctrl.K_i
  controller.io.N_i <> io.ctrl.N_i
  controller.io.subtraction_a_i <> io.ctrl.subtraction_a_i
  controller.io.subtraction_b_i <> io.ctrl.subtraction_b_i

  controller.io.start_do_i <> io.ctrl.start_do_i
  controller.io.data_valid_i <> io.ctrl.data_valid_i
  controller.io.ptr_addr_a_i <> io.ctrl.ptr_addr_a_i
  controller.io.ptr_addr_b_i <> io.ctrl.ptr_addr_b_i
  controller.io.ptr_addr_c_i <> io.ctrl.ptr_addr_c_i
  controller.io.gemm_read_valid_o <> io.ctrl.gemm_read_valid_o
  io.ctrl.gemm_write_valid_o := gemm_write_valid_o
  controller.io.addr_a_o <> io.ctrl.addr_a_o
  controller.io.addr_b_o <> io.ctrl.addr_b_o

  io.ctrl.addr_c_o := block_gemm_addr_c_o
  // when keep_gemm_write_valid_o is asserted, use old addr_c instead of current controller.io.addr_c_o
  block_gemm_addr_c_o := Mux(
    keep_gemm_write_valid_o,
    addr_c_o,
    controller.io.addr_c_o
  )
  controller.io.busy_o <> io.ctrl.busy_o
  controller.io.data_valid_o := gemm_array.io.c_valid_o
  controller.io.perf_counter <> io.ctrl.perf_counter

  gemm_array.io.data.a_i <> io.data.a_i
  gemm_array.io.data.b_i <> io.data.b_i

  gemm_array.io.subtraction_a_i <> controller.io.subtraction_a_o
  gemm_array.io.subtraction_b_i <> controller.io.subtraction_b_o

  io.data.c_o <> (gemm_array.io.data.c_o)

  gemm_array.io.a_b_valid_i := io.ctrl.data_valid_i
  gemm_array.io.accumulate_i := controller.io.accumulate_i
  gemm_array.io.c_ready_i := 1.B
}

object BlockGemm extends App {
  emitVerilog(
    new (BlockGemm),
    Array("--target-dir", "generated/gemm")
  )
}
