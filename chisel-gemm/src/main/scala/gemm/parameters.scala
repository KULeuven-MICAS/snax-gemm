package gemm
import chisel3._
import chisel3.util._

object GEMMConstant{

    def input = 8 
    def mul = input * 2
    def output = input * 4
    def acc = input * 4
    def tileSize = 4
    def meshRow = 8
    def meshCol = 6

}
