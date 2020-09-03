# On the Complexity of Checking Transactional Consistency
##### Ranadeep Biswas, IRIF, University of Paris & CNRS
##### Constantin Enea, IRIF, University Paris Diderot & CNRS

---

# Artifact guide

### Checksum of the artifact

```
$ md5sum paper_214.tar.bz 
e43b47006d2b8a64bac8c51370682aa8  paper_214.tar.bz
```

## Setting up the Docker image

### Instructions to run the docker image

The required [Docker CE](https://docs.docker.com/install) version is 18.09.

```
bzcat -k paper_214.tar.bz | docker load
mkdir -p plots
docker run --mount type=bind,source=`pwd`/plots,target=/root/dbcop/oopsla19/plots -it oopsla19
```

Plots will be available in `plots` directory after generating them in the Docker environment.

## Getting started

We implemented our work in a tool named [dbcop](https://gitlab.math.univ-paris-diderot.fr/ranadeep/dbcop) using [Rust-lang](https://www.rust-lang.org). It provides three things,

1.  A random generator for client programs to run on a database.
2.  A `trait`(equivalent to java interface) to run client programs on a database and log its executions. A user can use this trait to write an implementation specific to a database.
3.  A verifier that checks conformance of a given execution to a consistency model.

`dbcop` offers two subcommands: `generate` and `verify`.

1.  `generate` generates client programs to run on a database.
2.  `verify` verifies consistency of the executions of these client programs.

#### Description

`dbcop generate -d generated_clients` generates `bincode` files inside `generated_clients` directory. The `i`-th generated client program will be at path `"generated_clients/hist-{:05}.bincode".format(i)`. Each bincode file contains a randomly generated client with default parameters. These parameters can be changed by argument passing. Help is available at `dbcop generate --help`. Each randomly generated client will have `nnode` many sessions, `ntxn` many transactions per session, `nevt` many operations per transaction, `nvar` many maximum variables. Each operation is chosen randomly between `read` and `write`. The variable for that operation is chosen uniformly from the variable set. If it was a `write` operation, a unique value is chosen from a counter for each variable and increasing it every time a new value is used.

Once these client programs are generated, a client application can be implemented using our `trait` to execute the programs on a database and log the history. There are example implementations in `/root/dbcop/examples` for `galera`, `cockroachdb` and `antidotedb` as reference. These implementations define what code a client application should execute to connect to a database, to read or write a value or to clean up a database etc (usually, using a client library for the database). Each of these example binaries takes a directory to read the generated client programs, another directory to store the logged execution from the database and the IP addresses to the nodes of the database cluster.

For example, `cargo run --example galera -- -d generated_clients -o executed_histories 192.10.0.2  192.10.0.3` which read the generated client programs at `generated_clients/hist-{:05}.bincode`, connect to the database cluster at those IP addresses and execute each session concurrently on the database cluster mimicking a concurrent execution of the cluster and log the execution in `executed_histories/hist-{:05}`.

`dbcop verify -d executed_histories/hist-00001 -o verifier_log -c <consistency>` verifies a logged database execution stored at `executed_histories/hist-00001` for a `<consistency>` model and logs the details in `verifier_log` directory. It outputs on the terminal if the history is verified for that consistency model.

Our artifact includes the executions we have used to construct the plots included in our paper (Figure 14, 15 and Table 2). The collected executions are available in the folder  `/root/dbcop/oopsla19/executions`.

To get started, you can use the following to check causal consistency of an execution in AntidoteDB (this uses the algorithms we propose in our paper):
```
dbcop verify -d executions/antidote_all_writes/3_30_20_180/hist-00000 -o antidote_verifier_log -c cc
```
`-c` takes a consistency levels to check

1.  `cc` for Causal consistency
2.  `si` for Snapshot Isolation
3.  `ser` for Serialization

To verify consistency using a SAT solver (minisat), pass the `--sat` argument.
```
dbcop verify -d executions/antidote_all_writes/3_30_20_180/hist-00000 -o antidote_verifier_log -c cc --sat
```

Help instructions  `--help` are available for these commands.

## Instructions to reproduce plots from the paper

1.  We have generated executions for 3 databases.

    -   [CockroachDB](https://www.cockroachlabs.com/)
    -   [Galera cluster](https://galeracluster.com)
    -   [AntidoteDB](https://www.antidotedb.eu/)

2.  The execution generation process is parametrized by the number of sessions, transactions per session, operations per transaction, and maximal number of variables. We stored the executions in `'{}_{}_{}_{}'.format(n_sessions, n_transaction, n_operations, n_variables)` sub-directories for each combination of parameter values we report on.
    The history `executions/antidote_all_writes/3_30_20_180/hist-00000` verified above is an AntidoteDB execution with 3 sessions, 30 transactions per session, 20 operations per transaction, and 180 variables.

### Step-by-step instructions

1.  `bash run.sh verify` generates the verifier logs.
2.  `bash run.sh plot` generates the plots and data.

* `plots/roachdb_general_*.png` show the scalability of our Serializability verifying implementation in Figure 14 in the paper.
* `plots/{galera,roachdb}_sessions.png` show the scalability of our Snapshot Isolation verifying implementation in Figure 15a, 15b in the paper
* `plots/antidote_sessions.png` show the performance of our Causal Consistency verifying implementation in Figure 15c in the paper.
