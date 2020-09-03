package kvClient;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.google.common.hash.Hashing;

import graph.OpNode;
import graph.PrecedenceGraph;
import graph.TxnNode;
import util.ChengLogger;
import util.VeriConstants;
import util.VeriConstants.LoggerType;
import verifier.AbstractVerifier;

public class GarbageCollector {
	
	private static GarbageCollector instance = null;
	
	public static synchronized GarbageCollector getInstance() {
		if (instance == null) {
			instance = new GarbageCollector();
		}
		return instance;
	}
	
	// ==================
	// === local vars ===
	// ==================	
	
	private SqlKV.SingleConn db_conn = null;
	// state of this GCer
	private boolean signal_gc = false;
	// gc counter for generate gc_txn_id
	private int gc_counter = 0;
	
	
	private GarbageCollector() {
		// FIXME: support postgresql only for now
		new SqlKV(OnlineLoader.getTableName(VeriConstants.BENCH_TYPE)); // FIXME: hacky
		db_conn = new SqlKV.SingleConn(VeriConstants.DB_URL(), VeriConstants.PG_USERNAME, VeriConstants.PG_PASSWORD);
		set_gc_flag(false);
	}
	
	// ==================
	// === Main logic ===
	// ==================	
	
	// write GC_KEY in the database
	private void set_gc_flag(boolean start_gc) {
		String val = start_gc ? "true" : "false";
		long wid = start_gc ? VeriConstants.GC_WID_TRUE : VeriConstants.GC_WID_FALSE;
		while (true) {
			Object txn = null;
			try {
				txn = db_conn.begin();
				String encoded_val = OpEncoder.encodeCobraValue(val, VeriConstants.INIT_TXN_ID	, wid);
				db_conn.set(txn, VeriConstants.GC_KEY, encoded_val);
				boolean succ = db_conn.commit(txn);
				if (succ) {
					ChengLogger.println("setting GC_KEY[" + VeriConstants.GC_KEY + "] succeeded to [" + encoded_val + "]");
					break;
				}
			} catch (KvException e) {
				e.printStackTrace();
			} catch (TxnException e) {
				e.printStackTrace();
				db_conn.rollback(txn);
				ChengLogger.println(LoggerType.WARNING, "setting GC_KEY failed");
			}

			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void wrapUpGC() {
		signal_gc = false;
	}
	
	
	/*
	 * 1. [NO; now signalGC() will set GC flag in DB] set GC flag to true
	 * 2. get multi-versioned in frontier
	 * 3. read multi-versioned keys from DB; then immediately stop gc_flag (read epoch_key)
	 * 4. construct a txn as output
	 * x. [NO; let verifier do this] get a singled-version frontier
	 * x. [NO] sync states of GCer
	 */
	private TxnNode gc(PrecedenceGraph c_g, Map<Long, Set<TxnNode>> gc_frontier, int fr_epoch) {
		assert signal_gc;
		
		// 2.
		Map<Long, String> keyhash2str = new HashMap<Long,String>();
		Map<Long, Set<TxnNode>> mulv_kvs = new HashMap<Long, Set<TxnNode>>();
		for (long key : gc_frontier.keySet()) {
			if (gc_frontier.get(key).size() > 1) {
				mulv_kvs.put(key, gc_frontier.get(key));
				assert !keyhash2str.containsKey(key);
				keyhash2str.put(key, getKeyStr(key, gc_frontier.get(key).iterator().next()));
			}
		}
		
		// 3.
		boolean succ = false;
		Map<String, OpEncoder> res_kvs = new HashMap<String,OpEncoder>(); // keystr => decoded op
		Map<String, Long> res_k2valhash = new HashMap<String, Long>(); // keystr => value hash over encoded value
		String epoch_keystr = VeriConstants.EPOCH_KEY;
		while (!succ) {
			res_kvs.clear();
			Object txn = null;
			try {
				// FIXME: might be too huge a RO txn
				txn = db_conn.begin();
				// (a) read EPOCH_KEY
				String val = db_conn.get(txn, epoch_keystr);
				res_kvs.put(epoch_keystr, OpEncoder.decodeCobraValue(val));
				res_k2valhash.put(epoch_keystr, AbstractVerifier.hashKey(val));
				
				// (b) read multi-versioned key
				for (long key : mulv_kvs.keySet()) {
					String keystr = keyhash2str.get(key);
					val = db_conn.get(txn, keystr);
					assert !res_kvs.containsKey(keystr);
					res_kvs.put(keystr, OpEncoder.decodeCobraValue(val));
					res_k2valhash.put(keystr, AbstractVerifier.hashKey(val));
				}
				succ = db_conn.commit(txn);
			} catch (TxnException e) {
				e.printStackTrace();
				db_conn.rollback(txn);
				succ = false;
				ChengLogger.println(LoggerType.WARNING, "[GC-thread] ROtxn [#keys=" + mulv_kvs.size() + "] failed");
			} catch (KvException e) {
				e.printStackTrace();
				assert false;
			}
			ChengLogger.println("[GC-thread] ROtxn[#keys=" + mulv_kvs.size() + "], succ=[" + succ + "]");
		}
		// let clients go as early as possible
		set_gc_flag(false);
		
		// 4.
		long txnid = getGCTxnid();
		assert !c_g.containTxnid(txnid); // gc txn id must be unique
		int op_counter = 1; // op_pos != 0 (why??)
		TxnNode gc_txn = new TxnNode(txnid);
		
		// EPOCH_KEY must be the first op
		OpEncoder en_op = res_kvs.get(epoch_keystr);
		OpNode op = new OpNode(true, txnid, epoch_keystr, res_k2valhash.get(epoch_keystr), en_op.wid, en_op.txnid, op_counter++);
		gc_txn.appendOp(op);
		res_kvs.remove(epoch_keystr);
		
		// other ops in gc_txn
		for (String key : res_kvs.keySet()) {
			en_op = res_kvs.get(key);
			// Note: the val_hash is over encoded value
			// boolean isRead, long txnid, String key,  long val_hash, long wid, long ptid, int pos
			op = new OpNode(true, txnid, key, res_k2valhash.get(key), en_op.wid, en_op.txnid, op_counter++);
			gc_txn.appendOp(op);
		}
		gc_txn.setClientId(VeriConstants.GC_CLIENT_ID);
		gc_txn.commit(1L); // arbitrary timestamp
		
		return gc_txn;
	}
	
	
	private long getGCTxnid() {
		// return AbstractVerifier.hashKey("GC_TXN_ID"+gc_counter); // NOTE: assumption, hash collision is rare
		gc_counter++;
		return Hashing.sha256().hashString("GC_TXN_ID" + gc_counter, StandardCharsets.UTF_8).asLong();
	}

	
	// ==============================
	// ===== Helper functions =======
	// ==============================

	private String getKeyStr(long keyhash, TxnNode txn) {
		String keystr = null;
		for (OpNode op : txn.getOps()) {
			if (op.key_hash == keyhash) {
				keystr = op.key;
			}
		}
		assert keystr != null;
		return keystr;
	}

	// COPIED FROM: ChengInstrumentAPI.java
	static class OpEncoder {
		public String val;
		public long txnid;
		public long wid;
		
		public OpEncoder(String val, long txnid, long wid) {
			this.val = val;
			this.txnid = txnid;
			this.wid = wid;
		}
		
		@Override
		public String toString() {
			return "val: " + val + ", txnid: " + txnid + ", wid: " + wid;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof OpEncoder) {
				OpEncoder that = (OpEncoder) obj;
				return this.val.equals(that.val) && this.txnid == that.txnid
						&& this.wid == that.wid;
			} else {
				return false;
			}
		}
		
		public static String encodeCobraValue(String val, long txnid, long wid) {
			ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 2);
			buffer.putLong(txnid);
			buffer.putLong(wid);
			String str_sig = Base64.getEncoder().encodeToString(buffer.array());
			String val_sign = str_sig + val;
			return "&"+val_sign; // to mark this thing is encoded
		}
		
		public static OpEncoder decodeCobraValue(String encoded_str) {
			try {
				if(encoded_str.length() < 25 || encoded_str.charAt(0) != '&') {
					return null;
				}
				String str_sig = encoded_str.substring(1, 25);
				String real_val = encoded_str.substring(25);
				byte[] barray = Base64.getDecoder().decode(str_sig);
				ByteBuffer bf = ByteBuffer.wrap(barray);
				long txnid = bf.getLong();
				long wid = bf.getLong();
				return new OpEncoder(real_val, txnid, wid);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}
	}
	
	// invoked in AbstractVerifier during initalization
	public static void addGCops2Init(TxnNode init, Set<Long> init_txn_keys) {
		// add GC operations to the database
		OpNode w_gc_true = new OpNode(false, VeriConstants.INIT_TXN_ID,
				VeriConstants.GC_KEY, 
				AbstractVerifier.hashKey("true"), /* val hash */
				VeriConstants.GC_WID_TRUE /* wid */,
				0 /* prev_txnid */,
				init.getOps().size() + 1 /* pos */);
		OpNode w_gc_false = new OpNode(false, VeriConstants.INIT_TXN_ID,
				VeriConstants.GC_KEY, 
				AbstractVerifier.hashKey("false"), /* val hash */
				VeriConstants.GC_WID_FALSE /* wid */,
				0 /* prev_txnid */,
				init.getOps().size() + 1  /* pos */);
		OpNode w_gc_null = new OpNode(false, VeriConstants.INIT_TXN_ID,
				VeriConstants.GC_KEY, 
				0, /* val hash */
				AbstractVerifier.hashKey(VeriConstants.GC_KEY) /* wid */,
				0 /* prev_txnid */,
				init.getOps().size() + 1  /* pos */);
		init.appendOp(w_gc_null);
		init.appendOp(w_gc_true);
		init.appendOp(w_gc_false);
		init_txn_keys.add(AbstractVerifier.hashKey(VeriConstants.GC_KEY));
	}
	
	
	// ==================
	// ===== APIs =======
	// ==================
	
	public synchronized void signalGC() {
		assert !signal_gc;
		signal_gc = true;
		set_gc_flag(true);
	}
	
	public synchronized TxnNode doGC(PrecedenceGraph c_g, Map<Long, Set<TxnNode>> frontier, int fr_epoch) {
		// NOTE: require to signal clients first
		// ALSO: the protocol is that the verifier has seen all clients switch to GC_monitoring
		assert signal_gc;
		
		// real job
		TxnNode gc_txn = gc(c_g, frontier, fr_epoch);
		
		wrapUpGC();
		return gc_txn;
	}


}
