package gemm

import chisel3._
import scala.math.BigInt

// Scala math matrix multiplication library for Gemm verification
object MatrixLibBase {
  // This function generates two random data matrix. The size of matrix is (meshRow, tileSize) and (tileSize, meshCol)
  // The data type is signed integer 8 (which is Byte is Scala).
  // The matrixes data is flatten to one dimension to be compatible with memory data layout.
  def GenRandomMatrix(
      meshRow: Int,
      tileSize: Int,
      meshCol: Int
  ) = {
    val random_matrix_A =
      Array.ofDim[Byte](meshRow * tileSize)
    val random_matrix_B =
      Array.ofDim[Byte](tileSize * meshCol)
    val rand = new scala.util.Random
    for (i <- 0 until meshRow) {
      for (k <- 0 until tileSize) {
        random_matrix_A(i * tileSize + k) = rand.between(-128, 127).toByte
      }
    }
    for (k <- 0 until tileSize) {
      for (j <- 0 until meshCol) {
        random_matrix_B(k * meshCol + j) = rand.between(-128, 127).toByte
      }
    }

    (random_matrix_A, random_matrix_B)
  }

  def GenRandomSubtractionValue() = {
    val rand = new scala.util.Random
    (rand.between(0, 127).toByte, rand.between(0, 127).toByte)
  }

  // This function prints the matrix with Byte data type
  def PrintMatrixByte_1D(
      meshRow: Int,
      meshCol: Int,
      A: Array[Byte]
  ) = {
    for (i <- 0 until meshRow) {
      for (j <- 0 until meshCol) {
        println(i, j, A(i * meshCol + j))
      }
    }
    println("print 1D matrix over!")
  }

  // This function overrides the matrix print function with Int data type
  def PrintMatrixByte_1D(
      meshRow: Int,
      meshCol: Int,
      A: Array[Int]
  ) = {
    for (i <- 0 until meshRow) {
      for (j <- 0 until meshCol) {
        println(i, j, A(i * meshCol + j))
      }
    }
    println("print 1D matrix over!")
  }

  // Translate Byte / Int data to hex data for interaction with the Chisel module
  def int2hex(width: Int, intValue: Int) = {
    val paddingChar = '0'
    f"$intValue%x".reverse.padTo(width, paddingChar).reverse
  }

  // def int2hex(width: Int, intValue: Byte) = {
  //   val paddingChar = '0'
  //   f"$intValue%x".reverse.padTo(width, paddingChar).reverse
  // }

  // Translate the Scala matrix to Scala BigInt for inputting the Scala matrix to big Chisel bus
  // The matrix data type is Byte
  def Matrix2BigBuses(
      meshRow: Int,
      tileSize: Int,
      meshCol: Int,
      A: Array[Byte],
      B: Array[Byte]
  ) = {

    var flattenedUInt_A = ""
    var flattenedUInt_B = ""
    var intValue = 0

    /* PrintMatrixByte_1D(GemmConstant.meshCol,GemmConstant.tileSize,B) */
    /* PrintMatrixByte_1D(GemmConstant.tileSize,GemmConstant.meshCol,B_trans) */

    for (i <- 0 until meshRow) {
      for (k <- 0 until tileSize) {
        if (A(i * tileSize + k) < 0) {
          intValue = ((1 << 8) + A(i * tileSize + k))
          flattenedUInt_A = int2hex(2, intValue) + flattenedUInt_A
        } else {
          intValue = A(i * tileSize + k)
          flattenedUInt_A = int2hex(2, intValue) + flattenedUInt_A
        }
      }
    }
    // println(flattenedUInt_A,flattenedUInt_A.size)

    for (k <- 0 until tileSize) {
      for (j <- 0 until meshCol) {
        if (B(k * meshCol + j) < 0) {
          intValue = ((1 << 8) + B(k * meshCol + j))
          flattenedUInt_B = int2hex(2, intValue) + flattenedUInt_B
        } else {
          intValue = B(k * meshCol + j)
          flattenedUInt_B = int2hex(2, intValue) + flattenedUInt_B
        }
      }
    }
    // println(flattenedUInt_B,flattenedUInt_B.size)

    (
      BigInt(flattenedUInt_A, 16),
      BigInt(flattenedUInt_B, 16)
    )
  }

  // Translate the Chisel output big data bus to matrix for checking with the golden results
  def BigBus2Matrix(meshRow: Int, meshCol: Int, C: UInt) = {
    val result = Array.ofDim[Int](meshRow * meshCol)
    for (i <- 0 until meshRow) {
      for (j <- 0 until meshCol) {
        result(i + j * meshRow) = C(
          (i + j * meshRow + 1) * 32,
          (i + j * meshRow) * 32
        ).litValue.toInt
      }
    }
    result
  }

  // Check result. Assert the Chisel output results equal golden.
  // The data type is Array[Int]
  def CheckResults(
      Len: Int,
      A: Array[Int],
      B: Array[Int]
  ) = {
    for (i <- 0 until Len) {
      // println(A(i), B(i))
      assert(A(i) == B(i))
    }
  }

  // Override the check result function for Array[BigInt] data type
  def CheckResults(
      Len: Int,
      A: Array[BigInt],
      B: Array[BigInt]
  ) = {
    for (i <- 0 until Len) {
      // println(A(i), B(i))
      assert(A(i) == B(i))
    }
  }

  // Matrix multiplication with one dimension Array[Byte] data type
  def MatrixMul_1D(
      meshRow: Int,
      tileSize: Int,
      meshCol: Int,
      A: Array[Byte],
      B: Array[Byte],
      subtraction_a: Byte,
      subtraction_b: Byte
  ): Array[Int] = {
    val golden = Array.ofDim[Int](meshRow * meshCol)
    for (i <- 0 until meshRow) {
      for (j <- 0 until meshCol) {
        golden(i * meshCol + j) = 0
        for (k <- 0 until tileSize) {
          // println("A",A(i * tileSize + k))
          // println("B",B(k + j * tileSize))
          /* assuming the matrix B is arranged as N data layout */
          golden(i * meshCol + j) = golden(
            i * meshCol + j
          ) + (A(i * tileSize + k) - subtraction_a) * (B(
            k + j * tileSize
          ) - subtraction_b)
        }
        // println("golden",golden(i * meshCol + j))
      }
    }
    golden
  }

}
