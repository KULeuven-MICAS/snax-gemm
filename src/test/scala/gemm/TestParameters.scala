package gemm

object TestParameters {

  val MatrixLibBlock_random_M_range = 10
  val MatrixLibBlock_random_K_range = 10
  val MatrixLibBlock_random_N_range = 10

  val BlockGemmRandomTest_TestLoop = 2

  def MatrixLibBatch_random_B_range = 5
  def MatrixLibBatch_random_M_range = 5
  def MatrixLibBatch_random_K_range = 5
  def MatrixLibBatch_random_N_range = 5

  def BatchGemmRandomTest_TestLoop = 2

  def GemmTestWrapperRandomTest_TestLoop = 2

}
