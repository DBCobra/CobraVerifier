package util;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import util.VeriConstants.LoggerType;
import util.VeriConstants.OnlineDBType;

public class ConfigLoader {
	
	private static boolean toBoolean(String str) {
		if (str.toLowerCase().equals("true") ||
				str.toLowerCase().equals("t")
			 ) {
			return true;
		} else {
			return false;
		}
	}
	
	private static VeriConstants.OnlineDBType toDBType(String str) {
		if (str.equals("2")) {
			return OnlineDBType.ROCKSDB;
		} else if (str.equals("3")) {
			return OnlineDBType.POSTGRESQL;
		} else if (str.equals("1")) {
			return OnlineDBType.GOOGLE;
		} else {
			assert false;
			return null;
		}
	}
	
	private static int toInt(String str) {
		return Integer.parseInt(str);
	}
	
	private static double toDouble(String str) {
		return Double.parseDouble(str);
	}
	
	private static LoggerType toLoggerType(String str) {
		String STR = str.toUpperCase();
		if (STR.equals("ERROR")) {
			return LoggerType.ERROR;
		}
		if (STR.equals("WARNING")) {
			return LoggerType.WARNING;
		}
		if (STR.equals("INFO")) {
			return LoggerType.INFO;
		}
		if (STR.equals("DEBUG")) {
			return LoggerType.DEBUG;
		}
		if (STR.equals("TRACE")) {
			return LoggerType.TRACE;
		}
		assert false;
		return null;
	}
	
	private static void configSet(String key, String val) {
		
		String KEY = key.toUpperCase();
	
		// optimizations
		if (KEY.equals("INFER_RELATION_ON")) {
			VeriConstants.INFER_RELATION_ON = toBoolean(val);
		} else if (KEY.equals("MERGE_CONSTRAINT_ON")) {
			VeriConstants.MERGE_CONSTRAINT_ON = toBoolean(val);
		} else if (KEY.equals("PCSG_ON")) {
			VeriConstants.PCSG_ON = toBoolean(val);
		} else if (KEY.equals("WRITE_SPACE_ON")) {
			VeriConstants.WRITE_SPACE_ON = toBoolean(val);
		} else if (KEY.equals("HEAVY_VALIDATION_CODE_ON")) {
			VeriConstants.HEAVY_VALIDATION_CODE_ON = toBoolean(val);
		} else if (KEY.equals("LOG_FD_LOG")) {
			VeriConstants.LOG_FD_LOG = val;
		} else if (KEY.equals("MULTI_THREADING_OPT")) {
			VeriConstants.MULTI_THREADING_OPT = toBoolean(val);
		} else if (KEY.equals("REACHABILITY_QUICK_START")) {
			VeriConstants.REACHABILITY_QUICK_START = toBoolean(val);
		} else if (KEY.equals("BUNDLE_CONSTRAINTS")) {
			VeriConstants.BUNDLE_CONSTRAINTS = toBoolean(val);
		} else if (KEY.equals("WW_CONSTRAINTS")) {
			VeriConstants.WW_CONSTRAINTS = toBoolean(val);
		}
		// cheng logger
		else if (KEY.equals("LOGGER_ON_SCREEN")) {
			VeriConstants.LOGGER_ON_SCREEN = toBoolean(val);
		} else if (KEY.equals("LOGGER_PATH")) {
			VeriConstants.LOGGER_PATH = val;
		} else if (KEY.equals("LOGGER_LEVEL")) {
			VeriConstants.LOGGER_LEVEL = toLoggerType(val);
		}
		// running config
		else if (KEY.equals("FETCHING_DURATION_BASE")) {
			VeriConstants.FETCHING_DURATION_BASE = toInt(val);
		} else if (KEY.equals("FETCHING_DURATION_RAND")) {
			VeriConstants.FETCHING_DURATION_RAND = toInt(val);
		} else if (KEY.equals("NUM_BATCH_FETCH_TRACE")) {
			VeriConstants.NUM_BATCH_FETCH_TRACE = toInt(val);
		}
		// dump polyg for Z3
		else if (KEY.equals("DUMP_POLYG")) {
			VeriConstants.DUMP_POLYG = toBoolean(val);
		} else if (KEY.equals("MAX_INFER_ROUNDS")) {
			VeriConstants.MAX_INFER_ROUNDS = toInt(val);
		}
		// sser
		else if (KEY.equals("TIME_ORDER_ON")) {
			VeriConstants.TIME_ORDER_ON = toBoolean(val);
		} else if (KEY.equals("TIME_DRIFT_THRESHOLD")) {
			VeriConstants.TIME_DRIFT_THRESHOLD = toInt(val);
		}
		//
		else if (KEY.equals("BENCH_TYPE")) {
			VeriConstants.BENCH_TYPE = toInt(val);
		}
		else if (KEY.equals("ONLINE_DB_TYPE")) {
			VeriConstants.ONLINE_DB_TYPE = toDBType(val);
		} else if (KEY.equals("BATCH_TX_VERI_SIZE")) {
			VeriConstants.BATCH_TX_VERI_SIZE = toInt(val);
		}	else if (KEY.equals("GPU_MATRIX")) {
			VeriConstants.GPU_MATRIX = toBoolean(val);
		} else if (KEY.equals("TOTAL_CLIENTS")) {
			VeriConstants.TOTAL_CLIENTS = toInt(val);
		} else if (KEY.equals("DB_HOST")) {
			VeriConstants.DB_HOST = val;
		} else if (KEY.equals("MIN_PROCESSING_NEW_TXN")) {
			VeriConstants.MIN_PROCESSING_NEW_TXN = toInt(val);
		} else if (KEY.equals("GC_EPOCH_THRESHOLD")) {
			VeriConstants.GC_EPOCH_THRESHOLD = toInt(val);
		}
		// errors
		else
		{
			ChengLogger.println(LoggerType.ERROR, "One config entry [" + key + "]=[" + val + "] doesn't make sense");
			assert false;
		}
	}
	
	public static void loadConfig(String path) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(path));
			String line;
			while ((line = br.readLine()) != null) {
				String[] kv = line.split("=");
				assert kv.length == 2;
				configSet(kv[0].trim(), kv[1].trim());
			}
			
		} catch (FileNotFoundException e) {
			ChengLogger.println(LoggerType.ERROR, "Config file <" + path + "> not found");
			assert false;
		} catch (IOException e) {
			e.printStackTrace();
			ChengLogger.println(LoggerType.ERROR, "Config file parsing problem");
			assert false;
		}
	}

}
