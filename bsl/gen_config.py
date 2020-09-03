#!/bin/python
import subprocess

def parse_val(val):
    lval = val.lower()
    if lval in ("true", "t"):
        return True
    elif lval in ("false", "f"):
        return False
    return val

def dump_val(val):
    if isinstance(val, bool):
        return str(val).lower()
    return val

def read_config(fname):
    with open(fname) as f:
        lines = f.readlines()
    lines = [x.strip() for x in lines]

    conf = {}
    for line in lines:
        elems = line.split('=')
        assert len(elems) == 2, "Paring line: " + line + " problem"
        conf[elems[0]] = parse_val(elems[1])

    return conf

def write_config(config, fname):
    output = ""
    for k in config:
        output += ("%s=%s\n" % (k, dump_val(config[k])))
    print("Config: \n"+output)
    with open(fname, 'w') as f:
        f.write(output)

# TODO: port to yaml
class CobraConfig(object):

    def __init__(self, filename):
        self.confs = read_config(filename)
        self.all_set = False

    def set_default(self):
        self.confs['HEAVY_VALIDATION_CODE_ON'] = False
        self.confs['MULTI_THREADING_OPT'] = True
        self.confs['TIME_ORDER_ON'] = False
        self.confs['MERGE_CONSTRAINT_ON'] = False
        self.confs['WRITE_SPACE_ON'] = False
        return self

    def set_verifier(self, ww_cons, bundle, infer, pcsg=True):
        self.confs['INFER_RELATION_ON'] = infer
        self.confs['PCSG_ON'] = pcsg
        self.confs['BUNDLE_CONSTRAINTS'] = bundle
        self.confs['WW_CONSTRAINTS'] = ww_cons

    def set_online(self, database, base, random_wait, num_txn_in_fetch):
        db = 0
        if database == 'google':
            db = 1
        elif database == 'rocksdb':
            db = 2
        elif database == 'postgres':
            db = 3
        else:
            assert False, "database [%s] is unknown" % database
        self.confs['ONLINE_DB_TYPE'] = db
        self.confs['FETCHING_DURATION_BASE'] = base
        self.confs['FETCHING_DURATION_RAND'] = random_wait
        self.confs['NUM_BATCH_FETCH_TRACE'] = num_txn_in_fetch

    def set_dumpolyg(self, dump):
        self.confs['DUMP_POLYG'] = dump

    def set_scaling(self, batch_size):
        self.confs['BATCH_TX_VERI_SIZE'] = batch_size

    def set_all(self, database,
                ww_cons=True, bundle=True, infer=True, pcsg=True,
                base=500, random_wait=2000, num_txn_in_fetch=100, batch_size=200):
        self.set_default()
        self.set_verifier(ww_cons, bundle, infer, pcsg)
        self.set_online(database, base, random_wait, num_txn_in_fetch)
        self.set_scaling(batch_size)
        self.all_set = True

    def dump_to(self, fname="cobra.conf"):
        assert self.all_set, "you should call set_all() to build config"
        write_config(self.confs, fname)
        print("Dumped config to " + fname)


def main():
    subprocess.call('mkdir -p results', shell=True)

    config = CobraConfig("cobra.conf.default")
    config.set_all('postgres')
    config.dump_to();


if __name__ == "__main__":
    main()
