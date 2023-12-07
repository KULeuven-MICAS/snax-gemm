package gemm

import chisel3._
import scala.math.BigInt

// Scala math submatrix multiplication library for Block Gemm verification
// Some functions are based on the MatrixLibBase
object MatrixLibBlock {

  // Generate random M, K, N for testing the Block Gemm
  // The test matrix are (M * meshRow, K * tileSize) and (K * tileSize, N * meshCol)
  def GenRandSizeTest() = {
    val rand = new scala.util.Random
    val M = rand.between(1, TestParameters.MatrixLibBlock_random_M_range)
    val K = rand.between(1, TestParameters.MatrixLibBlock_random_K_range)
    val N = rand.between(1, TestParameters.MatrixLibBlock_random_N_range)
    (M, K, N)
  }

  // Generate the random test matrix with a size of (M * meshRow, K * tileSize) and (K * tileSize, N * meshCol)
  def GenBlockMatrix(M: Int, K: Int, N: Int) = {
    MatrixLibBase.GenRandomMatrix(
      GemmConstant.meshRow * M,
      GemmConstant.tileSize * K,
      GemmConstant.meshCol * N
    )
  }

  // Translate matrix to big bus for inputting to Chisel module
  // The data type is Array[Byte]
  def MatrixArray2BigBus(
      meshRow: Int,
      meshCol: Int,
      A: Array[Byte]
  ) = {

    var flattenedUInt_A = ""
    var intValue = 0

    for (i <- 0 until meshRow) {
      for (j <- 0 until meshCol) {
        if (A(i * meshCol + j) < 0) {
          intValue = ((1 << 8) + A(i * meshCol + j))
          flattenedUInt_A = MatrixLibBase.int2hex(2, intValue) + flattenedUInt_A
        } else {
          intValue = A(i * meshCol + j)
          flattenedUInt_A = MatrixLibBase.int2hex(2, intValue) + flattenedUInt_A
        }
      }
    }

    BigInt(flattenedUInt_A, 16)
  }

  // Translate matrix to big bus for inputting to Chisel module
  // The data type is Array[Int]
  def MatrixArray2BigBus(
      meshRow: Int,
      meshCol: Int,
      A: Array[Int],
      Trans: Boolean
  ) = {

    var flattenedUInt_A = ""
    var intValue: Int = 0

    for (i <- 0 until meshRow) {
      for (j <- 0 until meshCol) {
        if (A(i * meshCol + j) < 0) {
          intValue = ((1 << 32) + A(i * meshCol + j) - 1)
          flattenedUInt_A = MatrixLibBase.int2hex(8, intValue) + flattenedUInt_A
        } else {
          intValue = A(i * meshCol + j)
          flattenedUInt_A = MatrixLibBase.int2hex(8, intValue) + flattenedUInt_A
        }
        // println(flattenedUInt_A)
      }
    }

    BigInt(flattenedUInt_A, 16)
  }

  // Translate the large input matrixes to submatrices for inputting to Chisel module and golden result generation
  // The matrixes are arranged according to submatrix multiplication rules
  def SplitBlockMatrix(
      M: Int,
      K: Int,
      N: Int,
      A: Array[Byte],
      B: Array[Byte]
  ) = {
    val split_A = Array.ofDim[BigInt](M * K)
    val split_B = Array.ofDim[BigInt](K * N)

    for (i <- 0 until M) {
      for (j <- 0 until K) {
        val submatrx_A = A.slice(
          (i * K + j) * GemmConstant.meshRow * GemmConstant.tileSize,
          (i * K + j + 1) * GemmConstant.meshRow * GemmConstant.tileSize
        )
        split_A(i * K + j) = MatrixArray2BigBus(
          GemmConstant.meshRow,
          GemmConstant.tileSize,
          submatrx_A
        )
      }
    }

    for (i <- 0 until K) {
      for (j <- 0 until N) {
        val submatrx_B = B.slice(
          (i * N + j) * GemmConstant.tileSize * GemmConstant.meshCol,
          (i * N + j + 1) * GemmConstant.tileSize * GemmConstant.meshCol
        )
        split_B(i * N + j) = MatrixArray2BigBus(
          GemmConstant.tileSize,
          GemmConstant.meshCol,
          submatrx_B
        )
      }
    }

    (split_A, split_B)
  }

  // Block matrix multiplication implementation according to submatrix multiplication rule
  // The matrix A and matrix B also needs to have a right data layout according to the submatrix multiplication rule
  def BlockMarixMul_1D(
      M: Int,
      K: Int,
      N: Int,
      A: Array[Byte],
      B: Array[Byte]
  ): Array[BigInt] = {

    val golden =
      Array.ofDim[Int](
        M * N * GemmConstant.meshRow * GemmConstant.meshCol
      )
    val golden_split_mat = Array.ofDim[BigInt](M * N)

    var submatrx_temp =
      Array.ofDim[Int](
        GemmConstant.meshRow * GemmConstant.meshCol
      )

    for (i <- 0 until M) {
      for (j <- 0 until N) {
        for (k <- 0 until K) {
          var submatrx_temp1 =
            Array.ofDim[Int](
              GemmConstant.meshRow * GemmConstant.meshCol
            )
          submatrx_temp1 = MatrixLibBase.MatrixMul_1D(
            GemmConstant.meshRow,
            GemmConstant.tileSize,
            GemmConstant.meshCol,
            A.slice(
              (i * K + k) * GemmConstant.meshRow * GemmConstant.tileSize,
              (i * K + k + 1) * GemmConstant.meshRow * GemmConstant.tileSize
            ),
            B.slice(
              (k + j * K) * GemmConstant.tileSize * GemmConstant.meshCol,
              (k + j * K + 1) * GemmConstant.tileSize * GemmConstant.meshCol
            ),
            0,
            0
          )
          submatrx_temp = golden
            .slice(
              (i * N + j) * GemmConstant.meshRow * GemmConstant.meshCol,
              (i * N + j + 1) * GemmConstant.meshRow * GemmConstant.meshCol
            )
            .zip(submatrx_temp1)
            .map { case (a, b) => a + b }
          for (t <- 0 until GemmConstant.meshRow * GemmConstant.meshCol) {
            golden(
              (i * N + j) * GemmConstant.meshRow * GemmConstant.meshCol + t
            ) = submatrx_temp(t)
          }
        }

        golden_split_mat(i * N + j) = MatrixArray2BigBus(
          GemmConstant.meshRow,
          GemmConstant.meshCol,
          golden
            .slice(
              (i * N + j) * GemmConstant.meshRow * GemmConstant.meshCol,
              (i * N + j + 1) * GemmConstant.meshRow * GemmConstant.meshCol
            ),
          false
        )
      }
    }
    golden_split_mat
  }

}
