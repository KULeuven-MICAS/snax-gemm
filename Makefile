MK_DIR   := $(dir $(realpath $(lastword $(MAKEFILE_LIST))))

CHIISEL_GENERATED_DIR = $(MK_DIR)/generated

CHISEL_MODULE = BatchGemmSnaxTop
# CHISEL_MODULE = BatchGemm

CHIISEL_GENERATED_FILES = $(MK_DIR)/generated/gemm/$(CHISEL_MODULE).sv

$(CHIISEL_GENERATED_FILES):
	mkdir -p $(MK_DIR)/generated/gemm && cd $(MK_DIR) && sbt "runMain gemm.$(CHISEL_MODULE)"

.PHONY: clean-data clean

clean-chisel-generated-sv:
	rm -f -r $(CHIISEL_GENERATED_FILES) $(CHIISEL_GENERATED_DIR)

clean: clean-chisel-generated-sv	
