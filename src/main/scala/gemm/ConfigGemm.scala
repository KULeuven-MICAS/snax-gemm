package gemm
import chisel3._
import chisel3.util._

// A uniform Gemm Generator for generating three different kind of Gemm Accelerator
class GemmGenerator(selectedGemm: Int = 3)
    extends Module
    with RequireAsyncReset {
  require(
    selectedGemm == 1 || selectedGemm == 2 || selectedGemm == 3 || selectedGemm == 4,
    "Invalid configuration!"
  )

  // Select io type according to selectedGemm
  val io = IO(
    selectedGemm match {
      case (1) => new GemmArrayIO()
      case (2) => new BlockGemmIO()
      case (3) => new BatchGemmIO()
      case (4) => new BatchGemmSnaxTopIO()
      case _   => new GemmArrayIO()
    }
  )

  // Select Gemm module according to selectedGemm
  val gemm = selectedGemm match {
    case (1) => Module(new GemmArray()).io
    case (2) => Module(new BlockGemm()).io
    case (3) => Module(new BatchGemm()).io
    case (4) => Module(new BatchGemmSnaxTop(GemmConstant.TCDMWritePorts)).io
    case _   => Module(new GemmArray()).io
  }

  chisel3.dontTouch(gemm)
  // Connecting the io with gemm instiantions io
  io <> gemm
}
