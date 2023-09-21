vlog -f gemm_filelist.f
vsim -voptargs=+acc work.gemm_test
add wave -position insertpoint  \
sim:/gemm_test/clock \
sim:/gemm_test/reset \
sim:/gemm_test/io_start_do \
sim:/gemm_test/io_data_in_valid \
sim:/gemm_test/io_a_io_in \
sim:/gemm_test/io_b_io_in \
sim:/gemm_test/io_data_out_valid \
sim:/gemm_test/io_c_io_out
run
run
run