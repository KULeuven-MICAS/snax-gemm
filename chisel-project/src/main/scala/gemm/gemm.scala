package gemm
import chisel3._
import chisel3.util._
import chisel3.experimental._
// import chisel3.experimental.{chiselName,NoChiselNamePrefix}
import chisel3.experimental.IntParam
// import chisel3.experimental.verification.chiselName
import chisel3.VecInit
import chisel3.util.HasBlackBoxResource
import scala.util.matching.Regex
// import com.google.protobuf.UInt32Value
import chisel3.util.MixedVec

class GEMM_Cmd() extends Bundle {

    val M = Bits(width = 8.W)
    val K = Bits(width = 16.W)    
    val N = Bits(width = 8.W)
    val S = Bits(width = 1.W)

    val Rm = Bits(width = C.W_ADDR.W)
    val Rn = Bits(width = C.W_ADDR.W)
    val Rd = Bits(width = C.W_ADDR.W)

    val shift_direction = Bits(width = 1.W)
    val shift_number = Bits(width = 3.W)

}

class TileControl extends Bundle{
    val data_in_valid = Input(Bool())
    // val accumulation = Input(Bool())
    val shift_direction = Input(UInt(1.W))
    val shift_number = Input(UInt(3.W))
    val gemm_done = Input(Bool())
}

class TileIO extends Bundle{
    val a_io_in = Input(Vec(GEMMConstant.meshRow,GEMMConstant.inputType))
    val b_io_in = Input(Vec(GEMMConstant.meshCol,GEMMConstant.inputType))
    val c_io_out = Output(GEMMConstant.outputType)
    val data_out_valid = Output(Bool())   

    val doGemm = Input(Bool())
    val clear_valid = Input(Bool())
    val accelerator_valid = Input(Bool())
    //inter contoller
    val in_control = Input(new TileControl())
}

class Tile extends Module{
    val io = IO (new TileIO())

    // val accumulation_reg = Reg(GEMMConstant.accType)  
    val accumulation_reg = RegInit(0.U(GEMMConstant.acc.W))  
    val chek_data_in_valid = RegInit(0.B)
    val mul_add_result = Wire(UInt(GEMMConstant.mul.W))
    // val mul_add_result_vec = Wire(Vec(GEMMConstant.meshRow,GEMMConstant.accType)) 
    val mul_add_result_vec_reg = RegInit(VecInit(Seq.fill(GEMMConstant.meshRow)(0.U(GEMMConstant.mul.W))))
    val mid_reg_vec = RegInit(VecInit(Seq.fill(4)(0.U(GEMMConstant.mul.W))))
    val ctrl_acc = RegInit(0.B)
    val chek_data_in_valid_reg = RegInit(0.B)
    val chek_data_in_valid_reg_reg = RegInit(0.B)

    
    chisel3.dontTouch(mul_add_result)
    // chisel3.dontTouch(mul_add_result_vec)
    
    //posedge
    chek_data_in_valid := io.in_control.data_in_valid
    chek_data_in_valid_reg := chek_data_in_valid
    chek_data_in_valid_reg_reg := chek_data_in_valid_reg

    //stage1
    for( i <- 0 until GEMMConstant.meshRow){
        mul_add_result_vec_reg(i) := io.a_io_in(i) * io.b_io_in(i)
    }  
    //stage2
    // mul_add_result := mul_add_result_vec.reduce((a:UInt,b:UInt)=>a+b)
    for(j <- 0 until GEMMConstant.meshRow/4){
        mid_reg_vec(j) := mul_add_result_vec_reg(j*4) + mul_add_result_vec_reg(j*4 + 1) + mul_add_result_vec_reg(j*4 + 2)+mul_add_result_vec_reg(j*4 + 3)
    }
    //stage3  //out->valid
    mul_add_result := mid_reg_vec.reduce((a:UInt,b:UInt)=>a+b)
    when(chek_data_in_valid_reg === 1.B){
        accumulation_reg := accumulation_reg + mul_add_result
    }
    //stage4   clear
    when((io.clear_valid === 1.B) && (io.in_control.gemm_done === 1.B || ShiftRegister(io.in_control.gemm_done,10,1.B) === 1.B)){
        accumulation_reg := 0.U
    }
    // when((io.clear_valid === 1.B)){
    //     accumulation_reg := 0.U
    // }    

    io.c_io_out := accumulation_reg
    io.data_out_valid := chek_data_in_valid_reg_reg
}

class  GEMM_ControllerIO extends Bundle{
    
    val start_do = Input(Bool())
    val done = Output(Bool())
    val mesh_compute_done = Input(Bool())

    val addr_a_in = Output(UInt(20.W))
    val addr_b_in = Output(UInt(20.W))
    val addr_in_valid = Output(Bool())

    val data_out_valid = Output(Bool())
    val addr_c_out = Output(UInt(20.W))
    val doGemm = Output(Bool())

    val accelerator_valid = Output(Bool())
    // signal for mesh
    // val accumulation = Output(Bool())
    val shift_direction = Output(UInt(1.W))
    val shift_number = Output(UInt(3.W))
    val clear_valid = Output(Bool())
    // val shift_direction = Input(UInt(1.W))
    // val shift_number = Input(UInt(3.W))
    val inst = Input(new GEMM_Cmd())
}

class GEMM_Controller extends Module{
    val io = IO(new GEMM_ControllerIO())

    // save instructure

    val check_start_do = RegInit(false.B)
    val M_reg = RegInit(0.U(8.W))    
    val N_reg = RegInit(0.U(8.W))    
    val K_reg = RegInit(0.U(8.W))    
    val Rm_reg = RegInit(0.U(20.W))    
    val Rn_reg = RegInit(0.U(20.W))    
    val Rd_reg = RegInit(0.U(20.W))    
    val S_reg = RegInit(0.U(1.W))

    val shift_direction_reg = RegInit(0.U(1.W)) 
    val shift_number_reg = RegInit(0.U(3.W))    

    def MatrixByteIn = GEMMConstant.meshRow.U * GEMMConstant.meshCol.U * GEMMConstant.input.U / (C.W_DATA.U)
    def MatrixByteOut =  GEMMConstant.meshRow.U * GEMMConstant.meshCol.U * GEMMConstant.output.U / (C.W_DATA.U)

    // def IDLE = 0.U
    // def COMP = 1.U
    // def OUT  = 2.U
    // def DONE = 3.U

    val k_mul_done = Wire(Bool())
    // val n_mul_done = Wire(Bool()) 
    // val m_mul_done = Wire(Bool())   

    val sIDLE :: sFetch :: sCOMP :: sDONE :: Nil = Enum(4)
    val gemm_state_current = RegInit(sIDLE)
    // val gemm_state_next = Wire(0.U(2.W))

    val k_mul_counter = RegInit(0.U(16.W))
    val n_mul_counter = RegInit(0.U(16.W))
    val m_mul_counter = RegInit(0.U(16.W)) 


    io.clear_valid := (S_reg === 1.U)
    io.accelerator_valid := (gemm_state_current === sCOMP)
    check_start_do := io.start_do 

    when(check_start_do === 1.B){
        M_reg := io.inst.M
        N_reg := io.inst.N
        K_reg := io.inst.K
        Rm_reg := io.inst.Rm
        Rn_reg := io.inst.Rn
        Rd_reg := io.inst.Rd
        S_reg := io.inst.S
        shift_direction_reg := io.inst.shift_direction
        shift_number_reg := io.inst.shift_number
    }


    k_mul_done := k_mul_counter === K_reg
    chisel3.dontTouch(gemm_state_current)
    //state transform
    // gemm_state_current := gemm_state_next
    //state 
    switch(gemm_state_current){
        is(sIDLE){
            when(io.start_do){
                gemm_state_current := sFetch
            }.otherwise{
                gemm_state_current := sIDLE
            }
        }
        is(sFetch){
            gemm_state_current := sCOMP
        }
        is(sCOMP){
            when(io.mesh_compute_done){
                when(k_mul_done){
                    gemm_state_current := sDONE
                }.otherwise{
                    gemm_state_current := sFetch
                }
            }.otherwise{
                gemm_state_current := sCOMP
            }
        }
        is(sDONE){
            gemm_state_current := sIDLE
        }
    }

    io.addr_a_in := Rm_reg  + (k_mul_counter )* MatrixByteIn   
    io.addr_b_in := Rn_reg  + (k_mul_counter )* MatrixByteIn 
    io.addr_in_valid := gemm_state_current === sFetch
    
    when(io.addr_in_valid){
        k_mul_counter := k_mul_counter + 1.U
    }
    switch(gemm_state_current){
        is(sIDLE){
            io.addr_in_valid := false.B
            k_mul_counter := 0.U
        }
        // is(sFetch){
            
        // }
        // is(sCOMP){
        //     when(io.mesh_compute_done === 1.B){
        //         k_mul_counter := k_mul_counter + 1.U
        //     }
        // }
        // is(sDONE){
        //     io.addr_in_valid := false.B
        // }
    }
    io.data_out_valid := gemm_state_current === sDONE
    io.shift_direction := shift_direction_reg
    io.shift_number := shift_number_reg 
    io.addr_c_out := Rd_reg
    io.done := io.data_out_valid 
    io.doGemm := gemm_state_current === sCOMP

}

class MeshIO extends Bundle{
    val a_io_in = Input(Vec(GEMMConstant.meshRow,Vec(GEMMConstant.meshCol, GEMMConstant.inputType)))
    val b_io_in = Input(Vec(GEMMConstant.meshCol,Vec(GEMMConstant.meshRow,GEMMConstant.inputType)))

    val c_io_out = Output((Vec(GEMMConstant.meshRow,Vec(GEMMConstant.meshCol,GEMMConstant.outputType))))
    //accumulator_valid
    val accelerator_valid = Input(Bool())
    val clear_valid = Input(Bool()) 
    val doGemm = Input(Bool())
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
            mesh(r)(c).io.clear_valid := io.clear_valid
            mesh(r)(c).io.doGemm := io.doGemm
            
            mesh(r)(c).io.accelerator_valid := io.accelerator_valid
        }
    }
    io.data_out_valid := mesh(0)(0).io.data_out_valid
}

class  GemmIO extends Bundle{
    val start_do = Input(Bool())
    val done = Output(Bool())

    val addr_in_valid = Output(Bool())
    val addr_a_in = Output(UInt(20.W))
    val addr_b_in = Output(UInt(20.W))

    val data_in_valid = Input(Bool())
    // input data
    // a size of gemm data every time
    val a_io_in = Input(UInt((GEMMConstant.meshRow * GEMMConstant.meshCol * GEMMConstant.inputLen).W))
    val b_io_in = Input(UInt((GEMMConstant.meshRow * GEMMConstant.meshCol * GEMMConstant.inputLen).W))
    // val d_io_in = Input(UInt((GEMMConstant.meshRow * GEMMConstant.meshCol * GEMMConstant.outputLen).W))

    val data_out_valid = Output(Bool())

    // output data
    val c_io_out = Output(UInt((GEMMConstant.meshRow * GEMMConstant.meshCol * GEMMConstant.outputLen).W))
    val addr_c_out = Output(UInt(20.W))

    val inst = Input(new GEMM_Cmd())
}

class Gemm extends Module{

    val io = IO(new GemmIO())

    // meshcontrol signal
    val mesh = Module(new Mesh())

    val controller = Module(new GEMM_Controller())

    //clear_valid connect  
    mesh.io.clear_valid <> controller.io.clear_valid
    mesh.io.doGemm <> controller.io.doGemm
    controller.io.accelerator_valid <> mesh.io.accelerator_valid //no use
    //test clear
    chisel3.dontTouch(controller.io.clear_valid)

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
    // controller signal connect
    // input
    controller.io.start_do := io.start_do
    controller.io.mesh_compute_done := mesh.io.data_out_valid
    controller.io.inst <> io.inst
    // output
    io.done := controller.io.done

    io.addr_in_valid := controller.io.addr_in_valid
    io.addr_a_in := controller.io.addr_a_in
    io.addr_b_in := controller.io.addr_b_in
    io.data_out_valid := controller.io.data_out_valid
    io.addr_c_out := controller.io.addr_c_out

    // mesh.io.in_control.accumulation := controller.io.accumulation
    mesh.io.in_control.shift_direction := controller.io.shift_direction
    mesh.io.in_control.shift_number := controller.io.shift_number
    mesh.io.in_control.data_in_valid := io.data_in_valid
    mesh.io.in_control.gemm_done := controller.io.done

}