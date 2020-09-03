package util;

import java.util.HashMap;

public class Profiler {
	
	// global vars
	private static HashMap<Long,Profiler> profilers = new HashMap<Long,Profiler>();
	
	// local vars
	private HashMap<String, Long> start_time = new HashMap<String, Long>();
	private HashMap<String, Long> total_time = new HashMap<String, Long>();
	private HashMap<String, Integer> counter = new HashMap<String, Integer>();

	public synchronized static Profiler getInstance() {
		long tid = Thread.currentThread().getId();
		if (!profilers.containsKey(tid)) {
			profilers.put(tid, new Profiler());
		}
		return profilers.get(tid);
	}
	
	private Profiler() {
	}
	
	public synchronized void clear() {
		start_time.clear();
		total_time.clear();
		counter.clear();
	}
	
	public synchronized void startTick(String tag) {
		if (!counter.containsKey(tag)) {
			counter.put(tag, 0);
			total_time.put(tag, 0L);
		}
		
		// if we haven't stop this tick, stop it!!!
		if (!start_time.containsKey(tag)) {
			endTick(tag);
		}
		
		// start the tick!
		start_time.put(tag, System.currentTimeMillis());
	}
	
	public synchronized void endTick(String tag) {
		if (start_time.containsKey(tag)) {
			long cur_time = System.currentTimeMillis();
			long duration = cur_time - start_time.get(tag);
			
			// update the counter and total_time
			total_time.put(tag, (total_time.get(tag) + duration));
			counter.put(tag, (counter.get(tag) + 1));
			
			// rm the tick
			start_time.remove(tag);
		} else {
			// FIXME: shouldn't be here
			// but do nothing for now.
		}
	}
	
	public synchronized long getTime(String tag) {
		if (total_time.containsKey(tag)) {
			return total_time.get(tag);
		} else {
			return 0;
		}
	}
	
	public synchronized int getCounter(String tag) {
		if (counter.containsKey(tag)) {
			return counter.get(tag);
		} else {
			return 0;
		}
	}
	
}
