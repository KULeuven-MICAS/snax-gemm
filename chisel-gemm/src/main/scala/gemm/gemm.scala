package gemm
import chisel3._
import chisel3.util._
import chisel3.experimental._
// import chisel3.experimental.{chiselName,NoChiselNamePrefix}
import chisel3.experimental.IntParam
import chisel3.VecInit
import chisel3.util.HasBlackBoxResource
import scala.util.matching.Regex
import chisel3.util.MixedVec

class TileControl extends Bundle{
    val data_in_valid = Input(Bool())
}

class TileIO extends Bundle{
    val a_io_in = Input(Vec(GEMMConstant.meshRow,GEMMConstant.inputType))
    val b_io_in = Input(Vec(GEMMConstant.meshCol,GEMMConstant.inputType))
    val c_io_out = Output(GEMMConstant.outputType)
    val data_out_valid = Output(Bool())   

    val in_control = Input(new TileControl())
}

class Tile extends Module{
    val io = IO (new TileIO())
 
    val accumulation_reg = RegInit(0.U(GEMMConstant.acc.W))  
    val chek_data_in_valid = RegInit(0.B)
    val mul_add_result = Wire(UInt(GEMMConstant.mul.W))
    val mul_add_result_vec_reg = RegInit(VecInit(Seq.fill(GEMMConstant.meshRow)(0.U(GEMMConstant.mul.W))))
    val mid_reg_vec = RegInit(VecInit(Seq.fill(4)(0.U(GEMMConstant.mul.W))))
    val ctrl_acc = RegInit(0.B)
    val chek_data_in_valid_reg = RegInit(0.B)

    chisel3.dontTouch(mul_add_result)
    
    //posedge
    chek_data_in_valid := io.in_control.data_in_valid
    chek_data_in_valid_reg := chek_data_in_valid
    //stage1
    for( i <- 0 until GEMMConstant.meshRow){
        mul_add_result_vec_reg(i) := io.a_io_in(i) * io.b_io_in(i)
    }  

    //stage3  //out->valid
    mul_add_result := mul_add_result_vec_reg.reduce((a:UInt,b:UInt)=>a+b)
    when(chek_data_in_valid === 1.B){
        accumulation_reg := accumulation_reg + mul_add_result
    }
    //stage4   clear
    when(chek_data_in_valid_reg){
        accumulation_reg := 0.U
    }

    io.c_io_out := accumulation_reg
    io.data_out_valid := chek_data_in_valid_reg
}

class MeshIO extends Bundle{
    val a_io_in = Input(Vec(GEMMConstant.meshRow,Vec(GEMMConstant.meshCol, GEMMConstant.inputType)))
    val b_io_in = Input(Vec(GEMMConstant.meshCol,Vec(GEMMConstant.meshRow,GEMMConstant.inputType)))
    val c_io_out = Output((Vec(GEMMConstant.meshRow,Vec(GEMMConstant.meshCol,GEMMConstant.outputType))))
    val in_control = Input(new TileControl())
    val data_out_valid = Output(Bool())
}

class Mesh extends Module{

    val io = IO(new MeshIO())

    chisel3.dontTouch(io)

    val mesh = Seq.fill(GEMMConstant.meshRow,GEMMConstant.meshCol)(Module(new Tile()))

    for( r <- 0 until GEMMConstant.meshRow){
        for( c <- 0 until GEMMConstant.meshCol){
            mesh(r)(c).io.in_control <> io.in_control
            mesh(r)(c).io.a_io_in <> io.a_io_in(r)
            mesh(r)(c).io.b_io_in <> io.b_io_in(c)
            io.c_io_out(r)(c) := mesh(r)(c).io.c_io_out 
        }
    }
    io.data_out_valid := mesh(0)(0).io.data_out_valid
}

class  GemmIO extends Bundle{
    val data_in_valid = Input(Bool())
    val a_io_in = Input(UInt((GEMMConstant.meshRow * GEMMConstant.meshCol * GEMMConstant.inputLen).W))
    val b_io_in = Input(UInt((GEMMConstant.meshRow * GEMMConstant.meshCol * GEMMConstant.inputLen).W))

    val data_out_valid = Output(Bool())
    val c_io_out = Output(UInt((GEMMConstant.meshRow * GEMMConstant.meshCol * GEMMConstant.outputLen).W))
}

class Gemm extends Module{

    val io = IO(new GemmIO())

    val mesh = Module(new Mesh())

    // data partition
    val a_in_wire = Wire(Vec(GEMMConstant.meshRow,Vec(GEMMConstant.meshCol, GEMMConstant.inputType)))
    val b_in_wire = Wire(Vec(GEMMConstant.meshCol,Vec(GEMMConstant.meshRow, GEMMConstant.inputType)))
    val c_out_wire = Wire(Vec(GEMMConstant.meshRow,Vec(GEMMConstant.meshCol, GEMMConstant.outputType)))
    val c_out_wire_2 = Wire(Vec(GEMMConstant.meshRow,UInt((GEMMConstant.meshCol * GEMMConstant.outputLen).W)))

    for( r <- 0 until GEMMConstant.meshRow){
        for ( c <- 0 until GEMMConstant.meshCol){
            a_in_wire(r)(c) := io.a_io_in((r * GEMMConstant.meshRow  + c  + 1) * GEMMConstant.inputLen - 1, (r * GEMMConstant.meshRow  + c ) * GEMMConstant.inputLen) 
            b_in_wire(r)(c) := io.b_io_in((r * GEMMConstant.meshCol  + c  + 1) * GEMMConstant.inputLen - 1, (r * GEMMConstant.meshCol  + c ) * GEMMConstant.inputLen)
            c_out_wire(r)(c) := mesh.io.c_io_out(r)(c)            
        }
        c_out_wire_2(r) := Cat(c_out_wire(r).reverse)
    }

    // data signal connect
    a_in_wire <> mesh.io.a_io_in
    b_in_wire <> mesh.io.b_io_in

    io.c_io_out := Cat(c_out_wire_2.reverse)
    mesh.io.in_control.data_in_valid := io.data_in_valid
    io.data_out_valid := mesh.io.data_out_valid
}
