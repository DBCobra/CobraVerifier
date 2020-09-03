#! /bin/bash

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

EA="-ea"

function usage {
  echo " Usage: run.sh <trace_folder> <dump_file_folder>"
}

if [ "$#" != 2 ] || [ ! -d "$1" ] || [ ! -d "$2" ]; then
  usage
  exit 1
fi

TRACES="$1"
OUTPUTS="$2"
CONFIG_PATH="$SCRIPT_DIR/cobra.conf"


for d in $TRACES/*; do
    FNAME=$(filename $d)
    time java $EA $PROF -Djava.library.path=$SCRIPT_DIR/include/ -jar \
        target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar mono dump "$CONFIG_PATH" "$d" "$OUTPUTS/$FNAME.polyg"|| fail "FAIL: java benchmark"
done

echo "DONE"
