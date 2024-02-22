package gemm
import chisel3._
import chisel3.util._

import scala.math

object GemmConstant {

  def dataWidthA = 8
  def dataWidthB = dataWidthA
  def dataWidthMul = dataWidthA * 4
  def dataWidthC = dataWidthA * 4
  def dataWidthAccum = dataWidthA * 4

  def meshRow = 8
  def tileSize = 8
  def meshCol = 8

  def addrWidth = 32
  def sizeConfigWidth = 16

  def dataWidthPerAddr = 8
  def baseAddrIncrementA =
    dataWidthA * meshRow * tileSize / dataWidthPerAddr
  def baseAddrIncrementB =
    dataWidthB * meshCol * tileSize / dataWidthPerAddr
  def baseAddrIncrementC =
    dataWidthC * meshRow * meshCol / dataWidthPerAddr

  def TCDMWritePorts = 8
  def TCDMDataWidth = 64
  def idealTCDMWritePorts = meshRow * meshCol * dataWidthC / TCDMDataWidth

  def csrNum: Int = 5
  def csrAddrWidth: Int = 3

}
