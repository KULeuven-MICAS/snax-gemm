package gemm
import chisel3._
import chisel3.util._

// BatchGemmControllerIO 's port declaration.
// Detailed explanation of these ports can be found in the README.
// BatchGemmControllerIO inherits from BlockGemmControllerIO
class BatchGemmControllerIO extends BlockGemmControllerIO {
  val B_i = Input(UInt(8.W))
  val ldA_i = Input(UInt(8.W))
  val ldB_i = Input(UInt(8.W))
  val ldC_i = Input(UInt(8.W))
  val strideA_i = Input(UInt(8.W))
  val strideB_i = Input(UInt(8.W))
  val strideC_i = Input(UInt(8.W))

}

// BatchGemmController module. This module takes in the configurations
// and gives out read/write valid signals and the right addresses of the sub-matrices in each batch.
// BatchGemmController inherits from BlockGemmController
class BatchGemmController extends BlockGemmController {
  // override the io
  override lazy val io = IO(new BatchGemmControllerIO())

  // Registers to store the configurations
  val B = RegInit(0.U(8.W))

  val ldA = RegInit(0.U(8.W))
  val ldB = RegInit(0.U(8.W))
  val ldC = RegInit(0.U(8.W))

  val strideA = RegInit(0.U(8.W))
  val strideB = RegInit(0.U(8.W))
  val strideC = RegInit(0.U(8.W))

  // Counters for tracing the batch
  val Batch_read_counter = WireInit(0.U(8.W))
  val Batch_write_counter = WireInit(0.U(8.W))

  // Signal for start a new batch
  val start_batch = WireInit(false.B)

  //  Signals indicating one batch is done
  val read_tcdm_done_once = WireInit(false.B)
  val gemm_done_once = WireInit(false.B)

  // Store the configurations when io.start_do_i && !io.busy_o
  when(io.start_do_i && !io.busy_o) {
    B := io.B_i
    strideA := io.strideA_i
    strideB := io.strideB_i
    strideC := io.strideC_i
    ldA := io.ldA_i
    ldB := io.ldB_i
    ldC := io.ldC_i
  }.elsewhen(cstate === sIDLE) {
    B := 0.U
    strideA := 0.U
    strideB := 0.U
    strideC := 0.U
    ldA := 0.U
    ldB := 0.U
    ldC := 0.U
  }

  // Counters for generating the right addresses for block matrix multiplication
  // with the consideration of batch
  M_read_counter := (read_counter - Batch_read_counter * M * K * N) / (K * N)
  K_read_counter := ((read_counter - Batch_read_counter * M * K * N) % (K * N)) % K
  N_read_counter := ((read_counter - Batch_read_counter * M * K * N) % (K * N)) / K

  M_write_counter := ((write_counter - Batch_write_counter * M * K * N) / K) / N
  K_write_counter := (write_counter - Batch_write_counter * M * K * N) % K
  N_write_counter := ((write_counter - Batch_write_counter * M * K * N) / K) % N

  Batch_read_counter := Mux(cstate =/= sIDLE, (read_counter) / (M * K * N), 0.U)
  Batch_write_counter := Mux(
    cstate =/= sIDLE,
    (write_counter) / (M * K * N),
    0.U
  )

  // Intermediate or output control signals generation according to the counters
  read_tcdm_done_once := (M_read_counter === (M - 1.U)) && (N_read_counter === (N - 1.U)) && (K_read_counter === (K - 1.U)) && (Batch_read_counter =/= B - 1.U) && cstate =/= sIDLE && io.gemm_read_valid_o
  read_tcdm_done := (M_read_counter === (M - 1.U)) && (N_read_counter === (N - 1.U)) && (K_read_counter === (K - 1.U)) && (Batch_read_counter === B - 1.U) && cstate =/= sIDLE && io.gemm_read_valid_o

  gemm_done := (M_write_counter === (M - 1.U)) && (N_write_counter === (N - 1.U)) && (K_write_counter === (K - 1.U)) && (Batch_write_counter === B - 1.U) && cstate =/= sIDLE && io.gemm_write_valid_o
  gemm_done_once := (M_write_counter === (M - 1.U)) && (N_write_counter === (N - 1.U)) && (K_write_counter === (K - 1.U)) && (Batch_write_counter =/= B - 1.U) && cstate =/= sIDLE && io.gemm_write_valid_o

  io.gemm_read_valid_o := (start_do === 1.B || start_batch === 1.B) || (io.data_valid_i && (cstate === sREAD))
  io.gemm_write_valid_o := (write_valid_counter === K) && cstate =/= sIDLE
  io.accumulate_i := (accumulate_counter =/= K - 1.U && io.data_valid_i === 1.B)

  start_batch := (M_read_counter === (0.U)) && (N_read_counter === (0.U)) && (K_read_counter === (0.U)) && (Batch_read_counter =/= B - 1.U) && cstate === sREAD

  io.busy_o := (cstate =/= sIDLE)

  // Address generation
  io.addr_a_o := io.ptr_addr_a_i + Batch_read_counter * strideA + M_read_counter * ldA + (GemmConstant.baseAddrIncrementA.U) * (K_read_counter)
  io.addr_b_o := io.ptr_addr_b_i + Batch_read_counter * strideB + N_read_counter * ldB + (GemmConstant.baseAddrIncrementB.U) * (K_read_counter)
  io.addr_c_o := io.ptr_addr_c_i + Batch_write_counter * strideC + M_write_counter * ldC + (GemmConstant.baseAddrIncrementC.U) * (N_write_counter)
}

// The BatchGemm's control port declaration.
// Detailed explanation of these ports can be found in the README
// BatchGemmCtrlIO inherits BlockGemmCtrlIO
class BatchGemmCtrlIO extends BlockGemmCtrlIO {
  val B_i = Input(UInt(8.W))
  val ldA_i = Input(UInt(8.W))
  val ldB_i = Input(UInt(8.W))
  val ldC_i = Input(UInt(8.W))
  val strideA_i = Input(UInt(8.W))
  val strideB_i = Input(UInt(8.W))
  val strideC_i = Input(UInt(8.W))

}

// BatchGemmIO definition
class BatchGemmIO extends BlockGemmIO {
  override val ctrl = new BatchGemmCtrlIO()
}

// BatchGemm module.
// In this module, a GemmArray is generated to do the computation and
// a controller is generated to generate the control signals for
// read/write request and related address
class BatchGemm extends BlockGemm {
  override lazy val io = IO(new BatchGemmIO())
  io.suggestName("io")

  override lazy val controller = Module(new BatchGemmController())

  controller.io.B_i <> io.ctrl.B_i

  controller.io.ldA_i <> io.ctrl.ldA_i
  controller.io.ldB_i <> io.ctrl.ldB_i
  controller.io.ldC_i <> io.ctrl.ldC_i

  controller.io.strideA_i <> io.ctrl.strideA_i
  controller.io.strideB_i <> io.ctrl.strideB_i
  controller.io.strideC_i <> io.ctrl.strideC_i

}
