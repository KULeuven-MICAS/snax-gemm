package gemm
import chisel3._
import chisel3.util._
import chisel3.VecInit

// Control signals for Gemm. Currently, we only have data_valid_i
class TileControl extends Bundle {
  val data_valid_i = Input(Bool())
  val accumulate_i = Input(Bool())
}

// Tile IO definition
class TileIO extends Bundle {
  val a_i = Input(
    Vec(GEMMConstant.tileSize, UInt(GEMMConstant.input.W))
  )
  val b_i = Input(
    Vec(GEMMConstant.tileSize, UInt(GEMMConstant.input.W))
  )
  val c_o = Output(SInt(GEMMConstant.output.W))
  val control_i = Input(new TileControl())
  val data_valid_o = Output(Bool())
}

// Tile implementation, do a vector dot product of two vector
// When data_valid_i assert, do the computation, and give the result next cycle, with a data_valid_o assert
class Tile extends Module {
  val io = IO(new TileIO())

  val accumulation_reg = RegInit(0.S(GEMMConstant.accumulate.W))
  val result_reg = RegInit(0.S(GEMMConstant.accumulate.W))
  val check_data_in_valid_reg = RegInit(0.B)
  val mul_add_result_vec = Wire(
    Vec(GEMMConstant.tileSize, SInt(GEMMConstant.mul.W))
  )
  val mul_add_result = Wire(SInt(GEMMConstant.accumulate.W))

  chisel3.dontTouch(mul_add_result)

  check_data_in_valid_reg := io.control_i.data_valid_i

  // Element-wise multiply
  for (i <- 0 until GEMMConstant.tileSize) {
    mul_add_result_vec(i) := io.a_i(i).asSInt * io
      .b_i(i)
      .asSInt
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
  io.data_valid_o := check_data_in_valid_reg
}

// Mesh IO definition, an extended version of Tile IO
class MeshIO extends Bundle {
  val a_i = Input(
    Vec(
      GEMMConstant.meshRow,
      Vec(GEMMConstant.tileSize, UInt(GEMMConstant.input.W))
    )
  )
  val b_i = Input(
    Vec(
      GEMMConstant.meshCol,
      Vec(GEMMConstant.tileSize, UInt(GEMMConstant.input.W))
    )
  )
  val c_o = Output(
    (Vec(
      GEMMConstant.meshRow,
      Vec(GEMMConstant.meshCol, SInt(GEMMConstant.output.W))
    ))
  )
  val control_i = Input(new TileControl())
  val data_valid_o = Output(Bool())
}

// Mesh implementation, just creat a mesh of TIles and do the connection
class Mesh extends Module {

  val io = IO(new MeshIO())

  chisel3.dontTouch(io)

  val mesh =
    Seq.fill(GEMMConstant.meshRow, GEMMConstant.meshCol)(
      Module(new Tile())
    )

  for (r <- 0 until GEMMConstant.meshRow) {
    for (c <- 0 until GEMMConstant.meshCol) {
      mesh(r)(c).io.control_i <> io.control_i
      mesh(r)(c).io.a_i <> io.a_i(r)
      mesh(r)(c).io.b_i <> io.b_i(c)
      io.c_o(r)(c) := mesh(r)(c).io.c_o
    }
  }
  io.data_valid_o := mesh(0)(0).io.data_valid_o
}

class GemmDataIO extends Bundle {
  val a_i = Input(
    UInt(
      (GEMMConstant.meshRow * GEMMConstant.tileSize * GEMMConstant.input).W
    )
  )
  val b_i = Input(
    UInt(
      (GEMMConstant.tileSize * GEMMConstant.meshCol * GEMMConstant.input).W
    )
  )
  val c_o = Output(
    UInt(
      (GEMMConstant.meshRow * GEMMConstant.meshCol * GEMMConstant.output).W
    )
  )
}

// Gemm IO definition
class GemmArrayIO extends Bundle {
  val data_valid_i = Input(Bool())
  val accumulate_i = Input(Bool())
  val data_valid_o = Output(Bool())
  val data = new GemmDataIO()
}

// Gemm implementation, create a Mesh and give out input data and collect results of each Tile
class GemmArray extends Module {

  val io = IO(new GemmArrayIO())

  val mesh = Module(new Mesh())

  // define wires for data partition
  val a_in_wire = Wire(
    Vec(
      GEMMConstant.meshRow,
      Vec(GEMMConstant.tileSize, UInt(GEMMConstant.input.W))
    )
  )
  val b_in_wire = Wire(
    Vec(
      GEMMConstant.meshCol,
      Vec(GEMMConstant.tileSize, UInt(GEMMConstant.input.W))
    )
  )
  val c_out_wire = Wire(
    Vec(
      GEMMConstant.meshRow,
      Vec(GEMMConstant.meshCol, SInt(GEMMConstant.output.W))
    )
  )
  val c_out_wire_2 = Wire(
    Vec(
      GEMMConstant.meshRow,
      UInt((GEMMConstant.meshCol * GEMMConstant.output).W)
    )
  )

  // data partition
  for (r <- 0 until GEMMConstant.meshRow) {
    for (c <- 0 until GEMMConstant.tileSize) {
      a_in_wire(r)(c) := io.data.a_i(
        (r * GEMMConstant.tileSize + c + 1) * GEMMConstant.input - 1,
        (r * GEMMConstant.tileSize + c) * GEMMConstant.input
      )
    }
  }

  for (r <- 0 until GEMMConstant.meshCol) {
    for (c <- 0 until GEMMConstant.tileSize) {
      b_in_wire(r)(c) := io.data.b_i(
        (r * GEMMConstant.tileSize + c + 1) * GEMMConstant.input - 1,
        (r * GEMMConstant.tileSize + c) * GEMMConstant.input
      )
    }
  }

  for (r <- 0 until GEMMConstant.meshRow) {
    for (c <- 0 until GEMMConstant.meshCol) {
      c_out_wire(r)(c) := mesh.io.c_o(r)(c)
    }
    c_out_wire_2(r) := Cat(c_out_wire(r).reverse)
  }

  // data and control signal connect
  a_in_wire <> mesh.io.a_i
  b_in_wire <> mesh.io.b_i
  io.data.c_o := Cat(c_out_wire_2.reverse)

  mesh.io.control_i.data_valid_i := io.data_valid_i
  mesh.io.control_i.accumulate_i := io.accumulate_i
  io.data_valid_o := mesh.io.data_valid_o
}
