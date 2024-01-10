package gemm

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

// Simple Bare Block Gemm (for integrating with streamer) test to see if the control signals work well
class BareBlockGemmBaseTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" taggedAs (Unnecessary) in {
    test(new BareBlockGemm)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)

        // Setting the M = 2, K = 2, and N = 2
        dut.io.ctrl.valid.poke(true.B)
        dut.io.ctrl.bits.M_i.poke(2)
        dut.io.ctrl.bits.K_i.poke(2)
        dut.io.ctrl.bits.N_i.poke(2)

        dut.clock.step()
        dut.io.ctrl.valid.poke(false.B)

        dut.io.data.a_i.valid.poke(1.B)
        dut.io.data.b_i.valid.poke(1.B)

        // check the gemm back pressure
        dut.clock.step(3)
        dut.io.data.c_o.ready.poke(1.B)

        dut.clock.step(8)
        dut.io.data.a_i.valid.poke(0.B)
        dut.io.data.b_i.valid.poke(0.B)

        dut.clock.step(8)

      }
  }
}
