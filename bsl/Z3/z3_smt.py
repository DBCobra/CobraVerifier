from z3 import *
import sys
import time

# ====== load file =====

def extract_edge(edge):
    tokens = edge.split(",")
    assert len(tokens) == 2, "ill-format edge: a,b"
    return [int(tokens[0]), int(tokens[1])]


def load_polyg(poly_f):
    with open(poly_f) as f:
        lines = f.readlines()

    n = 0
    edges = []
    constraints = []
    for line in lines:
        if line == "":
            continue

        elems = line.split(':')
        assert len(elems) == 2, "ill-format log"
        symbol = elems[0]
        content = elems[1]

        if symbol == "n":
            assert n==0, "multiple n in file"
            n = int(content)
        elif symbol == "e":
            e = extract_edge(content)
            edges.append(e)
        elif symbol == "c":
            str_groups = content.split("|")
            assert len(str_groups) == 2, "ill-format constraints, not two groups"
            con = []
            for str_group in str_groups:
                group = []
                str_edges = str_group.split(";")
                assert len(str_edges) >= 1, "ill-format constraints, empty constraint"
                for str_edge in str_edges:
                    group.append(extract_edge(str_edge))
                con.append(group)
            constraints.append(con)
        else:
            print("Line = %s" % line)
            assert False, "should never be here"

    return n, edges, constraints

# ====== main solver logic =====

def add_constraint_int(s, c, N, n):
    assert len(c) == 2, "ill-formatd choice"
    left = c[0]
    right = c[1]
    assert len(left) == 1, "should be original constraint"
    assert len(right) == 1, "should be original constraint"

    l_edge = left[0]
    r_edge = right[0]

    s.add(Xor(N[l_edge[0]] < N[l_edge[1]],
              N[r_edge[0]] < N[r_edge[1]]))

def encode_polyg_linear(s, n, edges, constraints):
    # N is nodes; node as an integer
    N = IntVector('n', n)

    # add edges
    for e in edges:
        assert len(e) == 2, "ill-formatd edge"
        # e[0] -> e[1]
        s.add(N[e[0]] < N[e[1]])

    # add constraints
    for c in constraints:
        add_constraint_int(s, c, N, n)

    # acyclicity:
    # all nodes should have distinct values
    s.add([Distinct(N[i]) for i in range(n)])

# === main logic ===

def main(poly_f):
    n, edges, constraints = load_polyg(poly_f)
    print("n=%d"%n)
    #print(edges)
    #print(constraints)

    #set_option("smt.timeout", 120000) # 120s timeout
    s = Solver()

    t1 = time.time()
    encode_polyg_linear(s, n, edges, constraints)
    print("finish construction of clauses")

    t2 = time.time()
    ret = s.check()
    print(ret)
    assert ret != unsat, "must be SAT or UNKNOWN, but failed!"
    t3 = time.time()

    print("clause construction: %.fms" % ((t2-t1)*1000))
    print("solve constraints: %.fms" % ((t3-t2)*1000))
    print("Overall runtime = %dms" % int((t3-t1)*1000))

    if (False):
        m = s.model()
        for d in m.decls():
            print("%s = %s" % (d.name(), m[d]))


def usage_exit():
    print("Usage: z3_smt.py <polyg_file>")
    exit(1)

if __name__ == "__main__":
    if len(sys.argv) != 2:
        usage_exit()
    main(sys.argv[1])

