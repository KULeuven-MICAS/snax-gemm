# Accelerator with a GEMM
This accelerator is built to integrate into the SNAX core.

## Chisel environment
### install Java
```
sudo apt install openjdk-11-jre-headless
sudo apt install openjdk-11-jdk-headless
```
<!-- 
### install Scala
* download Scala source file from: https://www.scala-lang.org/download/all.html and unzip it. Scala vesion: 2.13.8.
* Add the bin of Scala to the PATH -->

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
<!-- 
## Chisle template project
Use this template Chisel project to test the environmen is ok and start playing with Chisel.
```
git clone git@github.com:freechipsproject/chisel-template.git
cd chisel-template
sbt test
``` -->

## Chisle project
Use this Chisel project to test the environmen is ok and start playing with Chisel. By defualt, you do a small test to Gemm and generate system verilog file for Gemm.
```
cd chisel-project
sbt test
``` 
