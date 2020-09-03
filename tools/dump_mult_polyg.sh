#! /bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

EA="-ea"

function fail {
    echo "ERROR: $1"
    exit 1
}

function usage {
  echo " Usage: run.sh <config> <traces-folder> <dump_file>"
}

function dumpPolyg {
    if [ "$#" != 3 ]; then
        echo "dumpPolyg needs three arguments: config, history, dump_file"
    fi
    echo "[INFO] dumppolyg $1 $2 $3"
    time java $EA -Djava.library.path=$SCRIPT_DIR/include/ -jar \
      target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar mono dump "$1" "$2" "$3"|| fail "FAIL: java benchmark"
}

if [ "$#" != 3 ]; then
  usage
  exit 1
fi

CONFIG_PATH=$1
if [ "$CONFIG_PATH" == "-" ]; then
  CONFIG_PATH="$SCRIPT_DIR/cobra.conf"
fi

INPUT_DIR=$2
OUTPUT_DIR=$3

if [ ! -d $INPUT_DIR ]; then
    echo "$INPUT_DIR is not DIR"
    usage
    exit 1
fi
if [ ! -d $OUTPUT_DIR ]; then
    echo "$OUTPUT_DIR is not DIR"
    usage
    exit 1
fi

for dir in $INPUT_DIR/*
do
    if [[ -d $dir ]]; then
        echo "[INFO] processing workload [$dir]"
        FILENAME=$(filename $dir)
        dumpPolyg $CONFIG_PATH $dir "$OUTPUT_DIR/$FILENAME.polyg"
    fi
done



echo "DONE"
