# Integer GEMM Accelerator for SNAX
This repository contains the RTL to build an 8-bit integer GEMM (General Matrix Multiply) accelerator 
to integrate into the [SNAX core](https://github.com/KULeuven-micas/snitch_cluster). This repository contains the chisel sources and unit tests for the GEMM accelerator.
It is written in CHISEL 5.0.0 and connected to the SNAX accelerator RISC-V manager core through SystemVerilog. 

## Microarchitecture
The microarchitecture of the GEMM accelerator is shown below. The GEMM array has meshRow row and meshCol column tiles. Each tile implements a tileSize vector dot product.
![](./docs/microarch.png)

## Functional description
<!-- This repository contains three GEMM versions: Base GEMM, Block GEMM, and Batch Block GEMM. -->
### Base GEMM
Base GEMM implements General Matrix Multiply: C = A * B. Base GEMM is parameterized and its parameters are listed below. These parameters can be configured in the `main/scala/gemm/Parameter.scala` file.
| Parameter | Meaning |
| - | -|
| dataWidthA | Input matrix data width (integer) |
| dataWidthC | Output matrix data width (integer) |
| dataWidthAccum | Accumulator data width (integer) |
| meshRow | The row number of the GEMM array |
| meshCol | The column number of the GEMM array |
| tileSize | The tile size of each tile |

The size of matrix A is (meshRow, tileSize) and the size of matrix B is (tileSize, meshCol). The size of result matrix C is (meshRow, meshCol). To get the right results, matrix A should be arranged in `row-major order` and matrix B should be arranged in `column-major order`. The result matrix C is arranged in `row-major order` by Base Gemm. The data layout of the three matrices is shown below. The data in each small block indicates the address. How the address increases shows the data arrangement in memory.
![](./docs/datalayout_mem.png)

Each row of the GEMM array takes in the corresponding row of matrix A, eg., all the tiles in row ith takes in the ith row of matrix A. And each column of the GEMM array takes in the corresponding column of matrix B, eg., all the tiles in column jth take in the jth column of matrix B. This process is shown in the figure below in detail.

![](./docs/datalayout.png)

The base GEMM also has an accumulation option. If the signal `accumulate` is one, then the accumulator register will store the current result. If the signal `accumulate` is zero, then the accumulator register will clear itself.

The Base GEMM function definition pseudocode is shown below.
```
bool gemm(int8_t *A, int8_t *B, int32_t * C, bool accumulate)
```

### Block GEMM
The Block GEMM is built with the Base GEMM. 
The GEMM accelerator uses the [Block matrix multiplication method](https://en.wikipedia.org/wiki/Block_matrix#Block_matrix_multiplication) to implement matrix multiplication in which the matrix sizes are larger than the physical GEMM array. 

It takes in the M, K, and N configurations as the size of the block gemm. 
In this case, the size of matrix A is (M' = M * meshRow, K' = K * tileSize) and the size of matrix B is (K' = K * tileSize, N' = N * meshCol). The size of result matrix C is (M' = M * meshRow, N' = N * meshCol) as shown below.

![](./docs/general_mm.png)

#### Computation analysis
Take a Block Gemm with hardware parmaters: `meshRow == tileSize == meshCol == 8` as an example, the M, K, and N should be configured as 4, 2, and 3 respectively if we want to do the block matrix multiplication with C (32,24) = A (32,16) * B (16,24), M’ = 32, K’ = 16, N’ = 24. The block matrix multiplication is illustrated in the picture below. With: 
* M<sub>u</sub> (M unrolling) = meshRow = 8
* K<sub>u</sub> (K unrolling) = tileSize = 8
* N<sub>u</sub> (N unrolling) = meshCol = 8

![](./docs/block_mm.png)

The pseudocode is :
```
for (m1 = 0 to M’/Mu - 1); 
for (n1 = 0 to N’/Nu - 1); 
for (k1 = 0 to K’/Ku - 1); 
  parallel_for(m2 = 0 to Mu)
  parallel_for(n2 = 0 to Nu)
  parallel_for(k2 = 0 to Ku)
  o[m1*Mu+m2][n1*Nu+n2] += i[m1*Mu+m2][k1*Ku+k2] * w[k1*Ku+k2][n1*Nu+n2];
```

#### Data layout
To get the right results, matrices should have the right layout in memory. The address of martix A, B, and C should also be given when configuring the Block Gemm. In the current version, these data should be stored in memory in a continuous address style. 

Take a Block Gemm with hardware parameters: `meshRow == tileSize == meshCol == 8` as an example, the M, K, and N should be configured as 3, 4, and 5 respectively if we want to do the block matrix multiplication with C (24,40) = A (24,32) * B (32,40), M’ = 24, K’ = 32, N’ = 40 as shown in below.

![](./docs/block_mm_for_new_datalayout.png)

To get the right results, the matrix A should be arranged as M<sub>3</sub> K<sub>4</sub> M<sub>8</sub> K<sub>8</sub>. B should be arranged as N<sub>5</sub> K<sub>4</sub> N<sub>8</sub> K<sub>8</sub>. C should be arranged as M<sub>3</sub> N<sub>5</sub> M<sub>8</sub> N<sub>8</sub>. Take matrix A for example, the innermost is 8 elements in the K dimension, then is the 8 rows in the M dimension, then is 4 big columns in the K dimension, and the outermost is 3 big rows in the M dimension.
![](./docs/data_layout.png)

To illustrate it more for clarification, another example is for Block Gemm with hardware parameters: `meshRow == tileSize == meshCol == 2` and with a block matrix multiplication for  M = 2, K = 2, and N = 2. The two figures below illustrate the detailed data layout in memory. The continuous liens in the first picture are the conceptual addresses in memory. The concrete layout in memory for each element is shown in the second picture.
![](./docs/block_matrix_mul.png)

![](./docs/flattened_data_layout.png)

#### Programming model
The Block GEMM function c wrapper pseudocode is shown below.
```
bool blockGemm(int M, int K, int N, int8_t *A, int8_t *B, int32_t * C)
```

#### Ports
| Signals | Signal name in generated SV | Width | Dir | Description |
| - | - | - | - | - |
| M_i | i_io_ctrl_M_i | 8 | In | The M size setting |
| K_i | i_io_ctrl_K_i | 8 | In | The K size setting |
| N_i | i_io_ctrl_N_i | 8 | In | The N size setting |
| ptr_addr_a_i | io_ctrl_ptr_addr_a_i | 32 | In | The address of the matrix A. It points to the first data element of matrix A.|
| ptr_addr_b_i | io_ctrl_ptr_addr_b_i | 32 | In | The address of the matrix B. It points to the first data element of matrix B.|
| ptr_addr_c_i | io_ctrl_ptr_addr_c_i | 32 | In | The address of the matrix C. It points to the first data element of matrix C.|
| start_do_i | io_ctrl_start_do_i | 1 | In |  Start do signal. When this `start_do_i` signal asserts, all the configuration signals (M_i, K_i, N_i, ptr_addr_a_i, ptr_addr_b_i and ptr_addr_c_i) should be ready| 
| data_valid_i | io_ctrl_data_valid_i | 1 | In | Input data valid signal|
| addr_a_o | io_ctrl_addr_a_o | 32 | Out | The address of the sub-matrix of A. It points to the first data element of the sub-matrix of A. |
| addr_b_o | io_ctrl_addr_b_o | 32 | Out | The address of the sub-matrix of B. It points to the first data element of the sub-matrix of B |
| addr_c_o | io_ctrl_addr_c_o | 32 | Out | The address of the sub-matrix of C. It points to the first data element of the sub-matrix of C. |
| busy_o | io_ctrl_busy_o | 1 | Out | Indicating if Block Gemm is busy. When `busy_o` is asserted, the Block Gemm doesn't take in any new `start_do_i` signal or configuration.|
| a_i | io_data_a_i | meshRow * tileSize * input | In | A big bus containing all the data elements of the input (sub-)matrix A |
| b_i | io_data_b_i | tileSize * meshCol * input | In | A big bus containing all the data elements of the input (sub-)matrix B |
| c_o | io_data_c_o | meshRow * meshCol * output | Out | A big bus containing all the data elements of the input (sub-)matrix C |
## Unit test
This repository also contains some unit tests for each version of GEMM. 
The unit tests are also written in Chisel. Firstly, random input matrices and random size configurations are generated. 
Then these matrices and configurations are fed into the GEMM accelerator. 
After the computation, the output result of the GEMM accelerator will be compared with the golden model in Scala.

There is also a corner case test for Block Gemm to see if the Block Gemm works well in extreme configurations, such as M == K == N == 1.

In the current unit test, we only test the GEMM with input datatype as int8 and output data type as int32. The GEMM array size is also fixed.

## Quickstart
Follow this quickstart to set up the Chisel environment and run the unit tests of the GEMM accelerator.
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
* Download the Firtool from https://github.com/llvm/circt/releases/tag/firtool-1.42.0 and unzip it. The version is 1.42.0.
* Add the bin of Firtool to the PATH

## Run tests
To run the Gemm accelerator tests, use:
```
sbt test
```
