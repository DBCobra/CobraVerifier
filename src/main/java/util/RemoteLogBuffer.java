package util;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


// create thread to read from socket and add to a buffer
public class RemoteLogBuffer {
	
	public static int buffer_size = 1024;
	public static int pipe_buffer_size = 4 * 1024 * 1024; // 4M
	private static RemoteLogBuffer instance = null;
	
	public synchronized static RemoteLogBuffer getInstance() {
		if (instance == null) {
			instance = new RemoteLogBuffer();
		}
		return instance;
	}

	// =======Loaders=======
	
	static class LoaderThread extends Thread {

		String read_from_client;
		BufferedInputStream client_stream; // read from this
		DataOutputStream pipe_out; // write to this

		public LoaderThread(String cid, BufferedInputStream cstream, DataOutputStream out) {
			read_from_client = cid;
			client_stream = cstream;
			pipe_out = out;
		}

		public void run() {
			byte[] buf = new byte[1024];
			while (true) {
				try {
					int len = client_stream.read(buf);
					assert len > 0;
					pipe_out.write(buf, 0, len);
				} catch (IOException e) {
					e.printStackTrace();
					assert false; // for now
				}
			}
		}
	}
	
	
	// =======local vars========
	
	boolean init = false;
	Map<String, LoaderThread> loaders = null;
	Map<String, DataInputStream> pipe_ins = null;
	Map<String, DataOutputStream> pipe_outs = null;
	
	
	private RemoteLogBuffer() {
		loaders = new HashMap<String, LoaderThread>();
		pipe_ins = new HashMap<String, DataInputStream>();
		pipe_outs = new HashMap<String, DataOutputStream>();
	}
	
	private void initThreads(LogReceiver lr) {
		assert !init;
		// 1. prepare threads and pipes
		for (String client : lr.getClients()) {
			try {
				BufferedInputStream cstream = lr.getClientStream(client);
				assert cstream.markSupported();

				// create local piped in/out
				PipedInputStream pin = new PipedInputStream(pipe_buffer_size);
				PipedOutputStream pout = new PipedOutputStream(pin);
				DataInputStream pipe_in = new DataInputStream(pin);
				DataOutputStream pipe_out = new DataOutputStream(pout);
				
				LoaderThread loader = new LoaderThread(client, cstream, pipe_out);
				
				assert !loaders.containsKey(client);
				loaders.put(client, loader);
				pipe_ins.put(client, pipe_in);
				pipe_outs.put(client, pipe_out);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		// 2. start threads
		for (LoaderThread loader : loaders.values()) {
			loader.start();
		}
		init = true;
	}
	
	
	// ====== APIs =====
	
	public Set<String> waitClientConnect() {
		assert !init;
		LogReceiver lr = LogReceiver.getInstance();
		
		// 1. wait for all clients [the first time]
		// make sure the verifier sees all clients
		while(lr.getClients().size() != VeriConstants.TOTAL_CLIENTS) {
			ChengLogger.println("waiting for remote logs...[" + lr.getClients().size() + "/" + VeriConstants.TOTAL_CLIENTS);
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			assert lr.getClients().size() <= VeriConstants.TOTAL_CLIENTS;
			// debug:
			for (String name : lr.getClients()) {
				System.out.println("connection: " + name);
			}
		}
		
		initThreads(lr);
		
		return lr.getClients();
	}
	
	public boolean ifInit() {
		return init;
	}
	
	public Set<String> getClients() {
		assert init;
		return loaders.keySet();
	}
	
	public DataInputStream getClientStream(String client) {
		assert init;
		assert loaders.containsKey(client);
		return pipe_ins.get(client);
	}
	
	public String status() {
		StringBuilder sb = new StringBuilder();
		sb.append("=====buffer [" + pipe_buffer_size/1024 + "k]=====\n");
		for (String client : pipe_ins.keySet()) {
			int avail = -1;
			try {
				avail = pipe_ins.get(client).available();
			} catch (IOException e) {
			}
			String percent = avail == -1 ? "null" : String.format("%.2f %%", (double) avail * 100 / pipe_buffer_size);
			sb.append(client + ": " + percent + "\n");
		}
		sb.append("===============\n");
		return sb.toString();
	}

	
}
