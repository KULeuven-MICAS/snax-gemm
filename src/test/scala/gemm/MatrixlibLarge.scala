package gemm

import chisel3._
import scala.math.BigInt

// Scala math submatrix multiplication libray for large Gemm verification
// Some functions are based on the MatrixLibBase
object MatrixlibLarge {

  // Generate random M, K, N for testing the large Gemm
  // The test matrix are (M * meshRow, K * tileSize) and (K * tileSize, N * meshCol)
  def genRandSizeTest() = {
    val rand = new scala.util.Random
    val M = rand.between(1, 10)
    val K = rand.between(1, 10)
    val N = rand.between(1, 10)
    (M, K, N)
  }

  // Generate the random test matrix with a size of (M * meshRow, K * tileSize) and (K * tileSize, N * meshCol)
  def GenLargeMatrix(M: Int, K: Int, N: Int) = {
    MatrixLibBase.GenRandomMatrix(
      GEMMConstant.meshRow * M,
      GEMMConstant.tileSize * K,
      GEMMConstant.meshCol * N
    )
  }

  // Translate matrix to big bus for inputing to Chisel module
  // The data type is Array[Byte]
  def MatrixArray2BigBus(
      meshRow: Int,
      meshCol: Int,
      A: Array[Byte],
      Trans: Boolean
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

  // Translate matrix to big bus for inputing to Chisel module
  // The data type is Array[Int]
  def MatrixArray2BigBus(
      meshRow: Int,
      meshCol: Int,
      A: Array[Int],
      Trans: Boolean
  ) = {

    var flattenedUInt_A = ""
    var intValue: Int = 0

    // println("padding to 8!")

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

  // Translate the large input matrixes to submatrixes for inputing to Chisel module and golden result generation
  // The matrixes are arranged according to submatrix multiplication rules
  def SpliteLargeMatrx(
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
          (i * K + j) * GEMMConstant.meshRow * GEMMConstant.tileSize,
          (i * K + j + 1) * GEMMConstant.meshRow * GEMMConstant.tileSize
        )
        split_A(i * K + j) = MatrixArray2BigBus(
          GEMMConstant.meshRow,
          GEMMConstant.tileSize,
          submatrx_A,
          false
        )
      }
    }

    for (i <- 0 until K) {
      for (j <- 0 until N) {
        val submatrx_B = B.slice(
          (i * N + j) * GEMMConstant.tileSize * GEMMConstant.meshCol,
          (i * N + j + 1) * GEMMConstant.tileSize * GEMMConstant.meshCol
        )
        split_B(i * N + j) = MatrixArray2BigBus(
          GEMMConstant.tileSize,
          GEMMConstant.meshCol,
          submatrx_B,
          false
        )
      }
    }

    (split_A, split_B)
  }

  // Large matrix multiplication implementation according to submatrix multiplication rule
  // The matrix A and matrix B also needs to have a right data layout according to the submatrix multiplication rule
  def LargeMarixMul_1D(
      M: Int,
      K: Int,
      N: Int,
      A: Array[Byte],
      B: Array[Byte]
  ): Array[BigInt] = {

    val golden =
      Array.ofDim[Int](
        M * N * GEMMConstant.meshRow * GEMMConstant.meshCol
      )
    val golden_split_mat = Array.ofDim[BigInt](M * N)

    var submatrx_temp =
      Array.ofDim[Int](
        GEMMConstant.meshRow * GEMMConstant.meshCol
      )

    for (i <- 0 until M) {
      for (j <- 0 until N) {
        for (k <- 0 until K) {
          var submatrx_temp1 =
            Array.ofDim[Int](
              GEMMConstant.meshRow * GEMMConstant.meshCol
            )
          submatrx_temp1 = MatrixLibBase.MatrixMul_1D(
            GEMMConstant.meshRow,
            GEMMConstant.tileSize,
            GEMMConstant.meshCol,
            A.slice(
              (i * K + k) * GEMMConstant.meshRow * GEMMConstant.tileSize,
              (i * K + k + 1) * GEMMConstant.meshRow * GEMMConstant.tileSize
            ),
            B.slice(
              (k + j * K) * GEMMConstant.tileSize * GEMMConstant.meshCol,
              (k + j * K + 1) * GEMMConstant.tileSize * GEMMConstant.meshCol
            )
          )
          submatrx_temp = golden
            .slice(
              (i * N + j) * GEMMConstant.meshRow * GEMMConstant.meshCol,
              (i * N + j + 1) * GEMMConstant.meshRow * GEMMConstant.meshCol
            )
            .zip(submatrx_temp1)
            .map { case (a, b) => a + b }
          for (t <- 0 until GEMMConstant.meshRow * GEMMConstant.meshCol) {
            golden(
              (i * N + j) * GEMMConstant.meshRow * GEMMConstant.meshCol + t
            ) = submatrx_temp(t)
          }
        }

        golden_split_mat(i * N + j) = MatrixArray2BigBus(
          GEMMConstant.meshRow,
          GEMMConstant.meshCol,
          golden
            .slice(
              (i * N + j) * GEMMConstant.meshRow * GEMMConstant.meshCol,
              (i * N + j + 1) * GEMMConstant.meshRow * GEMMConstant.meshCol
            ),
          false
        )
      }
    }
    golden_split_mat
  }

}
