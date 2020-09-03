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
#include <cmath>

using namespace std;

// ====== Optimiations ====
#define REGULATE_GPU  // ON
//#define OPT_TRIANGULAR_MM      // OFF
#define OPT_SPARSE_MATRIX        // OFF
#define OPT_EARLY_TERMINATION  // ON


// ====== Vars ======
#define THREADS_PER_BLOCK 512
#define REGULATE_BATCH 1000

#define MAX_N 30000ul
//#define MAX_N 16384ul
#define MAX_NNZ ((MAX_N) * 20)

// sparse matrix optimization
#ifdef OPT_SPARSE_MATRIX
  #define MAGIC_SPARSE_THRESHOLD1 0.01
  #define MAGIC_SPARSE_THRESHOLD2 12
#else
  #define MAGIC_SPARSE_THRESHOLD1 0
  #define MAGIC_SPARSE_THRESHOLD2 0
#endif

// early termination optimization
#define MAGIC_EARLY_TERMINATION_THRESHOLD 256





const float alpha = 1.0;
const float beta = 0.0;

// TODO: check which API needs sync
float *gpu_m, *gpu_m2, *gpu_csr_val;
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

bool
staySparse(int n, int nnz) {
  if (nnz < n * n * MAGIC_SPARSE_THRESHOLD1) {
    return true;
  } else {
    return false;
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
  cout << "  [GPU] dense matrix => sparse matrix \n";
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
  cout << "  [GPU] sparse matrix => dense matrix \n";
}

void
sparseSmm(cusparseHandle_t handle, cusparseMatDescr_t descr,
        float *csr_val, int *csr_rowptr, int *csr_colind,
        float *gpu_src, float *gpu_dst,
        int nnz_total, int n) {
  CUSPARSE_CALL(cusparseScsrmm(
      handle, CUSPARSE_OPERATION_NON_TRANSPOSE,
      n, n, n, nnz_total,
      &alpha, descr,
      csr_val, csr_rowptr, csr_colind,
      gpu_src, n,
      &beta, gpu_dst, n));
  CUDA_CALL(cudaThreadSynchronize());
  cout << "  [GPU] sparse mm\n";
}

void
denseSgemm(cublasHandle_t handle, float *gpu_src, float *gpu_dst, int n) {
  CUBLAS_CALL(cublasSgemm(
        handle,
        CUBLAS_OP_N, CUBLAS_OP_N,
        n, n, n,
        &alpha,
        gpu_src, n,
        gpu_src, n,
        &beta,
        gpu_dst, n));
  CUDA_CALL(cudaThreadSynchronize());
  cout<< "  [GPU] dense gemm\n";
}

void
denseStrmm(cublasHandle_t handle, float *gpu_src, float *gpu_dst, int n) {
  CUBLAS_CALL(cublasStrmm(
      handle,
      CUBLAS_SIDE_LEFT,
      CUBLAS_FILL_MODE_UPPER,
      CUBLAS_OP_N,
      CUBLAS_DIAG_UNIT,
      n, n,
      &alpha,
      gpu_src, n,
      gpu_src, n,
      gpu_dst, n));
  CUDA_CALL(cudaThreadSynchronize());
  cout<< "  [GPU] dense trmm\n";
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
  cout << "  [GPU] sparse-sparse mm \n";
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
    //assert(false);
  } else {
    // init the sparse matrix
  cudaDense2sparse(handle, descr, dense_m, nnz_row, csr_val,
        csr_rowptr, csr_colind, nnz_total, n);
    cout << "[INFO] matrix is sparse, using sparse\n";
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

void regulateCPU(float* a, int size) {
  for (int i=0; i<size; i++) {
    a[i] = 2 * (a[i] != 0);
  }
}

__global__
void regulateGPU(float *a, int length) {
  int index = (threadIdx.x + blockIdx.x * blockDim.x) * REGULATE_BATCH;
  //printf("block %d, thread %d, index[%d] => [%f]\n", blockIdx.x, threadIdx.x, index, a[index]);
  for (int i=0; i<REGULATE_BATCH; i++) {
    if (index+i < length) {
      a[index + i] = 2 * (a[index + i] != 0);
    }
  }
}

void regulate(float *gpu_m, int length, float *cpu_m) {
#ifdef REGULATE_GPU
  int num_blocks = ceil((double)length/THREADS_PER_BLOCK/REGULATE_BATCH);
  regulateGPU<<<num_blocks, THREADS_PER_BLOCK>>>(gpu_m, length);
  auto e = cudaGetLastError();
  if ( cudaSuccess !=  e ) {
    cout << "CUDA: " << cudaGetErrorString(e) << endl;
    assert(false);
  }
  CUDA_CALL(cudaThreadSynchronize());
#else
  CUDA_CALL(cudaMemcpy(cpu_m, gpu_m, length*sizeof(float), cudaMemcpyDeviceToHost));
  regulateCPU(cpu_m, length);
  CUDA_CALL(cudaMemcpy(gpu_m, cpu_m, length*sizeof(float), cudaMemcpyHostToDevice));
#endif
}


__device__ int matrix_diff;

__global__
void initEarlyTermination() {
  matrix_diff = 0;
}

__global__
void compareGPU(float *gpu_m_1, float *gpu_m_2, int length) {
  int index = (threadIdx.x + blockIdx.x * blockDim.x) * REGULATE_BATCH;
  for (int i=0; i<REGULATE_BATCH; i++) {
    if (index+i < length) {
      if ( (gpu_m_1[index + i] != 0) != (gpu_m_2[index + i] != 0) ) {
        matrix_diff = 1;
      }
    }
  }
}


bool earlyTermination2(float *gpu_m_1, float *gpu_m_2, int length, int dense_m, int sparse_m) {
#ifdef OPT_EARLY_TERMINATION
  if ( ((dense_m > sparse_m) ? dense_m : sparse_m) < MAGIC_EARLY_TERMINATION_THRESHOLD) {
    return false;
  }
  initEarlyTermination<<<1,1>>>();
  auto e = cudaGetLastError();
  if ( cudaSuccess !=  e ) {
    cout << "CUDA: " << cudaGetErrorString(e) << endl;
    assert(false);
  }
  int num_blocks = ceil((double)length/THREADS_PER_BLOCK/REGULATE_BATCH);
  compareGPU<<<num_blocks, THREADS_PER_BLOCK>>>(gpu_m_1, gpu_m_2, length);
  e = cudaGetLastError();
  if ( cudaSuccess !=  e ) {
    cout << "CUDA: " << cudaGetErrorString(e) << endl;
    assert(false);
  }
  CUDA_CALL(cudaThreadSynchronize());
  typeof(matrix_diff) diff;
  cudaMemcpyFromSymbol(&diff, matrix_diff, sizeof(diff), 0, cudaMemcpyDeviceToHost);
  // if they are the same, we're done
  return diff == 0;
#else
  return false;
#endif
}

bool earlyTermination(float *gpu_m_1, float *gpu_m_2, int length) {
#ifdef OPT_EARLY_TERMINATION
  float* result_1 = (float*) malloc (sizeof(float));
  CUBLAS_CALL(cublasSasum(handle_c, length, gpu_m_1, 1 /*?*/, result_1));
  float* result_2 = (float*) malloc (sizeof(float));
  CUBLAS_CALL(cublasSasum(handle_c, length, gpu_m_2, 1 /*?*/, result_2));
  if (*result_1 == *result_2) {
    printf("EarlyTermination: %.3f == %.3f\n", *result_1, *result_2);
  }
  return *result_1 == *result_2;
#else
  return false;
#endif
}




void swapSrcDst(float *&gpu_src, float *&gpu_dst) {
  // swap
  float *tmp = gpu_src;
  gpu_src = gpu_dst;
  gpu_dst = tmp;
}

// ====== exposed functions =====

JNIEXPORT void JNICALL Java_gpu_GPUmm_init(JNIEnv *env, jclass cls) {
  int n = MAX_N;
  int nnz_total = MAX_NNZ;

  // (1) allocate and initialize GPU matrix memory
  CUBLAS_CALL(cublasCreate(&handle_c));
  CUDA_CALL(cudaMalloc(&gpu_m, n*n*sizeof(float)));
  CUDA_CALL(cudaMalloc(&gpu_m2, n*n*sizeof(float)));

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
  CUDA_CALL(cudaFree(gpu_m2));
  CUDA_CALL(cudaFree(gpu_nnz_row));
  CUDA_CALL(cudaFree(gpu_csr_val));
  CUDA_CALL(cudaFree(gpu_csr_rowptr));
  CUDA_CALL(cudaFree(gpu_csr_colind));
}


void dumpM(float* a, int n);
/*
 * Connect src_list -> dst_list and update the reachability matrix
 */
JNIEXPORT void JNICALL Java_gpu_GPUmm_connect(JNIEnv *env, jclass cls,
    jfloatArray fb, jintArray src_list, jintArray dst_list, jint jn)
{
  //cout << "    [GPU] gpu connect start...\n";
  int n = (int) jn;
  int len = (int) env->GetArrayLength(src_list);
  int m_size = sizeof(float) * n * len;
  int src_inds[len], dst_inds[len];

  jint *jsrc_inds = env->GetIntArrayElements(src_list, 0);
  jint *jdst_inds = env->GetIntArrayElements(dst_list, 0);
  for (int i=0; i<len; i++) {
    src_inds[i] = jsrc_inds[i];
    dst_inds[i] = jdst_inds[i];
  }

  // FIXME: can reuse some of the other matrix space
  float *cpu_src_matrix, *cpu_dst_matrix, *gpu_src_matrix, *gpu_dst_matrix;
  cpu_src_matrix = (float*) malloc(m_size);
  cpu_dst_matrix = (float*) malloc(m_size);
  CUDA_CALL(cudaMalloc(&gpu_src_matrix, m_size));
  CUDA_CALL(cudaMalloc(&gpu_dst_matrix, m_size));

  float *cpu_matrix = (float*) env->GetPrimitiveArrayCritical(fb, 0);
  if (cpu_matrix == NULL) {
    cout << "cpu_matrix is NULL!!!\n";
    return;
  }

  // update connect nodes
  // This should happen before collecting the update matrix
  for (int i=0; i<len; i++) {
    int src = src_inds[i];
    int dst = dst_inds[i];
    // NOTE: this is COLUMN-MAJOR storage!!!!
    cpu_matrix[dst*n + src] = 1;
    // UTBABUG: src->[dst~->all] and [all~->src]->dst
    for (int j=0; j<n; j++) {
      // src->[dst~->all]
      if (cpu_matrix[j*n + dst] != 0) {
        cpu_matrix[j*n + src] = 1;
      }
      // [all~->src]->dst
      if (cpu_matrix[src*n + j] != 0) {
        cpu_matrix[dst*n + j] = 1;
      }
    }
  }

  // construct update matrix
  for (int i=0; i<len; i++) {
    int src = src_inds[i];
    int dst = dst_inds[i];
    // NOTE: this is COLUMN-MAJOR storage!!!!
    // vector[x->src]
    for (int j=0; j<n; j++) {
      //cout << "src---["<<i*n+j<<"/"<<m_size<<"], [" << src*n +j << "/" << n*n << "]\n";
      cpu_src_matrix[i*n + j] = cpu_matrix[src*n + j];
    }
    // vector[dst->x]
    for (int j=0; j<n; j++) {
      //cout << "dst---["<<j*len+i<<"/"<<m_size<<"], [" << j*n+dst << "/" << n*n << "]\n";
      cpu_dst_matrix[j*len + i] = cpu_matrix[j*n + dst];
    }
  }

  CUDA_CALL(cudaMemcpy(gpu_src_matrix, cpu_src_matrix, m_size, cudaMemcpyHostToDevice));
  CUDA_CALL(cudaMemcpy(gpu_dst_matrix, cpu_dst_matrix, m_size, cudaMemcpyHostToDevice));
  CUDA_CALL(cudaMemcpy(gpu_m, cpu_matrix, n*n*sizeof(float), cudaMemcpyHostToDevice));

  const float m_beta = 1.0;
  // core: A x B + C -> C
  CUBLAS_CALL(cublasSgemm(
        handle_c,
        CUBLAS_OP_N, CUBLAS_OP_N,
        n, n, len,
        &alpha,
        gpu_src_matrix, n,
        gpu_dst_matrix, len,
        &m_beta,
        gpu_m, n));
  CUDA_CALL(cudaThreadSynchronize());

  // regulate the matrix
  CUDA_CALL(cudaMemcpy(cpu_matrix, gpu_m, n*n*sizeof(float), cudaMemcpyDeviceToHost));
  regulateCPU(cpu_matrix, n*n);

  // done
  env->ReleasePrimitiveArrayCritical(fb, cpu_matrix, 0);

  // free GPU memory
  CUDA_CALL(cudaFree(gpu_src_matrix));
  CUDA_CALL(cudaFree(gpu_dst_matrix));
  free(cpu_src_matrix);
  free(cpu_dst_matrix);

  //cout << "  [GPU] ...connect ends\n";
}



void dumpPartM(float* a, int printn, int n);

int
power(float *cpu_m, int n, bool fresh) {
  if (n > MAX_N) {
    cout << "ERROR, too large a 'n'(" << n << ") size, maximum " << MAX_N << "\n";
    assert(false);
  }
  cout << "[INFO] n=" << n << "\n";


  // (1) copy the matrix to GPU
  CUDA_CALL(cudaMemcpy(gpu_m, cpu_m, n*n*sizeof(float), cudaMemcpyHostToDevice));

  // (2) check if to use sparse
  int nnz = fresh ? dense2sparse(handle_s, descr, gpu_nnz_row, gpu_m,
               gpu_csr_val, gpu_csr_rowptr, gpu_csr_colind, n) :
            MAX_NNZ;

  // (3) matrix multiplication
  timeval start, end;
  gettimeofday(&start, 0);

  int dense_m = 1;
  // (3.1) sparse mm first
  bool used_sparse = false;
  while(fresh && staySparse(n, nnz)) {
    nnz = sparseSparseMM(handle_ss, descr,
      gpu_csr_val, gpu_csr_rowptr, gpu_csr_colind, nnz, n);
    regulate(gpu_csr_val, nnz, cpu_m);
    dense_m*= 2;
    used_sparse = true;
  }

  // (3.2) convert sparse to dense
  if (used_sparse) {
    sparse2dense(handle_s, descr,
        gpu_csr_val, gpu_csr_rowptr, gpu_csr_colind,
        gpu_m, n);
    // reset the memory used by sparse MM
    CUDA_CALL(cudaFree(gpu_csr_val));
    CUDA_CALL(cudaFree(gpu_csr_rowptr));
    CUDA_CALL(cudaFree(gpu_csr_colind));
    CUDA_CALL(cudaMalloc(&gpu_csr_val, sizeof(float) * MAX_NNZ)  );
    CUDA_CALL(cudaMalloc(&gpu_csr_rowptr, sizeof(int) * (MAX_N +1) ) ) ;
    CUDA_CALL(cudaMalloc(&gpu_csr_colind, sizeof(int) * MAX_NNZ) ) ;
  }

  // (3.3) dense mm then
  float *gpu_src = gpu_m;
  float *gpu_dst = gpu_m2;

  while(dense_m < n) {
#ifdef OPT_TRIANGULAR_MM
    denseStrmm(handle_c, gpu_src, gpu_dst, n);
#else
    denseSgemm(handle_c, gpu_src, gpu_dst, n);
#endif
    dense_m *= 2;
    regulate(gpu_dst, n*n, cpu_m);
    if(earlyTermination(gpu_src, gpu_dst, n*n)) {
      cout << "Early termination, dense_m=" << dense_m << ", n=" << n << "\n";
      break;
    }
    swapSrcDst(gpu_src, gpu_dst);
  }

  gettimeofday(&end, 0);
  double milli = (end.tv_sec - start.tv_sec) * 1000 + (end.tv_usec - start.tv_usec) * .001;

  // (4) copy the result out
  CUDA_CALL(cudaMemcpy(cpu_m, gpu_m, n*n*sizeof(float), cudaMemcpyDeviceToHost));
  cout << "DONE, DM^" << dense_m << ", time = " << milli << "ms\n";

  return 0;
}



JNIEXPORT void JNICALL Java_gpu_GPUmm_power (JNIEnv *env, jclass cls, jfloatArray jarr, jint jn, jboolean jfresh) {
  int n = (int) jn;
  bool fresh = (bool) jfresh;
  float *matrix = (float*) env->GetPrimitiveArrayCritical(jarr, 0);
  //float *matrix = (float*) env->GetFloatArrayElements(jarr, 0);
  if (matrix == NULL) {
    cout << "NULL!!!\n";
    return;
  }

  power(matrix, n, fresh);

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


int
selfmm(float *cpu_m, int n) {
  if (n > MAX_N) {
    cout << "ERROR, selfmm, too large a 'n'(" << n << ") size, maximum " << MAX_N << "\n";
    assert(false);
  }
  cout << "[INFO] selfmm, n=" << n << "\n";

  float *gpu_src = gpu_m;
  float *gpu_dst = gpu_m2;

  // (1) copy the matrix to GPU
  CUDA_CALL(cudaMemcpy(gpu_src, cpu_m, n*n*sizeof(float), cudaMemcpyHostToDevice));

  // (3) matrix multiplication
  timeval start, end;
  gettimeofday(&start, 0);

#ifdef OPT_TRIANGULAR_MM
        denseStrmm(handle_c, gpu_src, gpu_dst, n);
#else
        denseSgemm(handle_c, gpu_src, gpu_dst, n);
#endif

  gettimeofday(&end, 0);
  double milli = (end.tv_sec - start.tv_sec) * 1000 + (end.tv_usec - start.tv_usec) * .001;

  // (4) copy the result out
  CUDA_CALL(cudaMemcpy(cpu_m, gpu_dst, n*n*sizeof(float), cudaMemcpyDeviceToHost));
  cout << "DONE, selfmm, time = " << milli << "ms\n";

  return 0;
}


JNIEXPORT void JNICALL Java_gpu_GPUmm_selfmm(JNIEnv *env, jclass cls, jfloatArray jarr, jint jn) {
  int n = (int) jn;
  float *matrix = (float*) env->GetPrimitiveArrayCritical(jarr, 0);
  //float *matrix = (float*) env->GetFloatArrayElements(jarr, 0);
  if (matrix == NULL) {
    cout << "NULL!!!\n";
    return;
  }

  selfmm(matrix, n);

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
  for (int i=n/2; i<n/2+printn; i++) {
    for (int j=n/2; j<n/2+printn; j++) {
      cout << a[i*n+j] << "  ";
    }
    cout << "\n";
  }
  cout << "===\n";
}
