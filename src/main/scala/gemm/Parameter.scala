package gemm
import chisel3._
import chisel3.util._

object GemmConstant {
  def dataWidthA = 8
  def dataWidthB = 8
  def dataWidthMul = dataWidthA * 4
  def dataWidthC = dataWidthA * 4
  def dataWidthAccum = dataWidthA * 4
  def tileSize = 8
  def meshRow = 8
  def meshCol = 8

  def DataWidthPerAddr = 8
  def baseAddrIncrementA =
    dataWidthA * meshRow * tileSize / DataWidthPerAddr
  def baseAddrIncrementB =
    dataWidthB * meshCol * tileSize / DataWidthPerAddr
  def baseAddrIncrementC =
    dataWidthC * meshRow * meshCol / DataWidthPerAddr

}
