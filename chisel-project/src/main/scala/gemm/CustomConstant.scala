package gemm
import chisel3._
import chisel3.util._

object GEMMConstant{
    def W_DATA = 32
    def W_ADDR = 32
    def input = 8 
    def inputType = UInt(input.W)
    def inputLen = input
    def inputLenUInt = input.U 

    def mul = 8 * 2
    def mulType = UInt(mul.W) 
    def mulLenUInt = mul.U 
    def mulLen = mul

    def output = 8 * 4
    def outputType = UInt(output.W) 
    def outputLenUInt = output.U 
    def outputLen = output

    def acc = 8 * 4
    def accType = UInt(acc.W) 
    def accLen = acc
    
    def dim = 8
    def meshRow = dim
    def meshCol = dim
    def meshRowUInt = dim.U
    def meshColUInt = dim.U

}
