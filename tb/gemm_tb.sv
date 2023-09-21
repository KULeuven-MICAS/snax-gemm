module gemm_test;

    reg clock;
    reg reset;

    reg io_start_do;
    reg io_data_in_valid;
    reg [511:0] io_a_io_in;
    reg [511:0] io_b_io_in;

    wire io_data_out_valid;
    wire [2047:0] io_c_io_out;

    Gemm inst_gemm(
    .clock(clock),	// <stdin>:9016:11
    .reset(reset),	// <stdin>:9017:11
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

    real         CYCLE_PERIOD = 10 ; //
    always begin
        clock = 0 ; 
        #(CYCLE_PERIOD/2) ;
        clock = 1 ; 
        #(CYCLE_PERIOD/2) ;
    end

    initial begin
        reset      = 1'b1 ;
        #8 reset      = 1'b0 ;
    end

    initial begin
        io_start_do = 1'b0;
        io_data_in_valid = 1'b0;
        #(CYCLE_PERIOD * 2)
        io_start_do = 1'b1;
        #(CYCLE_PERIOD)
        io_start_do = 1'b0;
        #(CYCLE_PERIOD)
        io_data_in_valid = 1'b1;
        io_a_io_in = {64{8'h1}};
        io_b_io_in = {64{8'h1}};
        #(CYCLE_PERIOD)
        io_data_in_valid = 1'b0;
        #(CYCLE_PERIOD)   
        #100;

        #(CYCLE_PERIOD * 2)
        io_start_do = 1'b1;
        #(CYCLE_PERIOD)
        io_start_do = 1'b0;
        #(CYCLE_PERIOD)
        io_data_in_valid = 1'b1;
        io_a_io_in = {{8'd0}, {8'd1}, {8'd2}, {8'd3}, {8'd4}, {8'd5}, {8'd6}, {8'd7}, {8'd8}, {8'd9}, {8'd10}, {8'd11}, {8'd12}, {8'd13}, {8'd14}, {8'd15}, {8'd16}, {8'd17}, {8'd18}, {8'd19}, {8'd20}, {8'd21}, {8'd22}, {8'd23}, {8'd24}, {8'd25}, {8'd26}, {8'd27}, {8'd28}, {8'd29}, {8'd30}, {8'd31}, {8'd32}, {8'd33}, {8'd34}, {8'd35}, {8'd36}, {8'd37}, {8'd38}, {8'd39}, {8'd40}, {8'd41}, {8'd42}, {8'd43}, {8'd44}, {8'd45}, {8'd46}, {8'd47}, {8'd48}, {8'd49}, {8'd50}, {8'd51}, {8'd52}, {8'd53}, {8'd54}, {8'd55}, {8'd56}, {8'd57}, {8'd58}, {8'd59}, {8'd60}, {8'd61}, {8'd62}, {8'd63}};
        io_b_io_in = {{8'd0}, {8'd1}, {8'd2}, {8'd3}, {8'd4}, {8'd5}, {8'd6}, {8'd7}, {8'd8}, {8'd9}, {8'd10}, {8'd11}, {8'd12}, {8'd13}, {8'd14}, {8'd15}, {8'd16}, {8'd17}, {8'd18}, {8'd19}, {8'd20}, {8'd21}, {8'd22}, {8'd23}, {8'd24}, {8'd25}, {8'd26}, {8'd27}, {8'd28}, {8'd29}, {8'd30}, {8'd31}, {8'd32}, {8'd33}, {8'd34}, {8'd35}, {8'd36}, {8'd37}, {8'd38}, {8'd39}, {8'd40}, {8'd41}, {8'd42}, {8'd43}, {8'd44}, {8'd45}, {8'd46}, {8'd47}, {8'd48}, {8'd49}, {8'd50}, {8'd51}, {8'd52}, {8'd53}, {8'd54}, {8'd55}, {8'd56}, {8'd57}, {8'd58}, {8'd59}, {8'd60}, {8'd61}, {8'd62}, {8'd63}};
        #(CYCLE_PERIOD)
        io_data_in_valid = 1'b0;
        #(CYCLE_PERIOD)    
        #100;

        #(CYCLE_PERIOD * 2)
        io_start_do = 1'b1;
        #(CYCLE_PERIOD)
        io_start_do = 1'b0;
        #(CYCLE_PERIOD)
        io_data_in_valid = 1'b1;
        io_a_io_in = {{8'd64}, {8'd63}, {8'd62}, {8'd61}, {8'd60}, {8'd59}, {8'd58}, {8'd57}, {8'd56}, {8'd55}, {8'd54}, {8'd53}, {8'd52}, {8'd51}, {8'd50}, {8'd49}, {8'd48}, {8'd47}, {8'd46}, {8'd45}, {8'd44}, {8'd43}, {8'd42}, {8'd41}, {8'd40}, {8'd39}, {8'd38}, {8'd37}, {8'd36}, {8'd35}, {8'd34}, {8'd33}, {8'd32}, {8'd31}, {8'd30}, {8'd29}, {8'd28}, {8'd27}, {8'd26}, {8'd25}, {8'd24}, {8'd23}, {8'd22}, {8'd21}, {8'd20}, {8'd19}, {8'd18}, {8'd17}, {8'd16}, {8'd15}, {8'd14}, {8'd13}, {8'd12}, {8'd11}, {8'd10}, {8'd9}, {8'd8}, {8'd7}, {8'd6}, {8'd5}, {8'd4}, {8'd3}, {8'd2}, {8'd1}};
        io_b_io_in = {{8'd64}, {8'd63}, {8'd62}, {8'd61}, {8'd60}, {8'd59}, {8'd58}, {8'd57}, {8'd56}, {8'd55}, {8'd54}, {8'd53}, {8'd52}, {8'd51}, {8'd50}, {8'd49}, {8'd48}, {8'd47}, {8'd46}, {8'd45}, {8'd44}, {8'd43}, {8'd42}, {8'd41}, {8'd40}, {8'd39}, {8'd38}, {8'd37}, {8'd36}, {8'd35}, {8'd34}, {8'd33}, {8'd32}, {8'd31}, {8'd30}, {8'd29}, {8'd28}, {8'd27}, {8'd26}, {8'd25}, {8'd24}, {8'd23}, {8'd22}, {8'd21}, {8'd20}, {8'd19}, {8'd18}, {8'd17}, {8'd16}, {8'd15}, {8'd14}, {8'd13}, {8'd12}, {8'd11}, {8'd10}, {8'd9}, {8'd8}, {8'd7}, {8'd6}, {8'd5}, {8'd4}, {8'd3}, {8'd2}, {8'd1}};
        #(CYCLE_PERIOD)
        io_data_in_valid = 1'b0;
        #(CYCLE_PERIOD)    
        #100;
    end


endmodule