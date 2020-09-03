package test;

import util.ChengLogger;
import util.VeriConstants;
import util.VeriConstants.LoggerType;
import verifier.AbstractLogVerifier;

public class Performance {

	public static void offline_performance(AbstractLogVerifier verifier) {
		ChengLogger.println("=== START ===");
		boolean ret = verifier.audit();
		if (ret) {
			ChengLogger.println("[[[[ ACCEPT ]]]]");
		} else {
			ChengLogger.println(LoggerType.ERROR, "[[[[ REJECT ]]]]");
		}
		//
		ChengLogger.println(verifier.profResults());
		//
		ChengLogger.println(verifier.statisticsResults());
		ChengLogger.println("=== END ===");
	}
	
	public static void offline_continuous(AbstractLogVerifier verifier) {
		ChengLogger.println("=== START ===");
		ChengLogger.println("ALPHA=" + VeriConstants.EXP_KEEP_WW_RATE);
		boolean ret = verifier.continueslyAudit();
		if (ret) {
			ChengLogger.println("[[[[ ACCEPT ]]]]");
		} else {
			ChengLogger.println(LoggerType.ERROR, "[[[[ REJECT ]]]]");
		}
		ChengLogger.println("=== END ===");
	}
	
}
