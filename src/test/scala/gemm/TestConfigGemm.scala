package gemm
import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

// Test for GEMM Generator
class ConfigGemmTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" in {

    def GenGEMM(GemmType: Int) = {
      test(new GemmGenerator)
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
          emitVerilog(
            new GemmGenerator(GemmType),
            Array("--target-dir", "generated/gemm")
          )
        }
    }

    // These test if each version gemm can be generated successfully
    GenGEMM(1)
    GenGEMM(2)
    GenGEMM(3)
    GenGEMM(4)

  }
}
