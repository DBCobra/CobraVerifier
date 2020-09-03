package util;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

import util.VeriConstants.LoggerType;

public class ChengLogger {
	
	private static String type2str(LoggerType t) {
		switch(t) {
		case ERROR:   return "[ERROR] ";
		case WARNING: return "[WARN ] ";
		case DEBUG:   return "[DEBUG] ";
		case INFO:    return "[INFO ] ";
		case TRACE:   return "[TRACE] ";

		default: return "[UNKNOWN] ";
		}
	}
	
	private static PrintWriter log = null;
	
	private static synchronized void logprint(String str) {
		try {
			// check the stream
			if (log == null) {
				log = new PrintWriter(VeriConstants.LOGGER_PATH);
			}

			log.print(str);
			log.flush();
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			assert false;
		}
	}
	
	public static void print(String msg) {
		print(LoggerType.INFO, msg);
	}
	
	public static void println() {
		println(LoggerType.INFO, "");
	}
	
	public static void println(String msg) {
		println(LoggerType.INFO, msg);
	}
	
	public static void print(LoggerType t, String msg) {
		if (t.compareTo(VeriConstants.LOGGER_LEVEL) > 0) return;
		
		if (VeriConstants.LOGGER_ON_SCREEN) {
			System.out.print(type2str(t) + msg);
		} else {
			logprint(type2str(t) + msg);
		}
	}
	
	public static void println(LoggerType t, String msg) {
		if (t.compareTo(VeriConstants.LOGGER_LEVEL) > 0) return;
		
		if (VeriConstants.LOGGER_ON_SCREEN) {
			System.out.println(type2str(t) + msg);
		} else {
			logprint(type2str(t) + msg + "\n");
		}
	}

}
