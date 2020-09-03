import pickle
import sys

def dump_output_space(result):
    str_out = None
    for bench in result:
        line = "%-8s" % bench
        exps = result[bench].keys()
        if str_out is None:
            str_out = 'workload ' + ' '.join(exps) + '\n'
        for exp in exps:
            line += "%20s" % result[bench][exp]
        str_out += line + "\n"
    print(str_out)


if __name__ == '__main__':
    if len(sys.argv) != 2:
        print("usage: {} <result.pkl>".format(sys.argv[0]))
        exit(1)
    fname = sys.argv[1]
    f = open(fname, 'r')
    result = pickle.load(f)
    dump_output_space(result)
