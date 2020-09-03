package kvClient;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Random;

import com.google.common.io.BaseEncoding;

import util.ChengLogger;
import util.VeriConstants;
import util.VeriConstants.LoggerType;

public class FetchTask implements Runnable {
	
	KvInterface kvi;
	PipedInputStream in;
	PipedOutputStream out;
	String task_id;
	//
	int index = 0;
	String prefix;
	String suffix;
	// hash or signatrue
	String prev_entry_sig;
	// task status
	private boolean running = false;
	
	
	public FetchTask(KvInterface kvi, PipedInputStream in, PipedOutputStream out, int cid, int tid) {
		this.in = in;
		this.out = out;
		this.kvi = kvi;
		task_id = ""+cid+"-"+tid;
		//
		prefix = "cid["+cid+"]_T"+tid+"_";
		suffix = VeriConstants.LOG_SUFFIX;
		//
		prev_entry_sig = "0";
	}
	
	public synchronized boolean isRunning() {
		return running;
	}
	
	public synchronized void startTask() {
		running = true;
	}
	
	public void run() {
		assert running == true;
		
		ArrayList<byte[]> traces = new ArrayList<byte[]>();
		ChengLogger.println(LoggerType.DEBUG, "  Task[" + task_id + "]: start to fetch index=" + index);
		// fetch & push to pipe
		Object tx = null;
		int read_size = 0;
		// prepare local states
		int cur_index = index;
		String[] tmp_prev_sig = new String[1];
		tmp_prev_sig[0] = prev_entry_sig;

		// ----> start tx
		try {
			tx = kvi.begin();
			while (true) {
				String key = prefix + cur_index + suffix;
				String val = kvi.get(tx, key);
				if (val != null) { // if we get anyting
					cur_index++;
					// ugly: a pass by ref simulation
					byte[] trace = decodeTraceEntry(val, tmp_prev_sig);
					traces.add(trace);
					read_size += trace.length;
					if (shouldStop(cur_index, read_size)) {
						break;
					}
				} else { // end of the trace, try to commit
					break;
				}
			}
			boolean ret = kvi.commit(tx);
			assert ret;
			// -----> end tx
			// successfully committed
			index = cur_index;
			prev_entry_sig = tmp_prev_sig[0];
			for (byte[] trace : traces) {
				out.write(trace);
			}
		} catch (AssertionError e) {
			e.printStackTrace();
			kvi.rollback(tx);
			return;
		} catch (IOException e) {
			kvi.rollback(tx);
			e.printStackTrace(); // why???
			ChengLogger.println(LoggerType.ERROR, "  Task[" + task_id + "] terminal, because of " + e.toString());
			return;
		} catch (KvException e) {
			kvi.rollback(tx);
			ChengLogger.println(LoggerType.ERROR, "  Task[" + task_id + "] " + e.toString());
			System.exit(-1);
		} catch (TxnException e) {
			// this might happen frequently
			kvi.rollback(tx);
			ChengLogger.println(LoggerType.WARNING, "  Task[" + task_id + "] " + e.toString());
		}
		
		running = false;
	}
	
	// ===== helper functions =====
	private boolean shouldStop(int cur_index, int read_size) throws IOException {
		// if the buffer is almost full, we stop fetching
		if (in.available() + read_size > 1024*1024 * (VeriConstants.BUFFER_SIZE_M - VeriConstants.PIPE_SAFE_M)) {
			ChengLogger.println(LoggerType.ERROR, "  Worker[" + task_id + "]: full pipe!");
			return true;
		}
		// if we reach the fetch size, we stop fetching
		if (cur_index - index >= VeriConstants.NUM_BATCH_FETCH_TRACE) {
			return true;
		}
		return false;
	}
	
	// NOTE: copied from Changgeng's code
	private static byte[] aggregateData(byte[] log, String hash) {
		byte[] hash_bytes = hash.getBytes();
		byte[] data = new byte[log.length + hash_bytes.length];
		System.arraycopy(log, 0, data, 0, log.length);
		System.arraycopy(hash_bytes, 0, data, log.length, hash_bytes.length);
		return data;
	}
	
	// NOTE: copied from Changgeng's code
	private static String SHA256(byte[] data) {
		String ret = null;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] encodedhash = digest.digest(data);
			ret = Base64.getEncoder().encodeToString(encodedhash);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return ret;
	}
	
	private byte[] decodeTraceEntry(String val, String[] prev_entry_sig) {
		// 1. decode
		String payload = val;
		String hash_or_sig = null;
		if (VeriConstants.PARSING_SIG_ON) {
			assert false;
		} else if (VeriConstants.PARSING_HASH_ON) {
			int coma = val.indexOf(',');
			payload = val.substring(0, coma);
			hash_or_sig = val.substring(coma + 1);
		}
		
		// 2. get binary trace
		byte[] trace =  BaseEncoding.base64().decode(payload);
		
		// 3. update signature/hash if needed
		if (VeriConstants.PARSING_SIG_ON) {
			assert false;
		} else if (VeriConstants.PARSING_HASH_ON) {
			// previous trace entry's hash should be the same as what in current
			if (!prev_entry_sig[0].equals(hash_or_sig)) {
				ChengLogger.println(LoggerType.ERROR,
						"Worker["+task_id+"] previous trace entry [" + prev_entry_sig[0] + "] differs from what we saw [" + hash_or_sig + "]");
				assert false;
			}
			prev_entry_sig[0] = SHA256(aggregateData(trace, hash_or_sig));
		}
		
		return trace;
	}
	
}
