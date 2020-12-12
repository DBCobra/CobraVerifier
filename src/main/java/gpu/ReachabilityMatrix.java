package gpu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import util.ChengLogger;
import util.Pair;
import util.VeriConstants;
import util.VeriConstants.LoggerType;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import algo.TopologicalSort;
import graph.OpNode;
import graph.TxnNode;

public class ReachabilityMatrix {
	
	// NOTE: cubalas and cusparse are bot *column-major* storage!
	private int n = 0;
	private float[] reachability_matrix = null;
	private long[] txn_list = null;  // a list of txnids
	private Map<Long, Integer> txnid2index = null;  // from txnid to index in list above
	
	private ReachabilityMatrix() {
	}
	
	
	// ==== simple getters ====
	
	/*
	 * -1: I don't know; 1: yes; 0: no
	 */
	public int reachUnsafe(long src_id, long dst_id) {
		if (txnid2index.containsKey(src_id) && txnid2index.containsKey(dst_id)) {
			return reach(src_id, dst_id) ? 1 : 0;
		}
		return -1;
	}
	
	public boolean reach(long src_id, long dst_id) {
		assert txnid2index != null;
		assert txnid2index.containsKey(src_id) && txnid2index.containsKey(dst_id);
		int src_indx = txnid2index.get(src_id);
		int dst_indx = txnid2index.get(dst_id);
		return reach(src_indx, dst_indx);
	}
	
	public boolean reach(int src_indx, int dst_indx) {
		// NOTE: colum-major
		return reachability_matrix[dst_indx * n + src_indx] != 0;
	}
	
	private void set(int src_indx, int dst_indx) {
		// NOTE: colum-major
		reachability_matrix[dst_indx * n + src_indx] = 1;
	}
	
	public int getN() {
		return n;
	}
	
	public long index2txnid(int indx) {
		assert indx < n;
		return txn_list[indx];
	}
	
	public int txnid2index(long id) {
		assert txnid2index.containsKey(id);
		return txnid2index.get(id);
	}
	
	public boolean containTxnid(long tid) {
		return txnid2index.containsKey(tid);
	}
	
	// ======= connect ======
	
	public void connect(Long[] src_ids, Long[] dst_ids) {
		connect_simple(src_ids, dst_ids);
	}
	
	// update the matrix by adding edge: src_id->dst_id
	private void connect_simple(Long[] src_ids, Long[] dst_ids) {
		assert txnid2index != null;
		assert src_ids.length == dst_ids.length;
		int size = src_ids.length;
		int[] src_idx = new int[size];
		int[] dst_idx = new int[size];
		
		// 1. translate the txnid into index in this matrix
		for (int i = 0; i < size; i++) {
			// we may get unknown nodes, if so, skip it
			// default src_idx,dst_idx is 0
			if (txnid2index.containsKey(src_ids[i]) && txnid2index.containsKey(dst_ids[i])) {
				src_idx[i] = txnid2index.get(src_ids[i]);
				dst_idx[i] = txnid2index.get(dst_ids[i]);
				// double check, they cannot reach each other
				assert !reach(src_idx[i], dst_idx[i]);
			}
		}
		
		assert VeriConstants.GPU_MATRIX;
		
		// 2. update the matrix
		for (int i = 0; i < size; i++) {
			this.set(src_idx[i], dst_idx[i]);
		}
		
		// 3. run selfmm log(n) times again
		setSelfLoop();
		GPUmm.matrixPower(reachability_matrix, n, false);
		if (VeriConstants.HEAVY_VALIDATION_CODE_ON) {
			validateNaN();
		}
		rmSelfLoop();
	}
	

	// update the matrix by adding edge: src_id->dst_id
	private void connect_complicate(Long[] src_ids, Long[] dst_ids) {
		assert txnid2index != null;
		assert src_ids.length == dst_ids.length;
		int size = src_ids.length;
		int[] src_list = new int[size];
		int[] dst_list = new int[size];
		
		// 1. double check, we do have these nodes & they cannot reach each other
		// 2. translate the txnid into index in this matrix
		for (int i = 0; i < size; i++) {
			assert txnid2index.containsKey(src_ids[i]) && txnid2index.containsKey(dst_ids[i]);
			src_list[i] = txnid2index.get(src_ids[i]);
			dst_list[i] = txnid2index.get(dst_ids[i]);
			assert !reach(src_list[i], dst_list[i]);
		}
		
		if (VeriConstants.GPU_MATRIX) {
			GPU_connect(src_list, dst_list);
		} else {
			CPU_connect(src_list, dst_list);
		}
	}
	
	// This is a helper function for GPU_connect()
	private ReachabilityMatrix genSmallRM(int[] src_list, int[] dst_list, ReachabilityMatrix rm) {
		// FIXME: should replace TxnNode to general node
		MutableGraph<TxnNode> g = GraphBuilder.directed().allowsSelfLoops(true).build();
		Map<Integer, TxnNode> nodes = new HashMap<Integer, TxnNode>();
		int len = src_list.length;
		assert len == dst_list.length;
		
		// add nodes
		for (int i=0; i<len; i++) {
			int src_id = src_list[i];
			if (!nodes.containsKey(src_id)) {
				TxnNode tmp = new TxnNode(src_id);
				nodes.put(src_id, tmp);
				g.addNode(tmp);
			}
			int dst_id = dst_list[i];
			if (!nodes.containsKey(dst_id)) {
				TxnNode tmp = new TxnNode(dst_id);
				nodes.put(dst_id, tmp);
				g.addNode(tmp);
			}
		}
		
		// add edges
		for (int i=0; i<len; i++) {
			TxnNode src = nodes.get(src_list[i]);
			TxnNode dst = nodes.get(dst_list[i]);
			g.putEdge(src, dst);
		}
		
		// add known edges
		ArrayList<Integer> indices = new ArrayList<Integer>(nodes.keySet());
		for (int i : indices) {
			for (int j : indices) {
				if (rm.reach(i, j)) {
					g.putEdge(nodes.get(i), nodes.get(j)); // i ~-> j
				}
			}
		}
		
		return getReachabilityMatrix(g, null, false); // do not use GPU
	}
	
	private void filter_connected(ArrayList<Integer> src, ArrayList<Integer> dst) {
		ArrayList<Integer> new_src = new ArrayList<Integer>();
		ArrayList<Integer> new_dst = new ArrayList<Integer>();
		for (int i=0; i<src.size(); i++) {
			// if they can reach, remove from the list
			if (!this.reach(src.get(i), dst.get(i))) {
				new_src.add(src.get(i));
				new_dst.add(dst.get(i));
			}
		}
		src.clear();
		dst.clear();
		src.addAll(new_src);
		dst.addAll(new_dst);
	}
	
	private void GPU_connect(int[] src_list, int[] dst_list) {
		// UTBABUG: src_list can be large, once met 7k.
		// need to do split
		assert n!= 0;
		
		ArrayList<Integer> src = new ArrayList<Integer>();
		ArrayList<Integer> dst = new ArrayList<Integer>();
		for (int i=0; i<src_list.length; i++) {
			src.add(src_list[i]);
			dst.add(dst_list[i]);
		}
		
		do {
			int len = src.size();
			assert len == dst.size();
			ChengLogger.println("      GPU connet batch size  " + VeriConstants.RMATRIX_CONNECT_BATCH + ", waiting list = " + len);
			
			len = Math.min(len, VeriConstants.RMATRIX_CONNECT_BATCH);
			int[] cur_src = new int[len];
			int[] cur_dst = new int[len];
			for (int i=0; i<len; i++) {
				cur_src[i] = src.get(i);
				cur_dst[i] = dst.get(i);
			}
			
			GPU_connect_inner(cur_src, cur_dst);
			
			// previous connect the paris should be removed automatically in the following line
			filter_connected(src, dst);
		} while(src.size()>0);
		
		assert dst.size() == 0;
	}
	
	private void GPU_connect_inner(int[] src_list, int[] dst_list) {
		assert n!=0;
		//ChengLogger.println("      gpu connect inner ---->");
		
		/*
		for (int i=0; i<src_list.length; i++) {
			System.out.println("Orig: " + src_list[i] + "->" + dst_list[i]);
		}
		*/

		
		// UTBABUG: if src_list={a,b}, dst_list={b,c}, GPU might miss several connections
		// because all the reachabilities are calculated simultaneously.
		// SOLUTION: we run a CPU small reachability first
		ReachabilityMatrix smallRM = genSmallRM(src_list, dst_list, this);
		
		ArrayList<Integer> n_src_list = new ArrayList<Integer>();
		ArrayList<Integer> n_dst_list = new ArrayList<Integer>();
		
		// we add those which are not captured by current reachability matrix
		// NOTE: here is tricky! be careful: long->[number in src_list/dst_list], int->[index within smallRM]
		int num_nodes = smallRM.getN();
		for (int i=0; i<num_nodes; i++) {
			for (int j=0; j<num_nodes; j++) {
				if (smallRM.reach(i, j)) {
					int src = (int) smallRM.index2txnid(i); // NOTE: this is number in src_list/dst_list
					int dst = (int) smallRM.index2txnid(j); // NOTE: this is number in src_list/dst_list
					if (!this.reach(src, dst)) {
						// if smallRM can reach, but current RM cannot, should update
						n_src_list.add(src);
						n_dst_list.add(dst);
					}
				}
			}
		}
		
		src_list = new int[n_src_list.size()];
		dst_list = new int[n_dst_list.size()];
		for (int i=0; i<src_list.length; i++) {
			src_list[i] = n_src_list.get(i);
			dst_list[i] = n_dst_list.get(i);
		}
		
		/*
		for (int i=0; i<src_list.length; i++) {
			System.out.println("After: " + src_list[i] + "->" + dst_list[i]);
		}
		*/
		
		//ChengLogger.println("      gpu connect gpu inner ---->");
		GPUmm.connect(reachability_matrix, src_list, dst_list, n);
	}

	private void CPU_connect(int[] src_list, int[] dst_list) {
		for (int x = 0; x < src_list.length; x++) {
			int src_indx = src_list[x];
			int dst_indx = dst_list[x];
			
			// set src_indx->dst_indx
			set(src_indx, dst_indx);
			
			// UTBABUG: src_indx->[dst_indx~->all] and [all~->src_indx]->dst_indx
			for (int i=0; i<n; i++) {
				if (reach(dst_indx, i)) {
					set(src_indx, i);
				}
				if (reach(i, src_indx)) {
					set(i, dst_indx);
				}
			}
			//System.out.println("[CPU] " + src_indx + "->" + dst_indx);

			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					if (!reach(i, j) && (reach(i, src_indx) && reach(dst_indx, j))) {
						set(i, j);
					}
				}
			}
			
		}
	}

	// ==== reachability matrix ====
	
	public static ReachabilityMatrix getReachabilityMatrix(MutableGraph<TxnNode> g, ReachabilityMatrix old_rm) {
		return getReachabilityMatrix(g, old_rm, VeriConstants.GPU_MATRIX);
	}
	
	private static ReachabilityMatrix getReachabilityMatrix(MutableGraph<TxnNode> g, ReachabilityMatrix old_rm, boolean use_gpu) {
		ReachabilityMatrix ret = new ReachabilityMatrix();
		
		int n = g.nodes().size();
		ret.n = n;
		ret.txn_list = new long[n];
		ret.txnid2index = mapNodeAndIndex(g, ret.txn_list);
		
		if (use_gpu) {
			ret.GPU_getReachabilityMatrix(g, old_rm);
		} else {
			ret.CPU_getReachabilityMatrix(g, old_rm);
		}
		
		return ret;
	}
	
	private void updateConnectivities(ReachabilityMatrix old_rm) {
		// get the intersect of these two matrices
		Set<Long> cur_txn = new HashSet<Long>(txnid2index.keySet());
		Set<Long> old_txn = old_rm.txnid2index.keySet();
		cur_txn.retainAll(old_txn); // intersection of cur and old txns
		
		for (long src_txnid : cur_txn) {
			for (long dst_txnid : cur_txn) {
				// update what old_rm knows about the connectivities
				if (old_rm.reach(src_txnid, dst_txnid)) {
					set(txnid2index.get(src_txnid), txnid2index.get(dst_txnid));
				}
			}
		}
	}
	
	//calculate one reachability matrix
	private void CPU_getReachabilityMatrix(MutableGraph<TxnNode> g, ReachabilityMatrix old_rm) {
		reachability_matrix = floydWarshall(g, txn_list, txnid2index);
	}
	
	// O(|V|^3)
	// NOTE: this is column-major!!!
	public static float[] floydWarshall(MutableGraph<TxnNode> g, long[] index2node, Map<Long, Integer> node2index) {
		int n = g.nodes().size();
		assert index2node.length == n; // assert the input map is empty

		float[] po = new float[n*n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				po[i*n+j] = 0;
			}
		}

		// for all edges
		// NOTE: this is column-major!!!
		for (EndpointPair<TxnNode> edge : g.edges()) {
			int src_indx = node2index.get(edge.source().getTxnid());
			int dst_indx = node2index.get(edge.target().getTxnid());
			po[dst_indx*n + src_indx] = 1;
		}
		// for all nodes
		for (int i = 0; i < n; i++) {
			po[i*n + i] = 0;
		}

		// propergate the partial orders
		for (int k = 0; k < n; k++) {
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++) {
					// if there is no path i->j so far, we try to see if there is one i->k->j
					// NOTE: this is column-major!!!
					if ( po[j*n + i]==0 && (po[j*n+k]==1 && po[k*n+i]==1)) {
						po[j*n + i] = 1;
					}
				}
			}
		}
		// assert there is no cycle
		for (int i = 0; i < n; i++) {
			assert po[i*n + i] == 0; // one node cannot reach itself, if there is no cycle
		}
		return po;
	}
	
	
	
	// calculate one reachability matrix
	// TODO: optimization using old_rm
	private  void GPU_getReachabilityMatrix(MutableGraph<TxnNode> g, ReachabilityMatrix old_rm) {
		
		// the array is guaranteed to be 0
		reachability_matrix = new float[n*n];
		
		// connect all edges in reachability matrix
		for (EndpointPair<TxnNode> edge : g.edges()) {
			int src_indx = txnid2index.get(edge.source().id());
			int dst_indx = txnid2index.get(edge.target().id());
			assert src_indx < dst_indx; // since it is a topological sort
			// NOTE: here is *column-major*!!!
			// NOTE: so in this case, src_indx < dst_indx,
			// the triangle matrix is left-bottom.
			reachability_matrix[dst_indx*n + src_indx] = 1;
		}
		
		boolean fresh = true;
		if (old_rm != null) {
			// update the connectivity hinted by old_rm
			updateConnectivities(old_rm);
			fresh = false;
		}
		
		setSelfLoop();
		// call GPU code
		GPUmm.matrixPower(reachability_matrix, n, fresh);
		
		if (VeriConstants.HEAVY_VALIDATION_CODE_ON) {
			validateNaN();
		}
		
		// get rid of self-pointing-edge
		rmSelfLoop();
		
		
		// BUG: I got a bunch of zeros here. Is it because of GPU memcpy???
		//System.out.println(dumpMatrix());
	}
	
	
	// ===== helper functions ====
	
	private void setSelfLoop() {
		// NOTE: (AvI) ^ (n-1) =A^(n-1) v A^(n-2) ... v A v I
		for (int i=0; i<n; i++) {
			reachability_matrix[i*n+i] = 1;
		}
	}
	
	private void rmSelfLoop() {
		for (int i=0; i<n; i++) {
			reachability_matrix[i*n+i] = 0;
		}
	}
	
	// FIXME: copy-paste from Reachability.java
	public static Map<Long, Integer> mapNodeAndIndex(MutableGraph<TxnNode> g, long[] index2node) {
		Map<Long, Integer> node2index = new HashMap<Long, Integer>();

		// triangle optimization
		// get the topological sort here
		ArrayList<TxnNode> topo = TopologicalSort.toposort(g);
		assert topo.size() == g.nodes().size();
		
		// for i and j, where i < j, there should never be a edge from j to i (i.e. j->i)
		int counter = 0;
		for (TxnNode node : topo) {
			index2node[counter] = node.getTxnid();
			node2index.put(node.getTxnid(), counter);
			counter++;
		}

		return node2index;
	}
	
	public boolean hasSameReachability(ReachabilityMatrix m) {
		if (m.n != this.n) return false;
		for (int i=0; i<n; i++) {
			for (int j=0; j<n; j++) {
				if (m.reach(i, j) != this.reach(i, j)) {
					return false;
				}
			}
		}
		return true;
	}
	
	public String diffMatrix(ReachabilityMatrix m) {
		assert m.n == this.n;
		StringBuilder sb = new StringBuilder();
		sb.append("diff:\n");
		for (int i=0; i<n; i++) {
			for (int j=0; j<n; j++) {
				if (m.reach(i, j) != this.reach(i, j)) {
					sb.append(i + "->" + j + ": " + this.reach(i, j) + " vs. " + m.reach(i, j)  + "\n");
				}
			}
		}
		return sb.toString();
	}
	
	public String dumpMapping() {
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<n; i++) {
			sb.append("Matrix[" + i + "]=" + Long.toHexString(txn_list[i]) + "\n");
		}
		return sb.toString();
	}
	
	public String dumpMatrix() {
		StringBuilder sb = new StringBuilder();
		for (int i=0; i<n; i++) {
			for (int j=0; j<n; j++) {
				sb.append(" " + ((reachability_matrix[i*n + j] > 0) ? "1" : "0"));
				//sb.append(" " + String.format("%.2f", reachability_matrix[i*n + j]));
			}
			sb.append("\n");
		}
		
		return sb.toString();
	}
	
	// ==== simple test ===
	public static void functionTest() {
		int num_nodes = 100;
		int num_edges = 100;
		Random r = new Random(19930610);
		MutableGraph<TxnNode> g = GraphBuilder.directed().allowsSelfLoops(true).build();
		ArrayList<TxnNode> txns = new ArrayList<TxnNode>();
		
		for (int i=0; i<num_nodes; i++) {
			TxnNode t = new TxnNode(i);
			g.addNode(t);
			txns.add(t);
		}
		for (int i=0; i<num_edges; i++) {
			int src = r.nextInt(num_nodes);
			int dst = r.nextInt(num_nodes);
			// prevent cycle
			if (src == dst) continue;
			int bigger = Math.max(src, dst);
			int smaller = Math.min(src, dst);
			g.putEdge(txns.get(smaller), txns.get(bigger));
		}
		
		ReachabilityMatrix rm1 = getReachabilityMatrix(g, null, false);
		ReachabilityMatrix rm2 = getReachabilityMatrix(g, null, true);
		
		// compare
		if(rm1.hasSameReachability(rm2)) {
			System.out.println("GPU matrix generation is CORRECT!");
		} else {
			System.out.println("GPU matrix generation is WRONG!");
		}
		
		System.out.print("   ");
		for (int i=0; i<rm1.n; i++) {
			System.out.print(String.format("%3d", i));
		}
		System.out.println();
		for (int i=0; i<rm1.n; i++) {
			System.out.print(String.format("%3d", i));
			for (int j=0; j<rm1.n; j++) {
				System.out.print(String.format("%3d", rm1.reach(i,j) ? 1 : 0));
			}
			System.out.println();
		}

		
		// find some non-connected nodes
		int num_pairs = 5; //5
		int[] src_list = new int[num_pairs];
		int[] dst_list = new int[num_pairs];
		int counter = 0;
		while(true) {
			int src = r.nextInt(num_nodes);
			int dst = r.nextInt(num_nodes);
			if (src == dst) continue;
			int bigger = Math.max(src, dst);
			int smaller = Math.min(src, dst);
			if (!rm1.reach(smaller, bigger)) {
				src_list[counter] = smaller;
				dst_list[counter] = bigger;
				counter++;
			}
			if (counter >= num_pairs) break;
		}
		
		// test the connect()
		rm1.CPU_connect(src_list, dst_list);
		rm2.GPU_connect(src_list, dst_list);
		
		// compare
		if(rm1.hasSameReachability(rm2)) {
			ChengLogger.println("GPU connect() is CORRECT!");
		} else {
			ChengLogger.println("GPU connect() is WRONG!");
			ChengLogger.println("cpu diff gpu");
			ChengLogger.println(rm1.diffMatrix(rm2));
			//ChengLogger.println("=====CPU=====");
			//ChengLogger.println(rm1.dumpMatrix());
			//ChengLogger.println("=====GPU=====");
			//ChengLogger.println(rm2.dumpMatrix());
		}
	}
	
	private void validateNaN() {
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (Float.isNaN(reachability_matrix[i * n + j])) {
					ChengLogger.println(LoggerType.ERROR, "(" + j + "," + i + ")==NaN");
					assert false;// sample several not NaN
				}
			}
		}
	}
}
