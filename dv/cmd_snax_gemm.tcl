vlog -f gemm_filelist.f
vsim -voptargs=+acc work.snax_gemm_tb
add wave -position insertpoint  \
sim:/snax_gemm_tb/i_snax_gemm/clk_i \
sim:/snax_gemm_tb/i_snax_gemm/rst_ni \
sim:/snax_gemm_tb/i_snax_gemm/snax_qvalid_i \
sim:/snax_gemm_tb/i_snax_gemm/snax_qready_o \
sim:/snax_gemm_tb/i_snax_gemm/snax_req_i \
sim:/snax_gemm_tb/i_snax_gemm/snax_resp_o \
sim:/snax_gemm_tb/i_snax_gemm/snax_pvalid_o \
sim:/snax_gemm_tb/i_snax_gemm/snax_pready_i \
sim:/snax_gemm_tb/i_snax_gemm/snax_tcdm_req_o \
sim:/snax_gemm_tb/i_snax_gemm/snax_tcdm_rsp_i
add wave -position insertpoint  \
sim:/snax_gemm_tb/i_snax_gemm/cstate \
sim:/snax_gemm_tb/i_snax_gemm/nstate
add wave -position insertpoint  \
sim:/snax_gemm_tb/i_snax_gemm/CSRs
add wave -position insertpoint  \
sim:/snax_gemm_tb/i_snax_gemm/io_start_do
add wave -position insertpoint  \
sim:/snax_gemm_tb/i_snax_gemm/io_a_io_in \
sim:/snax_gemm_tb/i_snax_gemm/io_b_io_in \
sim:/snax_gemm_tb/i_snax_gemm/io_data_out_valid \
sim:/snax_gemm_tb/i_snax_gemm/io_c_io_out
add wave -position insertpoint  \
sim:/snax_gemm_tb/i_snax_gemm/io_data_in_valid
add wave -position insertpoint  \
sim:/snax_gemm_tb/i_snax_gemm/io_c_io_out_reg
run
run
run 1000ns