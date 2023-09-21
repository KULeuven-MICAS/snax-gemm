`include "../src/type_def.svh"

module snax_gemm_tb;
    localparam int unsigned AddrWidth = 48;
    localparam int unsigned DataWidth = 64;
    localparam int unsigned SnaxTcdmPorts      =  16;
    localparam int unsigned NrCores            = 8;

    // TCDM derived types
    typedef logic [AddrWidth-1:0]   addr_t;
    typedef logic [DataWidth-1:0]   data_t;
    typedef logic [DataWidth/8-1:0] strb_t;
    typedef logic                   user_t;
    `TCDM_TYPEDEF_ALL(tcdm, addr_t, data_t, strb_t, user_t);

    // wires defination
    // SNAX wiring
    acc_req_t snax_req;
    logic snax_qvalid;
    logic snax_qready;
    acc_resp_t snax_resp;
    logic snax_pvalid;
    logic snax_pready;
    // Generation of SNAX wires
    tcdm_req_t [NrCores-1:0] [SnaxTcdmPorts-1:0 ] snax_tcdm_req;
    tcdm_rsp_t [NrCores-1:0] [SnaxTcdmPorts-1:0 ] snax_tcdm_rsp;

    // Clock
    reg clk_i;
    reg rst_ni;
    real         CYCLE_PERIOD = 10 ; //

    always begin
        clk_i = 0 ; 
        #(CYCLE_PERIOD/2) ;
        clk_i = 1 ; 
        #(CYCLE_PERIOD/2) ;
    end

    initial begin
        rst_ni      = 1'b0 ;
        #8 rst_ni      = 1'b1 ;
    end

    snax_gemm # (
        .DataWidth          ( DataWidth        ),
        .SnaxTcdmPorts      ( SnaxTcdmPorts    ),
        .acc_req_t          ( acc_req_t        ),
        .acc_rsp_t          ( acc_resp_t       ),
        .tcdm_req_t         ( tcdm_req_t       ),
        .tcdm_rsp_t         ( tcdm_rsp_t       )
    ) i_snax_gemm (
        .clk_i              ( clk_i            ),
        .rst_ni             ( rst_ni           ),
        .snax_req_i         ( snax_req         ),
        .snax_qvalid_i      ( snax_qvalid      ),
        .snax_qready_o      ( snax_qready      ),
        .snax_resp_o        ( snax_resp        ),
        .snax_pvalid_o      ( snax_pvalid      ),
        .snax_pready_i      ( snax_pready      ),
        .snax_tcdm_req_o    ( snax_tcdm_req[0] ),
        .snax_tcdm_rsp_i    ( snax_tcdm_rsp[0] )
    );

    initial begin
        // write 3 csr
        snax_qvalid = 1'b0;
        snax_pready = 1'b0;
        #(CYCLE_PERIOD * 2)

        snax_qvalid = 1'b1;
        snax_req.data_op = CSRRW;
        snax_req.data_arga = 0;
        snax_req.data_argb = 17'b1000_0000;
        #(CYCLE_PERIOD)   
        snax_qvalid = 1'b0;
        #(CYCLE_PERIOD * 2)

        snax_qvalid = 1'b1;
        snax_req.data_op = CSRRW;
        snax_req.data_arga = 1;
        snax_req.data_argb = 17'b1000_0000 + 512;
        #(CYCLE_PERIOD)   
        snax_qvalid = 1'b0;
        #(CYCLE_PERIOD * 2)

        snax_qvalid = 1'b1;
        snax_req.data_op = CSRRW;
        snax_req.data_arga = 2;
        snax_req.data_argb = 17'b1000_0000 + 512 + 512;
        #(CYCLE_PERIOD)   
        snax_qvalid = 1'b0;
        #(CYCLE_PERIOD * 10)

        // write start signal
        snax_qvalid = 1'b1;
        snax_req.data_op = CSRRW;
        snax_req.data_arga = 3;
        snax_req.data_argb = 17'b1000_0000 + 512;
        #(CYCLE_PERIOD)   
        snax_qvalid = 1'b0;
        #(CYCLE_PERIOD * 2)
        #100;

        // give data a and data b
        for ( int i = 0 ; i < SnaxTcdmPorts ; i = i + 1) begin
            snax_tcdm_rsp[0][i].p.data = {8'(i * 8 + 7), 8'(i * 8 + 6), 8'(i * 8 + 5), 8'(i * 8 + 4), 8'(i * 8 + 3), 8'(i * 8 + 2), 8'(i * 8 + 1), 8'(i * 8 + 0)};
            snax_tcdm_rsp[0][i].p_valid = 1'b1;
        end
        // #(CYCLE_PERIOD * 2)
        // snax_tcdm_rsp[0][SnaxTcdmPorts - 1].p.data = 16;
        // snax_tcdm_rsp[0][SnaxTcdmPorts - 1].p_valid = 1'b1;
        #(CYCLE_PERIOD)
        for ( int i = 0 ; i < SnaxTcdmPorts ; i = i + 1) begin
            snax_tcdm_rsp[0][i].p.data = i;
            snax_tcdm_rsp[0][i].p_valid = 1'b0;
        end
        #100;

        // view results
    end

endmodule
