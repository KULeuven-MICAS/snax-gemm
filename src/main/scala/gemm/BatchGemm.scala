package gemm
import chisel3._
import chisel3.util._

// BatchGemmControllerIO 's port declaration.
// Detailed explanation of these ports can be found in the README.
// BatchGemmControllerIO inherits from BlockGemmControllerIO
class BatchGemmControllerIO extends BlockGemmControllerIO {

  val Batch_i = Input(UInt(GemmConstant.sizeConfigWidth.W))
  val strideinnermost_A_i = Input(UInt(GemmConstant.addrWidth.W))
  val strideinnermost_B_i = Input(UInt(GemmConstant.addrWidth.W))
  val strideinnermost_C_i = Input(UInt(GemmConstant.addrWidth.W))

  val ldA_i = Input(UInt(GemmConstant.addrWidth.W))
  val ldB_i = Input(UInt(GemmConstant.addrWidth.W))
  val ldC_i = Input(UInt(GemmConstant.addrWidth.W))
  val strideA_i = Input(UInt(GemmConstant.addrWidth.W))
  val strideB_i = Input(UInt(GemmConstant.addrWidth.W))
  val strideC_i = Input(UInt(GemmConstant.addrWidth.W))

}

// BatchGemmController module. This module takes in the configurations
// and gives out read/write valid signals and the right addresses of the sub-matrices in each batch.
// BatchGemmController inherits from BlockGemmController
class BatchGemmController extends BlockGemmController {
  // override the io
  override lazy val io = IO(new BatchGemmControllerIO())

  // Registers to store the configurations
  val Bacth = RegInit(0.U(GemmConstant.sizeConfigWidth.W))

  val strideinnermost_A = RegInit(0.U(GemmConstant.addrWidth.W))
  val strideinnermost_B = RegInit(0.U(GemmConstant.addrWidth.W))
  val strideinnermost_C = RegInit(0.U(GemmConstant.addrWidth.W))

  val ld_A = RegInit(0.U(GemmConstant.addrWidth.W))
  val ld_B = RegInit(0.U(GemmConstant.addrWidth.W))
  val ld_C = RegInit(0.U(GemmConstant.addrWidth.W))

  val stride_A = RegInit(0.U(GemmConstant.addrWidth.W))
  val stride_B = RegInit(0.U(GemmConstant.addrWidth.W))
  val stride_C = RegInit(0.U(GemmConstant.addrWidth.W))

  // Counters for tracing the batch
  val batch_read_counter = WireInit(0.U(GemmConstant.sizeConfigWidth.W))
  val batch_write_counter = WireInit(0.U(GemmConstant.sizeConfigWidth.W))

  // Signal for start a new batch
  val start_batch = WireInit(false.B)

  //  Signals indicating one batch is done
  val read_tcdm_done_once = WireInit(false.B)
  val gemm_done_once = WireInit(false.B)

  // Store the configurations when io.start_do_i && !io.busy_o
  when(io.start_do_i && !io.busy_o) {
    Bacth := io.Batch_i
    stride_A := io.strideA_i
    stride_B := io.strideB_i
    stride_C := io.strideC_i
    ld_A := io.ldA_i
    ld_B := io.ldB_i
    ld_C := io.ldC_i
    strideinnermost_A := io.strideinnermost_A_i
    strideinnermost_B := io.strideinnermost_B_i
    strideinnermost_C := io.strideinnermost_C_i
    assert(io.Batch_i =/= 0.U, "B == 0, invalid configuration!")
  }.elsewhen(cstate === sIDLE) {
    Bacth := 0.U
    stride_A := 0.U
    stride_B := 0.U
    stride_C := 0.U
    ld_A := 0.U
    ld_B := 0.U
    ld_C := 0.U
    strideinnermost_A := 0.U
    strideinnermost_B := 0.U
    strideinnermost_C := 0.U
  }

  // Counters for generating the right addresses for block matrix multiplication
  // with the consideration of batch
  M_read_counter := (read_counter - batch_read_counter * M * K * N) / (K * N)
  K_read_counter := ((read_counter - batch_read_counter * M * K * N) % (K * N)) % K
  N_read_counter := ((read_counter - batch_read_counter * M * K * N) % (K * N)) / K

  M_write_counter := ((write_counter - batch_write_counter * M * K * N) / K) / N
  K_write_counter := (write_counter - batch_write_counter * M * K * N) % K
  N_write_counter := ((write_counter - batch_write_counter * M * K * N) / K) % N

  batch_read_counter := Mux(cstate =/= sIDLE, (read_counter) / (M * K * N), 0.U)
  batch_write_counter := Mux(
    cstate =/= sIDLE,
    (write_counter) / (M * K * N),
    0.U
  )

  // Intermediate or output control signals generation according to the counters
  read_tcdm_done_once := (M_read_counter === (M - 1.U)) && (N_read_counter === (N - 1.U)) && (K_read_counter === (K - 1.U)) && (batch_read_counter =/= Bacth - 1.U) && cstate =/= sIDLE && io.gemm_read_valid_o
  read_tcdm_done := (M_read_counter === (M - 1.U)) && (N_read_counter === (N - 1.U)) && (K_read_counter === (K - 1.U)) && (batch_read_counter === Bacth - 1.U) && cstate =/= sIDLE && io.gemm_read_valid_o

  gemm_done := (M_write_counter === (M - 1.U)) && (N_write_counter === (N - 1.U)) && (K_write_counter === (K - 1.U)) && (batch_write_counter === Bacth - 1.U) && cstate =/= sIDLE && io.gemm_write_valid_o
  gemm_done_once := (M_write_counter === (M - 1.U)) && (N_write_counter === (N - 1.U)) && (K_write_counter === (K - 1.U)) && (batch_write_counter =/= Bacth - 1.U) && cstate =/= sIDLE && io.gemm_write_valid_o

  io.gemm_read_valid_o := (start_do === 1.B || start_batch === 1.B) || (io.data_valid_i && (cstate === sREAD))
  io.gemm_write_valid_o := (write_valid_counter === K - 1.U) && io.data_valid_o && cstate =/= sIDLE
  io.accumulate_i := (accumulate_counter =/= K - 1.U && io.data_valid_i === 1.B)

  start_batch := (M_read_counter === (0.U)) && (N_read_counter === (0.U)) && (K_read_counter === (0.U)) && (batch_read_counter =/= Bacth - 1.U) && cstate === sREAD

  io.busy_o := (cstate =/= sIDLE)

  // Address generation
  io.addr_a_o := ptr_addr_a + batch_read_counter * stride_A + M_read_counter * ld_A + strideinnermost_A * (K_read_counter)
  io.addr_b_o := ptr_addr_b + batch_read_counter * stride_B + N_read_counter * ld_B + strideinnermost_B * (K_read_counter)
  io.addr_c_o := ptr_addr_c + batch_write_counter * stride_C + M_write_counter * ld_C + strideinnermost_C * (N_write_counter)
}

// The BatchGemm's control port declaration.
// Detailed explanation of these ports can be found in the README
// BatchGemmCtrlIO inherits BlockGemmCtrlIO
class BatchGemmCtrlIO extends BlockGemmCtrlIO {
  val Batch_i = Input(UInt(GemmConstant.sizeConfigWidth.W))
  val strideinnermost_A_i = Input(UInt(GemmConstant.addrWidth.W))
  val strideinnermost_B_i = Input(UInt(GemmConstant.addrWidth.W))
  val strideinnermost_C_i = Input(UInt(GemmConstant.addrWidth.W))
  val ldA_i = Input(UInt(GemmConstant.addrWidth.W))
  val ldB_i = Input(UInt(GemmConstant.addrWidth.W))
  val ldC_i = Input(UInt(GemmConstant.addrWidth.W))
  val strideA_i = Input(UInt(GemmConstant.addrWidth.W))
  val strideB_i = Input(UInt(GemmConstant.addrWidth.W))
  val strideC_i = Input(UInt(GemmConstant.addrWidth.W))
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

  controller.io.Batch_i <> io.ctrl.Batch_i

  controller.io.ldA_i <> io.ctrl.ldA_i
  controller.io.ldB_i <> io.ctrl.ldB_i
  controller.io.ldC_i <> io.ctrl.ldC_i

  controller.io.strideA_i <> io.ctrl.strideA_i
  controller.io.strideB_i <> io.ctrl.strideB_i
  controller.io.strideC_i <> io.ctrl.strideC_i

  controller.io.strideinnermost_A_i <> io.ctrl.strideinnermost_A_i
  controller.io.strideinnermost_B_i <> io.ctrl.strideinnermost_B_i
  controller.io.strideinnermost_C_i <> io.ctrl.strideinnermost_C_i

}

object BatchGemm extends App {
  emitVerilog(
    new (BatchGemm),
    Array("--target-dir", "generated/gemm")
  )
}
