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
run
run
run