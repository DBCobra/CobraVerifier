import subprocess
from subprocess import TimeoutExpired
import sys
import time
import os
import re
from gen_config import CobraConfig


g_timeout=600 # 600 # 10min
g_results = {} # sol => exp => runtime

def getRuntime(out_bin):
    out = out_bin.decode('ascii') # input is binary
    runtime = 0
    lines = out.split("\n")
    for line in lines:
        # [INFO ] >>> Overall runtime = 1497ms
        if "Overall runtime" in line:
            elems = line.split("=")
            assert len(elems) == 2
            runtime = int(elems[1][:-2])
    return runtime


def runCmdTimeout(cmd, sol, bench, drift):
    global g_timeout, g_results

    print(cmd)
    runtime = 0
    start = time.time()
    try:
        result = subprocess.run(cmd, timeout=g_timeout, stdout=subprocess.PIPE)
        runtime = getRuntime(result.stdout)
        print("runtime=%d" % runtime)
    except TimeoutExpired as e:
        print(e)
    except:
        print("Unexpected error:", sys.exc_info()[0])
        #raise
    finally:
        end = time.time()

    if sol not in g_results:
        g_results[sol] = {}
    if bench not in g_results[sol]:
        g_results[sol][bench] = {}

    assert drift not in g_results[sol][bench]
    if runtime > 0:  # for mono and cobra
        g_results[sol][bench][drift] = runtime
    else:  # for other bsls
        g_results[sol][bench][drift] = (end - start) * 1000  # in ms



def getBenchDrift(exp):
    # chengRW-2k-1s-0ms
    elems = re.split("-|\.", exp)
    print(elems)
    assert len(elems) == 4
    return elems[0], int(elems[3][:-2])

def getBench(exp):
    # chengRW-2k
    elems = re.split("-|\.", exp)
    print(elems)
    assert len(elems) == 2
    return elems[0]

def runSmtZ3(data_dir):
    sol = "smt-z3"
    for exp in os.listdir(data_dir):
        if "tpcc" in exp:
            continue
        if "100ms" not in exp:
            continue
        bench,drift = getBenchDrift(exp)
        exp_file = data_dir + exp
        # FIXME: hard code path here
        cmd = ["python", "./Z3/z3_smt.py", exp_file]
        runCmdTimeout(cmd, sol, bench, drift)

def runMono(data_dir):
    sol = "mono"
    runCobraBin(sol, data_dir, False)

def runCobra(data_dir):
    sol = "cobra"
    runCobraBin(sol, data_dir, True)

def runCobraBin(sol, data_dir, is_cobra):
    config = CobraConfig("cobra.conf.default")
    #drifts = [0, 10, 20, 50, 100]
    drifts = [100]
    # database doesn't matter
    if is_cobra:
        config.set_all('rocksdb', ww_cons=True, bundle=True, infer=True, pcsg=True)
    else:
        config.set_all('rocksdb', ww_cons=False, bundle=False, infer=False, pcsg=False)
    config_file = "cobra.conf"

    for exp in os.listdir(data_dir):
        if "tpcc" in exp:
            continue
        for drift in drifts:
            config.confs['TIME_ORDER_ON'] = True
            config.confs['TIME_DRIFT_THRESHOLD'] = drift
            config.dump_to(config_file)
            bench = getBench(exp)
            exp_raw_dir = data_dir + exp
            # FIXME: hard code path here
            cmd = ['java',
                    '-ea', '-Djava.library.path=../include/',
                    '-jar', '../target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar',
                    'mono', 'audit',
                    config_file, exp_raw_dir]
            runCmdTimeout(cmd, sol, bench, drift)


def dumpOutput():
    global g_results

    print("=============")
    print("%-20s%-15s%-15s%s" % ("Experiment", "bench", "size", "Runtime(ms)"))
    for sol in g_results:
        print("---------")
        for bench in g_results[sol]:
            sizes = list(g_results[sol][bench].keys())
            sizes.sort()
            for size in sizes:
                print("%-20s%-15s%-15d%d" % (sol, bench, size, int(g_results[sol][bench][size])))
    print("=============")



def main(data_dir):
    raw_data_dir = data_dir + "/raw/"
    polyg_data_dir = data_dir + "/polyg/"
    runSmtZ3(polyg_data_dir)
    runMono(raw_data_dir)
    runCobra(raw_data_dir)
    dumpOutput()


def usage():
    print("Usage: run_z3_mono_cobra.py <ss-test>")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        usage()
        exit(1)
    data_dir = sys.argv[1]
    main(data_dir)
