#!/bin/bash

# this is Verifier/bsl
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"

function fail {
    echo "FAIL: $1"
    exit 1
}

function deploy {
    # (1) copy BE19 transalator
    cp "$SCRIPT_DIR/BE19/BE19_translator/target/debug/translator" "$SCRIPT_DIR/bin/" || fail "copy BE19 translator"
    echo "[DONE] copy BE19 translator"

    # (2) copy BE19 dbcop
    cp "$SCRIPT_DIR/BE19/dbcop-master/target/debug/dbcop" "$SCRIPT_DIR/bin/" || fail "copy BE19 dbcop"
    echo "[DONE] copy BE19 dbcop"

    # (3) copy cnf buliiding
    cp "$SCRIPT_DIR/MiniSAT/build_cnf.py" "$SCRIPT_DIR/bin/" || fail "copy SAT cnf"
    echo "[DONE] copy SAT CNF"

    # (4) copy cnf buliiding
    cp "$SCRIPT_DIR/Z3/build_smt.py" "$SCRIPT_DIR/bin/" || fail "copy SMT"
    echo "[DONE] copy SMT"
}

function genworkloads {
    # clear data folder
    mkdir -p $SCRIPT_DIR/data 2> /dev/null
    rm -r $SCRIPT_DIR/data/* 2> /dev/null

    # copy raw data for Mono and Cobra
    mkdir -p $SCRIPT_DIR/data
    cp -r "$COBRA_LOGS/raw" "$SCRIPT_DIR/data/raw"

    # generate binhistory for (a)
    mkdir -p $SCRIPT_DIR/data/be19/

    for path in $COBRA_LOGS/raw/*; do
        FNAME=$(filename $path)
        echo "BE19-encoding: $path; $FNAME"
        $SCRIPT_DIR/bin/translator $path "$SCRIPT_DIR/data/be19/$FNAME/" || fail "generate bin-history for BE19"
    done
    echo "[DONE] generate bin-history for BE19"

    # generate CNF for (b) and (c)
    # generate SMT for (d)
    mkdir -p $SCRIPT_DIR/data/cnf/
    mkdir -p $SCRIPT_DIR/data/smt/

    for polyg in $COBRA_LOGS/polyg/*; do
        FNAME=$(filename $polyg)
        echo "CNF: $polyg; $FNAME"
        python3 $SCRIPT_DIR/bin/build_cnf.py be19 partial $polyg "$SCRIPT_DIR/data/cnf/$FNAME.cnf" || fail "generate cnf for $polyg"
        python3 $SCRIPT_DIR/bin/build_smt.py linear $polyg "$SCRIPT_DIR/data/smt/$FNAME.smt" || fail "generate SMT for $polyg"
    done
    echo "[DONE] generate CNF for BE19"
    echo "[DONE] generate SMT for linear"
}




if [ $# != 1 ]; then
    echo "Usage: ./deploy_gen.sh <CobraLogs/bsl>"
    exit 1
fi

COBRA_LOGS="$1"
if [ ! -d "$COBRA_LOGS" ]; then
    fail "$COBRA_LOGS needs to be the log folder"
fi


deploy
genworkloads
