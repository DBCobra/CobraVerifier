package verifier;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import com.google.common.graph.EndpointPair;

import algo.DFSCycleDetection;
import gpu.ReachabilityMatrix;
import graph.EdgeType;
import graph.OpNode;
import graph.PrecedenceGraph;
import graph.TxnNode;
import graph.TxnNode.TxnType;
import kvClient.GarbageCollector;
import util.ChengLogger;
import util.Pair;
import util.Profiler;
import util.VeriConstants;
import util.VeriConstants.LoggerType;

public class MonoSATVerifierSyncEpoch extends MonoSATVerifierOneshot {

	public MonoSATVerifierSyncEpoch(String logfd) {
		super(logfd);
	}

	public boolean audit() {
		assert false;
		return false;
	}

	public int[] count() {
		assert false;
		return null;
	}

	// =======round related======

	static class RoundContext2 {
		int txn_handled = 0;
		int rounds = 0;
		int todo_epoch = 0;
		// last round
		ReachabilityMatrix recent_rm = null;
		// epochs
		Map<Integer, TxnNode> chunk_heads = new HashMap<Integer, TxnNode>();
		Map<Integer, TxnNode> client_last_txn = new HashMap<Integer, TxnNode>();
		// frontier
		Map<Long, Set<TxnNode>> frontier = new HashMap<Long, Set<TxnNode>>();
		// info for each epoch
		// NOTE: we assume [epoch == conx.epoch_graphs index]
		ArrayList<PrecedenceGraph> epoch_graphs = new ArrayList<PrecedenceGraph>();
		// ArrayList<Set<Constraint>> epoch_cons = new ArrayList<Set<Constraint>>();
		ArrayList<Set<Pair<Long, Long>>> epoch_solutions = new ArrayList<Set<Pair<Long, Long>>>();
		// optimization: prior epochs need to be updated
		Set<Integer> recheck_epoch = new HashSet<Integer>();
		// for incremental complete graph
		PrecedenceGraph c_g = new PrecedenceGraph(); // share the same TxnNode with m_g
		Set<TxnNode> incomplete_reachable = null;
		// GC
		int last_gc_epoch = 0; // last epoch when the verifier did GC
		boolean wait4signal = false;
		boolean receivegcsignals = false;
		boolean issuegctxn = false;
		public int gc_veri_signal_epoch = 0;
		public int gc_cl_1st_sig_recv_epoch = 0;
		public int gc_cl_last_sig_recv_epoch = 0;
		public int gc_veri_gc_epoch = 0;
		public int gc_cl_gctxn_epoch = 0;  // doesn't clear after GC; used for checking overlapping GC
		public Map<Integer, Integer> gc_client2sigrecv = null;
		public TxnNode gc_txn = null;
		public Map<Long, Set<TxnNode>> gc_frontier = null;
		// staled
		Set<Integer> deleted_epochs = new HashSet<Integer>();
		Map<Integer, Set<TxnNode>> single_fr_untouched_epoch = new HashMap<Integer, Set<TxnNode>>(); // XXX: can be optimized

	}

	// ========= create graph =====

	private Set<TxnNode> GetIncompleteTxns(PrecedenceGraph g, Set<TxnNode> incomplete_txns) {
		// (1) remove those finished txns
		Set<TxnNode> done_txns = new HashSet<TxnNode>();
		for (TxnNode txn : incomplete_txns) {
			if (txn.getStatus() == TxnType.COMMIT) {
				done_txns.add(txn);
			}
		}
		incomplete_txns.removeAll(done_txns);
		// UTUBABUG: remove the INIT txn
		incomplete_txns.remove(g.getNode(VeriConstants.INIT_TXN_ID));

		// (2) get all nodes that can be reached by incomplete txns (except INIT)
		Set<TxnNode> incomplete_reachable = new HashSet<TxnNode>();
		Set<TxnNode> nextstep = new HashSet<TxnNode>(incomplete_txns);
		while (nextstep.size() != 0) {
			Set<TxnNode> nextstep2 = new HashSet<TxnNode>();
			for (TxnNode in_txn : nextstep) {
				if (!incomplete_reachable.contains(in_txn)) { // if haven't meet
					assert !in_txn.frozen;
					incomplete_reachable.add(in_txn);
					nextstep2.addAll(g.successors(in_txn)); // add all its successors for next exploration
				}
			}
			nextstep = nextstep2;
		}
		//incomplete_reachable.add(g.getNode(VeriConstants.INIT_TXN_ID));  // add back the INIT txn

		return incomplete_reachable;
	}
	
	private void AddCOEdges(PrecedenceGraph cg, Set<TxnNode> new_nodes) {
		for (TxnNode n : new_nodes) {
			if (n.getClientId() == VeriConstants.GC_CLIENT_ID) {continue;} // skip gc_txn
			long prev = n.getPrevClientTxn();
			TxnNode prev_n = cg.getNode(prev);
			assert prev_n != null; // this is a complete graph
			cg.addEdge(prev_n, n, EdgeType.CO);
			
			long next = n.getNextClientTxn();
			TxnNode next_n = cg.getNode(next);
			if (next_n != null) {
				cg.addEdge(n, next_n, EdgeType.CO);
			}
		}
	}

	protected PrecedenceGraph CreateKnownGraph(RoundContext2 conx) {
		Profiler prof = Profiler.getInstance();
		
		prof.startTick("loadfile");
		boolean ret = false;
		if (remote_log) {
			ret = loadRemoteLogs(m_g);
		} else {
			ArrayList<File> opfiles = findOpLogInDir(log_dir);
			ret = loadLogs(opfiles, m_g);
		}
		prof.endTick("loadfile");
		
		prof.startTick("checkvalue");
		CheckValues(m_g, this.new_txns_this_turn); // check whether all the read/write values match
		if (!ret)
			assert false; // Reject
		prof.endTick("checkvalue");

		prof.startTick("getincomplete");
		// construct the complete subgraph
		Set<TxnNode> cur_incomplete_reachable = GetIncompleteTxns(m_g, this.potential_ongoing_txns);
		prof.endTick("getincomplete");

		// NOTE: complete graph contains INIT TXN
		prof.startTick("getcomplete");
		if (conx.incomplete_reachable == null) { // first round
			Set<TxnNode> complete_nodes = new HashSet<TxnNode>(m_g.allNodes());
			complete_nodes.removeAll(cur_incomplete_reachable);
			conx.c_g = m_g.subgraphNoClone(complete_nodes);
		} else { // incrementally growing the complete graph
			// (1) adding complete nodes; nodes = {last round incomplete_reachable + new_txns_this_turn + potential_ongoing_txns}
			Set<TxnNode> new_nodes = new HashSet<TxnNode>();
			new_nodes.addAll(conx.incomplete_reachable); // last round's incomplete reachable
			new_nodes.addAll(this.new_txns_this_turn);
			new_nodes.addAll(this.potential_ongoing_txns);
			assert m_g.allNodes().size() == new_nodes.size() + conx.c_g.allNodes().size();
			
			Set<TxnNode> new_compl_nodes = new HashSet<TxnNode>(new_nodes);
			new_compl_nodes.removeAll(cur_incomplete_reachable);
			assert new_compl_nodes.size() + cur_incomplete_reachable.size() == new_nodes.size(); // cur_cincomplete_reachable \subset new_nodes
			
			// (2) add edges; RMW, WR, and RW (NOTE: order matters)
			TxnNode init = conx.c_g.getNode(VeriConstants.INIT_TXN_ID);
			for (TxnNode n : new_compl_nodes) {
				conx.c_g.addTxnNode(n);  // add node
				AddRMWEdge(conx.c_g, n, null);  // add RMW
				conx.c_g.addEdge(init, n, EdgeType.INIT); // add INIT edge
			}
			// add WR edges
			AddWREdges(conx.c_g, new_compl_nodes, null);
			// add RW edges
			AddRWEdges(conx.c_g, new_compl_nodes, this.wid2txnid, null);
			// add CO edges
			AddCOEdges(conx.c_g, new_compl_nodes);
		}
		conx.incomplete_reachable = cur_incomplete_reachable;
		prof.endTick("getcomplete");

		assert conx.incomplete_reachable.size() + conx.c_g.allNodes().size() == m_g.allNodes().size(); // quick check
		if (VeriConstants.HEAVY_VALIDATION_CODE_ON) {
			checkCompleteGraph(conx);
		}

		ChengLogger.println("[1] #Clients=" + this.client_list.size() + "; #new_txns=" + this.new_txns_this_turn.size());
		ChengLogger.println("[1] complete graph: #n=" + conx.c_g.allNodes().size());
		ChengLogger.println("[1] global graph: #n=" + m_g.allNodes().size());

		return conx.c_g;
	}
	
	
	private void checkCompleteGraph(RoundContext2 conx) {
		// check number of nodes
		assert conx.incomplete_reachable.size() + conx.c_g.allNodes().size() == m_g.allNodes().size();
		// check the whole graph; HEAVY
		assert m_g.allEdges().containsAll(conx.c_g.allEdges());
		// check number of edges
		Set<EndpointPair<TxnNode>> incmpl_edges = new HashSet<EndpointPair<TxnNode>>();
		for (TxnNode n : conx.incomplete_reachable) {
			for (TxnNode succ : m_g.successors(n)) {
				incmpl_edges.add(EndpointPair.ordered(n, succ));
			}
			for (TxnNode pre : m_g.predecessors(n)) {
				if (!conx.incomplete_reachable.contains(pre)) {
					incmpl_edges.add(EndpointPair.ordered(pre, n));
				}
			}
		}

		System.out.println("global graph #edges=" + m_g.allEdges().size());
		System.out.println("complete graph #edges=" + conx.c_g.allEdges().size());
		System.out.println("incomplete reachable #nodes=" + conx.incomplete_reachable.size());
		System.out.println("incomplete reachable #edges=" + incmpl_edges.size());
		
		assert m_g.allEdges().containsAll(incmpl_edges);
		
		Set<EndpointPair<TxnNode>> xxx = new HashSet<EndpointPair<TxnNode>>(m_g.allEdges());
		xxx.removeAll(conx.c_g.allEdges());
		xxx.removeAll(incmpl_edges);
		
		for (EndpointPair<TxnNode> e : xxx) {
			System.out.println("==========");
			TxnNode src = e.source();
			TxnNode dst = e.target();
			System.out.println(
					"src [CG:" + conx.c_g.allNodes().contains(src) + "][ICOMP:" + conx.incomplete_reachable.contains(src) +"] => " +
					"dst [CG:" + conx.c_g.allNodes().contains(dst) + "][ICOMP:" + conx.incomplete_reachable.contains(dst) +"]"
			);
			System.out.println("src=" + src.toString2());
			System.out.println("dst=" + dst.toString2());
		}

		assert incmpl_edges.size() + conx.c_g.allEdges().size() == m_g.allEdges().size();
	}

	// ===== epoch related ======
	
	private boolean isMonitoringTxn(TxnNode t) {
		int size = t.getOps().size();
		if (t.getOps().size() > 0 && t.getOps().get(size - 1).isRead
				&& t.getOps().get(size - 1).key_hash == VeriConstants.VERSION_KEY_HASH) {
			return true;
		}
		return false;
	}

	
	private long verWid(TxnNode t) {
		assert t.getOps().size() == 3; // r(epoch); w(epoch); r(gc)
		assert t.getOps().get(1).isRead == false;
		assert t.getOps().get(1).key_hash == VeriConstants.VERSION_KEY_HASH;
		return t.getOps().get(1).wid;
	}
	
	protected boolean getGCFlag(TxnNode t) {
		assert isEpochTxn(t);
		return t.getOps().get(2).wid == VeriConstants.GC_WID_TRUE;
	}
	
	protected boolean isEpochTxn(TxnNode t) {
		if (t.getOps().size() == 3 && t.getOps().get(0).isRead && !t.getOps().get(1).isRead && t.getOps().get(2).isRead
				&& t.getOps().get(0).key_hash == VeriConstants.VERSION_KEY_HASH
				&& t.getOps().get(1).key_hash == VeriConstants.VERSION_KEY_HASH
				&& t.getOps().get(2).key_hash == VeriConstants.GC_KEY_HASH) {
			return true;
		}
		return false;
	}
	
	protected boolean isGCTxn(TxnNode t) {
		return t.getClientId() == VeriConstants.GC_CLIENT_ID;
	}

	protected int AssignEpoch(PrecedenceGraph pg, RoundContext2 conx) {
		long version_key = VeriConstants.VERSION_KEY_HASH;
		
		// 1. find the first epoch txn in this round (the last epoch txn)
		TxnNode first_chunk_head = null;
		if (conx.todo_epoch == 0) {
			for (TxnNode n : pg.allNodes()) {
				if (isEpochTxn(n) && n.get(0).wid == version_key){ // check if the first epoch txn
					first_chunk_head = n;
					break;
				}
			}
		} else {
			first_chunk_head = conx.chunk_heads.get(conx.todo_epoch - 1);
		}
		assert first_chunk_head != null;
		
		// 2. traverse through the WR-edges find all following epoch txns and barrier txns
		TxnNode gc_txn = null;
		ArrayList<TxnNode> epoch_list = new ArrayList<TxnNode>();
		Set<TxnNode> barrier_txns = new HashSet<TxnNode>();
		TxnNode cur_txn = first_chunk_head;
		while (true) {
			epoch_list.add(cur_txn); // add current epoch txn to list
			long cur_wid = verWid(cur_txn);
			if (!pg.m_readFromMapping.containsKey(cur_wid)) { // (i) no successors; quit
				break;
			}
			cur_txn = null;
			for (OpNode rop : pg.m_readFromMapping.get(cur_wid)) {
				TxnNode t = pg.getNode(rop.txnid);
				if (isGCTxn(t)) {
					assert gc_txn == null; // there is only one GC_txn
					gc_txn = t;
				} else if (isMonitoringTxn(t)) { // NOTE: treat gc_txn as barrier
					barrier_txns.add(t);
				} else if (isEpochTxn(t)) {
					assert cur_txn == null; // there is only one successor epoch txn
					cur_txn = t;
				} else {
					assert false; // only cobra touches this key
				}
			}
			if (cur_txn == null) { // (ii) no epoch successor; quit
				break;
			}
		}
		
		//System.out.println("num epoch txns: " + epoch_list.size());
		//System.out.println("num barrier txns: " + barrier_txns.size());

		// 3. assign epoch numbers to all the epoch txns
		int cur_epoch = (conx.todo_epoch == 0) ?
				VeriConstants.FZ_INIT_VERSION - 1 : first_chunk_head.getVersion();
		List<TxnNode> chunk_heads = AssignEpochTxnNumber(epoch_list, cur_epoch);
		for (TxnNode ch : chunk_heads) {
			int ep_num = ch.getVersion();
			if (conx.chunk_heads.containsKey(ep_num)) {
				
				
				if (conx.chunk_heads.get(ep_num) != ch) {
					System.out.println("EP_NUM = " + ep_num);
					System.out.println("ch: " + ch.toString2());
					System.out.println("chunk head in conx: " + conx.chunk_heads.get(ep_num).toString2());
				}
				
				
				
				assert conx.chunk_heads.get(ep_num) == ch;
			} else {
				conx.chunk_heads.put(ep_num, ch);
			}
		}
		ChengLogger.println("Most recent chunk head = " + conx.chunk_heads.get(conx.chunk_heads.size()-1).getVersion());
		
		
		
		// 4. GC: to see if the verifier saw the GC signal from *all* clients
		if (conx.wait4signal && !conx.receivegcsignals) { // if the verifier waits for client's signal
			assert !conx.issuegctxn;
			if (conx.gc_client2sigrecv == null) {
				conx.gc_client2sigrecv = new HashMap<Integer, Integer>();
				assert conx.gc_cl_1st_sig_recv_epoch == 0;
			}
			for (TxnNode epoch_txn : epoch_list) {
				int txn_epoch = epoch_txn.getVersion();
				int clientid = epoch_txn.getClientId();
				
				if (getGCFlag(epoch_txn)) {
					if (conx.gc_cl_1st_sig_recv_epoch == 0) { // if first epoch txn that saw gc signal
						conx.gc_cl_1st_sig_recv_epoch = txn_epoch;
					}
					if (!conx.gc_client2sigrecv.containsKey(clientid)) {
						conx.gc_client2sigrecv.put(clientid, txn_epoch);
					}
				} else { // a client shouldn't have seen gc signal then unseen in this round (ASSUMPTION: sync rounds)
					if (conx.gc_client2sigrecv.containsKey(clientid)) {
						// USTBABUG: NOTE that one client may have two epoch_txn in one epoch! (so we use ">=")
						assert conx.gc_client2sigrecv.get(clientid) >= txn_epoch;
					}
				}
			}
			conx.receivegcsignals = (conx.gc_client2sigrecv.size() == this.client_list.size());
			if (conx.receivegcsignals) {
				assert conx.gc_cl_last_sig_recv_epoch == 0;
				conx.gc_cl_last_sig_recv_epoch = Collections.max(conx.gc_client2sigrecv.values());
			}
		}
		// assign epoch to gc_txn
		if (gc_txn != null) {
			int gc_epoch = getGCepoch(gc_txn, pg);
			// if previous epoch txn is null, gc_txn should not be included in the complete graph
			assert gc_epoch != VeriConstants.TXN_NULL_VERSION;
			gc_txn.setVersion(gc_epoch);
		}
		
		
		
		// 5. assing epoch to barrier txns
		for (TxnNode b : barrier_txns) {
			OpNode rop = b.getOps().get(b.getOps().size() - 1);
			assert rop.isRead && rop.key_hash == version_key;
			TxnNode prv_epoch = pg.getNode(rop.read_from_txnid);
			assert isEpochTxn(prv_epoch) && prv_epoch.getVersion() != VeriConstants.TXN_NULL_VERSION;
			b.setVersion(prv_epoch.getVersion());
		}
		
		// 6. assign epoch to normal txns
		int epoch_agree = clientVerUpdate(pg, conx);

		// check for now:
		if (VeriConstants.HEAVY_VALIDATION_CODE_ON) {
			checkEpochAssigning(pg); // NOTE: this starts from beginning
		}	
		
		return epoch_agree;
	}
	
	
	private void checkEpochAssigning(PrecedenceGraph pg) {
		Map<Integer, TxnNode> xxx = getClientHeads(pg);
		for (TxnNode cur : xxx.values()) {
			int epoch = 0;
			boolean inBarrier = false;
			TxnNode prev = null;

			while (cur != null) {
				assert cur.getVersion() == epoch || cur.getVersion() == epoch + 1;

				if (cur.getVersion() == epoch + 1) {
					assert isEpochTxn(cur) || isMonitoringTxn(cur);
					epoch = cur.getVersion();
				}

				if (isEpochTxn(cur)) {
					inBarrier = true;
				} else if (isMonitoringTxn(cur)) {
					assert inBarrier;
				} else {
					if (inBarrier) {
						assert isEpochTxn(prev) || isMonitoringTxn(prev);
					}
					inBarrier = false;
				}

				prev = cur;
				cur = pg.getNode(cur.getNextClientTxn());
			}
		}
	}
	

	// the last epoch txn in a series increases the epoch; all the prev ones are in the old epoch
	private List<TxnNode> AssignEpochTxnNumber(ArrayList<TxnNode> epoch_list, int cur_epoch) {
		assert epoch_list.size() != 0;
		int num_client = client_list.size();
		List<TxnNode> chunk_heads = new ArrayList<TxnNode>();

		// NOTE: assumption here is that the first txn in epoch_list is a start of a chunk
		Set<Integer> met_clients = new HashSet<Integer>();
		for (TxnNode e : epoch_list) {
			assert !met_clients.contains(e.getClientId()); // make sure clients follow protocol
			met_clients.add(e.getClientId()); // remember all clients' epoch txns I met
			
			// if found the last epoch txn in as series, increase epoch number (this epoch txn included)
			if (met_clients.size() == num_client) {
				cur_epoch++;
				met_clients.clear();
			} else if (met_clients.size() == 1) { // head of this chunk
				chunk_heads.add(e);
			}

			// update the epoch number
			if (e.getVersion() != VeriConstants.TXN_NULL_VERSION) {
				assert e.getVersion() == cur_epoch;
			} else {
				e.setVersion(cur_epoch);
			}
		}

		return chunk_heads;
	}
	
	private Map<Integer, TxnNode> getClientHeads(PrecedenceGraph pg) {
		Map<Integer, TxnNode> client_heads = new HashMap<Integer, TxnNode>();
		for (TxnNode n : pg.allNodes()) {
			if (n.getTxnid() == VeriConstants.INIT_TXN_ID) {continue;}
			int cid = n.getClientId();
			assert cid != VeriConstants.TXN_NULL_CLIENT_ID; // we're woring on a complete graph
			
			if (client_heads.containsKey(cid)) {
				continue;
			}

			while (n.getPrevClientTxn() != VeriConstants.NULL_TXN_ID) {
				n = pg.getNode(n.getPrevClientTxn());
			}
			client_heads.put(cid, n);
			assert cid == n.getClientId();
		}
		return client_heads;
	}

	private int clientVerUpdate(PrecedenceGraph pg, RoundContext2 conx) {
		int epoch_agree = Integer.MAX_VALUE;

		Map<Integer, TxnNode> client_last_txn = conx.client_last_txn;
		// find the source of each client
		if (client_last_txn.size() == 0) {
			client_last_txn = getClientHeads(pg);
		}
		assert client_last_txn.size() == this.client_list.size();

		for (int cid : client_last_txn.keySet()) {
			TxnNode cur = client_last_txn.get(cid);
			int ver = VeriConstants.FZ_INIT_VERSION - 1; // start from 0 for the first round
			if (cur.getVersion() != VeriConstants.TXN_NULL_CLIENT_ID) {
				ver = cur.getVersion();
			}

			//ChengLogger.print(LoggerType.DEBUG, "C=" + cid + ": ");
			do {
				if (!isEpochTxn(cur) && !isMonitoringTxn(cur)) { // if normal txn
					if (cur.getVersion() != VeriConstants.TXN_NULL_VERSION) { // if txns have been marked in prev rounds
						assert cur.getVersion() == ver;
					} else {
						cur.setVersion(ver);
					}
				} else { // if epoch txn or barrier txn
					//ChengLogger.print(LoggerType.DEBUG, "[" + (isWriteFence(cur) ? "W" : "R") + cur.getVersion() + "]");		
					assert cur.getVersion() != VeriConstants.TXN_NULL_VERSION; // all epoch txns should be marked
					assert ver == cur.getVersion() - 1 || ver == cur.getVersion(); // either the same or increasing by 1
					ver = cur.getVersion();
				}
				conx.client_last_txn.put(cid, cur); // update the 

				// find the next transaction
				if (cur.getNextClientTxn() == VeriConstants.NULL_TXN_ID) {
					cur = null;
				} else { // might be null; when the node is in "m_g"
					cur = pg.getNode(cur.getNextClientTxn());
				}
			} while (cur != null);
			//ChengLogger.println(LoggerType.DEBUG, "");
			epoch_agree = Math.min(epoch_agree, ver);

			ChengLogger.println(LoggerType.DEBUG, "C=" + cid + " last saw version is " + ver + "; agreed_Ver=" + epoch_agree);
		}

		ChengLogger.println("epoch_agree =" + epoch_agree);

		return epoch_agree;
	}
	
	private Map<Integer, TxnNode> getSafeStartersFromChunkhead(PrecedenceGraph pg, TxnNode chunkhead) {
		Map<Integer, TxnNode> safe_starters = new HashMap<Integer, TxnNode>();
		safe_starters.put(chunkhead.getClientId(), chunkhead);
		int num_clients = this.client_list.size();
		// loop for finding the chunks
		TxnNode cur_epoch = chunkhead;
		for (int i = 1; i < num_clients; i++) {
			long wid = verWid(cur_epoch);
			for (OpNode rop : pg.m_readFromMapping.get(wid)) {
				TxnNode txn = pg.getNode(rop.txnid);
				if (isEpochTxn(txn)) {
					cur_epoch = txn;
					assert !safe_starters.containsKey(cur_epoch.getClientId());
					safe_starters.put(cur_epoch.getClientId(), cur_epoch);
				}
			}
		}
		assert safe_starters.size() == num_clients;
		return safe_starters;
	}

	private List<PrecedenceGraph> GetEpochGraph(PrecedenceGraph cg, RoundContext2 conx) {
		// 1. assign epochs
		int epoch_next = AssignEpoch(cg, conx);
		assert epoch_next >= 1;

		// 2. get txns for each new epochs
		int start_epoch = conx.todo_epoch; // included
		int end_epoch = epoch_next; // excluded
		ArrayList<Set<TxnNode>> epoch_txns = new ArrayList<Set<TxnNode>>();
		TxnNode init_txn = cg.getNode(VeriConstants.INIT_TXN_ID);
		for (int i = start_epoch; i < end_epoch; i++) {
			epoch_txns.add(new HashSet<TxnNode>());
			epoch_txns.get(epoch_txns.size()-1).add(init_txn); // add INIT_TXN to all epoch graphs
		}
		
		// for all clients, loop to find corresponding epochs
		Map<Integer, TxnNode> client_starters = (start_epoch == 0) ?
				getClientHeads(cg) :
				getSafeStartersFromChunkhead(cg, conx.chunk_heads.get(start_epoch - 1));
		for (TxnNode cur_txn : client_starters.values()) {
			assert cur_txn.getVersion() == 0 || cur_txn.getVersion() <= start_epoch;
			while(cur_txn != null) {
				int n_epoch = cur_txn.getVersion();
				if (n_epoch >= start_epoch && n_epoch < end_epoch) {
					epoch_txns.get(n_epoch - start_epoch).add(cur_txn);
				}
				cur_txn = cg.getNode(cur_txn.getNextClientTxn());
			}
		}
		// add gc txn to corresponding epoch graph
		if (conx.gc_txn != null) {
			assert conx.wait4signal && conx.receivegcsignals && conx.issuegctxn;
			int gc_epoch = conx.gc_txn.getVersion();
			if (gc_epoch >= start_epoch && gc_epoch < end_epoch) {
				epoch_txns.get(gc_epoch - start_epoch).add(conx.gc_txn);
			}
		}
		ChengLogger.println("epoch considered in this round [" + start_epoch + ", " + end_epoch + ")");

		// 3. generate epoch graphs and save them
		int cur_epoch = start_epoch;
		ArrayList<PrecedenceGraph> epoch_graphs = new ArrayList<PrecedenceGraph>();
		for (Set<TxnNode> sub_txns : epoch_txns) {
			PrecedenceGraph subg = cg.subgraphNoClone(sub_txns);
			epoch_graphs.add(subg);
			System.out.printf("=====Epoch[%d]=====\n %s", cur_epoch++, subg.toString());
		}

		return epoch_graphs;
	}

	// ===== encode and solve ======

	private Set<Pair<Long, Long>> SolvePolygraph(PrecedenceGraph cg, Set<Constraint> cons) {
		// detect cycles after pruning
		if (DFSCycleDetection.hasCycleHybrid(cg.getGraph())) {
			DFSCycleDetection.PrintOneCycle(cg);
			assert false;
		}

		if (VeriConstants.HEAVY_VALIDATION_CODE_ON) {
			CheckConstraints(cg, cons);
		}

		Set<Pair<Long, Long>> solution = new HashSet<Pair<Long, Long>>();
		Set<SCCNode> suggraphs = GetIndependentClusters(cg, cons);
		Set<Pair<PrecedenceGraph, Set<Constraint>>> subpolys = GenSubgraphs(cg, cons, suggraphs);
		for (Pair<PrecedenceGraph, Set<Constraint>> poly : subpolys) {
			boolean acyclic = solveConstraints(poly.getFirst(), poly.getSecond(), solution);
			if (!acyclic) {
				assert false;
			}
		}

		// XXX: check solution

		return solution;
	}

	private Pair<Set<Constraint>, ReachabilityMatrix> EncodePolygraph(PrecedenceGraph cg, RoundContext2 conx) {
		Profiler prof = Profiler.getInstance();
		
		prof.startTick("genConsXXX");
		Set<Constraint> cons = GenConstraints(cg);
		prof.endTick("genConsXXX");
		
		prof.startTick("checkAcyc");
		// NOTE: too costly!
		if (DFSCycleDetection.hasCycleHybrid(cg.getGraph())) {
			DFSCycleDetection.PrintOneCycle(cg);
			assert false;
		}
		prof.endTick("checkAcyc");

		prof.startTick("buildRM");
		// NOTE: we need rm for deciding frontier!!
		ReachabilityMatrix rm = ReachabilityMatrix.getReachabilityMatrix(cg.getGraph(), null);
		prof.endTick("buildRM");
		
		// like TPCC, there is no constraint; check cycle on the known graph
		if (cons.size() == 0) {
			return new Pair<Set<Constraint>, ReachabilityMatrix>(new HashSet<Constraint>(), rm);
		}

		prof.startTick("addEdges");
		Map<Long, Set<Long>> new_edges2 = Prune(cg, cons, rm);
		// add to the real graph & complete graph
		for (long src : new_edges2.keySet()) {
			for (long dst : new_edges2.get(src)) {
				conx.c_g.addEdge(src, dst, EdgeType.CONS_SOLV);
				m_g.addEdge(src, dst, EdgeType.CONS_SOLV); // add edge to original graph
			}
		}
		prof.endTick("addEdges");

		ChengLogger.println("[2] #constraintsn=" + cons.size());
		ChengLogger.println("[2] epoch graph: #n=" + cg.allNodes().size());
		return new Pair<Set<Constraint>, ReachabilityMatrix>(cons, rm);
	}

	// ========= inter-epochs bookkeeping ======

	private void updateFrontier(PrecedenceGraph cg, PrecedenceGraph egraph,
			ReachabilityMatrix rm, int cur_epoch, Map<Long, Set<TxnNode>> frontier)
	{
		assert rm != null; // FIXME: for TPCC

		// loop all current writes and update frontier
		for (TxnNode n : egraph.allNodes()) {
			if (n.getTxnid() == VeriConstants.INIT_TXN_ID) {continue;} // skip init
			assert n.getStatus() == TxnType.COMMIT; // egraph is complete graph
			if (isEpochTxn(n)) {continue;} // skip the epoch txns
			assert n.getVersion() == cur_epoch;

			// check if txn is in frontier
			Set<Long> keys = writeKeys(n);
			for (Long k : keys) {
				
				// (1) one fresh key frontier
				if (!frontier.containsKey(k)) {
					frontier.put(k, new HashSet<TxnNode>());
					frontier.get(k).add(n);
					continue;
				}
				
				// (2) exist known frontier
				boolean is_new_fr = false;
				Set<TxnNode> outdated_fr = new HashSet<TxnNode>();
				for (TxnNode old_fr : frontier.get(k)) {
					if (old_fr.getVersion() == cur_epoch) {
						if (rm.reach(old_fr.getTxnid(), n.getTxnid())) { // old_fr ~-> n
							is_new_fr = true;
							outdated_fr.add(old_fr);
						} else if (rm.reach(n.getTxnid(), old_fr.getTxnid())) { // n ~-> old_fr
							// do nothing
						} else { // old_fr || n
							is_new_fr = true;
						}
					} else if (old_fr.getVersion() < cur_epoch) { // old_fr ~-> n
						is_new_fr = true;
						outdated_fr.add(old_fr);
					}
				}
				frontier.get(k).removeAll(outdated_fr);  // remove out-dated frontiers
				if (is_new_fr) {   // add new frontier txn
					frontier.get(k).add(n);
				}	
			} // end of checking key
		} // end of [B]
	}

	// check whether all reads read-from frontier; otherwise, reject
	// return <key,frontier> pairs that read a key having multiple frontiers
	private Map<Long, TxnNode> checkReadValidity(PrecedenceGraph cg, PrecedenceGraph egraph, int cur_epoch,
			Map<Long, Set<TxnNode>> frontier) {
		Map<Long, TxnNode> winners = new HashMap<Long, TxnNode>();
		for (TxnNode n : egraph.allNodes()) {
			if (isEpochTxn(n)) { continue; } // skip the epoch txns

			for (OpNode op : n.getOps()) {
				if (!op.isRead) {continue;}
				if (op.read_from_txnid == VeriConstants.INIT_TXN_ID) {continue;} // FIXME: is this true?

				TxnNode w_txn = cg.getNode(op.read_from_txnid);
				// 1. w_txn in the same epoch; continue
				// 2. w_txn in the next epoch; assert false
				// 3. w_txn in prior epochs; check frontier and remember it
				if (w_txn.getVersion() == cur_epoch) { // 1.
					continue;
				} else if (w_txn.getVersion() > cur_epoch) { // 2.
					
					System.out.println("Epoch = " + cur_epoch);
					System.out.println("this txn = " + n.toString2());
					System.out.println("read-from txn but has larger epoch [" + w_txn.getVersion() + "]\n " + w_txn.toString2());
					
					System.out.println("===========");
					TxnNode cur_txn = n;
					for (int i=0; i<10; i++) {
						cur_txn = cg.getNode(cur_txn.getNextClientTxn());
						System.out.println("--this next: " + cur_txn.toString());
					}
					
					System.out.println("===========");
					cur_txn = w_txn;
					for (int i=0; i<10; i++) {
						cur_txn = cg.getNode(cur_txn.getPrevClientTxn());
						System.out.println("--read-from prev: " + cur_txn.toString());
					}
					
					
					if (!frontier.containsKey(op.key_hash)) {
						System.out.println("======no frontier=====");
					} else {
						System.out.println("#frontier = " + frontier.get(op.key_hash).size());
						for (TxnNode f : frontier.get(op.key_hash)) {
							System.out.print("====frontier==" + f.toString2());
						}
						System.out.println("=======");
					}
					
					
					assert false;
				} else { // 3. should read-from a frontier
					assert frontier.containsKey(op.key_hash);
					if (!frontier.get(op.key_hash).contains(w_txn)) {

						System.out.println("Epoch = " + cur_epoch);
						System.out.println("read op = " + op.toString());
						System.out.println("this txn = " + n.toString2());
						System.out.println("---read-from but not frontier--- " + w_txn.toString2());
						System.out.println("#frontier = " + frontier.get(op.key_hash).size());
						System.out.println("=======");
						for (TxnNode f : frontier.get(op.key_hash)) {
							System.out.print("====frontier==" + f.toString2());
						}
						System.out.println("=======");

						assert false; // reject!
					}
					if (!winners.containsKey(op.key_hash)) {
						winners.put(op.key_hash, w_txn);
					}
					assert winners.get(op.key_hash) == w_txn; // for one key, only one w_txn is possible
				}

			} // end loop of ops
		} // end loop of txns
		return winners;
	}

	private void checkConsValidity(Map<Long, TxnNode> winners, Map<Long, Set<TxnNode>> frontier, int next_epoch, RoundContext2 conx) {
		// (1) find out which epoch has been touched (multi-frontier) and update it
		// (2) find which constraint has been updated
		// (3) update the m_g; epoch_graph
		// (4) re-solve the epoch graph

		// (1) & (2)
		// epoch => {a set of edges}
		TreeMap<Integer, Set<Pair<TxnNode, TxnNode>>> epoch_edges = new TreeMap<Integer, Set<Pair<TxnNode, TxnNode>>>();
		for (long key : winners.keySet()) {
			if (frontier.get(key).size() > 1) { // multi-frontier
				// (a) get affected epoch
				int affect_epoch = frontier.get(key).iterator().next().getVersion();
				// check all frontiers in the same epoch
				for (TxnNode fr : frontier.get(key)) {
					assert fr.getVersion() == affect_epoch;
				}
				if (!epoch_edges.containsKey(affect_epoch)) {
					epoch_edges.put(affect_epoch, new HashSet<Pair<TxnNode, TxnNode>>());
				}	
				assert affect_epoch < next_epoch;

				// (b) collect newly edges
				TxnNode recent_w = winners.get(key);
				for (TxnNode fr : frontier.get(key)) {
					if (fr != recent_w) {
						epoch_edges.get(affect_epoch).add(new Pair<TxnNode, TxnNode>(fr, recent_w));
					}
				}
				
				// (c) update the conx.frontier
				// recent write is the winner
				frontier.get(key).clear();
				frontier.get(key).add(recent_w);
			}
		}
		

		// (3) & (4)
		// NOTE: we assume [epoch == conx.epoch_graphs index]
		ChengLogger.println("-----#affected epochs=[" + epoch_edges.size() + "]----");
		int heavy_work = 0;
		for (int epoch : epoch_edges.keySet()) {
			PrecedenceGraph cg = conx.epoch_graphs.get(epoch);
			Set<Pair<Long, Long>> sol = conx.epoch_solutions.get(epoch);
			assert cg != null && sol != null;

			// update
			assert epoch_edges.get(epoch).size() > 0; // at least one multi-versioned key
			for (Pair<TxnNode, TxnNode> e : epoch_edges.get(epoch)) {
				TxnNode fr = e.getFirst();
				TxnNode to = e.getSecond();
				assert fr == cg.getNode(fr.getTxnid()) && to == cg.getNode(to.getTxnid());
				cg.addEdge(fr, to, EdgeType.CONS_SOLV);
				conx.c_g.addEdge(fr, to, EdgeType.CONS_SOLV);
				m_g.addEdge(fr, to, EdgeType.CONS_SOLV); // sync base graph as well
			}

			// solve:
			// (0) check if this epoch is marked as recheck; if so, continue
			// (1) check whether the solution works; if so, update
			// (2) if not; remember the epoch and solve it again later
			if (conx.recheck_epoch.contains(epoch)) {continue;} // (0)
				
			boolean sat_sol = satSolution(cg, sol); // (1)
			
			if (!sat_sol) { // (2)
				heavy_work++;
//				System.out.println("solution failed; do heavy work for graph[" + cg.toString() + "]");
//				Pair<Set<Constraint>, ReachabilityMatrix> res = EncodePolygraph(cg);
//				Set<Constraint> new_cons = res.getFirst();
//				Set<Pair<Long, Long>> new_sol = SolvePolygraph(cg, new_cons);
//				conx.epoch_solutions.set(epoch, new_sol);
				
				conx.recheck_epoch.add(epoch);
			}
		}

		ChengLogger.println("  do heavy checks: " + heavy_work + "/" + epoch_edges.size());
		ChengLogger.println(" DONE checking affected epochs");
	}
	
	private boolean satSolution(PrecedenceGraph epochg, Set<Pair<Long, Long>> solution) {
		PrecedenceGraph dup_graph = epochg.subgraphNoClone(epochg.allNodes()); // NOTE: new graph without cloning nodes
		for (Pair<Long, Long> sol_e : solution) {
			dup_graph.addEdge(sol_e.getFirst(), sol_e.getSecond(), EdgeType.CONS_SOLV);
		}
		boolean is_cyclic = DFSCycleDetection.hasCycleHybrid(dup_graph.getGraph());
		return !is_cyclic;  // has cycle == unsat
	}
	
	private void recheckEpochs(RoundContext2 conx, int recheck_num) {
		Set<Integer> checked = new HashSet<Integer>();
		// recheck the epochs
		for (int epoch : conx.recheck_epoch) {
			PrecedenceGraph cg = conx.epoch_graphs.get(epoch);
			Pair<Set<Constraint>, ReachabilityMatrix> res = EncodePolygraph(cg, conx);
			Set<Constraint> new_cons = res.getFirst();
			Set<Pair<Long, Long>> new_sol = SolvePolygraph(cg, new_cons);
			conx.epoch_solutions.set(epoch, new_sol); // update the solution for this epoch graph
			
			checked.add(epoch);
			if (checked.size() >= recheck_num) {
				conx.recheck_epoch.removeAll(checked);
				return;
			}
		}
		// clear
		conx.recheck_epoch.clear();
	}
	
	private void recheckEpochs(RoundContext2 conx) {
		recheckEpochs(conx, conx.recheck_epoch.size());
	}

	
	// ================
	// ====== GC ======
	// ================

	// NOTE: txn_gc is not part of graph yet!
	private int getGCepoch(TxnNode gc_txn, PrecedenceGraph cg) {
		OpNode first_op = gc_txn.getOps().get(0);
		assert first_op.isRead && first_op.key_hash == VeriConstants.VERSION_KEY_HASH;
		long prev_ep_txnid = first_op.read_from_txnid;
		if (cg.containTxnid(prev_ep_txnid)) {
			TxnNode e = cg.getNode(prev_ep_txnid);
			return e.getVersion();
		}
		return VeriConstants.TXN_NULL_VERSION;
	}

	private TxnNode getWinnerInner(PrecedenceGraph cg, long key, long wid_txnid, Set<TxnNode> candidate_txns) {
		// 1. if now_txnid is a member of candidates; return
		Set<Long> cand_txnids = new HashSet<Long>();
		for (TxnNode cand : candidate_txns) {
			if (cand.getTxnid() == wid_txnid) {
				return cand;
			}
			cand_txnids.add(cand.getTxnid());
		}
		
		// 2. we cannot find the one in candidates, then we will trace back in the graph
		TxnNode cur_txn = cg.getNode(wid_txnid);
		assert cur_txn != null;
		while(true) {
			// (a) find last write to this key from RMW
			OpNode read_in_RMW = null;
			for (OpNode op : cur_txn.getOps()) {
				if (op.isRead && op.key_hash == key) {
					read_in_RMW = op;
					break;
				}
			}
			
			
			if (read_in_RMW == null) {
				System.out.println("key = " + Long.toHexString(key));
				System.out.println("write_TXN in GC_TXN = " + cg.getNode(wid_txnid).toString2());
				TxnNode xxx = cg.getNode(wid_txnid);
				while(xxx != null) {
					OpNode xxxop = null;
					for (OpNode op : xxx.getOps()) {
						if (op.isRead && op.key_hash == key) {
							xxxop = op;
							break;
						}
					}
					xxx = cg.getNode(xxxop.read_from_txnid);
					if (xxx!=null) {
						System.out.println(" => " + xxx.toString2());
					} else {
						System.out.println(" => null");
					}
				}
				System.out.println("=======");
				for (TxnNode c : candidate_txns) {
					System.out.println("candidate = " + c.toString2());
				}
			}
			
			
			
			assert read_in_RMW != null; // clients should always do RMW in GC_monitoring
			// (b) check if this is one of the candidates
			cur_txn = cg.getNode(read_in_RMW.read_from_txnid);
			assert cur_txn != null;
			if (cand_txnids.contains(cur_txn.getTxnid())) { // found
				return cur_txn;
			}
		}
	}

	// get winners
	private Map<Long, TxnNode> getWinners(TxnNode gc_txn, Map<Long,Set<TxnNode>> gc_frontier, PrecedenceGraph cg) {
		Map<Long, TxnNode> winners = new HashMap<Long, TxnNode>();
		for (OpNode op : gc_txn.getOps()) {
			if (op.key_hash == VeriConstants.VERSION_KEY_HASH) {continue;}
			assert op.isRead;
			
			long key = op.key_hash;
			assert gc_frontier.containsKey(key) && gc_frontier.get(key).size() > 1;
			long wid_txnid = op.read_from_txnid;
			TxnNode winner = getWinnerInner(cg, key, wid_txnid, gc_frontier.get(key));
			winners.put(key, winner);
		}
		return winners;
	}
	
	// (1) go back to frontier epoch and get winners
	// (2) check cons validity and recheck affected epochs
	// (3) check that the frontier at gc_epoch have all single-versioned frontier
	// (4) get remaining txns { frontier_txns, ongoing txns, txn.epoch > gc_fr_epoch} [cheng: remove epoch txn]
	// (5) recreate m_g and c_g; wrap up conx
	private void realGC(RoundContext2 conx) {
		assert conx.wait4signal && conx.receivegcsignals && conx.issuegctxn;
		assert conx.gc_txn != null && conx.gc_frontier != null && conx.gc_veri_gc_epoch > 0;
		assert conx.gc_txn.getVersion() != VeriConstants.TXN_NULL_VERSION;
		
		// 1.
		Map<Long, TxnNode> winners = getWinners(conx.gc_txn, conx.gc_frontier, conx.c_g);
		
		// 2.
		checkConsValidity(winners, conx.gc_frontier, conx.gc_veri_gc_epoch + 1, conx); // frontier contains gc_frontier_epoch
		recheckEpochs(conx);
		
		
		// 3.
		for (Set<TxnNode> txns : conx.gc_frontier.values()) {
			assert txns.size() == 1;
		}
		ChengLogger.println("[GC] done checking gc epoch [" + conx.gc_veri_gc_epoch + "] frontier; single-valued!");
		
		// 4. remaining txns = { frontier_txns, epoch_txns (?), ongoing txns, txn.epoch > gc_epoch}
		// (a) frontier
		Set<TxnNode> mg_remaining_txns = new HashSet<TxnNode>();
		Set<TxnNode> cg_remaining_txns = new HashSet<TxnNode>();
		for (long key : conx.frontier.keySet()) {
			int fr_epoch = conx.frontier.get(key).iterator().next().getVersion();
			if (fr_epoch <= conx.gc_veri_gc_epoch) {
				assert conx.frontier.get(key).size() == 1; // must be single-versioned
				mg_remaining_txns.add(conx.frontier.get(key).iterator().next());
				cg_remaining_txns.add(conx.frontier.get(key).iterator().next());
			}
		}
		
		// (b) epoch_txn, ongoing,  txn.epoch > gc_epoch
		int num_del = 0;
		for (TxnNode n : this.m_g.allNodes()) {
			if (n.getStatus() != TxnType.COMMIT ||
					n.getVersion() > conx.gc_veri_gc_epoch ||
					n.getVersion() == VeriConstants.TXN_NULL_VERSION)
			{
				long txnid = n.getTxnid();
				mg_remaining_txns.add(n);
				if (conx.c_g.containTxnid(txnid)) {
					cg_remaining_txns.add(n);
				}
			} else {
				
				if (!(n.getVersion() >= 0 && n.getVersion() <= conx.gc_veri_gc_epoch)) {
					System.out.println("epoch = " + n.getVersion() + "; gc_frontier_epoch = " + conx.gc_veri_gc_epoch);
					System.out.println(n.toString2());
				}
				
				
				
				assert n.getVersion() >= 0 && n.getVersion() <= conx.gc_veri_gc_epoch;
				num_del++;
			}
		}
		ChengLogger.println("[GC] delete #txn = " + num_del + "/" + this.m_g.allNodes().size());
		
		// NOTE: before delete anything, make sure that the known graph was acyclic
		// this is an optimization for AbstractLogVerifier:updateEdges()
		if (DFSCycleDetection.hasCycleHybrid(conx.c_g.getGraph())) {
			DFSCycleDetection.PrintOneCycle(conx.c_g);
			assert false; // reject
		}
		
		// 4.
		// NOTE: must use NoClone because many places use ref comparison for TxnNode
		this.m_g = this.m_g.subgraphNoClone(mg_remaining_txns);
		conx.c_g = conx.c_g.subgraphNoClone(cg_remaining_txns);
		assert conx.recheck_epoch.size() == 0;
		for (int i = 0; i <= conx.gc_veri_gc_epoch; i++) {
			conx.epoch_graphs.set(i, null);
			conx.epoch_solutions.set(i, null);
		}
	}
	

	// =============

	// NOTE: design choice; no clone of graph

	public boolean continueslyAudit() {
		// need to have TTT for running truncation
		assert VeriConstants.BUNDLE_CONSTRAINTS && VeriConstants.WW_CONSTRAINTS && VeriConstants.INFER_RELATION_ON;

		Profiler prof = Profiler.getInstance();
		RoundContext2 conx = new RoundContext2();

		do {
			Random rand = new Random();
			conx.rounds++;
			this.ClearCounters();
			prof.clear();

			ChengLogger.println("======ROUND[" + conx.rounds + "]=======");
			prof.startTick("ROUND" + conx.rounds);

			// =============1. Create Known Graph================
			prof.startTick("part1.1");
			PrecedenceGraph cg = CreateKnownGraph(conx);
			prof.endTick("part1.1");
			
			prof.startTick("part1.2");
			List<PrecedenceGraph> epoch_graphs = GetEpochGraph(cg, conx);
			if (this.new_txns_this_turn.size() == 0) { // end of logs
				recheckEpochs(conx);
				return true;
			}
			assert this.new_txns_this_turn.size() > 0;
			prof.endTick("part1.2");

			// =============2. Encode And Solve================
			prof.startTick("part2");
			for (PrecedenceGraph egraph : epoch_graphs) {
				prof.startTick("part2.1");
				// (1) check current epoch graph is SER
				Pair<Set<Constraint>, ReachabilityMatrix> res = EncodePolygraph(egraph, conx);
				Set<Constraint> cons = res.getFirst();
				conx.recent_rm = res.getSecond();
				prof.endTick("part2.1");

				prof.startTick("part2.2");
				Set<Pair<Long, Long>> solution = SolvePolygraph(egraph, cons);
				// update context with new epoch graph and cons
				conx.epoch_graphs.add(egraph); // have a graph without cloning node
				conx.epoch_solutions.add(solution);
				prof.endTick("part2.2");

				prof.startTick("part2.3");
				// (2) all the reads read-from "frontier"
				Map<Long, TxnNode> winners = checkReadValidity(cg, egraph, conx.todo_epoch, conx.frontier);
				prof.endTick("part2.3");

				prof.startTick("part2.4");
				// (3) reads from prior epoch do not affect their SER
				checkConsValidity(winners, conx.frontier, conx.todo_epoch, conx); // frontier contains todo_epoch - 1
				prof.endTick("part2.4");

				prof.startTick("part2.5");
				// (4) bookkeeping
				updateFrontier(cg, egraph, conx.recent_rm, conx.todo_epoch, conx.frontier); // frontier contains todo_epoch
				conx.todo_epoch++;  // frontier contains todo_epoch - 1
				prof.endTick("part2.5");
			}
			assert conx.todo_epoch == conx.epoch_graphs.size();
			prof.endTick("part2");
			
			// =============3. Wrap Up================
			// after recheck, to see which can be deleted
			prof.startTick("part3");
			// an optimization: 50% chance to recheck some of the cached epochs
			if (rand.nextInt(2) == 1) {
				prof.startTick("recheck");
				int recheck_num = (conx.recheck_epoch.size() > 2) ? 2 : conx.recheck_epoch.size();
				recheckEpochs(conx, recheck_num);
				prof.endTick("recheck");
				
//				prof.startTick("delete");
//				Set<Integer> untouched = getUntouchedEpochs(conx);
//				deleteUntouchedEpochs(m_g, conx, untouched);
//				prof.endTick("delete");
			}
			prof.endTick("part3");
			
			// UTBABUG: deleted_txnids is used to skip txns during the CreateGraph!!
			// We must keep update it; otherwise, there are weird behaviors.
			// deleted_txnids.addAll(del_txns);
			conx.txn_handled += this.new_txns_this_turn.size();
			new_txns_this_turn.clear(); // gc_txn will add later
			
			
			// =============4. GC ================
			prof.startTick("part4");
			if (this.remote_log) {
				ChengLogger.println(" [BEFORE] GC state (wait4signal, receive_gc_signal, get_gc_txn) = [" 
						+ conx.wait4signal + "," + conx.receivegcsignals + "," + conx.issuegctxn + "]");
				ChengLogger.println(" [BEFORE] GC epoch (veri_sig, cl_1st_sig_recv, cl_last_sig_recv, veri_gc, cl_gc_epoch, current_epoch) = [" 
						+ conx.gc_veri_signal_epoch + "," + conx.gc_cl_1st_sig_recv_epoch + "," + conx.gc_cl_last_sig_recv_epoch + "," +  conx.gc_veri_gc_epoch
						+ "," + conx.gc_cl_gctxn_epoch + "," + (conx.todo_epoch-1) + "]");
				
				GarbageCollector gc = GarbageCollector.getInstance();
				if (!conx.wait4signal) {  // (1) idle mode
					assert !conx.receivegcsignals && !conx.issuegctxn;
					// check if it is time for next round of GC
					if (conx.todo_epoch > conx.last_gc_epoch + VeriConstants.GC_EPOCH_THRESHOLD &&
							conx.todo_epoch - 1 > conx.gc_cl_gctxn_epoch) // avoid GC overlapping
					{
						gc.signalGC();
						conx.wait4signal = true;
						conx.gc_veri_signal_epoch = conx.todo_epoch - 1;
					}
				} else if (conx.wait4signal && !conx.receivegcsignals) {
					// (2) verifier signal GC but hasn't seen clients starting GCing
					// wait and do nothing
					assert !conx.issuegctxn;
				} else if (conx.wait4signal && conx.receivegcsignals && !conx.issuegctxn) {
					// (3) verifier received signal from clients
					// check if we can issue GC_txn: the frontier must be after the last-being-gc-client
					if (conx.todo_epoch - 1 >= conx.gc_cl_last_sig_recv_epoch) {
						assert conx.gc_txn == null;
						conx.gc_veri_gc_epoch = conx.todo_epoch - 1; // frontier contains (todo_epoch - 1)
						conx.gc_txn = gc.doGC(conx.c_g, conx.frontier, conx.gc_veri_gc_epoch);
						conx.issuegctxn = true;
						conx.gc_frontier = new HashMap<Long, Set<TxnNode>>(); // deep clone of frontier
						for (long key : conx.frontier.keySet()) {
							conx.gc_frontier.put(key, new HashSet<TxnNode>(conx.frontier.get(key)));
						}

						// gc_txn should be in a future epoch (gc_txn.epoch == todo_epoch || gc_txn.epoch == NULL)
						long gc_epoch = getGCepoch(conx.gc_txn, conx.c_g);
						assert gc_epoch >= conx.todo_epoch || gc_epoch == VeriConstants.TXN_NULL_VERSION;
						// add gc_txn to graph
						m_g.addTxnNode(conx.gc_txn);
						new_txns_this_turn.add(conx.gc_txn); // gc_txn will be linked to graph naturally next round
					}
				} else if (conx.wait4signal && conx.receivegcsignals && conx.issuegctxn) {
					// (4) check and do real gc
					int gc_epoch = getGCepoch(conx.gc_txn, conx.c_g);
					if (gc_epoch > 0 && gc_epoch < conx.todo_epoch) { // if we've passed gc's epoch
						conx.gc_cl_gctxn_epoch = gc_epoch;
						realGC(conx); // recreate m_g and conx.c_g within
						
						// wrap up verifier's GC states
						conx.last_gc_epoch = conx.gc_veri_gc_epoch;
						conx.wait4signal = false; // clear GC status
						conx.receivegcsignals = false;
						conx.issuegctxn = false;
						conx.gc_veri_signal_epoch = 0;
						conx.gc_cl_1st_sig_recv_epoch = 0;
						conx.gc_cl_last_sig_recv_epoch = 0;
						conx.gc_veri_gc_epoch = 0;
						conx.gc_txn = null;
						conx.gc_client2sigrecv = null;
						conx.gc_frontier = null;
						ChengLogger.println("[GC] done gc");
					} else {
						ChengLogger.println("[GC] haven't seen gc txn in the history");
					}
				} else {
					ChengLogger.print(LoggerType.ERROR,
							"Verifier has WRONG states about GC (wait4signal, receive_gc_signal, get_gc_txn) = ["
							+ conx.wait4signal + "," + conx.receivegcsignals + "," + conx.issuegctxn + "]");
					assert false;
				}
				
				ChengLogger.println(" [AFTER] GC state (wait4signal, receive_gc_signal, get_gc_txn) = [" 
						+ conx.wait4signal + "," + conx.receivegcsignals + "," + conx.issuegctxn + "]");
				ChengLogger.println(" [AFTER] GC epoch (veri_sig, cl_1st_sig_recv, cl_last_sig_recv, veri_gc, cl_gc_epoch, current_epoch) = [" 
						+ conx.gc_veri_signal_epoch + "," + conx.gc_cl_1st_sig_recv_epoch + "," + conx.gc_cl_last_sig_recv_epoch + "," +  conx.gc_veri_gc_epoch
						+ "," + conx.gc_cl_gctxn_epoch + "," + (conx.todo_epoch-1) + "]");
			}
			prof.endTick("part4");


			prof.endTick("ROUND" + conx.rounds);
			ChengLogger.println("=======ROUND[" + conx.rounds + "]======");
			ChengLogger.println("#handled txn = " + conx.txn_handled);
			ChengLogger.println("-total: " + prof.getTime("ROUND" + conx.rounds) + " ms");
			ChengLogger.println("  --part 1: " + (prof.getTime("part1.1") + prof.getTime("part1.2")) + " ms");
			ChengLogger.println("    ---part 1.1: " + prof.getTime("part1.1") + " ms");
			ChengLogger.println("      ---- load file: " + prof.getTime("loadfile") + " ms");
			ChengLogger.println("        ---- read from log: " + prof.getTime("loadfile1") + " ms");
			ChengLogger.println("        ---- update edges: " + prof.getTime("loadfile2") + " ms");
			ChengLogger.println("      ---- check val: " + prof.getTime("checkvalue") + " ms");
			ChengLogger.println("      ---- get incom: " + prof.getTime("getincomplete") + " ms");
			ChengLogger.println("      ---- get compl: " + prof.getTime("getcomplete") + " ms");
			ChengLogger.println("    ---part 1.2: " + prof.getTime("part1.2") + " ms");
			ChengLogger.println("  --part 2: " + prof.getTime("part2")  + " ms");
			ChengLogger.println("    ---part 2.1: " + prof.getTime("part2.1") + " ms");
			ChengLogger.println("      ---gen cons:   " + prof.getTime("genConsXXX") + " ms");
			ChengLogger.println("      ---acyc graph: " + prof.getTime("checkAcyc") + " ms");
			ChengLogger.println("      ---build RM: " + prof.getTime("buildRM") + " ms");
			ChengLogger.println("      ---add edge: " + prof.getTime("addEdges") + " ms");
			ChengLogger.println("    ---part 2.2: " + prof.getTime("part2.2") + " ms");
			ChengLogger.println("    ---part 2.3: " + prof.getTime("part2.3") + " ms");
			ChengLogger.println("    ---part 2.4: " + prof.getTime("part2.4") + " ms");
			ChengLogger.println("    ---part 2.5: " + prof.getTime("part2.5") + " ms");
			ChengLogger.println("  --part 3: " + prof.getTime("part3")  + " ms");
			ChengLogger.println("    ---recheck : " + prof.getTime("recheck") + " ms");
			ChengLogger.println("  --part 4: " + prof.getTime("part4")  + " ms");
			prof.clear();

		} while (true);
	}


}
