# Integer GEMM Accelerator for SNAX
This repository contains the RTL to build an 8-bit integer GEMM accelerator 
to integrate into the [SNAX core](https://github.com/KULeuven-micas/snitch_cluster). 
It is written in CHISEL 5.0.0 and connected to the SNAX accelerator RISC-V manager core through SystemVerilog. 

## Directory structure
### chisel gemm

## Chisel environment
### install Java
```
sudo apt install openjdk-11-jre-headless
sudo apt install openjdk-11-jdk-headless
```

### install Sbt
```
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get update
sudo apt-get install sbt
```

### install Firrtl
* download the Firtool from https://github.com/llvm/circt/releases/tag/firtool-1.42.0 and unzip it. The verson is 1.42.0.
* Add the bin of Firrtl to the PATH

## Run tests
To run the gemm accelerator tests, use:
```
cd chisel-project
sbt test
``` 
