package gemm

import chisel3._

// object GemmGen extends App {
//   chisel3.Driver.execute(args, () => new Gemm)
// }

object GemmGen extends App {

//   (new chisel3.stage.ChiselStage).emitVerilog(new Gemm, args)
  getVerilogString(new Gemm)
  emitVerilog(new (Gemm), Array("--target-dir", "generated"))

}

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

class GemmTest extends AnyFlatSpec with ChiselScalatestTester {
"DUT" should "pass" in { test(new Gemm) .withAnnotations(Seq(WriteVcdAnnotation)) {
    dut => 
    dut.io.start_do.poke(1.U) 
    dut.io.inst.M.poke(1.U) 
    dut.io.inst.N.poke(1.U) 
    dut.io.inst.K.poke(1.U) 
    dut.io.inst.S.poke(0.U) 
    dut.clock.step() 
    dut.io.data_in_valid.poke(1.U) 
    for(i <- 0 until 8){
      for(j <- 0 until 8){
        // dut.io.a_io_in(i * 8 * 8 + j * 8 + 8 , i * 8 * 8 + j * 8).poke(1.U) 
        // dut.io.b_io_in(i * 8 * 8 + j * 8 + 8 , i * 8 * 8 + j * 8).poke(1.U) 
        // index = i * 8 * 8 + j * 8 + 8  i * 8 * 8 + j * 8
        dut.io.a_io_in.poke(1.U) 
        dut.io.b_io_in.poke(1.U) 
      
      }
    }    
    dut.io.start_do.poke(0.U) 
    dut.clock.step() 
    dut.io.data_in_valid.poke(0.U) 

    dut.clock.step(10) 
    
    dut.io.start_do.poke(1.U) 
    dut.clock.step() 
    dut.io.start_do.poke(0.U) 
    dut.io.data_in_valid.poke(1.U) 
    dut.clock.step() 
    dut.io.data_in_valid.poke(0.U) 
    dut.clock.step() 
    dut.clock.step()

    dut.clock.step(10) 

    dut.io.start_do.poke(1.U) 
    dut.io.inst.M.poke(1.U) 
    dut.io.inst.N.poke(1.U) 
    dut.io.inst.K.poke(1.U) 
    dut.io.inst.S.poke(1.U) 
    dut.clock.step() 
    dut.io.data_in_valid.poke(1.U) 
    for(i <- 0 until 8){
      for(j <- 0 until 8){
        // dut.io.a_io_in[i * 8 * 8 + j * 8].poke(1.U) 
        // dut.io.b_io_in(i * 8 * 8 + j * 8).poke(1.U) 
        // index = i * 8 * 8 + j * 8 + 8  i * 8 * 8 + j * 8
        dut.io.a_io_in.poke(1.U) 
        dut.io.b_io_in.poke(1.U) 
      
      }
    }    

    dut.io.start_do.poke(0.U) 
    dut.clock.step() 
    dut.io.data_in_valid.poke(0.U) 

    dut.clock.step(10) 

    dut.clock.step() 
    dut.io.start_do.poke(1.U) 
    for(i <- 0 until 8){
      for(j <- 0 until 8){
        // dut.io.a_io_in[i * 8 * 8 + j * 8].poke(1.U) 
        // dut.io.b_io_in(i * 8 * 8 + j * 8).poke(1.U) 
        // index = i * 8 * 8 + j * 8 + 8  i * 8 * 8 + j * 8
        dut.io.a_io_in.poke(1.U) 
        dut.io.b_io_in.poke(1.U) 
      
      }
    }    
    dut.clock.step() 
    dut.io.start_do.poke(0.U) 
    dut.clock.step(10) 
    dut.io.data_in_valid.poke(1.U) 

    dut.clock.step() 
    dut.io.data_in_valid.poke(0.U) 
    dut.clock.step(10) 

    emitVerilog(new (Gemm), Array("--target-dir", "generated/gemm"))

} } }