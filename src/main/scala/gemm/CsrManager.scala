package gemm

import chisel3._
import chisel3.util._

/** simplified csr read/write cmd
  *
  * @param csrAddrWidth
  *   csr registers address width
  */
class CsrReq(csrAddrWidth: Int) extends Bundle {
  val data = UInt(32.W)
  val addr = UInt(csrAddrWidth.W)
  val write = Bool()
}

class CsrRsp extends Bundle {
  val data = UInt(32.W)
}

/** This class represents the csr input and output ports of the streamer top
  * module
  *
  * @param csrAddrWidth
  *   csr registers address width
  */
class CsrReqRspIO(csrAddrWidth: Int) extends Bundle {

  val req = Flipped(Decoupled(new CsrReq(csrAddrWidth)))
  val rsp = Decoupled(new CsrRsp)

}

/** This class represents the input and output ports of the CsrManager module.
  * The input is connected to the SNAX CSR port. The output is connected to the
  * streamer configuration port.
  * @param csrNum
  *   the number of csr registers
  * @param csrAddrWidth
  *   the width of the address
  */
class CsrManagerIO(
    csrNum: Int,
    csrAddrWidth: Int
) extends Bundle {

  val csr_config_in = new CsrReqRspIO(csrAddrWidth)
  val csr_config_out = Decoupled(Vec(csrNum, UInt(32.W)))

  val GeMMBusy2Idle = Input(Bool())
}

/** This class represents the CsrManager module. It contains the csr registers
  * and the read and write control logic.
  * @param csrNum
  *   the number of csr registers
  * @param csrAddrWidth
  *   the width of the address
  */
class GemmCsrManager(
    csrNum: Int,
    csrAddrWidth: Int
) extends Module
    with RequireAsyncReset {

  val io = IO(new CsrManagerIO(csrNum, csrAddrWidth))

  // generate a vector of registers to store the csr state
  val csr = RegInit(VecInit(Seq.fill(csrNum)(0.U(32.W))))

  // read and write csr cmd
  val read_csr = io.csr_config_in.req.fire && !io.csr_config_in.req.bits.write
  val write_csr =
    (io.csr_config_in.req.fire || io.GeMMBusy2Idle) && io.csr_config_in.req.bits.write

  // keep sending response to a read request until we receive the response ready signal
  val keep_sending_csr_rsp = RegInit(0.B)
  keep_sending_csr_rsp := io.csr_config_in.rsp.valid && !io.csr_config_in.rsp.ready

  // a register to store the read request response data until the request is successful
  val csr_rsp_data_reg = RegInit(0.U(32.W))

  // store the csr data for later output because the address only valid when io.csr.fire
  csr_rsp_data_reg := Mux(
    read_csr,
    csr(io.csr_config_in.req.bits.addr),
    csr_rsp_data_reg
  )

  // streamer configuration valid signal
  val config_valid = WireInit(0.B)

  // check if the csr address overflow (access certain csr that doesn't exist)
  def startCsrAddr = (csrNum - 1).U

  when(io.csr_config_in.req.fire) {

    assert(
      io.csr_config_in.req.bits.addr <= startCsrAddr,
      "csr address overflow!"
    )

  }

  // write req
  when(write_csr) {
    csr(io.csr_config_in.req.bits.addr) := io.csr_config_in.req.bits.data
  }

  // handle read requests: keep sending response data until the request succeeds
  when(read_csr) {
    io.csr_config_in.rsp.bits.data := csr(io.csr_config_in.req.bits.addr)
    io.csr_config_in.rsp.valid := 1.B
  }.elsewhen(keep_sending_csr_rsp) {
    io.csr_config_in.rsp.bits.data := csr_rsp_data_reg
    io.csr_config_in.rsp.valid := 1.B
  }.otherwise {
    io.csr_config_in.rsp.valid := 0.B
    io.csr_config_in.rsp.bits.data := 0.U
  }

  // we are ready for a new request if two conditions hold:
  // if we write to the config_valid register (the last one), the streamer must not be busy (io.csr_config_out.ready)
  // if there is a read request in progress, we only accept new write requests
  io.csr_config_in.req.ready := !io.GeMMBusy2Idle && (io.csr_config_out.ready || !(io.csr_config_in.req.bits.addr === startCsrAddr)) && (!keep_sending_csr_rsp || io.csr_config_in.req.bits.write)

  // a write/read to the last csr means the config is valid
  config_valid := io.csr_config_in.req.fire && (io.csr_config_in.req.bits.addr === startCsrAddr) && io.csr_config_in.req.bits.data === 1.U

  // signals connected to the output ports
  io.csr_config_out.bits <> csr
  io.csr_config_out.valid <> config_valid

}

// Scala main function for generating CsrManager system verilog file
object GemmCsrManager extends App {
  emitVerilog(
    new GemmCsrManager(
      GemmConstant.csrNum,
      GemmConstant.csrAddrWidth
    ),
    Array("--target-dir", "generated/csr_manager")
  )
}
