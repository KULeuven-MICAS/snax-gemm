package gemm
import chisel3._
import chisel3.util._
import chisel3.VecInit

// Control signals for Gemm. Cuurently, we only have data_in_valid
class TileControl extends Bundle{
    val data_in_valid = Input(Bool())    
}

// Tile IO definition
class TileIO extends Bundle{
    val a_io_in = Input(Vec(GEMMConstant.tileSize,UInt(GEMMConstant.input.W)))
    val b_io_in = Input(Vec(GEMMConstant.tileSize,UInt(GEMMConstant.input.W)))
    val c_io_out = Output(UInt(GEMMConstant.output.W))
    val in_control = Input(new TileControl())
    val data_out_valid = Output(Bool())  
}

// Tile implementation, do a vector dot product of two vector
// When data_in_valid assert, do the computation, and give the result next cycle, with a data_out_valid assert
class Tile extends Module{
    val io = IO (new TileIO())
 
    val accumulation_reg = RegInit(0.U(GEMMConstant.acc.W))  
    val chek_data_in_valid = RegInit(0.B)
    val mul_add_result = Wire(UInt(GEMMConstant.mul.W))
    val mul_add_result_vec_reg = Wire(Vec(GEMMConstant.tileSize,UInt(GEMMConstant.mul.W)))

    chisel3.dontTouch(mul_add_result)
    
    chek_data_in_valid := io.in_control.data_in_valid

    for( i <- 0 until GEMMConstant.tileSize){
        mul_add_result_vec_reg(i) := io.a_io_in(i) * io.b_io_in(i)
    }  

    mul_add_result := mul_add_result_vec_reg.reduce((a:UInt,b:UInt)=>a+b)
    when(io.in_control.data_in_valid === 1.B){
        accumulation_reg := mul_add_result
    }

    when(chek_data_in_valid){
        accumulation_reg := 0.U
    }

    io.c_io_out := accumulation_reg
    io.data_out_valid := chek_data_in_valid
}

// Mesh IO definition, an extended version of Tile IO
class MeshIO extends Bundle{
    val a_io_in = Input(Vec(GEMMConstant.meshRow,Vec(GEMMConstant.tileSize, UInt(GEMMConstant.input.W))))
    val b_io_in = Input(Vec(GEMMConstant.meshCol,Vec(GEMMConstant.tileSize,UInt(GEMMConstant.input.W))))
    val c_io_out = Output((Vec(GEMMConstant.meshRow,Vec(GEMMConstant.meshCol,UInt(GEMMConstant.output.W)))))
    val in_control = Input(new TileControl())
    val data_out_valid = Output(Bool())  
}

// Mesh implementation, just creat a mesh of TIles and do the connection
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

// Gemm IO definition
class  GemmIO extends Bundle{
    val data_in_valid = Input(Bool())
    val a_io_in = Input(UInt((GEMMConstant.meshRow * GEMMConstant.tileSize * GEMMConstant.input).W))
    val b_io_in = Input(UInt((GEMMConstant.tileSize * GEMMConstant.meshCol * GEMMConstant.input).W))
    val data_out_valid = Output(Bool())
    val c_io_out = Output(UInt((GEMMConstant.meshRow * GEMMConstant.meshCol * GEMMConstant.output).W))
}

// Gemm implementation, create a Mesh and give out input data and collect results of each Tile
class Gemm extends Module{

    val io = IO(new GemmIO())

    val mesh = Module(new Mesh())

    // data partition
    val a_in_wire = Wire(Vec(GEMMConstant.meshRow,Vec(GEMMConstant.tileSize, UInt(GEMMConstant.input.W))))
    val b_in_wire = Wire(Vec(GEMMConstant.meshCol,Vec(GEMMConstant.tileSize, UInt(GEMMConstant.input.W))))
    val c_out_wire = Wire(Vec(GEMMConstant.meshRow,Vec(GEMMConstant.meshCol, UInt(GEMMConstant.input.W))))
    val c_out_wire_2 = Wire(Vec(GEMMConstant.meshRow,UInt((GEMMConstant.meshCol * GEMMConstant.output).W)))

    for( r <- 0 until GEMMConstant.meshRow){
        for ( c <- 0 until GEMMConstant.tileSize){
            a_in_wire(r)(c) := io.a_io_in((r * GEMMConstant.tileSize  + c  + 1) * GEMMConstant.input - 1, (r * GEMMConstant.tileSize  + c ) * GEMMConstant.input) 
        }
    }    

    for( r <- 0 until GEMMConstant.meshCol){
        for ( c <- 0 until GEMMConstant.tileSize){
            b_in_wire(r)(c) := io.b_io_in((r * GEMMConstant.tileSize  + c  + 1) * GEMMConstant.input - 1, (r * GEMMConstant.tileSize  + c ) * GEMMConstant.input)
        }
    }

    for( r <- 0 until GEMMConstant.meshRow){
        for ( c <- 0 until GEMMConstant.meshCol){
            c_out_wire(r)(c) := mesh.io.c_io_out(r)(c)            
        }
        c_out_wire_2(r) := Cat(c_out_wire(r).reverse)
    }

    // data signal connect
    a_in_wire <> mesh.io.a_io_in
    b_in_wire <> mesh.io.b_io_in
    io.c_io_out := Cat(c_out_wire_2.reverse)

    mesh.io.in_control.data_in_valid <> io.data_in_valid
    io.data_out_valid := mesh.io.data_out_valid
}
