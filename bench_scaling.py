import sys
import os
import time
from subprocess import TimeoutExpired
import subprocess
import pickle
from gen_config import CobraConfig



g_timeout = 600
g_clients = 24


def getWorkloadSize(out_bin):
    out = out_bin.decode('ascii') # input is binary
    #print(out)
    size = 0
    lines = out.split("\n")
    for line in lines:
        # [1] #Clients=24; #new_txns=2400
        if "new_txns" in line:
            elems = line.split("#new_txns=")
            assert len(elems) == 2
            size = size + int(elems[1])
    return size

def runCmdTimeout(cmd):
    global g_timeout

    print(cmd)
    start = time.time()
    try:
        result = subprocess.run(cmd, timeout=g_timeout, stdout=subprocess.PIPE)
        size = getWorkloadSize(result.stdout)
    except TimeoutExpired as e:
        print(e)
        return 1
    except:
        print("Unexpected error:", sys.exc_info()[0])
        raise
    finally:
        end = time.time()

    return (end - start) * 1000, size # in ms


def dump_output_space(results):
    global g_clients
    # results = {bench : {batch1: throughput; batch2: throughput; ...}; ...}
    str_out = "%-15s%-15s%s\n" % ("benchmark", "batch", "throughput")
    for bench in results:
        str_out += "------------\n"
        batch_sizes = list(results[bench].keys())
        batch_sizes.sort()
        for batch in batch_sizes:
            line = "%-15s%-15d%.2f\n" % (bench, batch*g_clients, results[bench][batch])
            str_out += line
    str_out += "------------\n"
    print(str_out)


def main(t_folder):
    config_file = "cobra.conf"

    # BATCH_TX_VERI_SIZE
    # Note: the actual size = BATCH_TX_VERI_SIZE * #clients (24)
    batch_sizes = [ 50, 100, 150, 200, 250, 300, 400 ]
    #skip_benches = ["chengRW-100000", "tpcc-100000"]
    #skip_benches = ["chengRW-100000", "chengRM-100000", "rubis-100000", "tpcc-100000", "twitter-100000"]
    skip_benches = ["chengRW-100000", "twitter-100000", "tpcc-100000"]

    results = {} # {bench : {batch1: throughput; batch2: throughput; ...}; ...}
    for bench in os.listdir(t_folder):
        if bench in skip_benches: # skip if necessary
            continue

        # clear naming, bench = "chengRW-6000"
        bench_name = (bench.split("-"))[0]
        num_txns = int(bench.split("-")[1])

        for batch in batch_sizes:
            if bench_name not in results:
                results[bench_name] = {}
            assert batch not in results[bench_name]

            config = CobraConfig("cobra.conf.default")
            config.set_all('rocksdb', batch_size=batch) # database doesn't matter
            config.dump_to(config_file)

            cmd = ['java',
                    '-ea', '-Djava.library.path=./include/',
                    '-jar', './target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar',
                    'mono', 'continue',
                    config_file, ("%s/%s" % (t_folder, bench))]
            runtime, num_commit_txns = runCmdTimeout(cmd)

            print("======\n %s-%s (%dk) runtime=%.2fs\n========\n" % (bench_name, batch, num_txns/1000, runtime/1000.0))
            results[bench_name][batch] = num_commit_txns / (runtime/1000.0) # throughput
            #with open('/tmp/scaling.pkl', 'w') as f:
            #    pickle.dump(results, f)

    # (3) print/dump
    #print(results)
    dump_output_space(results)


def usage_exit():
    print("usage: ./bench_scaling.py <scaling_folder>")
    exit(1)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        usage_exit()
    tpath = sys.argv[1]
    if not os.listdir(tpath) :
        print("Target %s is empty" % tpath)
        exit(1)
    else:
        print("Target %s contains files" % tpath)
    main(tpath)
