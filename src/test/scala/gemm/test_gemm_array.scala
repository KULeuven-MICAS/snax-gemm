package gemm

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._
import scala.math.BigInt
import org.scalatest.matchers.should.Matchers
import org.scalatest.Tag

// Add tags to filter some unncessary test for fast unit test
object Unnecessary extends Tag("Unnecessary")

// Random Integer 8 data test and check with the results of Gemm with golden model
class GemmArrayRandomTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {
  "DUT" should "pass" in {
    test(new GemmArray)
      .withAnnotations(
        Seq(WriteVcdAnnotation, VerilatorBackendAnnotation)
      ) { dut =>
        def RandomTest() = {
          /* Generate Random Integer 8 data (from -128 to 127) */
          val RandomMatrixs =
            MatrixLibBase.GenRandomMatrix(
              GEMMConstant.meshRow,
              GEMMConstant.tileSize,
              GEMMConstant.meshCol
            )
          val random_matrix_A = RandomMatrixs._1
          val random_matrix_B = RandomMatrixs._2

          // Generate golden result data for verification
          val golden_array = MatrixLibBase.MarixMul_1D(
            GEMMConstant.meshRow,
            GEMMConstant.tileSize,
            GEMMConstant.meshCol,
            random_matrix_A,
            random_matrix_B
          )
          /* Translate data array to big bus for Gemm module input */
          val RandomBigBuses =
            MatrixLibBase.Matrix2BigBuses(
              GEMMConstant.meshRow,
              GEMMConstant.tileSize,
              GEMMConstant.meshCol,
              random_matrix_A,
              random_matrix_B
            )
          val RandomBigBus_A = RandomBigBuses._1
          val RandomBigBus_B = RandomBigBuses._2

          // Invoke data_in_valid to send the test data
          dut.io.data_in_valid.poke(1.U)
          dut.io.data.a_io_in.poke(RandomBigBus_A)
          dut.io.data.b_io_in.poke(RandomBigBus_B)

          dut.clock.step()
          dut.io.data_in_valid.poke(0.U)
          /* Wait for data_out_valid assert, then take the result */
          while (dut.io.data_out_valid.peekBoolean()) {
            dut.clock.step()
          }
          val results = dut.io.data.c_io_out.peek()
          /* Translate the big bus from Gemm to int array for comparison */
          val results_array = MatrixLibBase.BigBus2Matrix(
            GEMMConstant.meshRow,
            GEMMConstant.meshCol,
            results
          )
          // Check the reuslts
          MatrixLibBase.CheckResults(
            GEMMConstant.meshRow * GEMMConstant.meshCol,
            results_array,
            golden_array
          )

          dut.clock.step(10)
        }

        dut.clock.step()
        val TestLoop = 10

        // Do the Random input data test
        for (i <- 0 until TestLoop) {
          RandomTest()
        }

        emitVerilog(
          new (GemmArray),
          Array("--target-dir", "generated/gemm")
        )

      }
  }
}

// simple Gemm test to see if the control signals work well
class GemmArrayBaseTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" taggedAs (Unnecessary) in {
    test(new GemmArray)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step()
        dut.io.data_in_valid.poke(1.U)
        for (i <- 0 until 8) {
          for (j <- 0 until 8) {
            dut.io.data.a_io_in.poke(1.U)
            dut.io.data.b_io_in.poke(1.U)
          }
        }
        dut.clock.step()
        dut.io.data_in_valid.poke(0.U)

        dut.clock.step(10)

        dut.clock.step()
        dut.io.data_in_valid.poke(1.U)
        dut.clock.step()
        dut.io.data_in_valid.poke(0.U)
        dut.clock.step()
        dut.clock.step()

        dut.clock.step(10)

        dut.clock.step()
        dut.io.data_in_valid.poke(1.U)
        dut.io.accumulate.poke(1)

        for (i <- 0 until 8) {
          for (j <- 0 until 8) {
            dut.io.data.a_io_in.poke(1.U)
            dut.io.data.b_io_in.poke(1.U)
          }
        }

        dut.clock.step()
        dut.io.data_in_valid.poke(0.U)

        dut.clock.step(10)

        dut.clock.step()
        for (i <- 0 until 8) {
          for (j <- 0 until 8) {
            dut.io.data.a_io_in.poke(1.U)
            dut.io.data.b_io_in.poke(1.U)

          }
        }

        dut.clock.step()
        dut.clock.step(10)
        dut.io.data_in_valid.poke(1.U)

        dut.clock.step()
        dut.io.data_in_valid.poke(0.U)
        dut.clock.step(10)

        emitVerilog(
          new (GemmArray),
          Array("--target-dir", "generated/gemm")
        )

      }
  }
}
