package gemm

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

// A trait with basic gemm test function to be used in different test
trait AbstractBlockGemmtest {
  // Block gemm test generation function
  def BlockGemmRandomTets[T <: BlockGemm](
      dut: T,
      size_M: Int,
      size_K: Int,
      size_N: Int
  ) = {

    // Randomly generation of input matrices
    val (matrix_A, matrix_B) =
      MatrixLibBlock.GenBlockMatrix(
        size_M,
        size_K,
        size_N
      )

    // Convert the sub-matrices to a big bus
    val (split_matrix_A, split_matrix_B) =
      MatrixLibBlock.SplitBlockMatrix(
        size_M,
        size_K,
        size_N,
        matrix_A,
        matrix_B
      )
    val split_matrix_C =
      Array.ofDim[BigInt](size_M * size_N)

    // Random generation of the matrices start address
    val (start_addr_A, start_addr_B, start_addr_C) =
      MatrixLibBlock.GenRandSizeTest()

    // Generation of golden result in Scala
    val golden_array =
      MatrixLibBlock.BlockMarixMul_1D(
        size_M,
        size_K,
        size_N,
        matrix_A,
        matrix_B
      )

    // If the gemm_write_valid_o is asserted, take out the c_o data for check
    def CheckOutput() = {
      if (dut.io.ctrl.gemm_write_valid_o.peekBoolean()) {
        val addr_slide_C = (dut.io.ctrl.addr_c_o
          .peekInt() - start_addr_C) / GemmConstant.baseAddrIncrementC
        // println("write", addr_slide_C)
        split_matrix_C(addr_slide_C.toInt) = dut.io.data.c_o.peekInt()
      }
    }

    // Give the Block gemm configuration
    dut.clock.step(5)
    dut.io.ctrl.start_do_i.poke(false.B)
    dut.clock.step(5)
    dut.io.ctrl.start_do_i.poke(true.B)

    dut.io.ctrl.M_i.poke(size_M)
    dut.io.ctrl.K_i.poke(size_K)
    dut.io.ctrl.N_i.poke(size_N)

    dut.io.ctrl.ptr_addr_a_i.poke(start_addr_A)
    dut.io.ctrl.ptr_addr_b_i.poke(start_addr_B)
    dut.io.ctrl.ptr_addr_c_i.poke(start_addr_C)

    dut.clock.step(1)
    dut.io.ctrl.start_do_i.poke(false.B)

    // If gemm_read_valid_o is asserted, give the right a_i and b_i data according to the address
    while (dut.io.ctrl.gemm_read_valid_o.peekBoolean()) {
      val addr_slide_A = (dut.io.ctrl.addr_a_o
        .peekInt() - start_addr_A) / GemmConstant.baseAddrIncrementA
      val addr_slide_B = (dut.io.ctrl.addr_b_o
        .peekInt() - start_addr_B) / GemmConstant.baseAddrIncrementB
      // println("read", addr_slide_A, addr_slide_B)

      dut.clock.step(1)
      dut.io.ctrl.data_valid_i.poke(true.B)
      dut.io.data.a_i
        .poke(split_matrix_A(addr_slide_A.toInt))
      dut.io.data.b_i
        .poke(split_matrix_B(addr_slide_B.toInt))

      CheckOutput()
    }

    dut.clock.step(1)
    dut.io.ctrl.data_valid_i.poke(false.B)

    while (dut.io.ctrl.busy_o.peekBoolean()) {
      CheckOutput()
      dut.clock.step(1)
    }

    dut.clock.step(5)

    // Compare the output data with the golden model
    MatrixLibBase.CheckResults(
      size_M * size_N,
      split_matrix_C,
      golden_array
    )
  }

}

// Random size of input matrices and Integer 8 data test and check with the results of Block Gemm with golden model
class BlockGemmRandomTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with AbstractBlockGemmtest {
  "DUT" should "pass" in {
    test(new BlockGemm)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step()

        for (i <- 0 until TestParameters.BlockGemmRandomTest_TestLoop) {
          // Randomly generate the size of the input matrices
          val (size_M, size_K, size_N) = MatrixLibBlock.GenRandSizeTest()
          BlockGemmRandomTets(dut, size_M, size_K, size_N)
        }

        emitVerilog(
          new (BlockGemm),
          Array("--target-dir", "generated/gemm")
        )

      }
  }
}

// Corner case test for Block Gemm to see if the Block Gemm works well in extreme configurations
class BlockGemmCornerCaseTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with AbstractBlockGemmtest {
  "DUT" should "pass" in {
    test(new BlockGemm)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)
        BlockGemmRandomTets(dut, 1, 1, 1)
        BlockGemmRandomTets(dut, 1, 4, 1)
        BlockGemmRandomTets(dut, 1, 1, 4)
        BlockGemmRandomTets(dut, 4, 1, 1)
        BlockGemmRandomTets(dut, 2, 2, 2)
      }
  }
}

// Simple Block Gemm test to see if the control signals work well
class BlockGemmBaseTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" taggedAs (Unnecessary) in {
    test(new BlockGemm)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step()
        dut.clock.step(5)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(5)
        dut.io.data.a_i.poke(1.U)
        dut.io.data.b_i.poke(1.U)

        // Setting the M = 2, K = 2, and N = 2
        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(2)
        dut.io.ctrl.K_i.poke(2)
        dut.io.ctrl.N_i.poke(2)
        // Setting the ptr_addr_a_i = 2, ptr_addr_b_i = 3, and ptr_addr_c_i = 4
        dut.io.ctrl.ptr_addr_a_i.poke(2)
        dut.io.ctrl.ptr_addr_b_i.poke(3)
        dut.io.ctrl.ptr_addr_c_i.poke(4)

        // Orchestrating the data_valid_i to see if the Gemm works well under different situations
        // Checking the output manually
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
        dut.clock.step(5)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)

        dut.clock.step(5)

        // Below is similar: setting different M, K and N configurations as well as the different address pointers
        // Also orchestrating the data_valid_i
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
        dut.clock.step(21)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)

        dut.clock.step(5)

        // Below, we test the corner cases with one of the configuration of M, K and N is 1
        // to see if the the Gemm still works well and these test also help to find bug
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(1)
        dut.io.ctrl.K_i.poke(4)
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
          new (BlockGemm),
          Array("--target-dir", "generated/gemm")
        )

      }
  }
}

// Simple test to see if the BlockGemmController works well with manual configuration
class BlockGemmControllerTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" taggedAs (Unnecessary) in {
    test(new BlockGemmController)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)
        dut.io.start_do_i.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(5)

        dut.io.start_do_i.poke(true.B)
        // Setting the M = 2, K = 2, and N = 2
        dut.io.M_i.poke(2)
        dut.io.K_i.poke(2)
        dut.io.N_i.poke(2)
        // Setting the ptr_addr_a_i = 2, ptr_addr_b_i = 3, and ptr_addr_c_i = 4
        dut.io.ptr_addr_a_i.poke(2)
        dut.io.ptr_addr_b_i.poke(3)
        dut.io.ptr_addr_c_i.poke(4)

        // Orchestrating the data_valid_i to see if the Gemm works well under different situations
        // Checking the output addresses and read/write valid signal manually
        dut.clock.step(1)
        dut.io.start_do_i.poke(false.B)
        dut.clock.step(2)
        dut.io.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(true.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_i.poke(true.B)
        dut.io.data_valid_o.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(true.B)
        dut.clock.step(1)
        dut.io.data_valid_i.poke(true.B)
        dut.clock.step(5)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(false.B)

        dut.clock.step(5)

        // Test the corner cases with one of the configuration of M, K and N is 1
        // to see if the the controller still works well and these test also help to find bug
        // with different delay of data_valid_i assert to simulate the delay of real memory respond
        dut.io.start_do_i.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.start_do_i.poke(true.B)
        dut.io.M_i.poke(1)
        dut.io.K_i.poke(4)
        dut.io.N_i.poke(1)
        dut.clock.step(1)
        dut.io.start_do_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(true.B)
        dut.clock.step(3)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(false.B)
        dut.clock.step(5)

        dut.clock.step(5)

        dut.io.start_do_i.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.start_do_i.poke(true.B)
        dut.io.M_i.poke(4)
        dut.io.K_i.poke(1)
        dut.io.N_i.poke(1)
        dut.clock.step(1)
        dut.io.start_do_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(true.B)
        dut.clock.step(3)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(false.B)
        dut.clock.step(5)

        dut.clock.step(5)

        dut.io.start_do_i.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.start_do_i.poke(true.B)
        dut.io.M_i.poke(1)
        dut.io.K_i.poke(1)
        dut.io.N_i.poke(4)
        dut.clock.step(1)
        dut.io.start_do_i.poke(false.B)
        dut.clock.step(2)
        dut.io.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(true.B)
        dut.clock.step(3)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(false.B)
        dut.clock.step(5)

        dut.clock.step(5)

        dut.io.start_do_i.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.start_do_i.poke(true.B)
        dut.io.M_i.poke(1)
        dut.io.K_i.poke(1)
        dut.io.N_i.poke(1)
        dut.clock.step(1)
        dut.io.start_do_i.poke(false.B)
        dut.clock.step(2)
        dut.io.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(true.B)
        dut.clock.step(3)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.data_valid_o.poke(false.B)
        dut.clock.step(5)

        dut.clock.step(5)

        emitVerilog(
          new (BlockGemmController),
          Array("--target-dir", "generated/gemm")
        )

      }
  }
}
