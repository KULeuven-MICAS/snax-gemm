package gemm

import chisel3._
import chisel3.util._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._
import scala.util.Random

// A delay module to simulate the delay of the read responds
// Also save the delayed address for comparison with the golden address later
class GenDataValidAddrDelay extends Module with RequireAsyncReset {
  val io = IO(new Bundle {
    val data_valid_i = Input(Bool())
    val data_valid_o = Output(Bool())
    val addr_a_i = Input(UInt(GemmConstant.addrWidth.W))
    val addr_b_i = Input(UInt(GemmConstant.addrWidth.W))
    val addr_a_o = Output(UInt(GemmConstant.addrWidth.W))
    val addr_b_o = Output(UInt(GemmConstant.addrWidth.W))
  })

  val random = new Random
  val randomNumber = random.nextInt(5)

  io.data_valid_o := ShiftRegister(io.data_valid_i, randomNumber + 1)
  io.addr_a_o := ShiftRegister(io.addr_a_i, randomNumber + 1)
  io.addr_b_o := ShiftRegister(io.addr_b_i, randomNumber + 1)

}

// A BatchGemmTCDMWritePorts test wrapper with the read responds delay module
class GemmTestWrapper(TCDMWritePorts: Int)
    extends Module
    with RequireAsyncReset {

  val io = IO(new Bundle {
    val batch_gemm = new BatchGemmIO()
    val data_valid_i = Output(Bool())
    val addr_a_o = Output(UInt(GemmConstant.addrWidth.W))
    val addr_b_o = Output(UInt(GemmConstant.addrWidth.W))
  })

  val gemm = Module(new BatchGemmTCDMWritePorts(TCDMWritePorts))
  val gen_data_valid_addr_delay = Module(new GenDataValidAddrDelay())

  io.batch_gemm <> gemm.io

  gen_data_valid_addr_delay.io.data_valid_i <> gemm.io.ctrl.gemm_read_valid_o
  gen_data_valid_addr_delay.io.data_valid_o <> gemm.io.ctrl.data_valid_i

  gen_data_valid_addr_delay.io.addr_a_i <> gemm.io.ctrl.addr_a_o
  gen_data_valid_addr_delay.io.addr_b_i <> gemm.io.ctrl.addr_b_o

  io.data_valid_i <> gen_data_valid_addr_delay.io.data_valid_o
  io.addr_a_o <> gen_data_valid_addr_delay.io.addr_a_o
  io.addr_b_o <> gen_data_valid_addr_delay.io.addr_b_o

}

// A trait with basic test function to be used in different test
trait AbstractGemmTestWrapperBaseTest {
  // A test function for testing if the BatchGemmTCDMWritePorts works well
  // under specific TCDMWritePorts and matrix size parameters
  def GemmTestWrapperTest[T <: GemmTestWrapper](
      dut: T,
      size_Batch: Int,
      size_M: Int,
      size_K: Int,
      size_N: Int
  ) = {

    // Counters for tracing the address in golden model
    var current_write_batch = 0
    var write_counter = 0
    var M_write_counter = 0
    var N_write_counter = 0

    var current_read_batch = 0
    var read_counter = 0
    var M_read_counter = 0
    var N_read_counter = 0
    var K_read_counter = 0

    // println("start batch gemm", size_Batch, size_M, size_K, size_N)

    // Randomly generation of input matrices
    val (matrix_A, matrix_B) =
      MatrixLibBatch.GenBatchBlockMatrix(
        size_Batch,
        size_M,
        size_K,
        size_N
      )
    // println(matrix_A.size, matrix_B.size)

    // Convert the sub-matrices to a big bus
    val (split_matrix_A, split_matrix_B) =
      MatrixLibBatch.SplitBatchBlockMatrx(
        size_Batch,
        size_M,
        size_K,
        size_N,
        matrix_A,
        matrix_B
      )
    val split_matrix_C = Array.ofDim[BigInt](size_Batch, size_M * size_N)

    // Random generation of the matrices start address and strides
    val (start_addr_A, start_addr_B, start_addr_C) =
      MatrixLibBlock.GenRandSizeTest()
    // val (ld_addr_A, ld_addr_B, ld_addr_C) = (0, 0, 0)
    val (ld_addr_A, ld_addr_B, ld_addr_C) = MatrixLibBlock.GenRandSizeTest()
    val (stride_addr_A, stride_addr_B, stride_addr_C) =
      MatrixLibBlock.GenRandSizeTest()
    val strideinnermost_A = GemmConstant.baseAddrIncrementA
    val strideinnermost_B = GemmConstant.baseAddrIncrementB
    val strideinnermost_C = GemmConstant.baseAddrIncrementC
    // println(split_matrix_A.size, split_matrix_B.size)

    // Generation of golden result in Scala
    val golden_array =
      MatrixLibBatch.BatchGemm(
        size_Batch,
        size_M,
        size_K,
        size_N,
        matrix_A,
        matrix_B
      )

    // If the gemm_write_valid_o is asserted, take out the c_o data for check
    def CheckOutput() = {
      if (dut.io.batch_gemm.ctrl.gemm_write_valid_o.peekBoolean()) {
        M_write_counter =
          (write_counter - current_write_batch * size_M * size_N) / size_N
        N_write_counter =
          (write_counter - current_write_batch * size_M * size_N) % size_N
        val addr_slide_C = M_write_counter * size_N + N_write_counter
        assert(
          dut.io.batch_gemm.ctrl.addr_c_o.peekInt() ==
            start_addr_C + current_write_batch * stride_addr_C + M_write_counter * ld_addr_C + strideinnermost_C * N_write_counter
        )

        split_matrix_C(current_write_batch)(addr_slide_C.toInt) =
          dut.io.batch_gemm.data.c_o.peekInt()
        // println(M_write_counter, N_write_counter)
        // println("write", current_write_batch, write_counter, addr_slide_C)

        // Update write counters
        write_counter = write_counter + 1
        if (write_counter % (size_M * size_N) == 0) {
          current_write_batch = current_write_batch + 1
        }
      }
    }

    // Give the Batch stride gemm configuration
    dut.clock.step(5)
    dut.io.batch_gemm.ctrl.start_do_i.poke(false.B)
    dut.clock.step(5)
    dut.io.batch_gemm.ctrl.start_do_i.poke(true.B)

    dut.io.batch_gemm.ctrl.Batch_i.poke(size_Batch)
    dut.io.batch_gemm.ctrl.M_i.poke(size_M)
    dut.io.batch_gemm.ctrl.K_i.poke(size_K)
    dut.io.batch_gemm.ctrl.N_i.poke(size_N)

    dut.io.batch_gemm.ctrl.ptr_addr_a_i.poke(start_addr_A)
    dut.io.batch_gemm.ctrl.ptr_addr_b_i.poke(start_addr_B)
    dut.io.batch_gemm.ctrl.ptr_addr_c_i.poke(start_addr_C)

    dut.io.batch_gemm.ctrl.strideinnermost_A_i.poke(strideinnermost_A)
    dut.io.batch_gemm.ctrl.strideinnermost_B_i.poke(strideinnermost_B)
    dut.io.batch_gemm.ctrl.strideinnermost_C_i.poke(strideinnermost_C)

    dut.io.batch_gemm.ctrl.ldA_i.poke(ld_addr_A)
    dut.io.batch_gemm.ctrl.ldB_i.poke(ld_addr_B)
    dut.io.batch_gemm.ctrl.ldC_i.poke(ld_addr_C)

    dut.io.batch_gemm.ctrl.strideA_i.poke(stride_addr_A)
    dut.io.batch_gemm.ctrl.strideB_i.poke(stride_addr_B)
    dut.io.batch_gemm.ctrl.strideC_i.poke(stride_addr_C)

    dut.clock.step(1)
    dut.io.batch_gemm.ctrl.start_do_i.poke(false.B)

    while (dut.io.batch_gemm.ctrl.busy_o.peekBoolean()) {

      // If data_valid_i is asserted, give the right a_i and b_i data according to the address
      if (dut.io.data_valid_i.peekBoolean()) {
        current_read_batch = read_counter / (size_M * size_K * size_N)
        M_read_counter =
          (read_counter - current_read_batch * size_M * size_K * size_N) / (size_N * size_K)
        K_read_counter =
          ((read_counter - current_read_batch * size_M * size_K * size_N) % (size_N * size_K)) % size_K
        N_read_counter =
          ((read_counter - current_read_batch * size_M * size_K * size_N) % (size_N * size_K)) / size_K

        // println(
        //   read_counter,
        //   current_read_batch,
        //   M_read_counter,
        //   K_read_counter,
        //   N_read_counter
        // )

        val addr_slide_A = M_read_counter * size_K + K_read_counter
        val addr_slide_B = N_read_counter * size_K + K_read_counter

        // Check if the output address matches the address in the golden model
        // dut.io.addr_a_o.peekInt() is delayed by the same cycle as data_valid_i
        assert(
          dut.io.addr_a_o.peekInt() ==
            start_addr_A + M_read_counter * ld_addr_A + stride_addr_A * current_read_batch + strideinnermost_A * K_read_counter
        )
        assert(
          dut.io.addr_b_o.peekInt() ==
            start_addr_B + N_read_counter * ld_addr_B + stride_addr_B * current_read_batch + strideinnermost_B * K_read_counter
        )

        // Give the right a_i and b_i data according to the address
        // println("read", addr_slide_A, addr_slide_B)
        dut.io.batch_gemm.data.a_i
          .poke(split_matrix_A(current_read_batch)(addr_slide_A.toInt))
        dut.io.batch_gemm.data.b_i
          .poke(split_matrix_B(current_read_batch)(addr_slide_B.toInt))

        read_counter = read_counter + 1

      }

      CheckOutput()
      dut.clock.step(1)

    }

    dut.clock.step(2)

    // Compare the output data with the golden model
    MatrixLibBatch.CheckResults(
      size_Batch,
      size_M * size_N,
      split_matrix_C,
      golden_array
    )

    dut.clock.step(5)
    println("One Batch Gemm test finished!")

  }

  // A test wrapper for testing different TCDMWritePorts parameter with same matrix size
  def TestWrapper[T <: GemmTestWrapper](dut: T) = {

    GemmTestWrapperTest(dut, 2, 1, 1, 1)

    GemmTestWrapperTest(dut, 2, 2, 1, 2)
    GemmTestWrapperTest(dut, 2, 1, 2, 2)
    GemmTestWrapperTest(dut, 2, 2, 2, 1)

    GemmTestWrapperTest(dut, 2, 1, 1, 2)
    GemmTestWrapperTest(dut, 2, 1, 2, 2)
    GemmTestWrapperTest(dut, 2, 2, 2, 1)

    GemmTestWrapperTest(dut, 2, 2, 2, 2)

    GemmTestWrapperTest(dut, 2, 2, 3, 2)
  }
}

// Testing different TCDMWritePorts parameter with same matrix size
class GemmTestWrapperBaseTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with AbstractGemmTestWrapperBaseTest {
  "DUT" should "pass" in {
    var TCDMWritePorts = 8
    test(new GemmTestWrapper(TCDMWritePorts))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)

        TestWrapper(dut)

        emitVerilog(
          new GemmTestWrapper(TCDMWritePorts),
          Array("--target-dir", "generated/gemm")
        )

      }

    TCDMWritePorts = 16
    test(new GemmTestWrapper(TCDMWritePorts))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)

        TestWrapper(dut)

        emitVerilog(
          new GemmTestWrapper(TCDMWritePorts),
          Array("--target-dir", "generated/gemm")
        )

      }

    TCDMWritePorts = 32
    test(new GemmTestWrapper(TCDMWritePorts))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)

        TestWrapper(dut)

        emitVerilog(
          new GemmTestWrapper(TCDMWritePorts),
          Array("--target-dir", "generated/gemm")
        )

      }

  }
}

// Testing random TCDMWritePorts number with random matrix size
class GemmTestWrapperRandomTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with AbstractGemmTestWrapperBaseTest {
  "DUT" should "pass" in {

    var TCDMWritePorts = 8
    val TCDMWritePortsCandidate = List(8, 16, 32)

    for (i <- 0 until TestParameters.GemmTestWrapperRandomTest_TestLoop) {

      // For generating random TCDMWritePorts number
      val random = new Random
      val randomIndex = random.nextInt(TCDMWritePortsCandidate.length)
      TCDMWritePorts = TCDMWritePortsCandidate(randomIndex)

      test(new GemmTestWrapper(TCDMWritePorts))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
          // Randomly generate the batch number and size of the input matrices
          val (size_Batch, size_M, size_K, size_N) =
            MatrixLibBatch.genRandSizeandBatchTest()
          GemmTestWrapperTest(dut, size_Batch, size_M, size_K, size_N)
        }
    }
  }
}

// Testing specific TCDMWritePorts number and specif K setting to
// see if BatchGemmTCDMWritePorts works well
// These tests have special TCDMWritePorts number and K setting
class BatchGemmTCDMWritePortsCornerCaseTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with AbstractGemmTestWrapperBaseTest {
  "DUT" should "pass" in {
    var TCDMWritePorts = 8
    test(new GemmTestWrapper(TCDMWritePorts))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)

        GemmTestWrapperTest(dut, 2, 1, 1, 1)
        GemmTestWrapperTest(dut, 2, 1, 2, 1)
        GemmTestWrapperTest(dut, 2, 1, 3, 1)
        GemmTestWrapperTest(dut, 2, 1, 4, 1)

        emitVerilog(
          new GemmTestWrapper(TCDMWritePorts),
          Array("--target-dir", "generated/gemm")
        )

      }

    TCDMWritePorts = 16
    test(new GemmTestWrapper(TCDMWritePorts))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)

        GemmTestWrapperTest(dut, 2, 1, 1, 1)
        GemmTestWrapperTest(dut, 2, 1, 2, 1)

        emitVerilog(
          new GemmTestWrapper(TCDMWritePorts),
          Array("--target-dir", "generated/gemm")
        )

      }

  }
}

// This test is for debugging
// Using specific settings to check if the stall works well
// And also orchestrating the data_valid_i after read request valid to see if the Gemm works well under different situations
// Finally checking the output manually in the waveform
class BatchGemmTCDMWritePortsManualTest
    extends AnyFlatSpec
    with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new BatchGemmTCDMWritePorts(8))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(5)
        dut.io.data.a_i.poke(1.U)
        dut.io.data.b_i.poke(1.U)

        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(2)
        dut.io.ctrl.K_i.poke(3)
        // dut.io.ctrl.K_i.poke(2)
        // dut.io.ctrl.K_i.poke(1)
        dut.io.ctrl.N_i.poke(2)
        dut.io.ctrl.Batch_i.poke(2)

        dut.io.ctrl.ldA_i.poke(0x10)
        dut.io.ctrl.ldB_i.poke(0x10)
        dut.io.ctrl.ldC_i.poke(0x10)

        dut.io.ctrl.strideA_i.poke(0xf0)
        dut.io.ctrl.strideB_i.poke(0xf0)
        dut.io.ctrl.strideC_i.poke(0xf0)

        dut.io.ctrl.ptr_addr_a_i.poke(2)
        dut.io.ctrl.ptr_addr_b_i.poke(3)
        dut.io.ctrl.ptr_addr_c_i.poke(4)

        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(false.B)

        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(5)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(false.B)

        dut.clock.step(3)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(false.B)

        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(3)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(false.B)

        dut.clock.step(5)

        emitVerilog(
          new BatchGemmTCDMWritePorts(8),
          Array("--target-dir", "generated/gemm")
        )
      }
  }
}
