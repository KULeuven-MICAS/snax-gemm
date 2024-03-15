package gemm

import chisel3._
import chisel3.util._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

// test wrapper module for test ideal situation when tcdm ready always asserted
class BatchGemmSnaxTopWrapper(TCDMWritePorts: Int)
    extends Module
    with RequireAsyncReset {

  val io = IO(new Bundle {
    val batch_gemm_snanx_top = new BatchGemmSnaxTopIO()
    val perf_counter = Output(UInt(32.W))
  })

  val gemm = Module(new BatchGemmSnaxTop(TCDMWritePorts))

  io.batch_gemm_snanx_top <> gemm.io

  val data_valid_i_from_tcdm = WireInit(false.B)

  data_valid_i_from_tcdm := RegNext(gemm.io.ctrl.gemm_read_valid_o)
  gemm.io.ctrl.data_valid_i := data_valid_i_from_tcdm
  // tcdm ready always asserted to mimic no contention
  gemm.io.ctrl.read_mem_ready := 1.B
  gemm.io.ctrl.write_mem_ready := 1.B
  io.perf_counter := gemm.io.ctrl.perf_counter

}

// This test is for test ideal situation when tcdm ready always asserted
class BatchGemmSnaxTopWrapperManualTest
    extends AnyFlatSpec
    with ChiselScalatestTester {
  "DUT" should "pass" in {
    // a function to simulate different matrix size
    // TODO: Test for random data input BatchGemmSnaxTop with refactored true double buffering strategy
    def CornerCaseTest(Batch: Int, M: Int, K: Int, N: Int) = {
      test(new BatchGemmSnaxTopWrapper(GemmConstant.TCDMWritePorts))
        .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
          dut.clock.step(5)
          dut.io.batch_gemm_snanx_top.ctrl.start_do_i.poke(false.B)
          dut.clock.step(5)
          dut.io.batch_gemm_snanx_top.ctrl.start_do_i.poke(true.B)

          dut.io.batch_gemm_snanx_top.ctrl.M_i.poke(M)
          dut.io.batch_gemm_snanx_top.ctrl.K_i.poke(K)
          dut.io.batch_gemm_snanx_top.ctrl.N_i.poke(N)
          dut.io.batch_gemm_snanx_top.ctrl.Batch_i.poke(Batch)

          dut.clock.step(1)
          dut.io.batch_gemm_snanx_top.ctrl.start_do_i.poke(false.B)

          dut.clock.step(2)

          while (dut.io.batch_gemm_snanx_top.ctrl.busy_o.peekBoolean()) {
            dut.clock.step(1)
          }

          println(
            "Computing Batch",
            Batch,
            "M",
            M,
            "K",
            K,
            "N",
            N,
            "ideal cycle number used: ",
            dut.io.perf_counter.peekInt()
          )
          println("test finish!")

          dut.clock.step(1)

        }
    }

    // matrix size various corner case test
    // smallest test case
    CornerCaseTest(1, 1, 1, 1)

    // tests with one smallest size which is 1 in each dimension
    CornerCaseTest(1, 2, 1, 2)
    CornerCaseTest(1, 1, 2, 2)
    CornerCaseTest(1, 2, 2, 1)
    CornerCaseTest(2, 1, 2, 2)

    // tests with different number of smallest size which is 1 in each dimension
    CornerCaseTest(1, 1, 2, 1)
    CornerCaseTest(2, 2, 1, 1)
    CornerCaseTest(2, 1, 2, 1)
    CornerCaseTest(2, 1, 1, 2)
    CornerCaseTest(2, 2, 2, 2)

    // for performance analysis, in this test, the computation cycle is 128
    // there should only be 1 extra cycle for the first read request
    // and the extra cycle for outputting the last result, which is 4 for tcdm write port = 8 and is 2 for tcdm write port = 16
    CornerCaseTest(1, 4, 8, 4)

    // extreme test for K = 1 which gives most pressure to the output part
    CornerCaseTest(1, 4, 1, 4)
    CornerCaseTest(1, 5, 1, 5)
    CornerCaseTest(1, 3, 1, 4)

    emitVerilog(
      new BatchGemmSnaxTopWrapper(GemmConstant.TCDMWritePorts),
      Array("--target-dir", "generated/gemm")
    )
    emitVerilog(
      new BatchGemmSnaxTop(GemmConstant.TCDMWritePorts),
      Array("--target-dir", "generated/gemm")
    )
  }

}

// This is a manual test for the Gemm with multiple cycle output port
// TODO: add random and automatic test for BatchGemmSnaxTop
class BatchGemmSnaxTopManualTest
    extends AnyFlatSpec
    with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new BatchGemmSnaxTop(GemmConstant.TCDMWritePorts))
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
        dut.clock.step(5)
        dut.io.ctrl.start_do_i.poke(false.B)
        dut.clock.step(5)
        dut.io.data.a_i.poke(1.U)
        dut.io.data.b_i.poke(1.U)
        dut.io.ctrl.read_mem_ready.poke(true.B)
        dut.io.ctrl.write_mem_ready.poke(true.B)

        dut.io.ctrl.start_do_i.poke(true.B)
        dut.io.ctrl.M_i.poke(4)
        dut.io.ctrl.K_i.poke(8)
        dut.io.ctrl.N_i.poke(4)
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
        dut.clock.step(200)
        dut.io.ctrl.data_valid_i.poke(false.B)

        dut.clock.step(15)

        emitVerilog(
          new BatchGemmSnaxTop(GemmConstant.TCDMWritePorts),
          Array("--target-dir", "generated/gemm")
        )
      }
  }
}
