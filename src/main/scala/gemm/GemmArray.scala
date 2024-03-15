package gemm
import chisel3._
import chisel3.util._
import chisel3.VecInit

// Control signals for Gemm. Currently, we only have a_b_valid_i and accumulate_i
class TileControl extends Bundle {
  val a_b_valid_i = Input(Bool())
  val accumulate_i = Input(Bool())
  val c_ready_i = Input(Bool())
}

// Tile IO definition
class TileIO extends Bundle {
  val data_a_i = Input(
    Vec(GemmConstant.tileSize, UInt(GemmConstant.dataWidthA.W))
  )
  val data_b_i = Input(
    Vec(GemmConstant.tileSize, UInt(GemmConstant.dataWidthB.W))
  )
  val data_c_o = Output(SInt(GemmConstant.dataWidthC.W))
  val subtraction_a_i = Input(UInt(GemmConstant.dataWidthA.W))
  val subtraction_b_i = Input(UInt(GemmConstant.dataWidthB.W))

  val control_i = Input(new TileControl())

  val c_valid_o = Output(Bool())
  val a_b_ready_o = Output(Bool())
}

// Tile implementation, do a vector dot product of two vector
// !!! When a_b_valid_i and a_b_ready_o assert, do the computation, and give the result next cycle, with a c_valid_o assert
class Tile extends Module with RequireAsyncReset {
  val io = IO(new TileIO())

  val accumulation_reg = RegInit(0.S(GemmConstant.dataWidthAccum.W))

  val data_i_fire = WireInit(0.B)
  val data_i_fire_reg = RegInit(0.B)

  val keep_output = RegInit(false.B)

  val data_a_i_subtracted = Wire(
    Vec(GemmConstant.tileSize, SInt((GemmConstant.dataWidthA + 1).W))
  )
  val data_b_i_subtracted = Wire(
    Vec(GemmConstant.tileSize, SInt((GemmConstant.dataWidthB + 1).W))
  )
  val mul_add_result_vec = Wire(
    Vec(GemmConstant.tileSize, SInt(GemmConstant.dataWidthMul.W))
  )
  val mul_add_result = Wire(SInt(GemmConstant.dataWidthAccum.W))

  chisel3.dontTouch(mul_add_result)

  // when a_b_valid_i assert, and a_b_ready_o assert, do the computation
  data_i_fire := io.control_i.a_b_valid_i === 1.B && io.a_b_ready_o === 1.B
  // give the result next cycle, with a c_valid_o assert
  data_i_fire_reg := data_i_fire

  // when out c not ready but having a valid result locally, keep sending c_valid_o
  keep_output := io.c_valid_o && !io.control_i.c_ready_i

  // Subtraction computation
  for (i <- 0 until GemmConstant.tileSize) {
    data_a_i_subtracted(i) := (io
      .data_a_i(i)
      .asSInt -& io.subtraction_a_i.asSInt).asSInt
    data_b_i_subtracted(i) := (io
      .data_b_i(i)
      .asSInt -& io.subtraction_b_i.asSInt).asSInt
  }

  // Element-wise multiply
  for (i <- 0 until GemmConstant.tileSize) {
    mul_add_result_vec(i) := (data_a_i_subtracted(i)) * (data_b_i_subtracted(i))
  }

  // Sum of element-wise multiply
  mul_add_result := mul_add_result_vec.reduce((a, b) => (a.asSInt + b.asSInt))

  // Accumulation, if io.control_i.accumulate === 0.B, clear the accumulation reg, otherwise store the current results
  when(
    data_i_fire === 1.B && io.control_i.accumulate_i === 0.B
  ) {
    accumulation_reg := mul_add_result
  }.elsewhen(
    data_i_fire === 1.B && io.control_i.accumulate_i === 1.B
  ) {
    accumulation_reg := accumulation_reg + mul_add_result
  }

  io.data_c_o := accumulation_reg
  io.c_valid_o := data_i_fire_reg || keep_output
  io.a_b_ready_o := !keep_output && !(io.c_valid_o && !io.control_i.c_ready_i)

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
  val data_c_o = Output(
    (Vec(
      GemmConstant.meshRow,
      Vec(GemmConstant.meshCol, SInt(GemmConstant.dataWidthC.W))
    ))
  )
  val subtraction_a_i = Input(UInt(GemmConstant.dataWidthA.W))
  val subtraction_b_i = Input(UInt(GemmConstant.dataWidthB.W))
  val control_i = Input(new TileControl())
  val c_valid_o = Output(Bool())
  val a_b_ready_o = Output(Bool())

}

// Mesh implementation, just create a mesh of TIles and do the connection
class Mesh extends Module with RequireAsyncReset {

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
      mesh(r)(c).io.subtraction_a_i <> io.subtraction_a_i
      mesh(r)(c).io.subtraction_b_i <> io.subtraction_b_i
      io.data_c_o(r)(c) := mesh(r)(c).io.data_c_o
    }
  }
  io.c_valid_o := mesh(0)(0).io.c_valid_o
  io.a_b_ready_o := mesh(0)(0).io.a_b_ready_o
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
  val a_b_valid_i = Input(Bool())
  val a_b_ready_o = Output(Bool())
  val c_valid_o = Output(Bool())
  val c_ready_i = Input(Bool())

  val accumulate_i = Input(Bool())

  val subtraction_a_i = Input(UInt(GemmConstant.dataWidthA.W))
  val subtraction_b_i = Input(UInt(GemmConstant.dataWidthB.W))

  val data = new GemmDataIO()
}

// Gemm implementation, create a Mesh and give out input data and collect results of each Tile
class GemmArray extends Module with RequireAsyncReset {

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
      c_out_wire(r)(c) := mesh.io.data_c_o(r)(c)
    }
    c_out_wire_2(r) := Cat(c_out_wire(r).reverse)
  }

  // data and control signal connect
  a_i_wire <> mesh.io.data_a_i
  b_i_wire <> mesh.io.data_b_i
  io.data.c_o := Cat(c_out_wire_2.reverse)

  mesh.io.control_i.a_b_valid_i := io.a_b_valid_i
  mesh.io.control_i.accumulate_i := io.accumulate_i
  mesh.io.control_i.c_ready_i := io.c_ready_i

  mesh.io.subtraction_a_i := io.subtraction_a_i
  mesh.io.subtraction_b_i := io.subtraction_b_i

  io.c_valid_o := mesh.io.c_valid_o
  io.a_b_ready_o := mesh.io.a_b_ready_o

}

object GemmArray extends App {
  val dir_name = "GemmArray_%s_%s_%s_%s".format(
    GemmConstant.meshRow,
    GemmConstant.tileSize,
    GemmConstant.meshCol,
    GemmConstant.dataWidthA
  )
  emitVerilog(
    new GemmArray,
    Array("--target-dir", "generated/%s".format(dir_name))
  )
}
