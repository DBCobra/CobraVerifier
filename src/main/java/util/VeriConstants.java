package util;

import net.openhft.hashing.LongHashFunction;
import verifier.AbstractVerifier;

public class VeriConstants {
	public static boolean HEAVY_VALIDATION_CODE_ON = true;
	
	public static String CONFIG_FILE_NAME = "cobra.conf";

	// algorithm features
	public static boolean WW_CONSTRAINTS = true; // use ww instead of rw constraints i.e., Write combine
	public static boolean BUNDLE_CONSTRAINTS = true; // bundle constraints i.e., Coalescing
	public static boolean INFER_RELATION_ON = true; // solve constraints i.e., Pruning
	public static boolean MERGE_CONSTRAINT_ON = false; // merge constraints
	public static boolean WRITE_SPACE_ON = true; // correlated constraints
	public static boolean PCSG_ON = true;    // PCG
	//
	public final static boolean RMW_EXTRACTION = true;
	//
	public static boolean FROZEN_ZONE_DETECTION = true;
	// optimization
	public static boolean GPU_MATRIX = true;
	public static boolean MULTI_THREADING_OPT = true;
	public final static boolean CACHE_SEARCHING_RESULTS = true;
	public static boolean REACHABILITY_QUICK_START = true;
	
	// Strict serializability
	public static boolean TIME_ORDER_ON = false;
	public static int TIME_DRIFT_THRESHOLD = 100; //ms
	
	// continues verifier
	public final static int VERI_BATCH_SIZE = 3000;
	
	//public final static long INIT_TXN_ID = 0;
	
	// This is the number of rounds for the anti-ww inference
	public static int MAX_INFER_ROUNDS = 10;
	public final static int RMATRIX_CONNECT_BATCH = 100;
	public final static int MAX_MATRIX_WIDTH = 20000;
	
	// some cap
	public final static int COUNT_MAX_TRIALS = 131072; // =2^17
	public final static int SEARCH_TIMEOUT = 60; //sec
	
	// This is for experiment only: keep how much proportion of ww-dependencies.
	public final static boolean EXP_DROP_WW = false;
	public static int EXP_KEEP_WW_RATE = 100 /*0*/;
	
	public final static String CLOUD_LOG_KIND = "cheng_log";
	public final static String TS_UPDATE_TAG = "updated_timestamp";
	
	// profiler
	public final static String PROF_OFFLINE_LOG_LOADING_TIME = "prof_off_log_loading";
	public static final String PROF_FILE_LOADING = "prof_file_loading";
	public static final String PROF_LOG_PARSING = "prof_log_parsing";
	public final static String PROF_GPU_MM_TIME = "prof_gpu_mm";
	public final static String PROF_POLY_GRAPH = "prof_poly_graph";
	public final static String PROF_POLY_GRAPH1 = "prof_poly_graph1";
	public final static String PROF_POLY_GRAPH2 = "prof_poly_graph2";
	public final static String PROF_POLY_GRAPH3 = "prof_poly_graph3";
	public final static String PROF_SOLVE_CONSTRAINTS = "prof_solve_constraints";
	public final static String PROF_SOLVE_CONSTRAINTS1 = "prof_solve_constraints1";
	public final static String PROF_SOLVE_CONSTRAINTS2 = "prof_solve_constraints2";
	public final static String PROF_SOLVE_CONSTRAINTS3 = "prof_solve_constraints3";
	public final static String PROF_MERGE_CONSTRAINTS = "prof_merge_constraints";
	public final static String PROF_PCSG_TIME = "prof_pcsg_time";
	public final static String PROF_PCSG_TIME_1 = "prof_pcsg_time1"; // speculative edges
	public final static String PROF_PCSG_TIME_2 = "prof_pcsg_time2"; // scc
	public final static String PROF_PCSG_TIME_3 = "prof_pcsg_time3"; // subgraph
	public final static String PROF_SEARCH = "prof_search";
	public final static String PROF_TRUNCATION = "prof_truncation";
	public final static String PROF_TRUNCATION_1 = "prof_truncation1";
	public final static String PROF_TRUNCATION_2 = "prof_truncation2";
	public final static String PROF_TRUNCATION_3 = "prof_truncation3";
	public final static String PROF_MONOSAT_1 = "prof_monosat_1";
	public final static String PROF_MONOSAT_2 = "prof_monosat_2";
	public static final String PROF_TRANSITIVE_REDUCTION = "prof_transitive_reduction";
	
	
	// synced with the cloud-lib
	// Delete operation (for read operations)
	public static long DELETE_WRITE_ID = 0xabcdefabL;
	public static long DELETE_TXN_ID = 0xabcdefabL;
	// key-value exist, but value not encoded (because of the initialization)
	// (for read operations)
	public static long INIT_WRITE_ID = 0xbebeebeeL;
	public static long INIT_TXN_ID = 0xbebeebeeL;
	// read a null value (for read/write operations)
	public static long NULL_WRITE_ID = 0xdeadbeefL;
	public static long NULL_TXN_ID = 0xdeadbeefL;
	// Only used in WW tracking, when we do blind write.
	public static long MISSING_WRITE_ID = 0xadeafbeeL;
	public static long MISSING_TXN_ID = 0xadeafbeeL;
	
	// Temp key for operations appear in WW-log, but not in client-log
	public static final long MISSING_KEY_HASH = 0xdeaddeadL;

	// for log hash
	public static final String CLOG_BEGIN_HASH = "0";
	public static final String WWLOG_BEGIN_HASH = "1";
	
	// fetching frequency
	public static final long SLEEP_TIME_PER_ROUND = 1000; // in ms
	public static final int QUERY_LAY_BACK_TIME = 1;   // in sec
	
	// log related
	public final static String LOG_FD = "/tmp/cobra/";
	public static String LOG_FD_LOG = "/tmp/cobra/log/";
	public final static String LOG_PATH = "/tmp/cobra/benchmark.log";
	public final static boolean APPEND_OTHERWISE_RECREATE = false;
	
	// cheng logger
	public enum LoggerType {ERROR, WARNING, INFO, DEBUG, TRACE};
	public static LoggerType LOGGER_LEVEL = LoggerType.INFO;
	public static boolean LOGGER_ON_SCREEN = true;
	public static String LOGGER_PATH = LOG_FD_LOG + "logger.log";
	
	// trace truncation
	public static String EPOCH_KEY = "FZVERSION";
	public static long VERSION_KEY_HASH = AbstractVerifier.hashKey(EPOCH_KEY);
	public static int FZ_INIT_VERSION = 1;
	
	// TxnNode null
	public static final long TXN_NULL_TS = -1;
	public static final int TXN_NULL_CLIENT_ID = -1;
	public static final int TXN_NULL_VERSION = -1;
	
	// random seed
	public final static long SEED = 19930610;
	
	// online verifier
	public static final int BUFFER_SIZE_M = 5;
	public static final int PIPE_SAFE_M = 1;
	public enum OnlineDBType {GOOGLE, ROCKSDB, POSTGRESQL};
	public static OnlineDBType ONLINE_DB_TYPE = OnlineDBType.ROCKSDB;
	public static final String LOG_PREFIX = "cid[1]_T";
	public static final String LOG_SUFFIX = "_CL";
	public static int BATCH_TX_VERI_SIZE = 300;
	public static int NUM_BATCH_FETCH_TRACE = 1000;
	public static int FETCHING_DURATION_BASE = 500;
	public static int FETCHING_DURATION_RAND = 500;
	public static String DB_HOST= "ye-cheng.duckdns.org";
	public static String PG_USERNAME="cobra";
	public static String PG_PASSWORD="Cobra<318";
	
	public static String DB_URL() {
		return "jdbc:postgresql://" + DB_HOST + ":5432/testdb";
	}
	
	// Z3
	public static boolean DUMP_POLYG = false;
	
	// multi-threading
	public static final int THREAD_POOL_SIZE = 24;
	
	// workers
	public static boolean PARSING_SIG_ON = false;
	public static boolean PARSING_HASH_ON = true;
	
	// workload related
	public enum BenchType {
		CHENG, TPCC, YCSB, RUBIS, TWITTER,
	}
	public static int BENCH_TYPE = 1;
	public static final int NUM_CLIENT_MACHINES = 2;
	public static int TOTAL_CLIENTS = 24;
	
	// socket
	public static boolean REMOTE_LOG = false; // will automatically be set by cmd args
	public static int VERIFIER_PORT = 10086;
	public static int MAX_BYTES_IN_TXN = 1000; // stop reading from socket if less bytes available in stream
	public static int MIN_PROCESSING_NEW_TXN = 5000; // continue reading if we haven't reading these many txns
	
	// garbage collection (GC)
	public static String GC_KEY = "COBRA_GC_KEY";
	public static int GC_CLIENT_ID = 0xabcdef;
	public static long GC_KEY_HASH = AbstractVerifier.hashKey(GC_KEY);
	public static int GC_EPOCH_THRESHOLD = 100;
	public static long GC_WID_TRUE = 0x23332333L;
	public static long GC_WID_FALSE = 0x66666666L;
}
