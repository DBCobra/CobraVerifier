import sys
import os
import signal
from time import sleep, time
import subprocess
import pickle
from gen_config import CobraConfig

def usage_exit():
    print("usage: ./bench_violation.py <trace_folder>")
    exit(1)

def run_cmd(cmd):
    p = subprocess.Popen(" ".join(cmd), shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    # Poll process for new output until finished
    while True:
        nextline = p.stdout.readline().decode("ascii")
        if nextline == '' and p.poll() is not None:
            break
        sys.stdout.write(nextline)
        sys.stdout.flush()
    output = p.communicate()[0]



def get_size(output):
    # [INFO ] [1] global graph: #n=37191
    lines = output.split("\n")
    for line in lines:
        if "global graph:" in line:
            return line.split("#n=")[-1]

def dump_output_space(result):
    str_out = None
    for bench in result:
        size, runtime = result[bench]
        line = "%-20s %-20s" % (bench, runtime)
        if str_out is None:
            str_out = 'workload runtime\n'
        str_out += line + "\n"
    print(str_out)


def main(t_folder):
    config_file = "cobra.conf"

    result = {}
    for bench in os.listdir(t_folder):
        result[bench] = {}
        config = CobraConfig("cobra.conf.default")
        # database doesn't matter
        config.set_all('rocksdb')
        config.dump_to(config_file)

        print("--------------%s--------------" % bench)
        cmd = ['java',
                '-ea', '-Djava.library.path=./include/',
                '-jar ./target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar',
                'mono', 'audit',
                config_file, ("%s/%s" % (t_folder, bench))]

        print(cmd)
        start = time()
        run_cmd(cmd)
        runtime = time() - start

        result[bench] = [0, runtime]

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
