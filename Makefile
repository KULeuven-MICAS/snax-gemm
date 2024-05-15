MK_DIR   := $(dir $(realpath $(lastword $(MAKEFILE_LIST))))

CHISEL_GENERATED_DIR = $(MK_DIR)generated

CHISEL_MODULE = BareBlockGemm

CHISEL_GENERATED_FILES = $(MK_DIR)generated/gemm/$(CHISEL_MODULE).sv

$(CHISEL_GENERATED_FILES):
	mkdir -p $(MK_DIR)generated/gemm && cd $(MK_DIR) && sbt "runMain gemm.$(CHISEL_MODULE)"

.PHONY: clean-data clean

clean-chisel-generated-sv:
	rm -f -r $(CHISEL_GENERATED_FILES) $(CHISEL_GENERATED_DIR)

clean: clean-chisel-generated-sv	
