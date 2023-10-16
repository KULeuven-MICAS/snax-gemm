# Integer GEMM Accelerator for SNAX
This repository contains the RTL to build an 8-bit integer GEMM (General Matrix Multiply) accelerator 
to integrate into the [SNAX core](https://github.com/KULeuven-micas/snitch_cluster). This repository contains the chisel sources and unit tests for the GEMM accelerator.
It is written in CHISEL 5.0.0 and connected to the SNAX accelerator RISC-V manager core through SystemVerilog. 

## Microarchitecture
The microarchitecture of the GEMM accelerator is shown below. The GEMM array has meshRow row and meshCol column tiles. Each tile implements a tileSize vector dot product.
![](./docs/microarch.png)

## Functional description
This repository contains three GEMM versions: Base GEMM, Large GEMM, and Batch Large GEMM.
### Base GEMM
Base GEMM implements General Matrix Multiply: C = A * B. Base GEMM is parameterized and its parameters are listed below.
| Parameter | Meaning |
| - | -|
| input | Input matrix data width (integer) |
| output | Output matrix data width (integer) |
| accumulate | Accumulator data width (integer) |
| meshRow | The row number of the GEMM array |
| meshCol | The column number of the GEMM array |
| tileSize | The tile size of each tile |

The size of matrix A is (meshRow, tileSize) and the size of matrix B is (tileSize, meshCol). The size of result matrix C is (meshRow, meshCol). To get the right results, matrix A should be arranged in row-major order and matrix B should be arranged in column-major order.
![](./docs/datalayout_mem.png)

Each row of the GEMM array takes in the corresponding row of matrix A, eg., all the tiles in row ith takes in the ith row of matrix A. And each column of the GEMM array takes in the corresponding column of matrix B, eg., all the tiles in column jth take in the jth column of matrix B.

![](./docs/datalayout.png)

The Base GEMM function definition pseudocode is shown below.
```
bool gemm(signed byte *A,signed byte *B, int * C)
```

### Large GEMM
The Large GEMM is built on the Base GEMM. It takes in the M, K, and N configurations.
In this case, the size of matrix A is (M * meshRow, K * tileSize) and the size of matrix B is (K * tileSize, N * meshCol). The size of result matrix C is (M * meshRow, N * meshCol). The GEMM accelerator uses Block matrix multiplication [](https://en.wikipedia.org/wiki/Block_matrix#Block_matrix_multiplication) method to implement matrix multiplication in which the matrix sizes are much larger than the physical GEMM array. To get the right results, matrixes should have the right layout in memory. Below is an example data layout in memory for M = 2, K = 2, and N = 2. 
![](./docs/block_matrix_mul.png)

The Large GEMM function definition pseudocode is shown below.
```
bool largegemm(int M, int K, int N, signed byte *A,signed byte *B, int * C)
```

### Batch Large GEMM
The Batch Large GEMM is built on the Large GEMM. It takes in an extra Batch configuration. The computation flow is shown as the following picture.
![](./docs/batch_block_matrix_mul.png)

The Batch Large GEMM function definition pseudocode is shown below.
```
bool batchlargegemm(int B, int M, int K, int N, signed byte *A,signed byte *B, int * C)
```

### Batch Stride Large GEMM
The Batch Stride Large GEMM is built on the Batch Large GEMM. It takes in three extra strides configuration, eg., strideA, strideB, and strideC, for computing the start matrix address for each batch. 

The Batch Stride Large GEMM function definition pseudocode is shown below.
```
bool batchstridelargegemm(int B, int M, int K, int N, int strideA, int strideB, int strideC, signed byte *A,signed byte *B, int * C)
```

## Unit test
This repository also contains some unit tests for each version of GEMM to do the numerical test. The unit tests are also written in Chisel. Firstly, the random input matrixes and random size configurations are generated. Then these matrixes and configurations are input into the GEMM accelerator. After the computation, the output result of the GEMM accelerator will be compared with the golden model.

## Quick Start
Following this quick start to set up the Chsiel environment and run the unit test of the GEMM accelerator.
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
To run the GEMM accelerator tests, use:
```
sbt test
```
