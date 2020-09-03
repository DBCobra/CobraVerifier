from z3 import *
import sys
import time

# ====== main solver logic =====
def add_constraint(s, c, X, n):
    assert len(c) == 2, "ill-formatd choice"
    left = c[0]
    right = c[1]

    # left: choose all or choose nothing
    l_all = True
    l_nothing = True
    for li in range(0,len(left)):
        e = left[li]
        assert len(e) == 2, "ill-format choice left edge"
        l_all = And(l_all, X[e[0]*n+e[1]])
        l_nothing = And(l_nothing, Not(X[e[0]*n+e[1]]))

    # right: choose all or choose nothing
    r_all = True
    r_nothing = True
    for li in range(0,len(right)):
        e = right[li]
        assert len(e) == 2, "ill-format choice right edge"
        r_all = And(r_all, X[e[0]*n+e[1]])
        r_nothing = And(r_nothing, Not(X[e[0]*n+e[1]]))

    # either choose left or right; cannot be both
    s.add(Or(l_all, l_nothing))  # atomicity of left, either all in or all out
    s.add(Or(r_all, r_nothing))  # atomicity of right
    # either (choose left, not right) or (choose right, not left)
    s.add(Or(
        And(l_all, r_nothing),
        And(r_all, l_nothing)
    ))

def add_constraint2(s, c, X, n):
    assert len(c) == 2, "ill-formatd choice"
    left = c[0]
    right = c[1]
    assert len(left) == 1, "should be original constraint"
    assert len(right) == 1, "should be original constraint"

    l_edge = left[0]
    r_edge = right[0]

    l_var = X[l_edge[0]*n + l_edge[1]]
    r_var = X[r_edge[0]*n + r_edge[1]]

    s.add(Xor(l_var, r_var))


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


def add_be19_constraints(s, c, n, E, CO):
    assert len(c) == 2, "ill-format constraint"
    left = c[0]
    right = c[1]

    # FIXME: here we only accept the original constraint
    assert len(left) == 1 and len(right) == 1
    e1 = left[0]
    e2 = right[0]
    assert len(e1) == 2 and len(e2) == 2, "ill-format edge"

    # find the write txn (t2)
    t1 = -1; t2 = -1; t3 = -1
    if (e1[0] == e2[1]):
        t2 = e1[0]
        t1 = e1[1]
        t3 = e2[0]
    elif (e1[1] == e2[0]):
        t2 = e1[1]
        t1 = e2[1]
        t3 = e1[0]
    else:
        assert False, "wrong constraint"

    # constraint
    s.add(Implies(
        And(E[t1*n + t3], CO[t2*n + t3]),
        CO[t2*n + t1]
    ))

# edges = [[i,j], [j,k], ...]
# choice = [ ([[i,j],[j,k],...], [...]), ...   ]
def encode_polyg_tc(s, n, edges, constraints):
    X = BoolVector('x', n*n)
    Y = BoolVector('y', n*n)

    # add edges
    for e in edges:
        assert len(e) == 2, "ill-formatd edge"
        s.add(X[e[0]*n + e[1]] == True)

    # add constraints
    for c in constraints:
        add_constraint(s, c, X, n)

    # cycle-1
    for i in range(n):
        s.add(Y[i*n + i] == False)
    # cycle-2
    for i in range(n):
        for j in range(n):
            for k in range(n):
                s.add( Implies(And(Y[i*n+j], Y[j*n+k]), Y[i*n+k]) )
    # cycle-3
    for i in range(n):
        for j in range(n):
            s.add(Implies(X[i*n+j], Y[i*n+j]))


# FIXME: what's the difference between unr and bin???
def encode_polyg_bin(s, n, edges, constraints):
    X = BoolVector('x', n*n)
    Y = IntVector('y', n)

    # add edges
    for e in edges:
        assert len(e) == 2, "ill-formatd edge"
        s.add(X[e[0]*n + e[1]] == True)

    # add constraints
    for c in constraints:
        add_constraint2(s, c, X, n)

    # cycle
    for i in range(n):
        for j in range(n):
            s.add(Implies(X[i*n+j], Y[i] < Y[j]))


def encode_polyg_unr(s, n, edges, constraints):
    # X is edges; Y is nodes
    # edge as a bool
    X = BoolVector('x', n*n)
    # node as an integer
    Y = IntVector('y', n)

    # add edges
    for e in edges:
        assert len(e) == 2, "ill-formatd edge"
        s.add(X[e[0]*n + e[1]] == True)

    # add constraints
    for c in constraints:
        add_constraint2(s, c, X, n)

    # acyclicity
    # rule1: edge(i,j) => Node(i) < Node(j)
    for i in range(n):
        for j in range(n):
            s.add(Implies(X[i*n + j], Y[i] < Y[j]))

    # rule2: all nodes should have distinct values
    #s.add([Distinct(X[i]) for i in range(n)]) # slow...
    for i in range(n-1):
        for j in range(i+1, n):
            s.add(Y[i] != Y[j])


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


def encode_polyg_be19(s, n, edges, constraints):
    E = BoolVector('e', n*n)
    CO = BoolVector('co', n*n)

    # (1) add known edges (SO and WR)
    for e in edges:
        assert len(e) == 2, "ill-formatd edge"
        s.add(E[e[0]*n + e[1]] == True)

    # (2) edge implies CO
    # (3) CO is totoal order
    for i in range(n):
        for j in range(n):
            s.add(Implies(E[i*n + j], CO[i*n + j])) # (2)
            # (3)
            if i != j:
                s.add(And(
                    Or(CO[i*n + j], CO[j*n + i]),
                    Or(Not(CO[i*n + j]), Not(CO[j*n + i]))
                ))
                # CO is transitive; copied logic from sat.rs in dbcop
                for k in range(n):
                    if k != i and k != j:
                        s.add(Implies(
                            And(CO[i*n + j], CO[j*n + k]),
                            CO[i*n + k]
                        ))
                        #s.add(And(
                        #    Not(CO[i*n + j]),
                        #    Not(CO[j*n + k]),
                        #    CO[i*n + k]
                        #))

    # (4) SER constraint
    for c in constraints:
        add_be19_constraints(s, c, n, E, CO)




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


# === main logic ===

def main(encoding, poly_f, output_f):
    n, edges, constraints = load_polyg(poly_f)
    print("n=%d"%n)
    #print(edges)
    #print(constraints)

    #set_option("smt.timeout", 120000) # 120s timeout
    s = Solver()

    t1 = time.time()
    if "tc" == encoding:
        encode_polyg_tc(s, n, edges, constraints)
    elif "bin" == encoding:
        encode_polyg_bin(s, n, edges, constraints)
    elif "unr" == encoding:
        encode_polyg_unr(s, n, edges, constraints)
    elif "be19" == encoding:
        encode_polyg_be19(s, n, edges, constraints)
    elif "linear" == encoding:
        encode_polyg_linear(s, n, edges, constraints)
    else:
        print("ERROR: unknown encoding [%s]. Stop." % encoding)
        assert False
    print("finish construction of clauses")


    smt = s.to_smt2()
    with open(output_f, "w") as f:
        f.write(smt)

    t2 = time.time()

    print("clause construction: %.fms" % ((t2-t1)*1000))



def usage_exit():
    print("Usage: build_smt.py [tc|bin|unr|be19|linear] <polyg_file> <smt_file>")
    exit(1)

if __name__ == "__main__":
    if len(sys.argv) != 4:
        usage_exit()
    main(sys.argv[1], sys.argv[2], sys.argv[3])

