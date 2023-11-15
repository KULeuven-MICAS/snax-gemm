package gemm

import chisel3._
import chisel3.util._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

class BatchGemmSnaxTopWrapper(TCDMWritePorts: Int) extends Module {

  val io = IO(new Bundle {
    val batch_gemm_snanx_top = new BatchGemmSnaxTopIO()
    val perf_counter = Output(UInt(32.W))
  })

  val gemm = Module(new BatchGemmSnaxTop(TCDMWritePorts))

  io.batch_gemm_snanx_top <> gemm.io

  val data_valid_i_from_tcdm = WireInit(false.B)

  data_valid_i_from_tcdm := RegNext(gemm.io.ctrl.gemm_read_valid_o)
  gemm.io.ctrl.data_valid_i := data_valid_i_from_tcdm
  gemm.io.ctrl.read_mem_ready := 1.B
  gemm.io.ctrl.write_mem_ready := 1.B
  io.perf_counter := gemm.io.ctrl.perf_counter

}

class BatchGemmSnaxTopWrapperManualTest
    extends AnyFlatSpec
    with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new BatchGemmSnaxTopWrapper(8))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        val Bacth = 1
        val M = 4
        val K = 8
        val N = 4

        dut.clock.step(5)
        dut.io.batch_gemm_snanx_top.ctrl.start_do_i.poke(false.B)
        dut.clock.step(5)
        dut.io.batch_gemm_snanx_top.ctrl.start_do_i.poke(true.B)

        dut.io.batch_gemm_snanx_top.ctrl.M_i.poke(M)
        dut.io.batch_gemm_snanx_top.ctrl.K_i.poke(K)
        dut.io.batch_gemm_snanx_top.ctrl.N_i.poke(N)
        dut.io.batch_gemm_snanx_top.ctrl.Batch_i.poke(Bacth)

        dut.clock.step(1)
        dut.io.batch_gemm_snanx_top.ctrl.start_do_i.poke(false.B)

        dut.clock.step(2)

        while (dut.io.batch_gemm_snanx_top.ctrl.busy_o.peekBoolean()) {
          dut.clock.step(1)
        }

        println(dut.io.perf_counter.peekInt())
        println("test finish!")

        dut.clock.step(1)

        emitVerilog(
          new BatchGemmSnaxTopWrapper(8),
          Array("--target-dir", "generated/gemm")
        )
      }
  }
}

// This is a manual test for the Gemm with multiple cycle output port
// TODO: add random and automatic test for BatchGemmSnaxTop
class BatchGemmSnaxTopManualTest
    extends AnyFlatSpec
    with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new BatchGemmSnaxTop(8))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(5)
        dut.io.data.a_i.poke(1.U)
        dut.io.data.b_i.poke(1.U)
        dut.io.ctrl.read_mem_ready.poke(true.B)
        dut.io.ctrl.write_mem_ready.poke(true.B)

        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(2)
        // dut.io.ctrl.K_i.poke(3)
        dut.io.ctrl.K_i.poke(2)
        // dut.io.ctrl.K_i.poke(1)
        dut.io.ctrl.N_i.poke(2)
        dut.io.ctrl.Batch_i.poke(1)

        dut.io.ctrl.strideinnermost_A_i.poke(0x5)
        dut.io.ctrl.strideinnermost_B_i.poke(0x5)
        dut.io.ctrl.strideinnermost_C_i.poke(0x5)

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
        // dut.io.ctrl.read_mem_ready.poke(false.B)
        // dut.clock.step(2)
        // dut.io.ctrl.read_mem_ready.poke(true.B)

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

        dut.clock.step(15)

        emitVerilog(
          new BatchGemmSnaxTop(8),
          Array("--target-dir", "generated/gemm")
        )
      }
  }
}
