#! /bin/bash

if [ "$COBRA_HOME" == "" ]; then
  echo "COBRA_HOME hasn't been set"
  exit 1
fi

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

EA="-ea"
#MONO="-Djava.library.path=$SCRIPT_DIR/monosat/libmonosat.so -cp $SCRIPT_DIR/monosat/monosat.jar"
#MONO="-cp $SCRIPT_DIR/monosat/"

source $COBRA_HOME/env.sh


function usage {
  echo " Usage: run.sh mono [audit|continue|epoch|ep-remote] <config> <traces>"
  echo " Usage: run mono dump <config path> <benchmark path> <dumpgraph path>"
  echo " Usage: run.sh build"
  echo " Usage: run.sh perf (=run.sh cobra continue)"
}


function buildJNI {
  ./jni.sh || fail "build jni"
  echo "JNI build done"
}

if [ "$1" == "build" ]; then
  buildJNI
  mvn install || fail "mvn install"
  exit 0
fi


if [ "$1" == "perf" ]; then
  PROF="-agentlib:hprof=file=hprof.txt,cpu=samples"
  time java $EA $PROF -Djava.library.path=$SCRIPT_DIR/include/ -jar \
    target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar cobra audit || fail "FAIL: java benchmark"
fi

if [ "$1" != "mono" ]; then
  usage
  exit 1
fi

if [ "$2" != "audit" ] && [ "$2" != "continue" ] && [ "$2" != "epoch" ] && [ "$2" != "ep-remote" ] && [ "$2" != "dump" ]; then
  usage
  exit 1
fi

CONFIG_PATH=$3
if [ "$CONFIG_PATH" == "-" ]; then
  CONFIG_PATH="$SCRIPT_DIR/cobra.conf"
fi


if [ "$#"  == "2" ]; then
  time java $EA $MONO -Djava.library.path=$SCRIPT_DIR/include/ -jar \
    target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar "$1" "$2" || fail "FAIL: java benchmark"
fi

if [ "$#"  == "3" ]; then
  time java $EA $MONO -Djava.library.path=$SCRIPT_DIR/include/ -jar \
    target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar "$1" "$2" "$CONFIG_PATH" || fail "FAIL: java benchmark"
fi

if [ "$#"  == "4" ]; then
  time java $EA $MONO -Djava.library.path=$SCRIPT_DIR/include/ -jar \
    target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar "$1" "$2" "$CONFIG_PATH" "$4"|| fail "FAIL: java benchmark"
fi

if [ "$#"  == "5" ]; then
  time java $EA $MONO -Djava.library.path=$SCRIPT_DIR/include/ -jar \
    target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar "$1" "$2" "$CONFIG_PATH" "$4" "$5"|| fail "FAIL: java benchmark"
fi

#elif [ "$1" == "debug" ]; then
#  time java -agentlib:hprof=cpu=samples $EA -Djava.library.path=$SCRIPT_DIR/include/ -jar \
#      target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar count T T T T /tmp/cobra/log || fail "FAIL: java benchmark"
#fi

echo "DONE"
