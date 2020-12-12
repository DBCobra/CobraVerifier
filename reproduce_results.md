# Instructions to reproduce results

This tutorial introduces how to reproduce the results
in Section 6.1 and 6.2 of Cobra paper [[1]](#cobrapaper):

1. [Verification runtime vs. number of transactions (Figure 5)](#bsl)
2. [Detecting serializability violations (Figure 6)](#ser_violation)
3. [Decomposition of cobra's verification runtime (Figure 7)](#oneshot10k)
4. [Differential analysis of Cobra's verification runtime (Figure 8)](#oneshot10k)
5. [Cobra verifier's scaling (Figure 10)](#scaling)


### 0. Fetching histories

    $ git submodule update --init --recursive


### 1. <a name='bsl' /> Compare Cobra with baselines on various workloads (Figure 5)

This experiment runs Cobra verifier and four baselines ([BE19, MiniSAT, Z3, and MonoSAT](#build_bsl)) on the BlindW-RW benchmark of various sizes (200-10,000 transactions).

Follow [build and deploy baselines](#build_bsl) to setup the baselines.

After deploying baselines,
use the following commands to run **all** baselines, which may take many hours to finish depending on the workloads in `./data/`.

    $ cd $COBRA_HOME/CobraVerifier/bsl/
    $ python ./run_bsl.py all ./bin/ ./data/
    
One can test one baseline at a time by:


    $ python ./run_bsl.py [be19|sat-mini|smt-z3|mono|cobra] ./bin/ ./data/
    // choose one from [be19|sat-mini|smt-z3|mono|cobra]

The running time for each workload will print on screen after finishing all workloads. This experiment reproduces Figure 5 in Section 6.1.


### 2. <a name='ser_violation' /> Detecting serializability violations (Figure 6)

This experiment uses Cobra verifier to check (known) serializability violations in productions systems.

    $ cd $COBRA_HOME/CobraVerifier/
    $ python ./bench_violation.py ./CobraLogs/ser-violation/

The results will print on screen after checking all cases.
This experiment reproduces Figure 6 in Section 6.1.

**Note**: for one case "yuga-G2-a", the history size (37.2k transactions) is too large for the default configuration. You will see an error message of either `java.lang.OutOfMemoryError: Java heap space` or `GPU: out of memory`. To check this case, follow these steps:

* allocate more memory for JVM: update file `$COBRA_HOME/CobraVerifier/run.sh` line 10 (`EA="-ea"`) to `EA="-ea -Xmx8192m"` (provide 8GB memory to JVM)

* allocate more GPU memory: update file `$COBRA_HOME/CobraVerifier/include/gpu_GPUmm.cu` line 34 (`#define MAX_N 30000ul`) to `#define MAX_N 38000ul`

* rebuild the verifier: `./run.sh build`

* check the case  "yuga-G2-a": `./run.sh mono audit ./cobra.conf.default ./CobraLogs/ser-violation/yuga-G2-a/`


### 3. & 4. <a name='oneshot10k' /> Cobra verifier performance analysis (Figures 7 & 8)

This experiment runs four variants of Cobra's verifier (MonoSAT, Cobra w/o prunning, Cobra w/o pruning and coalescing, and Cobra) on five benchmarks (TPC-C, C-Twitter, C-RUBiS, BlindW-RW, and BlindW-RM) of size 10,000 transactions. 
This experiment reproduces (1) decomposition of verification runtime (Figure 7) and (2) differential analysis (Figure 8).

Run the experiment:

    $ cd $COBRA_HOME/CobraVerifier/
    $ python bench_mono.py ./CobraLogs/one-shot-10k/
    
This experiment will take about 20min to finish.
It runs four verifier variants on five workloads, which contains 20 runs in total.
The default timeout is 60s,
which can be updated by changing `g_timeout = 60` in the file `bench_mono.py`.

After running all cases, one will see results on screen (for example):

>  workload  &nbsp;   FFF  &nbsp; TFF  &nbsp; TTF &nbsp; TTT  
>  tpcc-10000    &nbsp;     timeoutexpired &nbsp; 1.70/0.00/1.04/2.76 &nbsp; 1.55/0.00/0.75/2.33 &nbsp;  1.65/0.00/0.32/2.01  
>  ...
 
In the above results, `FFF`, `TFF`, `TTF`, and `TTT` represent the different Cobra variants: MonoSAT, Cobra w/o prunning, Cobra w/o pruning and coalescing, and Cobra, respectively.
`timeoutexpired` indicates that the verification terminates before finishing because of timeout; 
the numbered cell (for example, `1.70/0.00/1.04/2.76`) represents runtime (in seconds; separated by `/`) of constructing, pruning, solving, and the whole verification.

This experiment reproduces Figure 7 and 8 in Section 6.1.

### 5. <a name='scaling' /> Scaling (Figure 10)

To reproduce Figure 10 in Section 6.2, run the following commands:

    $ cd $COBRA_HOME/CobraVerifier/
    $ python bench_scaling.py ./CobraLogs/scaling/

This experiment will take about 0.5-1hr to finish.
It runs verification in rounds with seven different batch sizes (that is how many transactions fetched for each round) on two workloads (C-RUBiS and BlindW-RM), which contains 14 runs in total. Each run takes several minutes to finish.

 <a name='build_bsl'/> Building baselines
---

The Cobra paper [[1]](#cobrapaper) experiments four baselines (please see details in the paper, Section 6.1):

* (a) BE19: the serializability checking algorithm in [Biswas and Enea](https://arxiv.org/abs/1908.04509), which is implemented in Rust
* (b) MiniSAT: encoding serializability checking into SAT formulas and feeding this encoding to [MiniSAT](http://minisat.se/)
* (c) Z3: using a linear arithmetic SMT encoding and feeding this encoding to [Z3](https://github.com/Z3Prover/z3) 
* (d) MonoSAT: using the original polygraph encoding and feeding this encoding to [MonoSAT](http://www.cs.ubc.ca/labs/isd/Projects/monosat/)

In the following, we first build the baseline (a)-(c), and then deploy them for future usage.
Baseline (d) is implemented in Cobra verifier.

### Step 1: build BE19

#### (1) install Rust and Cargo

Install Rust following [official website](https://www.rust-lang.org/tools/install):

    $ sudo apt install libclang-dev
    $ curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

Note that Rust tools are installed to the `~/.cargo/bin` directory.
To run the following commands, one can either include the directory to their `PATH` environment variable or complete commands with the full path.


#### (2) compile BE19

With Rust installed, now we compile the baseline (BE19's source code is [here](https://gitlab.math.univ-paris-diderot.fr/ranadeep/dbcop); we included it in our repo):

    $ cd $COBRA_HOME/CobraVerifier/bsl/BE19/
    $ unzip dbcop-master.zip
    $ cd dbcop-master/
    $ cargo build

If the baseline is successfully compiled, you can test the binary by:

    $ ./target/debug/dbcop
    # should return "USAGE: dbcop <SUBCOMMAND>"

#### (3) compile BE19-translator

BE19-translator is a piece of software that converts Cobra's history to BE19's. To compile the translator, run:

    $ cd $COBRA_HOME/CobraVerifier/bsl/BE19/BE19_translator/
    $ cargo build

If the translator is successfully compiled, you can test the binary by:

    $ ./target/debug/translator
    # should return "Usage: translator <Cobra-log-folder> <BE19-log-folder>"

### Step 2: install MiniSAT

    $ sudo apt install minisat
    
    
### Step 3: install Z3

    $ sudo apt install z3
    $ pip install z3-solver

### Step 4: deploy baselines

Finally, we deploy these baselines, which includes (1) moving the binaries to a pre-defined location (`$COBRA_HOME/CobraVerifier/bsl/bin/`) and (2) converting Cobra's histories into the formats that these baselines can consume (under folder `$COBRA_HOME/CobraVerifier/bsl/data/`):

    $ cd $COBRA_HOME/CobraVerifier/bsl/
    $ ./deploy_gen.sh ../CobraLogs/bsl/

Note that the deployment may take tens of minutes (or longer) because converting histories to the DIMACS format (a CNF format for MiniSAT) is slow; also, the DIMACS file may consume substantial disk space (several to tens of GB) for workloads larger than 500 transactions. 


Troubleshooting
------- 
#### thread 'main' panicked at 'Unable to find libclang: "couldn\'t find any valid shared libraries matching: ...

Run `$ sudo apt install libclang-dev`

<a name="cobrapaper" /> Reference
---

[1] Cheng Tan, Changgeng Zhao, Shuai Mu, and Michael Walfish. Cobra: Making Transactional Key-Value Stores Verifiably Serializable. OSDI 2020.
