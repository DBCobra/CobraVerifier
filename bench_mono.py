import sys
import os
import signal
from time import sleep
import subprocess
from subprocess import TimeoutExpired
import pickle
from gen_config import CobraConfig

g_timeout = 60

def usage_exit():
    print("usage: ./bench_mono.py <trace_folder>")
    exit(1)

def run_cmd(cmd, time_out=None):
    print(cmd)
    try:
        result = subprocess.run(cmd, timeout=time_out, stdout=subprocess.PIPE)
        return result.stdout.decode('ascii')
    except TimeoutExpired as e:
        print(e)
        print("Wait GPU to free memory")
        sleep(2)
        return "timeoutexpired"
    except:
        print("Unexpected error:", sys.exc_info()[0])
        raise


def run_cmd_old(cmd, time_out=None):
    p = subprocess.Popen(" ".join(cmd), shell=True, stdout=subprocess.PIPE, preexec_fn=os.setsid)
    for t in range(time_out):
        sleep(1)
        if p.poll() is not None:
            (output, err) = p.communicate()
            if err:
                print("Error: %s" % err)
                exit(1)
            p.wait()
            return output
    os.killpg(os.getpgid(p.pid), signal.SIGTERM)
    try:
        p.kill()
        p.terminate()
        sleep(2) # wait GPU to free memory
    finally:
        pass
    while p.poll() is not None:
        sleep(1)
    return 'timeoutexpired'

    return output

def gettime(line):
    time = line.split(":")[-1]
    time = int(time[:-2])
    return time

def get_eq_time(line):
    time = line.split("=")[-1]
    time = int(time[:-2])
    return time

def get_phase(output):
    if output == 'timeoutexpired':
        return output
    phase_runtime = {}
    print(output)
    lines = output.split("\n")
    for line in lines:
        if "construct:" in line:
            phase_runtime["construct"] = gettime(line)
        elif "prune:" in line:
            phase_runtime["prune"] = gettime(line)
        elif "solve:" in line:
            phase_runtime["solve"] = gettime(line)
        elif "Overall runtime" in line:
            phase_runtime["overall_time"] = get_eq_time(line)
            print("Overall runtime: %d" % get_eq_time(line))


    print(phase_runtime)

    construct = phase_runtime["construct"]
    prune = phase_runtime["prune"]
    solve = phase_runtime["solve"]
    overall = phase_runtime["overall_time"]

    ret = "%.2f/%.2f/%.2f/%.2f" % (construct/1000.0, prune/1000.0, solve/1000.0, overall/1000.0)

    return ret


def dump_output_space(result):
    str_out = None
    for bench in result:
        line = "%-12s" % bench
        exps = result[bench].keys()
        if str_out is None:
            str_out = 'workload ' + ' '.join(exps) + '\n'
        for exp in exps:
            line += " %20s" % result[bench][exp]
        str_out += line + "\n"
    print(str_out)


def main(t_folder):
    global g_timeout
    config_file = "cobra.conf"

    # ww_cons, bundle_cons, infer_relation, pcsg_on
    opts = [
        [False, False, False, False],
        [True, False, False, False],
        [True, True, False, False],
        [True, True, True, True],
    ]

    result = {}
    for bench in os.listdir(t_folder):
        # clear naming, bench = "chengRW-6000"
        # bench_name = (bench.split("-"))[0]
        bench_name = bench

        result[bench_name] = {}
        for opt in opts:
            config = CobraConfig("cobra.conf.default")
            # database doesn't matter
            config.set_all('rocksdb', ww_cons=opt[0], bundle=opt[1], infer=opt[2], pcsg=opt[3])
            config.dump_to(config_file)

            exp = ("F","T")[opt[0]] + ("F","T")[opt[1]] + ("F","T")[opt[2]]
            print(bench + exp)
            #if ("FFF" in exp) and ("chengRW" in bench):
            #    print("skip %s %s", (bench, exp))
            #    continue
            # run!
            # subprocess.call("./run.sh cobra audit cobra.conf %s/%s" % (t_folder,bench), shell=True)
            cmd = ['java',
                    '-ea', '-Djava.library.path=./include/',
                    '-jar', './target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar',
                    'mono', 'audit',
                    config_file, ("%s/%s" % (t_folder, bench))]
            out = run_cmd(cmd, time_out=g_timeout)

            phase_time = get_phase(out)
            result[bench_name][exp] = phase_time
            print(phase_time)
            with open('/tmp/mono.pkl', 'wb') as f:
                pickle.dump(result, f)

    # (3) print/dump
    print(result)
    dump_output_space(result)



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
