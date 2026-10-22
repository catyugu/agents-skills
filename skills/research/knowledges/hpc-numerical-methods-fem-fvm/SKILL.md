---
name: hpc-numerical-methods-fem-fvm
description: HPC FEM/FVM开源项目知识库——deal.II、MFEM、PETSc等20+项目速查及核心算法模式。
version: "1.1"
author: "Agnes"
license: "MIT"
tags: [hpc, fem, fvm, open-source, numerical-methods]
related_skills: [metahotspot-project]
---

## When to Use

用户询问 FEM/FVM 开源项目、HPC 线性代数库、GPU 加速科学计算或 CMake 数值软件构建模式时加载此技能。

# HPC 数值方法：FEM & FVM 开源项目速查

## 项目总览

### FEM 框架

| 项目 | GitHub | 定位 | 并行 | GPU |
|------|--------|------|------|-----|
| deal.II | dealii/dealii | C++20 FEM，AMR，生产级 | MPI+线程 | ✗ |
| MFEM | mfem/mfem (LLNL) | 轻量模块化FEM，任意阶 | MPI+GPU | ✓ |
| FEniCS/DOLFINx | FEniCS/dolfinx | 自动化FEM，UFL变分语言 | MPI | ✗ |
| libMesh | libMesh/libmesh | 自适应并行FEM框架 | MPI | ✗ |
| MOOSE | idaholab/moose (INL) | 多物理场FEM，基于libMesh | MPI | ✗ |
| Elmer | ElmerCSC/elmerfem | 多物理场Fortran求解器 | MPI | ✗ |
| CalculiX | Dhondtguido/CalculiX | Abaqus兼容FEM | 串行 | ✗ |

核心抽象：`Triangulation`(网格) → `DoFHandler`(自由度) → `FE`(有限元空间) → `AffineConstraints`(约束)

### FVM 框架

| 项目 | GitHub | 定位 |
|------|--------|------|
| OpenFOAM | OpenFOAM/OpenFOAM-dev | 业界最广泛开源CFD，fvMesh + fvMatrix |
| OpenLB | openLB/openLB | 格子Boltzmann方法(LBM)，GPU加速 |

### 线性代数与求解器

| 项目 | GitHub | 定位 | GPU |
|------|--------|------|-----|
| Eigen | gitlab.com/libeigen/eigen | header-only线性代数，表达式模板 | ✗ |
| AMGCL | ddemidov/amgcl | header-only代数多重网格(AMG) | ✓ |
| PETSc | petsc/petsc | 可扩展科学计算工具包(KSP/SNES) | ✓ |
| Trilinos | trilinos/trilinos (Sandia) | 模块化HPC框架(Belos/Teko) | ✓ |
| Hypre | hypre-space/hypre (LLNL) | 并行预条件子(BoomerAMG/BoxMG) | ✓ |
| Ginkgo | ginkgo-project/ginkgo | GPU加速稀疏线性代数 | ✓ |

关键模式：`find_package(PETSc)` / `find_package(Eigen3 REQUIRED)`；PETSc抽象：`Vec`/`Mat`/`KSP`/`PC`/`SNES`

### 性能可移植性框架

| 项目 | GitHub | 核心抽象 |
|------|--------|---------|
| Kokkos | kokkos/kokkos (Sandia) | `View<T[]>` + `parallel_for/reduce` + 执行空间分离 |
| libCEED | CEED/libCEED | `CeedOperator`，运行时代码生成(NVRTC/HIPRTC) |
| AMReX | AMReX-Codes/amrex | `BoxArray` + `FArrayBox` + `AmrCore`，块结构化AMR |

Kokkos 典型用法：
```cpp
Kokkos::View<double*> x("x", n);
Kokkos::parallel_for("kernel", Kokkos::RangePolicy<ExecSpace>(0, n),
    KOKKOS_LAMBDA(const int& i) { output[i] = input[i] * 2.0; });
```

### 其他工具

- **SUNDIALS** (llnl/sundials): CVODE(ODE)/IDA(DAE)/KINSOL(非线性)
- **Gmsh** (gmsh): 3D网格生成，参数化几何
- **OpenCASCADE** (OCCT): BRep CAD内核，STEP/IGES交换

## 核心算法模式（精简）

### AMG V-cycle（AMGCL风格）
```
预平滑 → 计算残差 r=b-Ax → 限制 f=R·r → 递归粗问题 → 延拓 x+=P·u → 后平滑
```
- Ruge-Stüben粗化：强耦合阈值 eps_strong=0.25，C/F分割后构造插值算子P
- 平滑器：Jacobi(并行友好) vs Gauss-Seidel(串行但收敛快)

### FEM 组装（MFEM风格）
四种级别：LEGACY → FULL → ELEMENT → PARTIAL(PA,GPU友好) → NONE(matrix-free)
- PA模式：存储(B,G,Dv)积分器数据，算子作用时实时计算 y_e += B^T·G^T·Dv·G·B·x_e

### Krylov 方法（GMRES）
Arnoldi迭代 + Givens旋转更新Hessenberg矩阵，左/右预处理可选。

## 工程实践要点

1. **内存布局**：SoA优于AoS（SIMD友好），预分配 `reserve()` 避免反复malloc
2. **避免临时对象**：`A.adjoint()*b` 而非 `A.transpose()*b`（无转置拷贝）
3. **并行**：oneTBB `parallel_for(blocked_range)` 自动负载均衡；Eigen表达式自动融合
4. **跨平台**：MSVC `/W4 /WX /permissive- /utf-8 /bigobj`；Clang `-Werror -Wall -Wextra`

## 学习路径

入门：Eigen → AMGCL → libCEED → MFEM → deal.II  
深入：PETSc → Trilinos → Kokkos → AMReX → OpenFOAM

关键数学：变分形式、Sobolev空间、多重网格理论、Krylov子空间、预条件子理论
