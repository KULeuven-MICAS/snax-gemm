package gemm
import chisel3._
import chisel3.util._
import chisel3.VecInit

// Control signals for Gemm. Currently, we only have data_valid_i and accumulate_i
class TileControl extends Bundle {
  val data_valid_i = Input(Bool())
  val accumulate_i = Input(Bool())
  val data_ready_o = Input(Bool())
}

// Tile IO definition
class TileIO extends Bundle {
  val data_a_i = Input(
    Vec(GemmConstant.tileSize, UInt(GemmConstant.dataWidthA.W))
  )
  val data_b_i = Input(
    Vec(GemmConstant.tileSize, UInt(GemmConstant.dataWidthB.W))
  )
  val c_o = Output(SInt(GemmConstant.dataWidthC.W))
  val control_i = Input(new TileControl())
  val data_valid_o = Output(Bool())
}

// Tile implementation, do a vector dot product of two vector
// When data_valid_i assert, do the computation, and give the result next cycle, with a data_valid_o assert
class Tile extends Module {
  val io = IO(new TileIO())

  val accumulation_reg = RegInit(0.S(GemmConstant.dataWidthAccum.W))
  val result_reg = RegInit(0.S(GemmConstant.dataWidthAccum.W))
  val check_data_i_valid_reg = RegInit(0.B)
  val keep_output = RegInit(false.B)
  val mul_add_result_vec = Wire(
    Vec(GemmConstant.tileSize, SInt(GemmConstant.dataWidthMul.W))
  )
  val mul_add_result = Wire(SInt(GemmConstant.dataWidthAccum.W))

  chisel3.dontTouch(mul_add_result)

  check_data_i_valid_reg := io.control_i.data_valid_i
  // when ont ready, keep sending valid
  keep_output := io.data_valid_o && !io.control_i.data_ready_o

  // Element-wise multiply
  for (i <- 0 until GemmConstant.tileSize) {
    mul_add_result_vec(i) := io.data_a_i(i).asSInt * io.data_b_i(i).asSInt
  }

  // Sum of element-wise multiply
  mul_add_result := mul_add_result_vec.reduce((a, b) => (a.asSInt + b.asSInt))

  when(io.control_i.data_valid_i === 1.B) {
    result_reg := accumulation_reg + mul_add_result
  }

  // Accumulation, if io.control_i.accumulate === 0.B, clear the accumulation reg, otherwise store the current results
  when(
    io.control_i.data_valid_i === 1.B && io.control_i.accumulate_i === 0.B
  ) {
    accumulation_reg := 0.S
  }.elsewhen(
    io.control_i.data_valid_i === 1.B && io.control_i.accumulate_i === 1.B
  ) {
    accumulation_reg := accumulation_reg + mul_add_result
  }

  io.c_o := result_reg
  io.data_valid_o := check_data_i_valid_reg || keep_output
}

// Mesh IO definition, an extended version of Tile IO
class MeshIO extends Bundle {
  val data_a_i = Input(
    Vec(
      GemmConstant.meshRow,
      Vec(GemmConstant.tileSize, UInt(GemmConstant.dataWidthA.W))
    )
  )
  val data_b_i = Input(
    Vec(
      GemmConstant.meshCol,
      Vec(GemmConstant.tileSize, UInt(GemmConstant.dataWidthB.W))
    )
  )
  val c_o = Output(
    (Vec(
      GemmConstant.meshRow,
      Vec(GemmConstant.meshCol, SInt(GemmConstant.dataWidthC.W))
    ))
  )
  val control_i = Input(new TileControl())
  val data_valid_o = Output(Bool())
}

// Mesh implementation, just create a mesh of TIles and do the connection
class Mesh extends Module {

  val io = IO(new MeshIO())

  chisel3.dontTouch(io)

  val mesh =
    Seq.fill(GemmConstant.meshRow, GemmConstant.meshCol)(
      Module(new Tile())
    )

  for (r <- 0 until GemmConstant.meshRow) {
    for (c <- 0 until GemmConstant.meshCol) {
      mesh(r)(c).io.control_i <> io.control_i
      mesh(r)(c).io.data_a_i <> io.data_a_i(r)
      mesh(r)(c).io.data_b_i <> io.data_b_i(c)
      io.c_o(r)(c) := mesh(r)(c).io.c_o
    }
  }
  io.data_valid_o := mesh(0)(0).io.data_valid_o
}

class GemmDataIO extends Bundle {
  val a_i = Input(
    UInt(
      (GemmConstant.meshRow * GemmConstant.tileSize * GemmConstant.dataWidthA).W
    )
  )
  val b_i = Input(
    UInt(
      (GemmConstant.tileSize * GemmConstant.meshCol * GemmConstant.dataWidthB).W
    )
  )
  val c_o = Output(
    UInt(
      (GemmConstant.meshRow * GemmConstant.meshCol * GemmConstant.dataWidthC).W
    )
  )
}

// Gemm IO definition
class GemmArrayIO extends Bundle {
  val data_valid_i = Input(Bool())
  val accumulate_i = Input(Bool())
  val data_valid_o = Output(Bool())
  val data_ready_o = Input(Bool())
  val data = new GemmDataIO()
}

// Gemm implementation, create a Mesh and give out input data and collect results of each Tile
class GemmArray extends Module {

  val io = IO(new GemmArrayIO())

  val mesh = Module(new Mesh())

  // define wires for data partition
  val a_i_wire = Wire(
    Vec(
      GemmConstant.meshRow,
      Vec(GemmConstant.tileSize, UInt(GemmConstant.dataWidthA.W))
    )
  )
  val b_i_wire = Wire(
    Vec(
      GemmConstant.meshCol,
      Vec(GemmConstant.tileSize, UInt(GemmConstant.dataWidthB.W))
    )
  )
  val c_out_wire = Wire(
    Vec(
      GemmConstant.meshRow,
      Vec(GemmConstant.meshCol, SInt(GemmConstant.dataWidthC.W))
    )
  )
  val c_out_wire_2 = Wire(
    Vec(
      GemmConstant.meshRow,
      UInt((GemmConstant.meshCol * GemmConstant.dataWidthC).W)
    )
  )

  // data partition
  for (r <- 0 until GemmConstant.meshRow) {
    for (c <- 0 until GemmConstant.tileSize) {
      a_i_wire(r)(c) := io.data.a_i(
        (r * GemmConstant.tileSize + c + 1) * GemmConstant.dataWidthA - 1,
        (r * GemmConstant.tileSize + c) * GemmConstant.dataWidthA
      )
    }
  }

  for (r <- 0 until GemmConstant.meshCol) {
    for (c <- 0 until GemmConstant.tileSize) {
      b_i_wire(r)(c) := io.data.b_i(
        (r * GemmConstant.tileSize + c + 1) * GemmConstant.dataWidthB - 1,
        (r * GemmConstant.tileSize + c) * GemmConstant.dataWidthB
      )
    }
  }

  for (r <- 0 until GemmConstant.meshRow) {
    for (c <- 0 until GemmConstant.meshCol) {
      c_out_wire(r)(c) := mesh.io.c_o(r)(c)
    }
    c_out_wire_2(r) := Cat(c_out_wire(r).reverse)
  }

  // data and control signal connect
  a_i_wire <> mesh.io.data_a_i
  b_i_wire <> mesh.io.data_b_i
  io.data.c_o := Cat(c_out_wire_2.reverse)

  mesh.io.control_i.data_valid_i := io.data_valid_i
  mesh.io.control_i.accumulate_i := io.accumulate_i
  mesh.io.control_i.data_ready_o := io.data_ready_o
  io.data_valid_o := mesh.io.data_valid_o
}

object GemmArray extends App {
  emitVerilog(
    new (GemmArray),
    Array("--target-dir", "generated/gemm")
  )
}
