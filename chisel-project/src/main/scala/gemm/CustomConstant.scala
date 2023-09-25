package gemm
import chisel3._
import chisel3.util._
// import chisel3.experimental.{chiselName, NoChiselNamePrefix}
object DECODER_STATE{
  def IDLE    = 0.U
  def WRITE   = 1.U
  def MEMWAIT = 2.U
  def VLOAD   = 3.U
  def VSTORE  = 4.U
  def MOVE    = 5.U
  def GEMM    = 6.U
  def VE      = 7.U
  def LOOP    = 8.U
  def STOP    = 9.U
  
  def VLOADS = 10.U
  def VSTORES = 11.U 
}

object DMA{
  def W_WR_FW = C.W_DATA * 2 + C.W_ADDR * 2 + 2 //{wr_en_2,wr_en_1,addr2,addr1,data2,data1}
  def W_WR_BW = 2 // {wr_bw_en_2,wr_bw_en_1}
  def W_RD_FW = C.W_ADDR * 2 + 2//{rd_en_2,rd_en_1,addr2,addr1}
  def W_RD_BW = C.W_DATA * 2 + 2//{rd_bw_en_2,rd_bw_en_1,data2,data1}

  def NULL_SEL = C.N_NODE
  
  def PAGE_SIZE = 8
}

object C {
  // def W_ARRAY  = 8           //数据精度
  // def W_DATA   = W_ARRAY * 8 //数据粒度
  //  def W_DATA   = W_ARRAY * 4 //数据粒度
  //16*16*16
  // def W_ARRAY  = 16          //数据精度
  // def W_DATA   = W_ARRAY * 16 //数据粒度
  // def N_ARRAY = 16//脉动阵列size

  //8*8*8
  def W_ARRAY  = 8          //数据精度
  def W_DATA   = W_ARRAY * 4 //数据粒度
  def N_ARRAY = 8          //脉动阵列size
  
  def W_MOVE   = W_DATA * 2 //搬移位宽
  def W_MOVE3  = W_DATA * 8
  def W_ADDR   = 30  //地址位宽
  def W_ADDR_S = 20  //地址位宽
  def W_INDEX  = 4  //模块编号位宽

  def W_ROW = W_ARRAY * N_ARRAY

  def N_NODE = 12  //节点数量
  def N_BUFFER = 10//Buffer数量
  def N_MOVE3 = 6  //Move3节点数量

  // def N_ARRAY = 16//脉动阵列size
  // def N_ARRAY = 8//脉动阵列size
  
  def N_XBAR = 3

  def DEPTH_MOVE_CMD = 16
  def DEPTH_TAG = 18
  
  def LOOP_MAX = 32
}


object CN { //constant no.
  // op unit
  def NaN = 0
  def Core = 1
  def Move_1 = 2
  def Move_2 = 3
  def Move_3 = 4
  def GEMM = 5
  def VE = 6
  def MemSmall = 7
  // mem node
  def InputA = 0

  def InputB = 1

  def FeatureA = 2

  def FeatureB = 3

  def WeightA = 4

  def WeightB = 5

  def MiddleA = 6

  def MiddleB = 7

  def OutputA = 8

  def OutputB = 9

  def DDR4 = 10

  def Memory = 11
}

object CA { //constant addr
  def InputA = 0x20000000

  def InputB = 0x20010000

  def FeatureA = 0x20020000

  def FeatureB = 0x20030000

  def WeightA = 0x20040000

  def WeightB = 0x20050000

  def MiddleA = 0x20060000

  def MiddleB = 0x20070000

  def OutputA = 0x20080000

  def OutputB = 0x20090000

  def DDR4 = 0x00000000
}

object CS { //constant size / bit
  def Input   = 1024 * 1024 * 8 / 256 * C.W_DATA // 1 MB
  // C.W_DATA = 64 Input = 512
  def Feature = Input / 16
  def Weight  = Input / 16

  def Middle  = Input / 16 * 2

  def Output  = Input / 8
}

// ------------------------- gemm & ve constant -----------------------------
object VE_ALU{

    // //16*16*16
    // //length
    // def VE_SIZE = 16
    // //number
    // def VE_NUM = 8

    //8*8*8
    //length
    def VE_SIZE = 8
    //number
    def VE_NUM = 4
    

    def VE_FUNCT_WITH = 8
    // operate data width
    // def WITH = C.W_ARRAY * 2
    def WITH = 32
    def WITH_MODE8 = 8
    

    def ADD = 1.U
    def SUB = 2.U
    def MUL = 3.U
    // def DIV = 4.U

    def MAX = 5.U
    def MIN = 6.U

    def AND = 7.U
    def OR = 8.U
    def SR = 9.U    
    def SL = 10.U

}

object GEMMConstant{

    def input = C.W_ARRAY 
    def inputType = UInt(input.W)
    def inputLen = input
    def inputLenUInt = input.U 

    def mul = C.W_ARRAY * 2
    def mulType = UInt(mul.W) 
    def mulLenUInt = mul.U 
    def mulLen = mul

    def output = C.W_ARRAY * 4
    // def output = C.W_ARRAY 
    

    def outputType = UInt(output.W) 
    def outputLenUInt = output.U 
    def outputLen = output

    def acc = C.W_ARRAY * 4
    def accType = UInt(acc.W) 
    def accLen = acc
    
    def dim = C.N_ARRAY
    def meshRow = dim
    def meshCol = dim
    def meshRowUInt = dim.U
    def meshColUInt = dim.U

}

object BufferConstant{
  
  // 8 Ram 版本
  // def Stage2 = 4

  //16 Ram 版本
  def Input_Way = 16
  def Output_Way = 8
  def Output_Stage2 = Output_Way / 2
  def Stage2 = 8

  def WAY = Stage2  * 2 
}

