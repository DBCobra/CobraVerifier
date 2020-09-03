import subprocess
from subprocess import TimeoutExpired
import sys
import time
import os
import re
from gen_config import CobraConfig

# 1. run (a) BE19
# 2. run (b), BE19 SAT encoding + MiniSAT
# 3. run (c), BE19 SAT encoding + Z3
# 4. run (d), SMT encoding + Z3

g_timeout=600 # 600 # 10min
g_bsls = ["be19", "sat-mini", "smt-z3", "mono", "cobra",
         #"sat-z3", "be19-sat"
          ]
g_results = {} # exp => runtime

def getMonoRuntime(out_bin):
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


def runCmdTimeout(cmd, sol, size):
    global g_timeout, g_results

    print(cmd)
    runtime = 0
    start = time.time()
    try:
        result = subprocess.run(cmd, timeout=g_timeout, stdout=subprocess.PIPE)
        runtime = getMonoRuntime(result.stdout)
        print("runtime=%d" % runtime)
    except TimeoutExpired as e:
        print(e)
    except:
        print("Unexpected error:", sys.exc_info()[0])
        raise
    finally:
        end = time.time()

    if sol not in g_results:
        g_results[sol] = {}
    if runtime > 0:  # for mono and cobra
        g_results[sol][size] = runtime
    else:  # for other bsls
        g_results[sol][size] = (end - start) * 1000  # in ms

def getSize(exp):
    elems = re.split("-|\.", exp)
    size = 0
    is_next = False
    for elem in elems:
        if "chengRW" in elem:
            is_next = True
        elif is_next:
            size = int(elem)
            break
    assert size > 0;
    return size

def runBe19(bin_dir, data_dir):
    # # run BE19; your results are in "/output_dir"
    # $ cd /path/to/CobraVerifier/BE19/dbcop-master/
    # $ ./target/debug/dbcop verify --cons ser --out_dir /output_dir/ --ver_dir /newly_created_dir/
    # [Note: "--cons ser" means checking serializability]
    #
    # # run BE19-bic; your results are in "/output_dir"
    # $ ./target/debug/dbcop verify --bic --cons ser --out_dir /output_dir/ --ver_dir /newly_created_dir/
    #
    prog = bin_dir + "/dbcop"
    my_data = data_dir + "/be19/"
    rslt_data = data_dir + "/result/"
    sol = "be19"

    for exp in os.listdir(my_data):
        size = getSize(exp)
        exp_dir = my_data + exp
        exp_rslt = rslt_data + exp
        cmd = [prog, "verify", "--cons", "ser", "--out_dir", exp_rslt, "--ver_dir", exp_dir]
        runCmdTimeout(cmd, sol, size)

def runBe19SAT(bin_dir, data_dir):
    prog = bin_dir + "/dbcop"
    my_data = data_dir + "/be19/"
    rslt_data = data_dir + "/result/"
    sol = "be19"

    for exp in os.listdir(my_data):
        size = getSize(exp)
        exp_dir = my_data + exp
        exp_rslt = rslt_data + exp
        cmd = [prog, "verify", "--sat", "--cons", "ser", "--out_dir", exp_rslt, "--ver_dir", exp_dir]
        runCmdTimeout(cmd, sol, size)

def runCNF(prog, bin_dir, data_dir):
    my_data = data_dir + "/cnf/"
    sol = "sat-" + prog

    for exp in os.listdir(my_data):
        size = getSize(exp)
        exp_file = my_data + exp
        if prog == "z3":
            cmd = [prog, '-dimacs', exp_file]
            runCmdTimeout(cmd, sol, size)
        else:
            cmd = [prog, exp_file]
            runCmdTimeout(cmd, sol, size)


def runSatZ3(bin_dir, data_dir):
    runCNF("z3", bin_dir, data_dir)


def runSatMini(bin_dir, data_dir):
    runCNF("minisat", bin_dir, data_dir)

def runSmtZ3(bin_dir, data_dir):
    my_data = data_dir + "/smt/"
    sol = "smt-z3"

    for exp in os.listdir(my_data):
        size = getSize(exp)
        exp_file = my_data + exp
        cmd = ["z3", "-smt2", exp_file]
        runCmdTimeout(cmd, sol, size)

def runMono(bin_data, data_dir):
    sol = "mono"
    runCobraBin(sol, data_dir, False)

def runCobra(bin_data, data_dir):
    sol = "cobra"
    runCobraBin(sol, data_dir, True)

def runCobraBin(sol, data_dir, is_cobra):
    my_data = data_dir + "/raw/"
    config = CobraConfig("cobra.conf.default")
    # database doesn't matter
    if is_cobra:
        config.set_all('rocksdb', ww_cons=True, bundle=True, infer=True, pcsg=True)
    else:
        config.set_all('rocksdb', ww_cons=False, bundle=False, infer=False, pcsg=False)
    config_file = "cobra.conf"
    config.dump_to(config_file)

    for exp in os.listdir(my_data):
        size = getSize(exp)
        exp_raw_dir = my_data + exp
        # FIXME: hard code path here
        cmd = ['java',
                '-ea', '-Djava.library.path=../include/',
                '-jar', '../target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar',
                'mono', 'audit',
                config_file, exp_raw_dir]
        runCmdTimeout(cmd, sol, size)


def dumpOutput():
    global g_results

    print("=============")
    print("%-20s%-15s%s" % ("Experiment", "size", "Runtime(ms)"))
    for sol in g_results:
        print("---------")
        sizes = list(g_results[sol].keys())
        sizes.sort()
        for size in sizes:
            print("%-20s%-15d%d" % (sol, size, int(g_results[sol][size])))
    print("=============")



def main(bsls, bin_dir, data_dir):
    print(bsls)
    # run (a)-(d) and count the time
    if "be19" in bsls:
        runBe19(bin_dir, data_dir)
    if "sat-mini" in bsls:
        runSatMini(bin_dir, data_dir)
    if "smt-z3" in bsls:
        runSmtZ3(bin_dir, data_dir)
    if "mono" in bsls:
        runMono(bin_dir, data_dir)
    if "cobra" in bsls:
        runCobra(bin_dir, data_dir)

    # not included in all
    if "sat-z3" in bsls:
        runSatZ3(bin_dir, data_dir)
    if "be19-sat" in bsls:
        runBe19SAT(bin_dir, data_dir)


    dumpOutput()


def usage():
    print("Usage: run_bsl.py [be19|sat-mini|smt-z3|mono|cobra|sat-z3|be19-sat|all] <bin-folder> <data-folder>")

def parseBaselines(str_bsls):
    bsls = []
    if "all" in str_bsls:
        return g_bsls
    for bsl in g_bsls:
        if bsl in str_bsls:
            bsls.append(bsl)
    return bsls


if __name__ == "__main__":
    if len(sys.argv) != 4:
        usage()
        exit(1)
    bsls = parseBaselines(sys.argv[1])
    bin_dir = sys.argv[2]
    data_dir = sys.argv[3]
    main(bsls, bin_dir, data_dir)
