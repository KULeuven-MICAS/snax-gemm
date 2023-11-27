package gemm
import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

class ConfigGemmTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" in {
    def GemmType = 4
    test(new GemmGenerator)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        emitVerilog(
          new GemmGenerator(GemmType),
          Array("--target-dir", "generated/gemm")
        )
      }
  }
}
