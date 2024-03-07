//--------------------------------------------------------------------
// Copyright 2023 Katolieke Universiteit Leuven (KUL)
// Solderpad Hardware License, Version 0.51, see LICENSE for details.
// SPDX-License-Identifier: SHL-0.51
//
// Author: Xiaoling Yi (xiaoling.yi@kuleuven.be)
//--------------------------------------------------------------------

// verilog_lint: waive-start line-length
// verilog_lint: waive-start no-trailing-spaces

`ifdef TARGET_SYNTHESIS_SNAX_DEV_ONLY
import riscv_instr::*;
import reqrsp_pkg::*;
`endif 

module snax_gemm_wrapper #(
    parameter int unsigned DataWidth     = 64,
    parameter int unsigned SnaxTcdmPorts = 24,
    parameter type         acc_req_t     = logic,
    parameter type         acc_rsp_t     = logic,
    parameter type         tcdm_req_t    = logic,
    parameter type         tcdm_rsp_t    = logic
) (
    input logic clk_i,
    input logic rst_ni,

    input  logic     snax_qvalid_i,
    output logic     snax_qready_o,
    input  acc_req_t snax_req_i,

    output acc_rsp_t snax_resp_o,
    output logic     snax_pvalid_o,
    input  logic     snax_pready_i,

    output tcdm_req_t [SnaxTcdmPorts-1:0] snax_tcdm_req_o,
    input  tcdm_rsp_t [SnaxTcdmPorts-1:0] snax_tcdm_rsp_i,
    output logic                          snax_barrier_o
);

  // CSR addresses setting
  // matrix size setting, MatrixSizeCsr[31:24] = Batch 
  // MatrixSizeCsr[23:16] = M, MatrixSizeCsr[15:8] = K, 
  // MatrixSizeCsr[7:0] = N
  localparam int unsigned MatrixSizeCsr = 32'h3c0;

  // pointers for start address of matrix A, B and C
  localparam int unsigned AddrACsr = 32'h3c1;
  localparam int unsigned AddrBCsr = 32'h3c2;
  localparam int unsigned AddrCCsr = 32'h3c3;

  // address strides setting, see readme for better understanding
  localparam int unsigned StrideInnermostACsr = 32'h3c4;
  localparam int unsigned StrideInnermostBCsr = 32'h3c5;
  localparam int unsigned StrideInnermostCCsr = 32'h3c6;

  localparam int unsigned LdACsr = 32'h3c7;
  localparam int unsigned LdBCsr = 32'h3c8;
  localparam int unsigned LdCCsr = 32'h3c9;

  localparam int unsigned StrideACsr = 32'h3ca;
  localparam int unsigned StrideBCsr = 32'h3cb;
  localparam int unsigned StrideCCsr = 32'h3cc;

  // performance counter csr for how many cycle used for finishing GEMM operation
  localparam int unsigned PerfCounterCsr = 32'h3cd;

  // substraction CSR
  localparam int unsigned SubstractionCsr = 32'h3ce;

  // state csr, indicating when to start GEMM and if the GEMM is busy
  localparam int unsigned StateCsr = 32'h3cf;

  // Local parameters for input and output sizes
  localparam int unsigned InputTcdmPorts = 16;
  localparam int unsigned OutputTcdmPorts = 8;
  localparam int unsigned InputMatrixSize = DataWidth * InputTcdmPorts / 2;
  localparam int unsigned OutputMatrixSize = DataWidth * OutputTcdmPorts;          // x4 because of multiplication and addition considerations

  localparam int unsigned AddrWidth = 32;
  localparam int unsigned SizeConfigWidth = 8;

  // Local parameters for subtraction a and subtraction b
  localparam int unsigned dataWidthA = 8;
  localparam int unsigned dataWidthB = 8;

  // CSR wires
  localparam int unsigned RegNum = 16;
  localparam int unsigned CsrAddrOFfset = 32'h3c0;

  logic      [                     31:0] CSR                         [RegNum];
  logic      [                     31:0] csr_addr;

  logic                                  write_csr;
  logic                                  read_csr;
  logic                                  keep_read_csr;
  logic                                  read_perf_counter;
  logic                                  keep_read_perf_counter;
  logic                                  gemm_state;
  logic                                  gemm_busy2idle;
  logic                                  gemm_idle2busy;
  logic                                  write_state_csr;

  // Gemm wires
  // gemm data ports
  logic      [      InputMatrixSize-1:0] io_data_a_i;
  logic      [      InputMatrixSize-1:0] io_data_b_i;
  logic      [     OutputMatrixSize-1:0] io_data_multi_stage_c_o;

  // gemm control signals
  logic                                  io_ctrl_start_do_i;
  logic                                  io_ctrl_data_valid_i;
  logic                                  io_ctrl_gemm_read_valid_o;
  logic                                  io_ctrl_gemm_write_valid_o;
  logic                                  io_ctrl_busy_o;
  logic                                  io_ctrl_read_mem_ready;
  logic                                  io_ctrl_write_mem_ready;
  logic      [                     31:0] io_ctrl_perf_counter;

  // gemm matrix size configuration and address setting signals
  logic      [    SizeConfigWidth - 1:0] io_ctrl_Batch_i;
  logic      [    SizeConfigWidth - 1:0] io_ctrl_M_i;
  logic      [    SizeConfigWidth - 1:0] io_ctrl_K_i;
  logic      [    SizeConfigWidth - 1:0] io_ctrl_N_i;

  logic      [          AddrWidth - 1:0] io_ctrl_ptr_addr_a_i;
  logic      [          AddrWidth - 1:0] io_ctrl_ptr_addr_b_i;
  logic      [          AddrWidth - 1:0] io_ctrl_ptr_addr_c_i;

  logic      [          AddrWidth - 1:0] io_ctrl_strideinnermostA_i;
  logic      [          AddrWidth - 1:0] io_ctrl_strideinnermostB_i;
  logic      [          AddrWidth - 1:0] io_ctrl_strideinnermostC_i;

  logic      [          AddrWidth - 1:0] io_ctrl_ldA_i;
  logic      [          AddrWidth - 1:0] io_ctrl_ldB_i;
  logic      [          AddrWidth - 1:0] io_ctrl_ldC_i;

  logic      [          AddrWidth - 1:0] io_ctrl_strideA_i;
  logic      [          AddrWidth - 1:0] io_ctrl_strideB_i;
  logic      [          AddrWidth - 1:0] io_ctrl_strideC_i;

  logic      [          AddrWidth - 1:0] io_ctrl_addr_a_o;
  logic      [          AddrWidth - 1:0] io_ctrl_addr_b_o;
  logic      [          AddrWidth - 1:0] io_ctrl_addr_c_o;

  // substraction a and b for gemm
  logic      [         dataWidthA - 1:0] substraction_a;
  logic      [         dataWidthB - 1:0] substraction_b;

  // local input matrix buffer
  logic      [InputMatrixSize * 2 - 1:0] data_reg;

  // tracing p_valid and q_ready signals for solving contentions
  logic      [       InputTcdmPorts-1:0] snax_tcdm_rsp_i_p_valid;
  logic      [        SnaxTcdmPorts-1:0] snax_tcdm_rsp_i_q_ready;
  logic      [       InputTcdmPorts-1:0] snax_tcdm_rsp_i_p_valid_reg;
  logic      [        SnaxTcdmPorts-1:0] snax_tcdm_rsp_i_q_ready_reg;

  // signals indicating if gemm is stalled by contention
  logic                                  wait_for_q_ready_read;
  logic                                  wait_for_p_valid_read;
  logic                                  wait_for_q_ready_write;

  // 
  logic                                  wait_for_q_ready_read_reg;
  logic                                  wait_for_q_ready_write_reg;

  always_ff @(posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) begin
      wait_for_q_ready_read_reg <= 1'b0;
      wait_for_q_ready_write_reg <= 1'b0;
    end
    else begin
      wait_for_q_ready_read_reg <= wait_for_q_ready_read;
      wait_for_q_ready_write_reg <= wait_for_q_ready_write;
    end
  end
  
  // split tcdm request to input and output
  tcdm_req_t [       InputTcdmPorts-1:0] snax_tcdm_req_o_input;
  tcdm_req_t [      OutputTcdmPorts-1:0] snax_tcdm_req_o_output;

  // Write CSR
  always_ff @(posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) begin
      for (int i = 0; i < RegNum - 1; i++) begin
        CSR[i] <= 32'd0;
      end
      CSR[StateCsr-CsrAddrOFfset] <= {30'b0, 2'b10};
    end else begin
      // if changing gemm state, no state CSR settings
      if (gemm_idle2busy == 1'b1) begin
        CSR[StateCsr-CsrAddrOFfset][1] <= 32'd0;
      end else if (gemm_busy2idle == 1'b1) begin
        CSR[StateCsr-CsrAddrOFfset][1] <= 32'd1;
      end
      if (write_csr == 1'b1 && snax_qready_o) begin
        CSR[csr_addr] <= snax_req_i.data_arga[31:0];
      end
    end
  end

  // Read CSR
  always_comb begin
    if (!rst_ni) begin
      snax_resp_o.data  = 0;
      snax_resp_o.id    = 0;
      snax_resp_o.error = 1'b0;
      snax_pvalid_o     = 1'b0;
    end else begin
      if (read_csr || keep_read_csr) begin
        snax_resp_o.data  = {32'b0, CSR[csr_addr]};
        snax_resp_o.id    = snax_req_i.id;
        snax_resp_o.error = 1'b0;
        snax_pvalid_o     = 1'b1;
      end else if (read_perf_counter || keep_read_perf_counter) begin
        snax_resp_o.data  = {32'b0, io_ctrl_perf_counter};
        snax_resp_o.id    = snax_req_i.id;
        snax_resp_o.error = 1'b0;
        snax_pvalid_o     = 1'b1;
      end else begin
        snax_resp_o.data  = 0;
        snax_resp_o.id    = 0;
        snax_resp_o.error = 1'b0;
        snax_pvalid_o     = 1'b0;
      end
    end
  end

  // keep sending responds when receive side not ready
  always_ff @(posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) begin
      keep_read_csr <= 1'b0;
      keep_read_perf_counter <= 1'b0;
    end else begin
      keep_read_csr <= (keep_read_csr || read_csr) && !snax_pready_i;
      keep_read_perf_counter <= (keep_read_perf_counter || read_perf_counter) && !snax_pready_i;
    end
  end

  // Read or write control logic
  always_ff @(posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) begin
      gemm_state <= 1'b0;
    end else begin
      gemm_state <= io_ctrl_busy_o;
    end
  end

  // gemm state change signal
  assign gemm_busy2idle  = gemm_state == 1'b1 && io_ctrl_busy_o == 1'b0;
  assign gemm_idle2busy  = gemm_state == 1'b0 && io_ctrl_busy_o == 1'b1;
  assign write_state_csr = gemm_state != io_ctrl_busy_o;

  // assert read/write csr signal according to the data_op when snax_qvalid_i
  always_comb begin
    if (!rst_ni) begin
      read_csr = 1'b0;
      write_csr = 1'b0;
      read_perf_counter = 1'b0;
    end else if (snax_qvalid_i) begin
      unique casez (snax_req_i.data_op)
        CSRRS, CSRRSI, CSRRC, CSRRCI: begin
          if (csr_addr == PerfCounterCsr - CsrAddrOFfset) begin
            read_perf_counter = 1'b1;
            read_csr = 1'b0;
          end else begin
            read_csr = 1'b1;
            read_perf_counter = 1'b0;
          end
          write_csr = 1'b0;
        end
        default: begin
          write_csr = 1'b1;
          read_csr = 1'b0;
          read_perf_counter = 1'b0;
        end
      endcase
    end else begin
      read_csr = 1'b0;
      write_csr = 1'b0;
      read_perf_counter = 1'b0;
    end
  end

  // when writing state csr and attempt accessing state csr, not ready for CSR settings
  assign snax_qready_o = !(write_state_csr == 1'b1 && csr_addr == StateCsr - CsrAddrOFfset && write_csr);
  assign csr_addr = snax_req_i.data_argb - CsrAddrOFfset;

  // configuration of gemm
  assign io_ctrl_Batch_i = CSR[MatrixSizeCsr-CsrAddrOFfset][31:24];
  assign io_ctrl_M_i = CSR[MatrixSizeCsr-CsrAddrOFfset][23:16];
  assign io_ctrl_K_i = CSR[MatrixSizeCsr-CsrAddrOFfset][15:8];
  assign io_ctrl_N_i = CSR[MatrixSizeCsr-CsrAddrOFfset][7:0];

  assign substraction_a = CSR[SubstractionCsr-CsrAddrOFfset][7:0];
  assign substraction_b = CSR[SubstractionCsr-CsrAddrOFfset][15:8];

  assign io_ctrl_ptr_addr_a_i = CSR[AddrACsr-CsrAddrOFfset];
  assign io_ctrl_ptr_addr_b_i = CSR[AddrBCsr-CsrAddrOFfset];
  assign io_ctrl_ptr_addr_c_i = CSR[AddrCCsr-CsrAddrOFfset];

  assign io_ctrl_strideinnermostA_i = CSR[StrideInnermostACsr-CsrAddrOFfset];
  assign io_ctrl_strideinnermostB_i = CSR[StrideInnermostBCsr-CsrAddrOFfset];
  assign io_ctrl_strideinnermostC_i = CSR[StrideInnermostCCsr-CsrAddrOFfset];

  assign io_ctrl_ldA_i = CSR[LdACsr-CsrAddrOFfset];
  assign io_ctrl_ldB_i = CSR[LdBCsr-CsrAddrOFfset];
  assign io_ctrl_ldC_i = CSR[LdCCsr-CsrAddrOFfset];

  assign io_ctrl_strideA_i = CSR[StrideACsr-CsrAddrOFfset];
  assign io_ctrl_strideB_i = CSR[StrideBCsr-CsrAddrOFfset];
  assign io_ctrl_strideC_i = CSR[StrideCCsr-CsrAddrOFfset];

  // gemm instiantion and ports connection
  BatchGemmSnaxTop inst_BatchGemmSnaxTop (
      .clock(clk_i),
      .reset(!rst_ni),
      .io_ctrl_M_i(io_ctrl_M_i),
      .io_ctrl_K_i(io_ctrl_K_i),
      .io_ctrl_N_i(io_ctrl_N_i),
      .io_ctrl_subtraction_a_i(substraction_a),
      .io_ctrl_subtraction_b_i(substraction_b),
      .io_ctrl_start_do_i(io_ctrl_start_do_i),
      .io_ctrl_data_valid_i(io_ctrl_data_valid_i),
      .io_ctrl_ptr_addr_a_i(io_ctrl_ptr_addr_a_i),
      .io_ctrl_ptr_addr_b_i(io_ctrl_ptr_addr_b_i),
      .io_ctrl_ptr_addr_c_i(io_ctrl_ptr_addr_c_i),
      .io_ctrl_Batch_i(io_ctrl_Batch_i),
      .io_ctrl_strideinnermost_A_i(io_ctrl_strideinnermostA_i),
      .io_ctrl_strideinnermost_B_i(io_ctrl_strideinnermostB_i),
      .io_ctrl_strideinnermost_C_i(io_ctrl_strideinnermostC_i),
      .io_ctrl_ldA_i(io_ctrl_ldA_i),
      .io_ctrl_ldB_i(io_ctrl_ldB_i),
      .io_ctrl_ldC_i(io_ctrl_ldC_i),
      .io_ctrl_strideA_i(io_ctrl_strideA_i),
      .io_ctrl_strideB_i(io_ctrl_strideB_i),
      .io_ctrl_strideC_i(io_ctrl_strideC_i),
      .io_data_a_i(io_data_a_i),
      .io_data_b_i(io_data_b_i),
      .io_ctrl_read_mem_ready(io_ctrl_read_mem_ready),
      .io_ctrl_write_mem_ready(io_ctrl_write_mem_ready),
      .io_ctrl_gemm_read_valid_o(io_ctrl_gemm_read_valid_o),
      .io_ctrl_gemm_write_valid_o(io_ctrl_gemm_write_valid_o),
      .io_ctrl_addr_a_o(io_ctrl_addr_a_o),
      .io_ctrl_addr_b_o(io_ctrl_addr_b_o),
      .io_ctrl_addr_c_o(io_ctrl_addr_c_o),
      .io_ctrl_busy_o(io_ctrl_busy_o),
      .io_data_c_o(),
      .io_data_multi_stage_c_o(io_data_multi_stage_c_o),
      .io_ctrl_perf_counter(io_ctrl_perf_counter)
  );

  assign io_ctrl_start_do_i = snax_qvalid_i && (csr_addr == (StateCsr - CsrAddrOFfset)) && snax_qready_o && snax_req_i.data_arga[0] == 1'b1;

  // request for reading data from TCDM and writing data to TCDM
  // reading request
  always_comb begin
    for (int i = 0; i < InputTcdmPorts / 2; i++) begin
      if (!rst_ni) begin
        snax_tcdm_req_o_input[i].q_valid = 1'b0;
        snax_tcdm_req_o_input[i].q.addr = 17'b0;
        snax_tcdm_req_o_input[i].q.write = 1'b0;
        snax_tcdm_req_o_input[i].q.amo = AMONone;
        snax_tcdm_req_o_input[i].q.data = {DataWidth{1'b0}};
        snax_tcdm_req_o_input[i].q.strb = {(DataWidth / 8) {1'b0}};
        snax_tcdm_req_o_input[i].q.user = '0;

        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q_valid = 1'b0;
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.addr = 17'b0;
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.write = 1'b0;
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.amo = AMONone;
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.data = {DataWidth{1'b0}};
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.strb = {(DataWidth / 8) {1'b0}};
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.user = '0;
      end else if (io_ctrl_gemm_read_valid_o) begin
        if(wait_for_q_ready_read_reg) begin
          snax_tcdm_req_o_input[i].q_valid = ~snax_tcdm_rsp_i_q_ready_reg[i];
        end
        else begin
          snax_tcdm_req_o_input[i].q_valid = 1'b1;
        end
        snax_tcdm_req_o_input[i].q.addr = io_ctrl_addr_a_o + i * 8;
        snax_tcdm_req_o_input[i].q.write = 1'b0;
        snax_tcdm_req_o_input[i].q.amo = AMONone;
        snax_tcdm_req_o_input[i].q.data = {DataWidth{1'b0}};
        snax_tcdm_req_o_input[i].q.strb = {(DataWidth / 8) {1'b1}};
        snax_tcdm_req_o_input[i].q.user = '0;

        if(wait_for_q_ready_read_reg) begin
          snax_tcdm_req_o_input[i+InputTcdmPorts/2].q_valid = ~snax_tcdm_rsp_i_q_ready_reg[i+InputTcdmPorts/2];
        end
        else begin
          snax_tcdm_req_o_input[i+InputTcdmPorts/2].q_valid = 1'b1;
        end
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.addr = io_ctrl_addr_b_o + i * 8;
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.write = 1'b0;
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.amo = AMONone;
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.data = {DataWidth{1'b0}};
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.strb = {(DataWidth / 8) {1'b1}};
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.user = '0;
      end else begin
        snax_tcdm_req_o_input[i].q_valid = 1'b0;
        snax_tcdm_req_o_input[i].q.addr = 17'b0;
        snax_tcdm_req_o_input[i].q.write = 1'b0;
        snax_tcdm_req_o_input[i].q.amo = AMONone;
        snax_tcdm_req_o_input[i].q.data = {DataWidth{1'b0}};
        snax_tcdm_req_o_input[i].q.strb = {(DataWidth / 8) {1'b0}};
        snax_tcdm_req_o_input[i].q.user = '0;

        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q_valid = 1'b0;
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.addr = 17'b0;
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.write = 1'b0;
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.amo = AMONone;
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.data = {DataWidth{1'b0}};
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.strb = {(DataWidth / 8) {1'b0}};
        snax_tcdm_req_o_input[i+InputTcdmPorts/2].q.user = '0;
      end
    end
  end

  // writing request
  always_comb begin
    for (int i = 0; i < OutputTcdmPorts; i = i + 1) begin
      if (!rst_ni) begin
        snax_tcdm_req_o_output[i].q_valid = 1'b0;
        snax_tcdm_req_o_output[i].q.addr  = 17'b0;
        snax_tcdm_req_o_output[i].q.write = 1'b0;
        snax_tcdm_req_o_output[i].q.amo   = AMONone;
        snax_tcdm_req_o_output[i].q.data  = {DataWidth{1'b0}};
        snax_tcdm_req_o_output[i].q.strb  = {(DataWidth / 8) {1'b0}};
        snax_tcdm_req_o_output[i].q.user  = '0;
      end else if (io_ctrl_gemm_write_valid_o) begin
        if(wait_for_q_ready_write) begin
          snax_tcdm_req_o_output[i].q_valid = ~snax_tcdm_rsp_i_q_ready_reg[i + InputTcdmPorts];
        end
        else begin
          snax_tcdm_req_o_output[i].q_valid = 1'b1;
        end
        snax_tcdm_req_o_output[i].q.addr  = io_ctrl_addr_c_o + i * 8;
        snax_tcdm_req_o_output[i].q.write = 1'b1;
        snax_tcdm_req_o_output[i].q.amo   = AMONone;
        snax_tcdm_req_o_output[i].q.data  = io_data_multi_stage_c_o[i*DataWidth+:DataWidth];
        snax_tcdm_req_o_output[i].q.strb  = {(DataWidth / 8) {1'b1}};
        snax_tcdm_req_o_output[i].q.user  = '0;
      end else begin
        snax_tcdm_req_o_output[i].q_valid = 1'b0;
        snax_tcdm_req_o_output[i].q.addr  = 17'b0;
        snax_tcdm_req_o_output[i].q.write = 1'b0;
        snax_tcdm_req_o_output[i].q.amo   = AMONone;
        snax_tcdm_req_o_output[i].q.data  = {DataWidth{1'b0}};
        snax_tcdm_req_o_output[i].q.strb  = {(DataWidth / 8) {1'b0}};
        snax_tcdm_req_o_output[i].q.user  = '0;
      end
    end
  end

  // combining all request together
  always_comb begin
    for (int i = 0; i < SnaxTcdmPorts; i = i + 1) begin
      if (!rst_ni) begin
        snax_tcdm_req_o[i].q_valid = 1'b0;
        snax_tcdm_req_o[i].q.addr  = 17'b0;
        snax_tcdm_req_o[i].q.write = 1'b0;
        snax_tcdm_req_o[i].q.amo   = AMONone;
        snax_tcdm_req_o[i].q.data  = {DataWidth{1'b0}};
        snax_tcdm_req_o[i].q.strb  = {(DataWidth / 8) {1'b0}};
        snax_tcdm_req_o[i].q.user  = '0;
      end else begin
        if (i < InputTcdmPorts) begin
          snax_tcdm_req_o[i] = snax_tcdm_req_o_input[i];
        end else begin
          snax_tcdm_req_o[i] = snax_tcdm_req_o_output[i-InputTcdmPorts];
        end
      end
    end
  end

  // get input data for gemm from tcdm responds
  // store p_valid and data when p_valid during the wait_for_p_valid_read && !io_ctrl_data_valid_i period
  always_ff @(posedge clk_i or negedge rst_ni) begin
      if (!rst_ni) begin
        for (int i = 0; i < InputTcdmPorts; i++) begin
          snax_tcdm_rsp_i_p_valid_reg[i]   <= 1'b0;
          data_reg[i*DataWidth+:DataWidth] <= {DataWidth{1'b0}};
        end
      end else begin
          for (int i = 0; i < InputTcdmPorts; i++) begin
            if (wait_for_p_valid_read && !io_ctrl_data_valid_i) begin
              if (snax_tcdm_rsp_i[i].p_valid) begin
                snax_tcdm_rsp_i_p_valid_reg[i]   <= snax_tcdm_rsp_i[i].p_valid;
                data_reg[i*DataWidth+:DataWidth] <= snax_tcdm_rsp_i[i].p.data;
              end
            end else begin
              snax_tcdm_rsp_i_p_valid_reg[i] <= 1'b0;
            end
      end
    end
  end

  // p_valid when p_valid previously (above) or currently
  always_comb begin
    for (int i = 0; i < InputTcdmPorts; i++) begin
      if (!rst_ni) begin
        snax_tcdm_rsp_i_p_valid[i] = 1'b0;
      end else begin
        snax_tcdm_rsp_i_p_valid[i] = snax_tcdm_rsp_i[i].p_valid || snax_tcdm_rsp_i_p_valid_reg[i];
      end
    end
  end

  // giving right data to gemm
  // when p_valid previouly, giving the previously stored data
  // when p_valid currently, giving the current response data
  always_comb begin
    if (!rst_ni) begin
      io_data_a_i = {InputMatrixSize{1'b0}};
      io_data_b_i = {InputMatrixSize{1'b0}};
    end else begin
      for (int i = 0; i < InputTcdmPorts / 2; i++) begin
        if (io_ctrl_data_valid_i) begin
          if (snax_tcdm_rsp_i[i].p_valid) begin
            io_data_a_i[i*DataWidth+:DataWidth] = snax_tcdm_rsp_i[i].p.data;
          end else begin
            io_data_a_i[i*DataWidth+:DataWidth] = data_reg[i*DataWidth+:DataWidth];
          end
          if (snax_tcdm_rsp_i[i+InputTcdmPorts/2].p_valid) begin
            io_data_b_i[i*DataWidth+:DataWidth] = snax_tcdm_rsp_i[i+InputTcdmPorts/2].p.data;
          end else begin
            io_data_b_i[i * DataWidth +: DataWidth] = data_reg[(i + InputTcdmPorts / 2) * DataWidth +: DataWidth];
          end
        end else begin
          io_data_a_i[i*DataWidth+:DataWidth] = 0;
          io_data_b_i[i*DataWidth+:DataWidth] = 0;
        end
      end
    end
  end

  // mataining stall for contention signals
  always_comb begin
    if (!rst_ni) begin
      wait_for_p_valid_read = 1'b0;
    end else begin
      if (io_ctrl_gemm_read_valid_o && !io_ctrl_data_valid_i) begin
        wait_for_p_valid_read = 1'b1;
      end else begin
        wait_for_p_valid_read = 1'b0;
      end
    end
  end

  always_comb begin
    if (!rst_ni) begin
      wait_for_q_ready_read = 1'b0;
    end else begin
      if (io_ctrl_gemm_read_valid_o && !io_ctrl_read_mem_ready) begin
        wait_for_q_ready_read = 1'b1;
      end else begin
        wait_for_q_ready_read = 1'b0;
      end
    end
  end

  always_comb begin
    if (!rst_ni) begin
      wait_for_q_ready_write = 1'b0;
    end else begin
      if (io_ctrl_gemm_write_valid_o && !io_ctrl_write_mem_ready) begin
        wait_for_q_ready_write = 1'b1;
      end else begin
        wait_for_q_ready_write = 1'b0;
      end
    end
  end
 
  // store q_ready signals
  // when q_ready, it means that the request has been sent correctly
  always_ff @(posedge clk_i or negedge rst_ni) begin
      if (!rst_ni) begin
        for (int i = 0; i < SnaxTcdmPorts; i++) begin
          snax_tcdm_rsp_i_q_ready_reg[i] <= 1'b0;
        end
      end else begin
        for (int i = 0; i < SnaxTcdmPorts; i++) begin
          // for read q_ready
          if (i < InputTcdmPorts) begin
            if (wait_for_q_ready_read && !io_ctrl_read_mem_ready) begin
              if (snax_tcdm_rsp_i[i].q_ready) begin
                snax_tcdm_rsp_i_q_ready_reg[i] <= snax_tcdm_rsp_i[i].q_ready;
              end
            end else begin
              snax_tcdm_rsp_i_q_ready_reg[i] <= 1'b0;
            end
            // for write q_ready
          end else begin
            if (wait_for_q_ready_write && !io_ctrl_write_mem_ready) begin
              if (snax_tcdm_rsp_i[i].q_ready) begin
                snax_tcdm_rsp_i_q_ready_reg[i] <= snax_tcdm_rsp_i[i].q_ready;
              end
            end else begin
              snax_tcdm_rsp_i_q_ready_reg[i] <= 1'b0;
            end
          end
      end
    end
  end

  // similiar as p_valid, q_ready when q_ready previously (above) or currently
  always_comb begin
    for (int i = 0; i < SnaxTcdmPorts; i++) begin
      if (!rst_ni) begin
        snax_tcdm_rsp_i_q_ready[i] = 1'b0;
      end else begin
        snax_tcdm_rsp_i_q_ready[i] = snax_tcdm_rsp_i[i].q_ready || snax_tcdm_rsp_i_q_ready_reg[i];
      end
    end
  end

  // controls signals for gemm
  assign io_ctrl_data_valid_i = (&snax_tcdm_rsp_i_p_valid) === 1'b1;
  assign io_ctrl_write_mem_ready = (&snax_tcdm_rsp_i_q_ready[SnaxTcdmPorts - 1 : InputTcdmPorts]) === 1'b1;
  assign io_ctrl_read_mem_ready = (&snax_tcdm_rsp_i_q_ready[InputTcdmPorts-1 : 0]) === 1'b1;

  assign snax_barrier_o = gemm_state == 1'b0;

endmodule
