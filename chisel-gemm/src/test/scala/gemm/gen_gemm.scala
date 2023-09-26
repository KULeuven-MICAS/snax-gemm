package gemm

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest._

object GemmGen extends App {

  getVerilogString(new Gemm)
  emitVerilog(new (Gemm), Array("--target-dir", "generated"))

}

class GemmTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" in { test(new Gemm) .withAnnotations(Seq(WriteVcdAnnotation)) {
      dut => 
      dut.io.start_do.poke(1.U) 
      dut.clock.step() 
      dut.io.data_in_valid.poke(1.U) 
      for(i <- 0 until 8){
        for(j <- 0 until 8){
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
      dut.clock.step() 
      dut.io.data_in_valid.poke(1.U) 

      for(i <- 0 until 8){
        for(j <- 0 until 8){
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

  } 
} 
}