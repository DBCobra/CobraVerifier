# Instructions to reproduce results

There are five topics about experimenting Cobra's verifier in Section 6.1 and 6.2 in Cobra paper (to appear):

1. [Verification runtime vs. number of transactions](#bsl)
2. [Detecting serializability violations](#ser_violation)
3. [Decomposition of cobra's verification runtime](#oneshot10k)
4. [Differential analysis of Cobra's verification runtime](#oneshot10k)
5. [Scaling of Cobra's verification](#scaling)

In the following, we will introduce how to reproduce the results.

### 0. Fetching histories

    $ git submodule update --init --recursive


### 1. <a name='bsl' /> Comparing with baselines on various workload sizes

This experiment runs four baselines ([BE19](https://gitlab.math.univ-paris-diderot.fr/ranadeep/dbcop), [MiniSAT](http://minisat.se/), [Z3](https://github.com/Z3Prover/z3), and [MonoSAT](http://www.cs.ubc.ca/labs/isd/Projects/monosat/)) and Cobra's verifier on workloads with various sizes.

See [build and deploy baselines](#build_bsl) to setup the tested baselines.

After deploying baselines,
use the following cmds to run **all** baselines, which may take **many hours** to finish depending on the workload sizes.

    $ cd $COBRA_HOME/CobraVerifier/bsl/
    $ python ./run_bsl.py all ./bin/ ./data/
    
One can test one baseline at a time using:


    $ python ./run_bsl.py [be19|sat-mini|smt-z3|mono|cobra] ./bin/ ./data/
    // choose one from [be19|sat-mini|smt-z3|mono|cobra]


### 2. <a name='ser_violation' /> Detecting serializability violations

This experiment uses Cobra's verifier to check serializability violations in productions systems.

    $ cd $COBRA_HOME/CobraVerifier/
    $ python ./bench_violation.py ./CobraLogs/ser-violation/


Note: for the case "yuga-G2-a", the history size (37.2k transactions) is too large for the default configuration. You will see a error message of either `java.lang.OutOfMemoryError: Java heap space` or `GPU: out of memory`. To check this case, one need to:

* allocate more memory for JVM: modify the line `EA="-ea"` in the file `$COBRA_HOME/CobraVerifier/run.sh` to `EA="-ea" -Xmx8192m` (provide 8GB memory to JVM)

* allocate more memory for the GPU: change the parameter `#define MAX_N 16384ul` (default) to `38000ul` in file `$COBRA_HOME/CobraVerifier/include/gpu_GPUmm.cu`, and rebuild the verifier (`./run.sh build`).

* check the case  "yuga-G2-a": `./run.sh mono aduit ./cobra.conf.default ./CobraLogs/ser-violation/yuga-G2-a/`


### 3 & 4. <a name='oneshot10k' /> Cobra's verifier performance analysis

This experiment runs four variants of Cobra's verifier (MonoSAT, Cobra w/o prunning, Cobra w/o pruning and coalescing, and Cobra) on workloads of 10,000 transactions with our five benchmarks (TPC-C, C-Twitter, C-RUBiS, BlindW-RW, and BlindW-RM). 
By analyzing the results, we have results for (1) decomposition of cobra's verification runtime and (2) differential analysis.

    $ cd $COBRA_HOME/CobraVerifier/
    $ python bench_mono.py ./CobraLogs/one-shot-10k/
    
This experiment will take about 20min to finish (with the default settings).
This experiment runs four verifier variants on five workloads, which contains 20 runs in total. The default timeout is 60s,
which can be updated by changing `g_timeout = 60` in the file `bench_mono.py`.

One will see results (for example):

>  workload FFF TFF TTF TTT
> 
>  tpcc-10000         timeoutexpired  1.70/0.00/1.04/2.76 1.55/0.00/0.75/2.33  1.65/0.00/0.32/2.01
 
In the above results, `FFF`, `TFF`, `TTF`, and `TTT` represents the variants of MonoSAT, Cobra w/o prunning, Cobra w/o pruning and coalescing, and Cobra, respectively.
`timeoutexpired` indicates the verification is cut because of timeout; 
The numbered cell (for example, `1.70/0.00/1.04/2.76`) represents runtime (in seconds; separated by `/`) of constructing, pruning, solving, and the whole verification.

### 5. <a name='scaling' /> Scaling

To reproduce the results of scaling, run the following cmds:

    $ cd $COBRA_HOME/CobraVerifier/
    $ python bench_scaling.py ./CobraLogs/scaling/

This experiment will take about 0.5-1hr to finish.
It runs verification in rounds with seven different batch sizes (number of transactions for each round) on two workloads, which is 14 runs in total. Each run takes about several minutes to finish.

Building baselines <a name='build_bsl'/>
---

There are four baselines:

* (a) BE19: the algorithm of [Biswas and Enea](https://arxiv.org/abs/1908.04509) to check serializability, which is in Rust
* (b) MiniSAT: encoding serializability verification into SAT formulas and feeding this encoding to [MiniSAT](http://minisat.se/)
* (c) Z3: a linear arithmetic SMT encoding and feeding this encoding to [Z3](https://github.com/Z3Prover/z3) 
* (d) [MonoSAT](http://www.cs.ubc.ca/labs/isd/Projects/monosat/)

In the following, we first build and install the baseline (a)-(c), and then deploy them for further usage.

### Step 1: build BE19

#### (1) install Rust and Cargo

Install Rust following [official website](https://www.rust-lang.org/tools/install):

    $ sudo apt-get install libclang-dev
    $ curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh


#### (2) compile BE19

With Rust installed, now we compile the baseline:

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

    $ sudo apt-get install minisat
    
    
### Step 3: install Z3

    $ sudo apt-get install z3
    $ pip install z3-solver

### Step 4: deploy baselines

Finally, we deploy these baselines and convert histories into the formats that these baselines can consume:

    $ cd $COBRA_HOME/CobraVerifier/bsl/
    $ ./deploy_gen.sh ../CobraLogs/bsl/

Note that the deployment may take a while (tens of minutes) because converting histories to some format (DIMACS, a CNF format) is slow; also, the DIMACS file may consume substantial disk space (several to tens of GB) for workloads larger than 500 transactions. 


Troubleshooting
------- 
#### thread 'main' panicked at 'Unable to find libclang: "couldn\'t find any valid shared libraries matching: ....

Run `$ sudo apt-get install libclang-dev`
