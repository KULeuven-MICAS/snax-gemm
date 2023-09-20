// Copyright 2023 Katolieke Universiteit Leuven (KUL)
// Solderpad Hardware License, Version 0.51, see LICENSE for details.
// SPDX-License-Identifier: SHL-0.51

// Author: Xiaoling Yi (xiaoling.yi@kuleuven.be)

// verilog_lint: waive-start line-length
// verilog_lint: waive-start no-trailing-spaces
`include "type_def.svh"
module snax_gemm # (
  parameter int unsigned DataWidth         = 64,
  parameter int unsigned SnaxTcdmPorts = 16,
  parameter type         acc_req_t         = logic,
  parameter type         acc_rsp_t         = logic,
  parameter type         tcdm_req_t        = logic,
  parameter type         tcdm_rsp_t        = logic
)(
  input     logic                               clk_i,
  input     logic                               rst_ni,

  input     logic                               snax_qvalid_i,
  output    logic                               snax_qready_o,
  input     acc_req_t                           snax_req_i,

  output    acc_rsp_t                           snax_resp_o,
  output    logic                               snax_pvalid_o,
  input     logic                               snax_pready_i,

  output    tcdm_req_t  [SnaxTcdmPorts-1:0] snax_tcdm_req_o,
  input     tcdm_rsp_t  [SnaxTcdmPorts-1:0] snax_tcdm_rsp_i
);
  // CSRs
  localparam reg_num = 5;
  reg [31:0] CSRs [reg_num - 1:0];
  logic write_csr;
  logic read_csr;
  logic csr_read_done;
  logic csr_write_done;

  // CSR States
  typedef enum logic [1:0] {
    IDLE,
    READ,
    WRITE
  } ctrl_csr_states_t;

  ctrl_csr_states_t csr_cstate, csr_nstate;

  // Changing states
  always_ff @ (posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) begin
      csr_cstate <= IDLE;
    end else begin
      csr_cstate <= csr_nstate;
    end
  end

  // Next state changes
  always_comb begin
    case(csr_cstate)
      IDLE: begin
        if (read_csr) begin
          csr_nstate = READ;
        end else if (write_csr) begin
          csr_nstate = WRITE;
        end else begin
          csr_nstate = IDLE;
        end
      end 
      READ: begin
        if (csr_read_done) begin 
          csr_nstate = IDLE; 
        end else begin
          csr_nstate = READ; 
        end
      end          
      WRITE: begin
        if (csr_write_done) begin 
          csr_nstate = IDLE; 
        end else begin
          csr_nstate = WRITE; 
        end
      end
      default: begin
        csr_nstate = IDLE;
      end
    endcase
  end

  // write CSRs
  always_ff @ (posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) begin
      for (int i=0; i<reg_num; i++) begin
        CSRs[i] <= 32'b0;
      end     
    end else begin
      if(csr_nstate == WRITE) begin
        CSRs[snax_req_i.data_arga] <= snax_req_i.data_argb;
      end
    end
  end

  // and read CSRs
  always_comb begin
    if (!rst_ni) begin
        snax_pvalid_o = 1'b0;        
    end else begin
      if(csr_nstate == READ) begin
        snax_resp_o.data = CSRs[snax_req_i.data_arga];
        snax_resp_o.id = snax_req_i.id;
        snax_resp_o.error = 1'b0;
        snax_pvalid_o = 1'b1;
      end
      else begin
        snax_resp_o.data = 0;
        snax_resp_o.id = 0;
        snax_resp_o.error = 1'b0;
        snax_pvalid_o = 1'b0;        
      end
    end
  end

  always_comb begin
    if (!rst_ni) begin
      read_csr = 1'b0;
      write_csr = 1'b0;      
    end
    else if(snax_qvalid_i) begin
      unique casez (snax_req_i.data_op)
        CSRRS, CSRRSI, CSRRC, CSRRCI: begin
          read_csr = 1'b1;
          write_csr = 1'b0;
        end
        default: begin
          write_csr = 1'b1;
          read_csr = 1'b0;
        end
      endcase      
    end
    else begin
      read_csr = 1'b0;
      write_csr = 1'b0;
    end
    
  end

  assign csr_read_done = snax_pvalid_o & snax_pready_i;
  assign csr_write_done = csr_nstate == WRITE;

  assign snax_qready_o = csr_nstate == IDLE;

  // Gemm wires
  logic io_start_do;
  logic io_data_in_valid;
  logic [511:0] io_a_io_in;
  logic [511:0] io_b_io_in;

  logic io_data_out_valid;
  logic [2047:0] io_c_io_out;
  reg [1023:0] io_c_io_out_reg;

  Gemm inst_gemm(
    .clock(clk_i),	// <stdin>:9016:11
    .reset(~rst_ni),	// <stdin>:9017:11
    .io_start_do(io_start_do),	// src/main/scala/gemm/gemm.scala:309:16
    .io_data_in_valid(io_data_in_valid),	// src/main/scala/gemm/gemm.scala:309:16
    .io_a_io_in(io_a_io_in),	// src/main/scala/gemm/gemm.scala:309:16
    .io_b_io_in(io_b_io_in),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_M(1),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_K(1),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_N(1),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_S(0),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_Rm(),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_Rn(),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_Rd(),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_shift_direction(),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_shift_number(),	// src/main/scala/gemm/gemm.scala:309:16
    .io_done(),	// src/main/scala/gemm/gemm.scala:309:16
    .io_addr_in_valid(),	// src/main/scala/gemm/gemm.scala:309:16
    .io_addr_a_in(),	// src/main/scala/gemm/gemm.scala:309:16
    .io_addr_b_in(),	// src/main/scala/gemm/gemm.scala:309:16
    .io_data_out_valid(io_data_out_valid),	// src/main/scala/gemm/gemm.scala:309:16
    .io_c_io_out(io_c_io_out),	// src/main/scala/gemm/gemm.scala:309:16
    .io_addr_c_out()	
  );

  always_ff @ (posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) begin
      io_c_io_out_reg <= 0;
    end else begin
      if (io_data_out_valid) begin
        io_c_io_out_reg <= io_c_io_out[2047 : 1024];
      end
    end
  end

  // 2 cycle to write data out
  logic read_tcdm;
  logic write_tcdm_1;
  logic write_tcdm_2;
  logic read_tcdm_done;
  logic write_tcdm_done;
  logic write_tcdm_done_1;
  logic write_tcdm_done_2;
  logic tcdm_not_ready;

  // States
  typedef enum logic [2:0] {
    IDLE_GEMM,
    READ_GEMM,
    COMP_GEMM,
    WRITE1_GEMM,
    WRITE2_GEMM
  } ctrl_states_t;

  ctrl_states_t cstate, nstate;

  // Changing states
  always_ff @ (posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) begin
      cstate <= IDLE_GEMM;
    end else begin
      cstate <= nstate;
    end
  end

  // Next state changes
  always_comb begin
    case(cstate)
      IDLE_GEMM: begin
        if (io_start_do) begin
          nstate = READ_GEMM;
        end else begin
          nstate = IDLE_GEMM;
        end
      end 
      READ_GEMM: begin
        if (read_tcdm_done) begin 
          nstate = COMP_GEMM; 
        end else begin
          nstate = READ_GEMM; 
        end
      end   
      COMP_GEMM: begin
        if (io_data_out_valid) begin 
          nstate = WRITE1_GEMM; 
        end else begin
          nstate = COMP_GEMM; 
        end
      end          
      WRITE1_GEMM: begin
        if (write_tcdm_done_1) begin 
          nstate = WRITE2_GEMM; 
        end else begin
          nstate = WRITE1_GEMM; 
        end
      end
      WRITE2_GEMM: begin
        if (write_tcdm_done_2) begin 
          nstate = IDLE_GEMM; 
        end else begin
          nstate = WRITE2_GEMM; 
        end
      end      
      default: begin
        nstate = IDLE_GEMM;
      end
    endcase

  end

  assign io_start_do = snax_req_i & snax_req_i.addr == 3 & snax_qready_o;

  // read data from TCDM and write data to TCDM

  always_comb begin
      for (int i = 0; i < SnaxTcdmPorts / 2; i++) begin
        if(!rst_ni) begin
          snax_tcdm_req_o[i].q_valid = 1'b0;
          snax_tcdm_req_o[i].addr = 17'b0;
          snax_tcdm_req_o[i].write = 1'b0;
          snax_tcdm_req_o[i].amo = 1'b0;
          snax_tcdm_req_o[i].data = {DataWidth{1'b0}};
          snax_tcdm_req_o[i].strb = {(DataWidth / 8){1'b0}};
          snax_tcdm_req_o[i].user = '0;
        end
        else if(read_tcdm) begin
          snax_tcdm_req_o[i].q_valid = 1'b1;
          snax_tcdm_req_o[i].addr = CSRs[0] + i * 8;
          snax_tcdm_req_o[i].write = 1'b0;
          snax_tcdm_req_o[i].amo = 1'b0;
          snax_tcdm_req_o[i].data = {DataWidth{1'b0}};
          snax_tcdm_req_o[i].strb = {(DataWidth / 8){1'b1}};
          snax_tcdm_req_o[i].user = '0;

          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q_valid = 1'b1;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].addr = CSRs[1] + i * 8;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].write = 1'b0;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].amo = 1'b0;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].data = {DataWidth{1'b0}};
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].strb = {(DataWidth / 8){1'b1}};
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].user = '0;                    
        end
        else if(write_tcdm_1) begin
          snax_tcdm_req_o[i].q_valid = 1'b1;
          snax_tcdm_req_o[i].addr = CSRs[2] + i * 8;
          snax_tcdm_req_o[i].write = 1'b1;
          snax_tcdm_req_o[i].amo = 1'b0;
          snax_tcdm_req_o[i].data = io_c_io_out[i * DataWidth + DataWidth -: i * DataWidth];
          snax_tcdm_req_o[i].strb = {(DataWidth / 8){1'b1}};
          snax_tcdm_req_o[i].user = '0;

          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q_valid = 1'b1;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].addr = CSRs[2] + 1024 / 2 / 8 + i * 8;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].write = 1'b1;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].amo = 1'b0;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].data = io_c_io_out[i * DataWidth + DataWidth + 1024 / 2 / 8 -: i * DataWidth + 1024 / 2 / 8 ];
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].strb = {(DataWidth / 8){1'b1}};
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].user = '0;                    
        end  
        else if(write_tcdm_1) begin
          snax_tcdm_req_o[i].q_valid = 1'b1;
          snax_tcdm_req_o[i].addr = CSRs[2] + i * 8 + 1024 / 8;
          snax_tcdm_req_o[i].write = 1'b1;
          snax_tcdm_req_o[i].amo = 1'b0;
          snax_tcdm_req_o[i].data = io_c_io_out_reg[i * DataWidth + DataWidth -: i * DataWidth];
          snax_tcdm_req_o[i].strb = {(DataWidth / 8){1'b1}};
          snax_tcdm_req_o[i].user = '0;

          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q_valid = 1'b1;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].addr = CSRs[2] + 1024 / 2 / 8 + i * 8 + 1024 / 8;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].write = 1'b1;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].amo = 1'b0;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].data = io_c_io_out_reg[i * DataWidth + DataWidth + 1024 / 2 / 8 -: i * DataWidth + 1024 / 2 / 8 ];
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].strb = {(DataWidth / 8){1'b1}};
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].user = '0;                    
        end              
        else begin
          snax_tcdm_req_o[i].q_valid = 1'b0;
          snax_tcdm_req_o[i].addr = 17'b0;
          snax_tcdm_req_o[i].write = 1'b0;
          snax_tcdm_req_o[i].amo = 1'b0;
          snax_tcdm_req_o[i].data = {DataWidth{1'b0}};
          snax_tcdm_req_o[i].strb = {(DataWidth / 8){1'b0}};
          snax_tcdm_req_o[i].user = '0;               
        end 
      end
  end 

  always_comb begin
    if (!rst_ni) begin
        io_a_io_in = 512'b0;        
        io_b_io_in = 512'b0;        
    end else begin
      for (int i = 0; i < SnaxTcdmPorts / 2; i++) begin
        if(io_data_in_valid) begin
          io_a_io_in[i * DataWidth + DataWidth -: i * DataWidth] = snax_tcdm_rsp_i[i].data;
          io_b_io_in[i * DataWidth + DataWidth -: i * DataWidth] = snax_tcdm_rsp_i[i + SnaxTcdmPorts / 2].data;        
        end
        else begin
          io_a_io_in[i * DataWidth + DataWidth -: i * DataWidth] = 0;
          io_b_io_in[i * DataWidth + DataWidth -: i * DataWidth] = 0;                
        end
      end
    end
  end  

  always_comb begin
      for (int i = 0; i < SnaxTcdmPorts; i++) begin
        if(!rst_ni) begin
          snax_tcdm_rsp_i[i].q_ready = 1'b0;
        end
        else if(tcdm_not_ready) begin
          snax_tcdm_rsp_i[i].q_ready = 1'b0;
        end
        else begin
          snax_tcdm_rsp_i[i].q_ready = 1'b1;                
        end 
      end
  end 

  assign tcdm_not_ready = nstate == READ_GEMM; 
  assign io_data_in_valid = &snax_tcdm_rsp_i.p_valid;
  assign read_tcdm = cstate == READ_GEMM;
  assign write_tcdm_1 = cstate == WRITE1_GEMM;
  assign write_tcdm_2 = cstate == WRITE2_GEMM;
  assign read_tcdm_done = io_data_in_valid;
  assign write_tcdm_done_1 = (&snax_tcdm_req_o.q_valid) & cstate == WRITE1_GEMM;
  assign write_tcdm_done_2 = (&snax_tcdm_req_o.q_valid) & cstate == WRITE2_GEMM;
  assign write_tcdm_done = write_tcdm_done_1 & write_tcdm_done_2;

endmodule
