#!/bin/bash
if [ "$JAVA_HOME" == "" ]; then
  echo "JAVA_HOME is empty"
  exit 1
fi
if [ "$CUDA_PATH" == "" ]; then
  echo "CUDA_PATH is empty"
  exit 1
fi

#CUDA_PATH="/usr/local/cuda"
CUDA_INC=$CUDA_PATH/include
CUDA_LIB=$CUDA_PATH/lib64
CUDNN_PATH=$HOME/scratch/cudnn/cuda
CUDNN_INC=$CUDNN_PATH/include
CUDNN_LIB=$CUDNN_PATH/lib64

CFLAGS="-I$CUDA_INC -I$CUDNN_INC -g -O3"
LDFLAGS="-L$CUDA_LIB -L$CUDNN_LIB -Xlinker "-rpath,$CUDA_LIB" -Xlinker "-rpath,$CUDNN_LIB" -lcuda -lcudart -lcublas -lcusparse"

unameOut="$(uname -s)"
case "${unameOut}" in
    Linux*)     SYS=Linux;;
    Darwin*)    SYS=Mac;;
    CYGWIN*)    SYS=Cygwin;;
    MINGW*)     SYS=MinGw;;
    *)          SYS="UNKNOWN:${unameOut}"
esac

JAVA_V=$(java -version 2>&1 | grep -i version | cut -d'"' -f2 | cut -d'.' -f1-2)

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

function fail {
  echo $1
  exit 1
}

if [ "$JAVA_V" == "1.8" ]; then
  javac -h $DIR/include/ $DIR/src/main/java/gpu/GPUmm.java || fail "ERROR javac"
elif [ "$JAVA_V" == "1.7" ]; then
  javac $DIR/src/main/java/gpu/GPUmm.java || fail "ERROR javac"
  javah gpu.GPUmm || fail "ERROR javah"
  mv gpu_GPUmm.h include/ || fail "ERROR mv"
else
  fail "ERROR: unknown java version [ $JAVA_V ]"
fi

if [ "$SYS" == "Mac" ]; then
  g++ -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin/" \
    -shared -o $DIR/include/libgpumm.dylib $DIR/include/verifier_gpu_GPUmm.cpp || fail "ERROR g++"
elif [ "$SYS" == "Linux" ]; then
  #g++ -fPIC -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux/" \
  #  -shared -o $DIR/include/libgpumm.so $DIR/include/verifier_gpu_GPUmm.cpp || fail "ERROR g++"
  nvcc \
    -Xcompiler -fPIC\
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux/" \
    -shared -o $DIR/include/libgpumm.so $DIR/include/gpu_GPUmm.cu \
    -std=c++11 $LDFLAGS $CFLAGS $CUDA_ARCH  || fail "ERROR nvcc"
fi


if [ "$1" == "run" ]; then
  echo "---------RUN-------------"
  cd $DIR
  # FIXME: not working? cannot load class GPUmm
  java -Djava.library.path=$DIR/include/ src/main/java/verifier.gpu.GPUmm
  cd -
fi
