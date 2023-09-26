# Integer GEMM Accelerator for SNAX
This repository contains the RTL to build an 8-bit integer GEMM accelerator 
to integrate into the [SNAX core](https://github.com/KULeuven-micas/snitch_cluster). 
It is written in CHISEL 5.0.0 and connected to the SNAX accelerator RISC-V manager core through SystemVerilog. 

## Directory structure
### chisel-gemm
This directory contains the chisel src file and unit test of Gemm. We also need this `chisel-gemm` project to generate the SystemVerilog file to be integrated into SNAX.
### src
This directory contains the SystemVerilog controller for connecting the Gemm to the SNAX core.
### tb
This directory contains the testbench for the SystemVerilog version of Gemm and a simple testbench for Accelerator with the Gemm.
### dv
This directory contains the design verification utils for running the testbench for Gemm and the Accelerator.

## Chisel environment
### Install Java
```
sudo apt install openjdk-11-jre-headless
sudo apt install openjdk-11-jdk-headless
```

### Install Sbt
```
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get update
sudo apt-get install sbt
```

### Install Firtool
* Download the Firtool from https://github.com/llvm/circt/releases/tag/firtool-1.42.0 and unzip it. The verson is 1.42.0.
* Add the bin of Firtool to the PATH

## Run tests
To run the gemm accelerator tests, use:
```
cd chisel-project
sbt test
``` 
