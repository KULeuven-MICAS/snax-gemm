// Copyright 2023 Katolieke Universiteit Leuven (KUL)
// Solderpad Hardware License, Version 0.51, see LICENSE for details.
// SPDX-License-Identifier: SHL-0.51

// Author: Xiaoling Yi (xiaoling.yi@kuleuven.be)

// verilog_lint: waive-start line-length
// verilog_lint: waive-start no-trailing-spaces

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

  // Workaround for definitions not to overwrite riscv_instr
  // Quick workaround
  localparam logic [31:0] CSRRW              = 32'b?????????????????001?????1110011;
  localparam logic [31:0] CSRRS              = 32'b?????????????????010?????1110011;
  localparam logic [31:0] CSRRC              = 32'b?????????????????011?????1110011;
  localparam logic [31:0] CSRRWI             = 32'b?????????????????101?????1110011;
  localparam logic [31:0] CSRRSI             = 32'b?????????????????110?????1110011;
  localparam logic [31:0] CSRRCI             = 32'b?????????????????111?????1110011;

  // CSRs
  localparam reg_num = 5;
  localparam csr_addr_offset = 32'h3c0;
  reg [31:0] CSRs [reg_num - 1:0];
  logic write_csr;
  logic read_csr;
  logic csr_read_done;
  logic csr_write_done;
  logic [31:0] csr_addr;

  // CSR States
  typedef enum logic [1:0] {
    IDLE,
    READ,
    WRITE
  } ctrl_csr_states_t;

  ctrl_csr_states_t csr_cstate, csr_nstate;


  // 2 cycle to write data out
  logic read_tcdm;
  logic write_tcdm_1;
  logic write_tcdm_2;
  logic read_tcdm_done;
  logic write_tcdm_done;
  logic write_tcdm_done_1;
  logic write_tcdm_done_2;
  logic tcdm_not_ready;
  logic [SnaxTcdmPorts - 1 : 0] snax_tcdm_rsp_i_p_valid;
  logic [SnaxTcdmPorts - 1 : 0] snax_tcdm_req_o_q_valid;

  // States
  typedef enum logic [2:0] {
    IDLE_GEMM,
    READ_GEMM,
    COMP_GEMM,
    WRITE1_GEMM,
    WRITE2_GEMM
  } ctrl_states_t;

  ctrl_states_t cstate, nstate;

  // write CSRs
  always_ff @ (posedge clk_i or negedge rst_ni) begin
    if (!rst_ni) begin
      for (int i=0; i < reg_num; i++) begin
        CSRs[i] <= 32'b0;
      end     
    end else begin
      if(write_csr) begin
        CSRs[csr_addr] <= snax_req_i.data_arga[31:0];
      end 
      else begin
        if (write_tcdm_done_2 == 1'b1) begin
          CSRs[4] <= 31'b1;
        end
        else begin
          CSRs[4] <= 31'b0;          
        end
      end
    end
  end

  // and read CSRs
  always_comb begin
    if (!rst_ni) begin
        snax_resp_o.data = 0;
        snax_resp_o.id = 0;
        snax_resp_o.error = 1'b0;
        snax_pvalid_o = 1'b0;        
    end else begin
      if(read_csr) begin
        snax_resp_o.data = {32'b0,CSRs[csr_addr]};
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
  assign snax_qready_o = 1'b1;
  assign csr_addr = snax_req_i.data_argb - csr_addr_offset;

  // Gemm wires
  logic io_start_do;
  logic io_data_in_valid;
  logic [511:0] io_a_io_in;
  logic [511:0] io_b_io_in;

  logic io_data_out_valid;
  logic [2047:0] io_c_io_out;
  reg [2047:0] io_c_io_out_reg;
  reg [DataWidth - 1:0] req_write_data [SnaxTcdmPorts - 1:0] ;
  reg [DataWidth - 1:0] req_write_data_test;
  localparam half_half_c_addr = 1024 / 2 / 8;
  localparam half_c_addr = 1024 / 8;
  localparam half_half_c = 1024 / 2;
  localparam half_c = 1024;

  Gemm inst_gemm(
    .clock(clk_i),	// <stdin>:9016:11
    .reset(!rst_ni),	// <stdin>:9017:11
    .io_start_do(io_start_do),	// src/main/scala/gemm/gemm.scala:309:16
    .io_data_in_valid(io_data_in_valid),	// src/main/scala/gemm/gemm.scala:309:16
    .io_a_io_in(io_a_io_in),	// src/main/scala/gemm/gemm.scala:309:16
    .io_b_io_in(io_b_io_in),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_M(1),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_K(1),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_N(1),	// src/main/scala/gemm/gemm.scala:309:16
    .io_inst_S(1'b1),	// src/main/scala/gemm/gemm.scala:309:16
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
        io_c_io_out_reg <= io_c_io_out;
      end
    end
  end

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
        // if (read_tcdm_done) begin 
        //   nstate = COMP_GEMM; 
        // end else begin
        //   nstate = READ_GEMM; 
        // end
        nstate = COMP_GEMM;
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

  assign io_start_do = snax_qvalid_i & (csr_addr == 3) & snax_qready_o;

  // read data from TCDM and write data to TCDM

  always_comb begin
      for (int i = 0; i < SnaxTcdmPorts / 2; i++) begin
        if(!rst_ni) begin
          snax_tcdm_req_o[i].q_valid = 1'b0;
          snax_tcdm_req_o[i].q.addr = 17'b0;
          snax_tcdm_req_o[i].q.write = 1'b0;
          snax_tcdm_req_o[i].q.amo = reqrsp_pkg::AMONone;
          snax_tcdm_req_o[i].q.data = {DataWidth{1'b0}};
          snax_tcdm_req_o[i].q.strb = {(DataWidth / 8){1'b0}};
          snax_tcdm_req_o[i].q.user = '0;

          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q_valid = 1'b0;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.addr = 17'b0;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.write = 1'b0;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.amo = reqrsp_pkg::AMONone;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.data = {DataWidth{1'b0}};
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.strb = {(DataWidth / 8){1'b0}};
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.user = '0;          
        end
        else if(read_tcdm) begin
          snax_tcdm_req_o[i].q_valid = 1'b1;
          snax_tcdm_req_o[i].q.addr = CSRs[0] + i * 8;
          snax_tcdm_req_o[i].q.write = 1'b0;
          snax_tcdm_req_o[i].q.amo = reqrsp_pkg::AMONone;
          snax_tcdm_req_o[i].q.data = {DataWidth{1'b0}};
          snax_tcdm_req_o[i].q.strb = {(DataWidth / 8){1'b1}};
          snax_tcdm_req_o[i].q.user = '0;

          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q_valid = 1'b1;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.addr = CSRs[1] + i * 8;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.write = 1'b0;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.amo = reqrsp_pkg::AMONone;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.data = {DataWidth{1'b0}};
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.strb = {(DataWidth / 8){1'b1}};
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.user = '0;                    
        end
        else if(write_tcdm_1) begin
          snax_tcdm_req_o[i].q_valid = 1'b1;
          snax_tcdm_req_o[i].q.addr = CSRs[2] + i * 8;
          snax_tcdm_req_o[i].q.write = 1'b1;
          snax_tcdm_req_o[i].q.amo = reqrsp_pkg::AMONone;
          snax_tcdm_req_o[i].q.data = io_c_io_out_reg[i * DataWidth +: DataWidth];
          snax_tcdm_req_o[i].q.strb = {(DataWidth / 8){1'b1}};
          snax_tcdm_req_o[i].q.user = '0;

          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q_valid = 1'b1;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.addr = CSRs[2] + i * 8 + half_half_c_addr;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.write = 1'b1;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.amo = reqrsp_pkg::AMONone;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.data = io_c_io_out_reg[(i * DataWidth + half_half_c) +: DataWidth];
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.strb = {(DataWidth / 8){1'b1}};
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.user = '0;                    
        end  
        else if(write_tcdm_2) begin
          snax_tcdm_req_o[i].q_valid = 1'b1;
          snax_tcdm_req_o[i].q.addr = CSRs[2] + i * 8 + half_c_addr;
          snax_tcdm_req_o[i].q.write = 1'b1;
          snax_tcdm_req_o[i].q.amo = reqrsp_pkg::AMONone;
          snax_tcdm_req_o[i].q.data = io_c_io_out_reg[(i * DataWidth + half_c) +: DataWidth];
          snax_tcdm_req_o[i].q.strb = {(DataWidth / 8){1'b1}};
          snax_tcdm_req_o[i].q.user = '0;

          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q_valid = 1'b1;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.addr = CSRs[2] + i * 8 + half_c_addr + half_half_c_addr;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.write = 1'b1;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.amo = reqrsp_pkg::AMONone;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.data = io_c_io_out_reg[(i * DataWidth + half_c + half_half_c) +: DataWidth];
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.strb = {(DataWidth / 8){1'b1}};
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.user = '0;                    
        end              
        else begin
          snax_tcdm_req_o[i].q_valid = 1'b0;
          snax_tcdm_req_o[i].q.addr = 17'b0;
          snax_tcdm_req_o[i].q.write = 1'b0;
          snax_tcdm_req_o[i].q.amo = reqrsp_pkg::AMONone;
          snax_tcdm_req_o[i].q.data = {DataWidth{1'b0}};
          snax_tcdm_req_o[i].q.strb = {(DataWidth / 8){1'b0}};
          snax_tcdm_req_o[i].q.user = '0;        

          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q_valid = 1'b0;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.addr = 17'b0;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.write = 1'b0;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.amo = reqrsp_pkg::AMONone;
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.data = {DataWidth{1'b0}};
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.strb = {(DataWidth / 8){1'b0}};
          snax_tcdm_req_o[i + SnaxTcdmPorts / 2].q.user = '0;                  
        end 
      end
  end 

  always_comb begin
    for (int i = 0; i < SnaxTcdmPorts / 2; i++) begin
      if (!rst_ni) begin
        req_write_data[i] = 0;
        req_write_data[i + SnaxTcdmPorts / 2] = 0;
      end
      else if(write_tcdm_1) begin
        req_write_data[i] = io_c_io_out_reg[i * DataWidth +: DataWidth];
        req_write_data[i + SnaxTcdmPorts / 2] = io_c_io_out_reg[(i * DataWidth + half_half_c) +: DataWidth];       
      end
      else if(write_tcdm_2) begin
        req_write_data[i] = io_c_io_out_reg[(i * DataWidth + half_c) +: DataWidth];
        req_write_data[i + SnaxTcdmPorts / 2] = io_c_io_out_reg[(i * DataWidth + half_c + half_half_c) +: DataWidth];       
      end
      else begin
        req_write_data[i] = 0;
        req_write_data[i + SnaxTcdmPorts / 2] = 0;                
      end
    end
  end
  // assign req_write_data_test = io_c_io_out_reg['h40+64 : 'h40];
  assign req_write_data_test = io_c_io_out_reg[2047 : 'h40];

  always_comb begin
    if (!rst_ni) begin
        io_a_io_in = 512'b0;        
        io_b_io_in = 512'b0;        
    end else begin
      for (int i = 0; i < SnaxTcdmPorts / 2; i++) begin
        if(io_data_in_valid) begin
          io_a_io_in[i * DataWidth +: DataWidth] = snax_tcdm_rsp_i[i].p.data;
          io_b_io_in[i * DataWidth +: DataWidth] = snax_tcdm_rsp_i[i + SnaxTcdmPorts / 2].p.data;        
        end
        else begin
          io_a_io_in[i * DataWidth +: DataWidth] = 0;
          io_b_io_in[i * DataWidth +: DataWidth] = 0;                
        end
      end
    end
  end  

  always_comb begin
      for (int i = 0; i < SnaxTcdmPorts; i++) begin
        if(!rst_ni) begin
          snax_tcdm_rsp_i_p_valid[i] = 1'b0;
          snax_tcdm_req_o_q_valid[i] = 1'b0;
        end
        else begin
          snax_tcdm_rsp_i_p_valid[i] = snax_tcdm_rsp_i[i].p_valid;
          snax_tcdm_req_o_q_valid[i] = snax_tcdm_req_o[i].q_valid;                
        end 
      end
  end 

  assign tcdm_not_ready = ~io_data_in_valid; 
  assign io_data_in_valid = (&snax_tcdm_rsp_i_p_valid) === 1'b1 ? 1'b1 : 1'b0;
  assign read_tcdm = cstate == READ_GEMM;
  assign write_tcdm_1 = cstate == WRITE1_GEMM;
  assign write_tcdm_2 = cstate == WRITE2_GEMM;
  assign read_tcdm_done = io_data_in_valid;
  assign write_tcdm_done_1 = (&snax_tcdm_req_o_q_valid) && cstate == WRITE1_GEMM;
  assign write_tcdm_done_2 = (&snax_tcdm_req_o_q_valid) && cstate == WRITE2_GEMM;
  assign write_tcdm_done = write_tcdm_done_1 & write_tcdm_done_2;

endmodule
