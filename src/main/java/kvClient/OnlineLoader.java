package kvClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import util.ChengLogger;
import util.VeriConstants;
import util.VeriConstants.LoggerType;
import util.VeriConstants.OnlineDBType;

public class OnlineLoader {
	// connect to database
	KvInterface kvi;
	// connect to verifier
	ArrayList<PipedInputStream> ins;
	ArrayList<PipedOutputStream> outs;
	ArrayList<FetchTask> tasks;
	// 
	ArrayList<Integer> num_clients = new ArrayList<Integer>();
	
	public static String getTableName(int b) {
		switch (b) {
		case 0:
			return "chengTxn";
		case 1:
			return "tpcc";
		case 2:
			return "ycsb";
		case 3:
			return "rubis";
		case 4:
			return "twitter";
		default:
			assert false;
			break;
		}
		return "";
	}

	
	public OnlineLoader(OnlineDBType odt) {
		if (odt == OnlineDBType.ROCKSDB) {
			kvi = RpcClient.getInstance();
		} else if (odt == OnlineDBType.POSTGRESQL) {
			kvi = new SqlKV(getTableName(VeriConstants.BENCH_TYPE));
		} else {
			assert false;
		}
		
		ins = new ArrayList<PipedInputStream>();
		outs = new ArrayList<PipedOutputStream>();
		initPipes(ins, outs);
		
		tasks = new ArrayList<FetchTask>();
		initTasks(tasks); // init tasks
		
		initThreadPools(tasks);
	}
	
	// FIXME: hard-code for now
	// Verifier should have known the number of clients and who they are
	private int detectNumClients(int cid){
		String prefix = "cid["+cid+"]_T";
		String suffix = "_0" + VeriConstants.LOG_SUFFIX;
		int max_clients = 200;
		int num_clients = -1;
		boolean end = false;
		
		Object tx = null;
		try {
			tx = kvi.begin();
			for (int i = 0; i < max_clients; i++) {
				String v = kvi.get(tx, prefix + i + suffix);
				if (v != null) {
					ChengLogger.println(LoggerType.DEBUG, "Client[" + i + "] detected.");
					assert num_clients + 1 == i; // should be contiguous
					assert !end;
					num_clients = i;
				} else {
					end = true;
				}
			}
			kvi.commit(tx);
		} catch (AssertionError e1) {
			e1.printStackTrace();
			kvi.rollback(tx);
		} catch (Exception e) {
			e.printStackTrace();
			kvi.rollback(tx);
		}
		assert num_clients != -1;
		assert end;
		return num_clients + 1;
	}
	
	private void initPipes(	ArrayList<PipedInputStream> ins, ArrayList<PipedOutputStream> outs) {
		for (int cid = 1; cid <= VeriConstants.NUM_CLIENT_MACHINES; cid++) {
			int this_num_clients = 0;
			try {
				this_num_clients = detectNumClients(cid);
				num_clients.add(this_num_clients);
				ChengLogger.println("OnlineVerifier found " + this_num_clients + " threads in client " + cid);
			} catch (Exception e) {
				e.printStackTrace();
				assert false;
			}
			
			for (int i = 0; i < this_num_clients; i++) {
				try {
					PipedInputStream in =new PipedInputStream(1024 * 1024 * VeriConstants.BUFFER_SIZE_M);
					PipedOutputStream out = new PipedOutputStream(in);
					ins.add(in);
					outs.add(out);
				} catch (IOException e) {
					e.printStackTrace();
					assert false;
				}
			}
		}
	}
	
	
	private void initTasks(ArrayList<FetchTask> tasks) {
		int total_num_clients = 0;
		for (Integer this_num_clients : num_clients) {
			total_num_clients += this_num_clients;
		}
		assert total_num_clients == ins.size();
		assert total_num_clients == outs.size();
		
		int nextTaskId = 0;
		for (int cid = 1; cid <= num_clients.size(); cid++) {
			for (int tid = 0; tid < num_clients.get(cid-1); tid++) {
				FetchTask w = new FetchTask(kvi, ins.get(nextTaskId), outs.get(nextTaskId), cid, tid);
				nextTaskId++;
				tasks.add(w);
			}
		}
	}
	
	
	private void initThreadPools(ArrayList<FetchTask> tasks) {
		final ArrayList<FetchTask> fixed_tasks = tasks;
		
		new Thread() { public void run() {
				// #fetcher = half of verifier workers
				ThreadPoolExecutor exec = (ThreadPoolExecutor) Executors.newFixedThreadPool(VeriConstants.THREAD_POOL_SIZE / 2);
				int total_tasks = fixed_tasks.size();
				Random rand = new Random();
				
				while (true) {
					// round-robin
					int busy_counter = 0;
					for (FetchTask t : fixed_tasks) {
						if (!t.isRunning()) {
							t.startTask();
							exec.execute(t);
						} else {
							busy_counter++;
						}
					}
					
					// sleep for two reasons:
					// 1. there are too much work on the pool
					// 2. most of the tasks cannot fetch anything
					if (/* 1 */ busy_counter > 0.9 * total_tasks ||
							/* 2 */ busy_counter < 0.1 * total_tasks)
					{
						try {
							ChengLogger.println(LoggerType.DEBUG, "  too much or too little work, sleep...");
							Thread.sleep(VeriConstants.FETCHING_DURATION_BASE + rand.nextInt(VeriConstants.FETCHING_DURATION_RAND));
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				
			}}.start();
	}



	
	// ==== getters ====
	
	public int NumPipes() {
		return ins.size();
	}
	
	public PipedInputStream GetPipe(int i) {
		assert i < ins.size();
		return ins.get(i);
	}
	
	//
	public static void main(String[] args) {
		OnlineLoader l = new OnlineLoader(OnlineDBType.ROCKSDB);
		
		// wait a long time
		try {
			Thread.sleep(60000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		

		int counter = 0;
		for (PipedInputStream in : l.ins) {
			byte[] buffer = new byte[1000];
			Path p = Paths.get("/tmp/Cobra/T" + counter + ".log");
			System.out.println("Write to local log: " + p.toString()+ "...");
			try {
				while (true) {
					int len;
					len = in.read(buffer);
					System.out.println(p.toString() + "...read length = " + len);
					byte[] new_buffer = Arrays.copyOfRange(buffer, 0, len);
					Files.write(p, new_buffer, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
					if (len != 1000) {
						break;
					}
				}
				System.out.println("...DONE");
			} catch (IOException e) {
				e.printStackTrace();
			}
			counter++;
		}

	}

}
