package gemm
import chisel3._
import chisel3.util._

object GEMMConstant {
  def input = 8
  def mul = input * 4
  def output = input * 4
  def accumulate = input * 4
  def tileSize = 8
  def meshRow = 8
  def meshCol = 8

  def DataWidthPerAddr = 8
  def InputMatrixBaseAddrA =
    input * meshRow * tileSize / DataWidthPerAddr
  def InputMatrixBaseAddrB =
    input * meshCol * tileSize / DataWidthPerAddr
  def OnputMatrixBaseAddrC =
    output * meshRow * meshCol / DataWidthPerAddr

}
