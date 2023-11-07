package gemm

import chisel3._
import chisel3.util._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

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
        dut.io.ctrl.B_i.poke(2)

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

// This is a manual test for the Gemm with multiple cycle output port
// TODO: add random and automatic test for BatchGemmTCDMWritePortsMultiOutput
class BatchGemmTCDMWritePortsMultiOutputManualTest
    extends AnyFlatSpec
    with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new BatchGemmTCDMWritePortsMultiOutput(8))
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
        dut.io.ctrl.B_i.poke(2)

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
        dut.io.ctrl.read_mem_ready.poke(false.B)
        dut.clock.step(2)
        dut.io.ctrl.read_mem_ready.poke(true.B)

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
          new BatchGemmTCDMWritePortsMultiOutput(8),
          Array("--target-dir", "generated/gemm")
        )
      }
  }
}
