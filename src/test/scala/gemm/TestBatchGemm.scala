package gemm

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

object MatrixLibBatch {

  // Batch Gemm golden model
  def BatchGemm(
      Batch: Int,
      M: Int,
      K: Int,
      N: Int,
      A: Array[Array[Byte]],
      B: Array[Array[Byte]]
  ) = {
    val golden = Array
      .ofDim[BigInt](Batch, M * N * GemmConstant.meshRow * GemmConstant.meshCol)

    for (b <- 0 until Batch) {
      golden(b) = MatrixLibBlock.BlockMarixMul_1D(M, K, N, A(b), B(b))
    }

    golden
  }

  // Generate the random size and the random batch number
  def genRandSizeandBatchTest() = {
    val rand = new scala.util.Random

    val B = rand.between(1, TestParameters.MatrixLibBatch_random_B_range)
    val M = rand.between(1, TestParameters.MatrixLibBatch_random_M_range)
    val K = rand.between(1, TestParameters.MatrixLibBatch_random_K_range)
    val N = rand.between(1, TestParameters.MatrixLibBatch_random_N_range)

    (B, M, K, N)
  }

  // Generate the input matrix A and B with random data
  def GenBatchBlockMatrix(Batch: Int, M: Int, K: Int, N: Int) = {
    val A = Array
      .ofDim[Byte](Batch, M * K * GemmConstant.meshRow * GemmConstant.tileSize)
    val B = Array
      .ofDim[Byte](Batch, N * K * GemmConstant.meshCol * GemmConstant.tileSize)

    for (k <- 0 until Batch) {
      val (a, b) = MatrixLibBlock.GenBlockMatrix(
        M,
        K,
        N
      )
      A(k) = a
      B(k) = b
    }

    (A, B)
  }

  // Convert the input matrix to a big bus
  def SplitBatchBlockMatrx(
      Batch: Int,
      M: Int,
      K: Int,
      N: Int,
      A: Array[Array[Byte]],
      B: Array[Array[Byte]]
  ) = {
    val split_A = Array.ofDim[BigInt](Batch, M * K)
    val split_B = Array.ofDim[BigInt](Batch, K * N)

    for (k <- 0 until Batch) {
      val (split_matrix_A, split_matrix_B) = MatrixLibBlock.SplitBlockMatrix(
        M,
        K,
        N,
        A(k),
        B(k)
      )
      split_A(k) = split_matrix_A
      split_B(k) = split_matrix_B
    }
    (split_A, split_B)
  }

  // Check the result of Gemm with the golden model
  def CheckResults(
      Batch: Int,
      Len: Int,
      A: Array[Array[BigInt]],
      B: Array[Array[BigInt]]
  ) = {
    for (b <- 0 until Batch) {
      for (i <- 0 until Len) {
        // println(A(b)(i), B(b)(i))
        assert(A(b)(i) == B(b)(i))
      }
    }

  }
}

// A trait with basic gemm test function to be used in different test
trait AbstractBatchGemmtest {
  def BatchGemmRandomTest[T <: BatchGemm](
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

    // println(size_Batch, size_M, size_K, size_N)

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
    def checkOutput() = {
      if (dut.io.ctrl.gemm_write_valid_o.peekBoolean()) {
        M_write_counter =
          (write_counter - current_write_batch * size_M * size_N) / size_N
        N_write_counter =
          (write_counter - current_write_batch * size_M * size_N) % size_N
        val addr_slide_C = M_write_counter * size_N + N_write_counter
        assert(
          dut.io.ctrl.addr_c_o.peekInt() ==
            start_addr_C + current_write_batch * stride_addr_C + M_write_counter * ld_addr_C + strideinnermost_C * N_write_counter
        )

        split_matrix_C(current_write_batch)(addr_slide_C.toInt) =
          dut.io.data.c_o.peekInt()
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
    dut.io.ctrl.start_do_i.poke(false.B)
    dut.clock.step(5)
    dut.io.ctrl.start_do_i.poke(true.B)

    dut.io.ctrl.Batch_i.poke(size_Batch)
    dut.io.ctrl.M_i.poke(size_M)
    dut.io.ctrl.K_i.poke(size_K)
    dut.io.ctrl.N_i.poke(size_N)

    dut.io.ctrl.ptr_addr_a_i.poke(start_addr_A)
    dut.io.ctrl.ptr_addr_b_i.poke(start_addr_B)
    dut.io.ctrl.ptr_addr_c_i.poke(start_addr_C)

    dut.io.ctrl.strideinnermost_A_i.poke(strideinnermost_A)
    dut.io.ctrl.strideinnermost_B_i.poke(strideinnermost_B)
    dut.io.ctrl.strideinnermost_C_i.poke(strideinnermost_C)

    dut.io.ctrl.ldA_i.poke(ld_addr_A)
    dut.io.ctrl.ldB_i.poke(ld_addr_B)
    dut.io.ctrl.ldC_i.poke(ld_addr_C)

    dut.io.ctrl.strideA_i.poke(stride_addr_A)
    dut.io.ctrl.strideB_i.poke(stride_addr_B)
    dut.io.ctrl.strideC_i.poke(stride_addr_C)

    dut.clock.step(1)
    dut.io.ctrl.start_do_i.poke(false.B)

    // If gemm_read_valid_o is asserted, give the right a_i and b_i data according to the address
    while (dut.io.ctrl.gemm_read_valid_o.peekBoolean()) {
      M_read_counter =
        (read_counter - current_read_batch * size_M * size_K * size_N) / (size_N * size_K)
      K_read_counter =
        ((read_counter - current_read_batch * size_M * size_K * size_N) % (size_N * size_K)) % size_K
      N_read_counter =
        ((read_counter - current_read_batch * size_M * size_K * size_N) % (size_N * size_K)) / size_K

      val addr_slide_A = M_read_counter * size_K + K_read_counter
      val addr_slide_B = N_read_counter * size_K + K_read_counter

      // println(M_read_counter, K_read_counter, N_read_counter)
      // println(
      //   "read: ",
      //   current_read_batch,
      //   read_counter,
      //   addr_slide_A,
      //   addr_slide_B
      // )

      // Check if the output address matches the address in the golden model
      assert(
        dut.io.ctrl.addr_a_o.peekInt() ==
          start_addr_A + M_read_counter * ld_addr_A + stride_addr_A * current_read_batch + strideinnermost_A * K_read_counter
      )
      assert(
        dut.io.ctrl.addr_b_o.peekInt() ==
          start_addr_B + N_read_counter * ld_addr_B + stride_addr_B * current_read_batch + strideinnermost_B * K_read_counter
      )

      dut.clock.step(1)
      dut.io.ctrl.data_valid_i.poke(true.B)

      // Give the right a_i and b_i data according to the address
      dut.io.data.a_i
        .poke(split_matrix_A(current_read_batch)(addr_slide_A.toInt))
      dut.io.data.b_i
        .poke(split_matrix_B(current_read_batch)(addr_slide_B.toInt))
      read_counter = read_counter + 1

      // Update read counters
      if (
        read_counter % (size_K * size_M * size_N) == 0 && current_read_batch != size_Batch - 1
      ) {
        current_read_batch = current_read_batch + 1
      }

      checkOutput()

    }

    dut.clock.step(1)
    dut.io.ctrl.data_valid_i.poke(false.B)

    while (dut.io.ctrl.busy_o.peekBoolean()) {
      checkOutput()
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
    // println("One Batch Gemm test finished!")

  }

}

// Random size of input matrices and Integer 8 data test and check with the results of Batch Gemm with golden model
class BatchGemmRandomTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with AbstractBatchGemmtest {
  "DUT" should "pass" in {
    test(new BatchGemm)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step()

        for (i <- 0 until TestParameters.BatchGemmRandomTest_TestLoop) {
          // Randomly generate the batch number and size of the input matrices
          val (size_Batch, size_M, size_K, size_N) =
            MatrixLibBatch.genRandSizeandBatchTest()
          BatchGemmRandomTest(dut, size_Batch, size_M, size_K, size_N)
        }

        emitVerilog(
          new (BatchGemm),
          Array("--target-dir", "generated/gemm")
        )

      }

  }
}

// Corner case test for Batch Gemm to see if the Batch Gemm works well in extreme configurations
class BatchGemmCornerCaseTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with AbstractBatchGemmtest {
  "DUT" should "pass" in {
    test(new BatchGemm)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step()
        BatchGemmRandomTest(
          dut,
          size_Batch = 1,
          size_M = 1,
          size_K = 1,
          size_N = 1
        )
        BatchGemmRandomTest(
          dut,
          size_Batch = 3,
          size_M = 1,
          size_K = 1,
          size_N = 1
        )
        BatchGemmRandomTest(
          dut,
          size_Batch = 2,
          size_M = 1,
          size_K = 1,
          size_N = 1
        )
        BatchGemmRandomTest(
          dut,
          size_Batch = 1,
          size_M = 1,
          size_K = 2,
          size_N = 1
        )
        BatchGemmRandomTest(
          dut,
          size_Batch = 1,
          size_M = 1,
          size_K = 1,
          size_N = 2
        )
        BatchGemmRandomTest(
          dut,
          size_Batch = 1,
          size_M = 2,
          size_K = 3,
          size_N = 4
        )

        val (size_Batch, size_M, size_K, size_N) =
          MatrixLibBatch.genRandSizeandBatchTest()
        BatchGemmRandomTest(dut, size_Batch, size_M, size_K, size_N)
      }
  }
}

// Simple Batch Gemm test to see if the control signals work well before random data test for debug purpose
// In this test, the B, M, K, N are set manually to see the behavior of the Batch Gemm and to check if the control signals works well
// Testing what if the start_do_i is asserted when the Batch Gemm is busy
// And also orchestrating the data_valid_i to see if the Gemm works well under different situations
// Finally checking the output manually in the waveform
class BatchGemmTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new BatchGemm)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step()
        dut.clock.step(5)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(5)
        dut.io.data.a_i.poke(1.U)
        dut.io.data.b_i.poke(1.U)

        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(2)
        dut.io.ctrl.K_i.poke(1)
        dut.io.ctrl.N_i.poke(2)

        dut.io.ctrl.Batch_i.poke(2)
        dut.io.ctrl.strideA_i.poke(1)
        dut.io.ctrl.strideB_i.poke(1)
        dut.io.ctrl.strideC_i.poke(1)

        dut.io.ctrl.ptr_addr_a_i.poke(2)
        dut.io.ctrl.ptr_addr_b_i.poke(3)
        dut.io.ctrl.ptr_addr_c_i.poke(4)

        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(false.B)

        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(true.B)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(5)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)

        dut.clock.step(5)

        dut.clock.step()
        dut.clock.step(5)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(5)
        dut.io.data.a_i.poke(1.U)
        dut.io.data.b_i.poke(1.U)

        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(2)
        dut.io.ctrl.K_i.poke(3)
        dut.io.ctrl.N_i.poke(4)
        dut.io.ctrl.ptr_addr_a_i.poke(2)
        dut.io.ctrl.ptr_addr_b_i.poke(3)
        dut.io.ctrl.ptr_addr_c_i.poke(4)

        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(45)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)

        dut.clock.step(5)

        dut.io.ctrl.start_do_i.poke(false.B)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(1)
        dut.io.ctrl.K_i.poke(4)
        dut.io.ctrl.N_i.poke(1)
        dut.io.ctrl.Batch_i.poke(1)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.clock.step(3)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(5)

        dut.clock.step(5)

        dut.io.ctrl.start_do_i.poke(false.B)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(4)
        dut.io.ctrl.K_i.poke(1)
        dut.io.ctrl.N_i.poke(1)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.clock.step(3)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(5)

        dut.clock.step(5)

        dut.io.ctrl.start_do_i.poke(false.B)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(1)
        dut.io.ctrl.K_i.poke(1)
        dut.io.ctrl.N_i.poke(4)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.clock.step(3)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(5)

        dut.clock.step(5)

        emitVerilog(
          new (BatchGemm),
          Array("--target-dir", "generated/gemm")
        )

      }
  }
}
