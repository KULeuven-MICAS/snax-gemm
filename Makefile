MK_DIR   := $(dir $(realpath $(lastword $(MAKEFILE_LIST))))

define gen_sv_file
	mkdir -p $(MK_DIR)generated/gemm && cd $(MK_DIR) && sbt "runMain gemm.$(1)"
endef

CHISEL_GENERATED_DIR = $(MK_DIR)generated

CHISEL_MODULE = BareBlockGemm

CHISEL_GENERATED_FILES = $(MK_DIR)generated/gemm/$(CHISEL_MODULE).sv

$(CHISEL_GENERATED_FILES):
	$(call gen_sv_file,$(CHISEL_MODULE))

# add for the BatchGemmSnaxTop for the old version gemm(base gemm)
CHISEL_MODULE_OLD = BatchGemmSnaxTop

CHISEL_GENERATED_FILES_OLD = $(MK_DIR)generated/gemm/$(CHISEL_MODULE_OLD).sv

$(CHISEL_GENERATED_FILES_OLD):
	$(call gen_sv_file,$(CHISEL_MODULE_OLD))

.PHONY: clean-data clean

clean-chisel-generated-sv:
	rm -f -r $(CHISEL_GENERATED_FILES) $(CHISEL_GENERATED_FILES_OLD) $(CHISEL_GENERATED_DIR)

clean: clean-chisel-generated-sv	
