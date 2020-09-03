// JNI
#include <jni.h>
#include "gpu_GPUmm.h"

// CUDA
#include <cuda_runtime.h>
#include <cublas_v2.h>
#include <cusparse.h>

#include <stdio.h>
#include <cassert>
#include <iostream>
#include <fstream>
#include <cstring>
#include <sstream>
#include <sys/time.h>
#include <string>
#include <cstdlib>

using namespace std;

// ====== Optimiations ====
//#define OPT_SPARSE_MATRIX
//#define OPT_EARLY_TERMINATION
//#define OPT_TRIANGULAR_MM

//#define REGULATE_CPU

// ====== Vars ======
#define THREADS_PER_BLOCK 512

#define MAX_N 20000ul
//#define MAX_N 16384ul
#define MAX_NNZ ((MAX_N) * 20)

// sparse matrix optimization
#ifdef OPT_SPARSE_MATRIX
  #define MAGIC_SPARSE_THRESHOLD1 0.01
  #define MAGIC_SPARSE_THRESHOLD2 0
#else
  #define MAGIC_SPARSE_THRESHOLD1 0
  #define MAGIC_SPARSE_THRESHOLD2 0
#endif

// early termination optimization
#ifdef OPT_EARLY_TERMINATION
  #define MAGIC_EARLY_TERMINATION_THRESHOLD 4096
#else
  #define MAGIC_EARLY_TERMINATION_THRESHOLD MAX_N
#endif





const float alpha = 1.0;
const float beta = 0.0;

// TODO: check which API needs sync
float *gpu_m, *gpu_csr_val;
int *gpu_nnz_row, *gpu_csr_rowptr, *gpu_csr_colind;

cublasHandle_t handle_c;
cusparseHandle_t handle_s;
cusparseHandle_t handle_ss;
cusparseMatDescr_t descr;

// ====== Helpers ======

const char* cublasGetErrorString(cublasStatus_t status) {
  switch(status) {
    case CUBLAS_STATUS_SUCCESS: return "CUBLAS_STATUS_SUCCESS";
    case CUBLAS_STATUS_NOT_INITIALIZED: return "CUBLAS_STATUS_NOT_INITIALIZED";
    case CUBLAS_STATUS_ALLOC_FAILED: return "CUBLAS_STATUS_ALLOC_FAILED";
    case CUBLAS_STATUS_INVALID_VALUE: return "CUBLAS_STATUS_INVALID_VALUE";
    case CUBLAS_STATUS_ARCH_MISMATCH: return "CUBLAS_STATUS_ARCH_MISMATCH";
    case CUBLAS_STATUS_MAPPING_ERROR: return "CUBLAS_STATUS_MAPPING_ERROR";
    case CUBLAS_STATUS_EXECUTION_FAILED: return "CUBLAS_STATUS_EXECUTION_FAILED";
    case CUBLAS_STATUS_INTERNAL_ERROR: return "CUBLAS_STATUS_INTERNAL_ERROR";
  }
  return "unknown error";
}

// TODO
const char* cusparseGetErrorString(cusparseStatus_t status) {
  return "cusparse error";
}

#define CUDA_CALL(func) { \
  cudaError_t e = (func); \
  if(e != cudaSuccess) {\
    cout << "CUDA: " << cudaGetErrorString(e) << endl; \
    assert(false);\
  }\
}

#define CUBLAS_CALL(func) {\
  cublasStatus_t e = (func); \
  if(e != CUBLAS_STATUS_SUCCESS) {\
    cout << "cuBlas: " << cublasGetErrorString(e) << endl; \
    assert(false);\
  }\
}

#define CUSPARSE_CALL(func) {\
  cusparseStatus_t e = (func); \
  if(e != CUSPARSE_STATUS_SUCCESS) {\
    cout << "cusparse: " << cusparseGetErrorString(e) << endl; \
    assert(false);\
  }\
}

// ===== functional =====

/* return
 * 0: dense
 * 1: sparse+dense
 * 2: sparse^2
 */
int
chooseMethod(int dense_m, int sparse_m, int n, int nnz) {
  if (nnz < n * n * MAGIC_SPARSE_THRESHOLD1) {
    return 2;
  } else {
    int exp = (dense_m > sparse_m) ? dense_m : sparse_m;
    if (exp < MAGIC_SPARSE_THRESHOLD2) {
      return 1;
    }
    return 0;
  }
}

void
countNNZ(cusparseHandle_t handle, cusparseMatDescr_t descr,
        int* nnzrow, int &nnz_total, float* gpu_m, int n) {
  CUSPARSE_CALL(
    cusparseSnnz(handle, CUSPARSE_DIRECTION_ROW, n,
               n, descr,
               gpu_m,
               n, nnzrow, &nnz_total)
  );
  CUDA_CALL(cudaThreadSynchronize());
  cout << "  count nnz [nnz_total=" << nnz_total << "]\n";
}

int
countResultNNZ(cusparseHandle_t handle, cusparseMatDescr_t descr,
        float *csr_val, int *csr_rowptr, int *csr_colind,
        int* &csr_rowptr_c,
        int nnz_total, int n) {
  int baseC, nnzC;
  // nnzTotalDevHostPtr points to host memory
  int *nnzTotalDevHostPtr = &nnzC;
  CUSPARSE_CALL(cusparseXcsrgemmNnz(handle,
        CUSPARSE_OPERATION_NON_TRANSPOSE, CUSPARSE_OPERATION_NON_TRANSPOSE,
        n, n, n,
        descr, nnz_total, csr_rowptr, csr_colind,
        descr, nnz_total, csr_rowptr, csr_colind,
        descr, csr_rowptr_c, nnzTotalDevHostPtr ));
  if (NULL != nnzTotalDevHostPtr){
      nnzC = *nnzTotalDevHostPtr;
  } else {
      CUDA_CALL(cudaMemcpy(&nnzC, csr_rowptr_c+n, sizeof(int), cudaMemcpyDeviceToHost));
      CUDA_CALL(cudaMemcpy(&baseC, csr_rowptr_c, sizeof(int), cudaMemcpyDeviceToHost));
      nnzC -= baseC;
  }
  return nnzC;
}

void
cudaDense2sparse(cusparseHandle_t handle, cusparseMatDescr_t descr,
        float* gpu_m, int *nnz_row,
        float* &csr_val, int* &csr_rowptr, int* &csr_colind,
        int nnz_total, int n) {
  CUSPARSE_CALL(
    cusparseSdense2csr(handle, n, n,
               descr,
               gpu_m,
               n, nnz_row,
               csr_val,
               csr_rowptr, csr_colind)
   );
  CUDA_CALL(cudaThreadSynchronize());
  cout << "  dense matrix => sparse matrix \n";
}


void
sparse2dense(cusparseHandle_t handle, cusparseMatDescr_t descr,
        float* csr_val, int* csr_rowptr, int* csr_colind,
        float* gpu_m, int n) {
  CUSPARSE_CALL(
    cusparseScsr2dense(handle, n, n,
               descr,
               csr_val, csr_rowptr, csr_colind,
               gpu_m,
               n)
   );
  CUDA_CALL(cudaThreadSynchronize());
  cout << "  sparse matrix => dense matrix \n";
}

void
sparseSmm(cusparseHandle_t handle, cusparseMatDescr_t descr,
        float *csr_val, int *csr_rowptr, int *csr_colind, float *gpu_m,
        int nnz_total, int n) {
  CUSPARSE_CALL(cusparseScsrmm(
      handle, CUSPARSE_OPERATION_NON_TRANSPOSE,
      n, n, n, nnz_total,
      &alpha, descr,
      csr_val, csr_rowptr, csr_colind,
      gpu_m, n,
      &beta, gpu_m, n));
  CUDA_CALL(cudaThreadSynchronize());
  cout << "  sparse mm\n";
}

void
denseSgemm(cublasHandle_t handle, float *gpu_m, int n) {
  CUBLAS_CALL(cublasSgemm(
      handle,
      CUBLAS_OP_N, CUBLAS_OP_N,
      n, n, n,
      &alpha,
      gpu_m, n,
      gpu_m, n,
      &beta,
      gpu_m, n));
  CUDA_CALL(cudaThreadSynchronize());
  cout<< "  dense gemm\n";
}

void
denseStrmm(cublasHandle_t handle, float *gpu_m, int n) {
  CUBLAS_CALL(cublasStrmm(
      handle,
      CUBLAS_SIDE_LEFT,
      CUBLAS_FILL_MODE_UPPER,
      CUBLAS_OP_N,
      CUBLAS_DIAG_UNIT,
      n, n,
      &alpha,
      gpu_m, n,
      gpu_m, n,
      gpu_m, n));
  CUDA_CALL(cudaThreadSynchronize());
  cout<< "  dense trmm\n";
}

void
sparseSparseSmm(cusparseHandle_t handle, cusparseMatDescr_t descr,
        float *csr_val, int *csr_rowptr, int *csr_colind,
        float *csr_val_c, int *csr_rowptr_c, int *csr_colind_c,
        int nnz_total, int n) {
  CUSPARSE_CALL(cusparseScsrgemm(
      handle, CUSPARSE_OPERATION_NON_TRANSPOSE, CUSPARSE_OPERATION_NON_TRANSPOSE,
      n, n, n,
      descr, nnz_total,
      csr_val, csr_rowptr, csr_colind,
      descr, nnz_total,
      csr_val, csr_rowptr, csr_colind,
      descr,
      csr_val_c, csr_rowptr_c, csr_colind_c));
  CUDA_CALL(cudaThreadSynchronize());
  cout << "  sparse-sparse mm \n";
}

int
dense2sparse(cusparseHandle_t handle, cusparseMatDescr_t descr,
  int *nnz_row, float *dense_m,
  float *csr_val, int *csr_rowptr, int *csr_colind,
  int n) {
  int nnz_total;
  // count number of non-zero element
  countNNZ(handle, descr, nnz_row, nnz_total, dense_m, n);
  if (nnz_total > MAX_NNZ) {
    cout << "[INFO] too many non-zeros(" << nnz_total << "), maximum " << MAX_NNZ << "\n";
    cout << "[INFO] stop using sparse\n";
    assert(false);
  } else {
    // init the sparse matrix
  cudaDense2sparse(handle, descr, dense_m, nnz_row, csr_val,
        csr_rowptr, csr_colind, nnz_total, n);
  }
  return nnz_total;
}

int
sparseSparseMM(cusparseHandle_t handle, cusparseMatDescr_t descr,
    float* &csr_val, int* &csr_rowptr, int* &csr_colind,
    int nnz, int n) {
  int *csr_rowptr_ret;
  CUDA_CALL(cudaMalloc(&csr_rowptr_ret, sizeof(int)*(n+1)));

  // (1) count result nnz
  int nnz_ret = countResultNNZ(handle, descr,
      csr_val, csr_rowptr, csr_colind,
      csr_rowptr_ret, nnz, n);

  // (2) alloc result memory
  float *csr_val_ret;
  int   *csr_colind_ret;
  CUDA_CALL(cudaMalloc(&csr_val_ret, sizeof(float)*nnz_ret));
  CUDA_CALL(cudaMalloc(&csr_colind_ret, sizeof(int)*nnz_ret));

  // (3) calc!
  sparseSparseSmm(handle, descr,
    csr_val, csr_rowptr, csr_colind,
    csr_val_ret, csr_rowptr_ret, csr_colind_ret,
    nnz, n);

  // (4) free previous resource and swap the poniter
  CUDA_CALL(cudaFree(csr_rowptr));
  CUDA_CALL(cudaFree(csr_val));
  CUDA_CALL(cudaFree(csr_colind));
  csr_rowptr = csr_rowptr_ret;
  csr_val = csr_val_ret;
  csr_colind = csr_colind_ret;

  return nnz_ret;
}

// ====== exposed functions =====

JNIEXPORT void JNICALL Java_gpu_GPUmm_init(JNIEnv *env, jclass cls) {
  int n = MAX_N;
  int nnz_total = MAX_NNZ;

  // (1) allocate and initialize GPU matrix memory
  CUBLAS_CALL(cublasCreate(&handle_c));
  CUDA_CALL(cudaMalloc(&gpu_m, n*n*sizeof(float)));

  // (2) decide whether use sparse matrix
  //     if so, allocate sparse matrix memory
  CUSPARSE_CALL(cusparseCreate(&handle_s));
  CUSPARSE_CALL(cusparseCreate(&handle_ss));
  CUSPARSE_CALL(cusparseSetPointerMode(handle_ss, CUSPARSE_POINTER_MODE_HOST));
  CUSPARSE_CALL(cusparseCreateMatDescr(&descr));
  CUDA_CALL(cudaMalloc(&gpu_nnz_row, sizeof(int) * n));
  CUDA_CALL(cudaMalloc(&gpu_csr_val, sizeof(float) * nnz_total)  );
  CUDA_CALL(cudaMalloc(&gpu_csr_rowptr, sizeof(int) * (n+1) ) ) ;
  CUDA_CALL(cudaMalloc(&gpu_csr_colind, sizeof(int) * nnz_total) ) ;
}

JNIEXPORT void JNICALL Java_gpu_GPUmm_destroy(JNIEnv *env, jclass cls) {
  CUDA_CALL(cudaFree(gpu_m));
  CUDA_CALL(cudaFree(gpu_nnz_row));
  CUDA_CALL(cudaFree(gpu_csr_val));
  CUDA_CALL(cudaFree(gpu_csr_rowptr));
  CUDA_CALL(cudaFree(gpu_csr_colind));
}


void dumpPartM(float* a, int printn, int n);

void regulateCPU(float* a, int size) {
  for (int i=0; i<size; i++) {
    if (a[i] != 0) a[i] = 1;
  }
}

__global__ void regulateGPU(float *a) {
  int index = threadIdx.x + blockIdx.x * blockDim.x;
  // will divide be inefficient?
  a[index] = a[index] / (a[index] + 1);
}


void regulate(float *gpu_m, int n, float *cpu_m) {
#ifdef REGULATE_CPU
    CUDA_CALL(cudaMemcpy(cpu_m, gpu_m, n*n*sizeof(float), cudaMemcpyDeviceToHost));
    regulateCPU(cpu_m, n*n);
    CUDA_CALL(cudaMemcpy(gpu_m, cpu_m, n*n*sizeof(float), cudaMemcpyHostToDevice));
#else
    regulateGPU<<<n*n/THREADS_PER_BLOCK, THREADS_PER_BLOCK>>>(gpu_m);
#endif
}

int
power(float *cpu_m, int n) {
  if (n > MAX_N) {
    cout << "ERROR, too large a 'n'(" << n << ") size, maximum " << MAX_N << "\n";
    assert(false);
  }
  cout << "[INFO] n=" << n << "\n";


  // (1) copy the matrix to GPU
  CUDA_CALL(cudaMemcpy(gpu_m, cpu_m, n*n*sizeof(float), cudaMemcpyHostToDevice));

  // (3) matrix multiplication
  timeval start, end;
  gettimeofday(&start, 0);

  int dense_m = 1;
  while(dense_m < n) {
    denseSgemm(handle_c, gpu_m, n);
    dense_m *= 2;

    regulate(gpu_m, n, cpu_m);
    dumpPartM(cpu_m, 4, n);
  }

  gettimeofday(&end, 0);
  double milli = (end.tv_sec - start.tv_sec) * 1000 + (end.tv_usec - start.tv_usec) * .001;

  // (4) copy the result out
  CUDA_CALL(cudaMemcpy(cpu_m, gpu_m, n*n*sizeof(float), cudaMemcpyDeviceToHost));
  cout << "DONE, DM^" << dense_m << ", time = " << milli << "ms\n";
}





int
__power(float *cpu_m, int n) {
  if (n > MAX_N) {
    cout << "ERROR, too large a 'n'(" << n << ") size, maximum " << MAX_N << "\n";
    assert(false);
  }
  cout << "[INFO] n=" << n << "\n";

  // (1) copy the matrix to GPU
  CUDA_CALL(cudaMemcpy(gpu_m, cpu_m, n*n*sizeof(float), cudaMemcpyHostToDevice));

  // (2) check if to use sparse
  int nnz = dense2sparse(handle_s, descr, gpu_nnz_row, gpu_m,
      gpu_csr_val, gpu_csr_rowptr, gpu_csr_colind, n);

  // (3) matrix multiplication
  timeval start, end;
  gettimeofday(&start, 0);

  // keep track of the orign matrix
  //float *old_mem = CUDA_CALL(cudaMalloc(&old_mem, n*n*sizeof(float)));
  float *old_mem = (float*) malloc(n*n*sizeof(float));
  bool empty_old_mem = true;

  int dense_m = 1, sparse_m = 1;
  while(dense_m < n && sparse_m < n) {

    if (dense_m > MAGIC_EARLY_TERMINATION_THRESHOLD && empty_old_mem) {
      CUDA_CALL(cudaMemcpy(old_mem, gpu_m, n*n*sizeof(float), cudaMemcpyDeviceToHost));
      empty_old_mem = false;
    }


    switch (chooseMethod(dense_m, sparse_m, n, nnz)) {
      case 0:
        // if we don't have the newest matrix, get one
        if (sparse_m > dense_m) {
          sparse2dense(handle_s, descr,
              gpu_csr_val, gpu_csr_rowptr, gpu_csr_colind,
              gpu_m, n);
          dense_m = sparse_m;
        }
#ifdef OPT_TRIANGULAR_MM
        denseStrmm(handle_c, gpu_m, n);
#else
        denseSgemm(handle_c, gpu_m, n);
#endif
        dense_m *= 2;
        break;
      case 1:
        sparseSmm(handle_s, descr, gpu_csr_val, gpu_csr_rowptr, gpu_csr_colind,
            gpu_m, nnz, n);
        dense_m += sparse_m;
        break;
      case 2:
        nnz = sparseSparseMM(handle_ss, descr,
            gpu_csr_val, gpu_csr_rowptr, gpu_csr_colind, nnz, n);
        sparse_m *= 2;
        break;
      default:
        cout << "ERROR, should never be here\n";
        assert(false);
    }

    // check whether
    if (!empty_old_mem) {
      assert(dense_m >= sparse_m); // I believe it should be dense for now
      // get the current gpu_matrix
      CUDA_CALL(cudaMemcpy(cpu_m, gpu_m, n*n*sizeof(float), cudaMemcpyDeviceToHost));
      // compare old_mem and cpu_m
      int same = memcmp(old_mem, cpu_m, n*n*sizeof(float));
      if (same == 0) {
        cout << "*** Early termination: DM^" << dense_m << " SP^" << sparse_m << "\n";
        break;
      }
      // switch the old_mem and cpu_m, for the reason that,
      // (1) old_mem for next turn will be current cup_m
      // and (2) the cpu_m will not be used anyway
      float *tmp = old_mem;
      old_mem = cpu_m;
      cpu_m = tmp;
    }
  }

  // just in case
  if (sparse_m > dense_m) {
    sparse2dense(handle_s, descr,
        gpu_csr_val, gpu_csr_rowptr, gpu_csr_colind,
        gpu_m, n);
  }
  gettimeofday(&end, 0);
  double milli = (end.tv_sec - start.tv_sec) * 1000 + (end.tv_usec - start.tv_usec) * .001;

  // (4) copy the result out
  CUDA_CALL(cudaMemcpy(cpu_m, gpu_m, n*n*sizeof(float), cudaMemcpyDeviceToHost));
  cout << "DONE, DM^" << dense_m << ", SP^" << sparse_m << ", time = " << milli << "ms\n";
}

JNIEXPORT void JNICALL Java_gpu_GPUmm_power (JNIEnv *env, jclass cls, jfloatArray jarr, jint jn) {
  int n = (int) jn;
  float *matrix = (float*) env->GetPrimitiveArrayCritical(jarr, 0);
  //float *matrix = (float*) env->GetFloatArrayElements(jarr, 0);
  if (matrix == NULL) {
    cout << "NULL!!!\n";
    return;
  }

  power(matrix, n);

  /*
  // debug code
  ofstream outf;
  outf.open("/tmp/mmresult");
  for(int i=0; i<n*n; i++) {
    if (matrix[i] != 0) {
      outf << "1";
    } else {
      outf << "0";
    }
  }
  outf.close();
  */

  env->ReleasePrimitiveArrayCritical(jarr, matrix, 0);
  //env->ReleaseFloatArrayElements(jarr, matrix, 0);
}

void dumpM(float* a, int n) {
  cout << "=== n=" << n <<"\n";
  for (int i=0; i<n; i++) {
    for (int j=0; j<n; j++) {
      cout << a[i*n+j] << "  ";
    }
    cout << "\n";
  }
  cout << "===\n";
}

void dumpPartM(float* a, int printn, int n) {
  cout << "=== n=" << n <<"\n";
  for (int i=0; i<printn; i++) {
    for (int j=0; j<printn; j++) {
      cout << a[i*n+j] << "  ";
    }
    cout << "\n";
  }
  cout << "===\n";
}


