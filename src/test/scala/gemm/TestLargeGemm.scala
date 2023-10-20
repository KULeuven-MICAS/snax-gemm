package gemm

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

// Random size of input matrices and Integer 8 data test and check with the results of largd Gemm with golden model
class LargeGemmRandomTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new LargeGemm)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        // large gemm test generation function
        def LargeGemmRandomTets() = {
          // Randomly generate the size of the input matrices
          val (size_M, size_K, size_N)                   = MatrixlibLarge.genRandSizeTest()
          // Randomly generation of input matrices
          val (matrix_A, matrix_B)                       =
            MatrixlibLarge.GenLargeMatrix(
              size_M,
              size_K,
              size_N
            )
            // Convert the sub-matrices to a big bus 
          val (split_matrix_A, split_matrix_B)           =
            MatrixlibLarge.SpliteLargeMatrx(
              size_M,
              size_K,
              size_N,
              matrix_A,
              matrix_B
            )
          val split_matrix_C                             =
            Array.ofDim[BigInt](size_M * size_N)
            // Random generation of the matrices start address
          val (start_addr_A, start_addr_B, start_addr_C) =
            MatrixlibLarge.genRandSizeTest()
          // Generation of golden result in Scala
          val golden_array =
            MatrixlibLarge.LargeMarixMul_1D(
              size_M,
              size_K,
              size_N,
              matrix_A,
              matrix_B
            )
          // If the gemm_write_valid is asserted, take out the c_o data for check
          def check_output() = {
            if (dut.io.ctrl.gemm_write_valid.peekBoolean()) {
              val addr_slide_C = (dut.io.ctrl.addr_c_out
                .peekInt() - start_addr_C) / GEMMConstant.OnputMatrixBaseAddrC
              split_matrix_C(addr_slide_C.toInt) = dut.io.data.c_o.peekInt()
            }
          }
          // Give the large gemm configuration
          dut.clock.step(5)
          dut.io.ctrl.start_do.poke(false.B)
          dut.clock.step(5)
          dut.io.ctrl.start_do.poke(true.B)
          dut.io.ctrl.M_in.poke(size_M)
          dut.io.ctrl.K_in.poke(size_K)
          dut.io.ctrl.N_in.poke(size_N)
          dut.io.ctrl.addr_a_in.poke(start_addr_A)
          dut.io.ctrl.addr_b_in.poke(start_addr_B)
          dut.io.ctrl.addr_c_in.poke(start_addr_C)
          // If gemm_read_valid is asserted, give the right a_i and b_in data according to the address
          while (dut.io.ctrl.gemm_read_valid.peekBoolean()) {
            val addr_slide_A = (dut.io.ctrl.addr_a_out
              .peekInt() - start_addr_A) / GEMMConstant.InputMatrixBaseAddrA
            val addr_slide_B = (dut.io.ctrl.addr_b_out
              .peekInt() - start_addr_B) / GEMMConstant.InputMatrixBaseAddrB
            // println(addr_slide_A, addr_slide_B)
            dut.clock.step(1)
            dut.io.ctrl.start_do.poke(false.B)
            dut.io.ctrl.data_valid_i.poke(true.B)
            dut.io.data.a_i
              .poke(split_matrix_A(addr_slide_A.toInt))
            dut.io.data.b_i
              .poke(split_matrix_B(addr_slide_B.toInt))
            check_output()
          }

          dut.clock.step(1)
          dut.io.ctrl.data_valid_i.poke(false.B)

          while (dut.io.ctrl.gemm_busy.peekBoolean()) {
            check_output()
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

        dut.clock.step()
        val TestLoop = 2

        for (i <- 0 until TestLoop) {
          LargeGemmRandomTets()
        }

        emitVerilog(
          new (LargeGemm),
          Array("--target-dir", "generated/gemm")
        )

      }
  }
}

// Simple large Gemm test to see if the control signals work well
class LargeGemmBaseTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" taggedAs (Unnecessary) in {
    test(new LargeGemm)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step()
        dut.clock.step(5)
        dut.io.ctrl.start_do.poke(false.B)
        dut.clock.step(5)
        dut.io.data.a_i.poke(1.U)
        dut.io.data.b_i.poke(1.U)

        dut.io.ctrl.start_do.poke(true.B)
        dut.io.ctrl.M_in.poke(2)
        dut.io.ctrl.K_in.poke(2)
        dut.io.ctrl.N_in.poke(2)
        dut.io.ctrl.addr_a_in.poke(2)
        dut.io.ctrl.addr_b_in.poke(3)
        dut.io.ctrl.addr_c_in.poke(4)

        dut.clock.step(1)
        dut.io.ctrl.start_do.poke(false.B)
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

        dut.clock.step()
        dut.clock.step(5)
        dut.io.ctrl.start_do.poke(false.B)
        dut.clock.step(5)
        dut.io.data.a_i.poke(1.U)
        dut.io.data.b_i.poke(1.U)

        dut.io.ctrl.start_do.poke(true.B)
        dut.io.ctrl.M_in.poke(2)
        dut.io.ctrl.K_in.poke(3)
        dut.io.ctrl.N_in.poke(4)
        dut.io.ctrl.addr_a_in.poke(2)
        dut.io.ctrl.addr_b_in.poke(3)
        dut.io.ctrl.addr_c_in.poke(4)

        dut.clock.step(1)
        dut.io.ctrl.start_do.poke(false.B)
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

        dut.io.ctrl.start_do.poke(false.B)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.start_do.poke(true.B)
        dut.io.ctrl.M_in.poke(1)
        dut.io.ctrl.K_in.poke(4)
        dut.io.ctrl.N_in.poke(1)
        dut.clock.step(1)
        dut.io.ctrl.start_do.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.clock.step(3)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(5)

        dut.clock.step(5)

        dut.io.ctrl.start_do.poke(false.B)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.start_do.poke(true.B)
        dut.io.ctrl.M_in.poke(4)
        dut.io.ctrl.K_in.poke(1)
        dut.io.ctrl.N_in.poke(1)
        dut.clock.step(1)
        dut.io.ctrl.start_do.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.clock.step(3)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(5)

        dut.clock.step(5)

        dut.io.ctrl.start_do.poke(false.B)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.ctrl.start_do.poke(true.B)
        dut.io.ctrl.M_in.poke(1)
        dut.io.ctrl.K_in.poke(1)
        dut.io.ctrl.N_in.poke(4)
        dut.clock.step(1)
        dut.io.ctrl.start_do.poke(false.B)
        dut.clock.step(2)
        dut.io.ctrl.data_valid_i.poke(true.B)
        dut.clock.step(1)
        dut.clock.step(3)
        dut.io.ctrl.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.clock.step(5)

        dut.clock.step(5)

        emitVerilog(
          new (LargeGemm),
          Array("--target-dir", "generated/gemm")
        )

      }
  }
}

// simple test to see if the LargeGemmController work well with manual configuration
class LargeGemmControllerTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" taggedAs (Unnecessary) in {
    test(new LargeGemmController)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)
        dut.io.start_do.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(5)

        dut.io.start_do.poke(true.B)
        dut.io.M_in.poke(2)
        dut.io.K_in.poke(2)
        dut.io.N_in.poke(2)
        dut.io.addr_a_in.poke(2)
        dut.io.addr_b_in.poke(3)
        dut.io.addr_c_in.poke(4)

        dut.clock.step(1)
        dut.io.start_do.poke(false.B)
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

        dut.io.start_do.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.start_do.poke(true.B)
        dut.io.M_in.poke(1)
        dut.io.K_in.poke(4)
        dut.io.N_in.poke(1)
        dut.clock.step(1)
        dut.io.start_do.poke(false.B)
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

        dut.io.start_do.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.start_do.poke(true.B)
        dut.io.M_in.poke(4)
        dut.io.K_in.poke(1)
        dut.io.N_in.poke(1)
        dut.clock.step(1)
        dut.io.start_do.poke(false.B)
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

        dut.io.start_do.poke(false.B)
        dut.io.data_valid_i.poke(false.B)
        dut.clock.step(1)
        dut.io.start_do.poke(true.B)
        dut.io.M_in.poke(1)
        dut.io.K_in.poke(1)
        dut.io.N_in.poke(4)
        dut.clock.step(1)
        dut.io.start_do.poke(false.B)
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
          new (LargeGemmController),
          Array("--target-dir", "generated/gemm")
        )

      }
  }
}
