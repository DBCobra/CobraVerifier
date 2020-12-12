package verifier;

import gpu.ReachabilityMatrix;
import graph.EdgeType;
import graph.OpNode;
import graph.PrecedenceGraph;
import graph.TxnNode;
import graph.TxnNode.TxnType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import algo.DFSCycleDetection;
import algo.TarjanStronglyConnectedComponents;

import com.google.api.Endpoint;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

import util.ChengLogger;
import util.Pair;
import util.Profiler;
import util.VeriConstants;
import util.VeriConstants.LoggerType;
import verifier.MonoSATVerifierSyncEpoch.RoundContext2;
import monosat.*;
import static monosat.Logic.*;

public class MonoSATVerifierRounds extends AbstractLogVerifier {

	ExecutorService executor; 
	public MonoSATVerifierRounds(String logfd) {
	  super(logfd);
  }
	
	
	
	
	// ==============================
	// ===== main logic =============
  // ==============================
	
	protected Set<Constraint> GenConstraints(PrecedenceGraph g) {
		Map<Long, Set<List<TxnNode>>> chains = new HashMap<Long, Set<List<TxnNode>>>(); // key -> Set<List>
		Map<Long,Long> wid2txnid = new HashMap<Long,Long>();
		Map<Long,Long> wid2key = new HashMap<Long,Long>();
		
		// each key maps to set of chains; each chain is an ordered list
		for (TxnNode tx : g.allNodes()) {
			for (OpNode op : tx.getOps()) {
				if (!op.isRead) {
					Long key = op.key_hash;
					if (!chains.containsKey(key)) {
						chains.put(key, new HashSet<List<TxnNode>>());
					}
					List<TxnNode> singleton_list = new ArrayList<TxnNode>();
					singleton_list.add(tx);
					chains.get(key).add(singleton_list);
					// wid => txnid
					assert !wid2txnid.containsKey(op.wid);
					wid2txnid.put(op.wid, op.txnid);
					wid2key.put(op.wid, op.key_hash);
				}
			}
		}
		
		CombineWrites(chains, g.getWWpairs(), wid2txnid, wid2key);
		
		// construct the constraints
		Set<Constraint> cons = new HashSet<Constraint>();
		for (Long key : chains.keySet()) {
			// skip fence txs
			if (key == VeriConstants.VERSION_KEY_HASH) {continue;}
			
			// take care of init tx, that chain happens before all the other chains
			List<TxnNode> init_chain = null;
			for (List<TxnNode> chain : chains.get(key)) {
				if (chain.get(0).getTxnid() == VeriConstants.INIT_TXN_ID) {
					init_chain = chain;
				}
			}
			if (init_chain != null) {
				for (List<TxnNode> chain : chains.get(key)) {
					if (chain != init_chain) {
						g.addEdge(init_chain.get(init_chain.size()-1), chain.get(0), EdgeType.CONS_SOLV);
					}
				}
			}
			
			// create a constraint for each pair of chains
			List<List<TxnNode>> chainset = new ArrayList<List<TxnNode>>(chains.get(key));
			if (init_chain != null) {
				chainset.remove(init_chain); // init_chain is ahead of any other chains; no need to generate constraints
			}
			// tag whether a chain is frozen (frozen if all the txs are frozen)
			Map<Integer, Boolean> chain_frozen = new HashMap<Integer,Boolean>();
			for (int i=0; i<chainset.size(); i++) {
				boolean frozen = true;
				for (TxnNode tx : chainset.get(i)) {
					if (!tx.frozen) {
						frozen = false; break;
					}
				}
				chain_frozen.put(i, frozen);
			}
			
			// generate constraints from a pair of chains
			int len = chainset.size();
			for (int i = 0; i < len; i++) {
				for (int j = i + 1; j < len; j++) {
					// if both are frozen; we can skip this (because they've been tested before)
					if (chain_frozen.get(i) && chain_frozen.get(j)) {
						continue;
					}
					// UTBABUG: if chain[i] ~-> chain[j], it doesn't mean we can skip the constraint,
					// because the reachabilities we know might not include what read-txns  should be placed!
					Constraint con = Coalesce(chainset.get(i), chainset.get(j), key, g.m_readFromMapping, wid2txnid);
					cons.add(con);
				}
			}
		}
		
		return cons;
	}
	
	private Constraint Coalesce(List<TxnNode> chain_1, List<TxnNode> chain_2, Long key,
			Map<Long, Set<OpNode>> readfrom, Map<Long, Long> wid2txnid)
	{
		Set<Pair<Long, Long>> edge_set1 = GenChainToChainEdge(chain_1, chain_2, key, readfrom, wid2txnid);
		Set<Pair<Long, Long>> edge_set2 = GenChainToChainEdge(chain_2, chain_1, key, readfrom, wid2txnid);
		return new Constraint(edge_set1, edge_set2, chain_1, chain_2);
	}

	private Set<Pair<Long, Long>> GenChainToChainEdge(List<TxnNode> chain_1, List<TxnNode> chain_2, Long key,
			Map<Long, Set<OpNode>> readfrom, Map<Long, Long> wid2txnid) {
		Long tail_1_wid = null;
		for (OpNode op : chain_1.get(chain_1.size()-1).getOps()) {
			if (!op.isRead && op.key_hash == key) {
				tail_1_wid = op.wid;
			}
		}
		assert tail_1_wid != null;
		
		Long tail_1 = chain_1.get(chain_1.size()-1).getTxnid();
		Long head_2 = chain_2.get(0).getTxnid();
		
		Set<Pair<Long,Long>> ret = new HashSet<Pair<Long,Long>>();
		if (!readfrom.containsKey(tail_1_wid)) {
			ret.add(new Pair<Long,Long>(tail_1, head_2));
			return ret;
		}
		
		assert readfrom.get(tail_1_wid).size() > 0;
		for (OpNode op : readfrom.get(tail_1_wid)) {
			Long rtx = op.txnid;
			ret.add(new Pair<Long,Long>(rtx, head_2));
		}
		return ret;
	}

	private void CombineWrites(Map<Long, Set<List<TxnNode>>> chains, Map<Long, Long> wwpairs, Map<Long,Long> wid2txnid, Map<Long,Long> wid2key) {
		for (Long src_wid : wwpairs.keySet()) {
			Long dst_wid = wwpairs.get(src_wid);
			Long src_txnid = wid2txnid.get(src_wid);
			Long dst_txnid = wid2txnid.get(dst_wid);
			Long key = wid2key.get(src_wid);
			if (key == null) {
				// UTBABUG: this means prev write txn hasn't been seen by the verifier
				// skip this WW then.
				continue;
			}
			assert key.equals(wid2key.get(dst_wid));
			
			List<TxnNode> chain_1 = null, chain_2 = null;
			Set<List<TxnNode>> key_chains = chains.get(key);
			for (List<TxnNode> chain : key_chains) {
				if (chain.get(chain.size()-1).getTxnid() == src_txnid) {
					assert chain_1 == null;
					chain_1 = chain;
				}
				if (chain.get(0).getTxnid() == dst_txnid) {
					assert chain_2 == null;
					chain_2 = chain;
				}
			}
			assert chain_1 != null && chain_2 != null;
			// concate two chains
			key_chains.remove(chain_1);
			key_chains.remove(chain_2);
			chain_1.addAll(chain_2);
			key_chains.add(chain_1);
		}
		
	}
	
	protected Map<Long,Set<Long>> Prune(PrecedenceGraph g, Set<Constraint> cons, ReachabilityMatrix rm) {
		ChengLogger.println(" -- Before PRUNE #constraint[1] = " + cons.size());
		Profiler prof = Profiler.getInstance();
		Map<Long,Set<Long>> ret = new HashMap<Long,Set<Long>>();
		
		for (int i = 0; i < VeriConstants.MAX_INFER_ROUNDS; i++) {			
			// FIXME: can use multi-threading
			List<Pair<Long,Long>> new_edges = PruneConstraintsInner(g, cons, rm);
			if (new_edges.size() == 0) break;
			
			prof.startTick(VeriConstants.PROF_SOLVE_CONSTRAINTS3);
			// update the graph
			for (Pair<Long, Long> e : new_edges) {
				g.addEdge(e.getFirst(), e.getSecond(), EdgeType.CONS_SOLV);
			}
			
			// update matrix
			int counter = 0;
			Long[] src_list = new Long[new_edges.size()];
			Long[] dst_list = new Long[new_edges.size()];
			for (Pair<Long,Long> ww : new_edges) {
				long src_txnid = ww.getFirst();
				long dst_txnid = ww.getSecond();
				src_list[counter] = src_txnid;
				dst_list[counter] = dst_txnid;
				counter++;
				// add to ret
				if (!ret.containsKey(src_txnid)) {
					ret.put(src_txnid, new HashSet<Long>());
				}
				ret.get(src_txnid).add(dst_txnid);
			}

			ChengLogger.println("    SolveConstraints, gpu connect ---->");
			rm.connect(src_list, dst_list);
			ChengLogger.println("    ----->  gpu connect done");

			prof.endTick(VeriConstants.PROF_SOLVE_CONSTRAINTS3);
		}
		ChengLogger.println(" -- After PRUNE #constraint[2] = " + cons.size());
		
		return ret;
	}
	
	private List<Pair<Long,Long>> PruneConstraintsInner(PrecedenceGraph g, Set<Constraint> cons, ReachabilityMatrix rm) {
		Map<Long, Set<Long>> new_edges = new HashMap<Long,Set<Long>>();
		Profiler prof = Profiler.getInstance();
		
		prof.startTick(VeriConstants.PROF_SOLVE_CONSTRAINTS1);
		Set<Constraint> resolved_cons = new HashSet<Constraint>();
		boolean found_conflict = false;
		for (Constraint con : cons) {
			// edge-based pruning
			/* -1: choose no one; 1: choose set 1; 2: choose set 2 */
			int choose = -1;
			for (Pair<Long,Long> e : con.edge_set1) {
				int i2j = isItoJ(g.getNode(e.getFirst()), g.getNode(e.getSecond()), rm);
				if (i2j == 1) { // edge in set 1 satisfies
					//assert choose != 2;
					if (choose == 2) {found_conflict = true; break;}
					choose = 1;
				} else if (i2j == 2) {  // edge in set 1 conflicts
					//assert choose != 1;
					if (choose == 1) {found_conflict = true; break;}
					choose = 2;
				} else {
					assert i2j == 0;
					// check if they have direct edge
					TxnNode node_i = g.getNode(e.getFirst());
					TxnNode node_j = g.getNode(e.getSecond());
					if (g.successors(node_i).contains(node_j)) { // i->j
						//assert choose != 2;
						if (choose == 2) {found_conflict = true; break;}
						choose = 1;
					} else if (g.predecessors(node_i).contains(node_j)) { // j->i
						//assert choose != 1;
						if (choose == 1) {found_conflict = true; break;}
						choose = 2;
					}
				}
			}
			
			for (Pair<Long,Long> e : con.edge_set2) {
				if (found_conflict) {break;} // if there is already conflict, we skip this part
				
				int i2j = isItoJ(g.getNode(e.getFirst()), g.getNode(e.getSecond()), rm);
				if (i2j == 1) { // edge in set 2 satisfies
					//assert choose != 1;
					if (choose == 1) {found_conflict = true; break;}
					choose = 2;
				} else if (i2j == 2) { // edge in set 2 conflicts		
					//assert choose != 2;
					if (choose == 2) {found_conflict = true; break;}
					choose = 1;
				} else {
					assert i2j == 0;
					// check if they have direct edge
					TxnNode node_i = g.getNode(e.getFirst());
					TxnNode node_j = g.getNode(e.getSecond());
					if (g.successors(node_i).contains(node_j)) { // i->j
						// assert choose != 1;
						if (choose == 1) {found_conflict = true; break;}
						choose = 2;
					} else if (g.predecessors(node_i).contains(node_j)) { // j->i
						//assert choose != 2;
						if (choose == 2) {found_conflict = true; break;}
						choose = 1;
					}
				}
			}
			
			// if we found violation; we report immediately.	
			if (found_conflict) {
				ChengLogger.println(LoggerType.ERROR, "=====Found conflict when pruning constraint====");
				ChengLogger.println(LoggerType.ERROR, con.toString(g, true));
				ChengLogger.println(LoggerType.ERROR, "================================");
				// we got a violation.
				// add the edges from one side and detect cycles
				for (Pair<Long,Long> e2 : con.edge_set2) {
					g.addEdge(e2.getFirst(), e2.getSecond(), EdgeType.CONS_SOLV);
				}
				boolean hasCycle = Graphs.hasCycle(g.getGraph());
				assert hasCycle;
				DFSCycleDetection.PrintOneCycle(g);
				assert false;
			}
		
			if (choose == 1) {
				Add2NewEdges(con.edge_set1, new_edges);
				resolved_cons.add(con);
			} else if (choose == 2) {
				Add2NewEdges(con.edge_set2, new_edges);
				resolved_cons.add(con);
			} else {
				assert choose == -1;
			}
		}
		prof.endTick(VeriConstants.PROF_SOLVE_CONSTRAINTS1);
		
		cons.removeAll(resolved_cons);
		
		List<Pair<Long,Long>> ret = new LinkedList<Pair<Long,Long>>();
		for (Long src : new_edges.keySet()) {
			for (Long dst : new_edges.get(src)) {
				// do NOT add edges that are already known by the RM and the (path) graph
				if (rm.reachUnsafe(src, dst) != 1) {
					ret.add(new Pair<Long,Long>(src, dst));
				}
			}
		}
		
		//ChengLogger.println("   ----> Inner PRUNE #constraint = " + cons.size());
		ChengLogger.println("   ----> Inner PRUNE #solved_cons = " + resolved_cons.size());
		//ChengLogger.println("   ----> Inner PRUNE #new_edges = " + ret.size());
		return ret;
	}
	
	private void Add2NewEdges(Set<Pair<Long,Long>> edge_set, Map<Long, Set<Long>> new_edges) {
		for (Pair<Long,Long> e : edge_set) {
			Long src = e.getFirst();
			Long dst = e.getSecond();
			if (!new_edges.containsKey(src)) {
				new_edges.put(src, new HashSet<Long>());
			}
			new_edges.get(src).add(dst);
		}
	}
	
	// =============================
	// ======= safe deletion =======
  // =============================


	protected PrecedenceGraph GetSuperpositionGraph(PrecedenceGraph m_g, Set<Constraint> cons) {
		PrecedenceGraph spg = new PrecedenceGraph(m_g, false);
		for (Constraint con : cons) {
			for (Pair<Long,Long> e : con.edge_set1) {
				spg.addEdge(e.getFirst(), e.getSecond(), EdgeType.CONS_SOLV);
			}
			for (Pair<Long,Long> e : con.edge_set2) {
				spg.addEdge(e.getFirst(), e.getSecond(), EdgeType.CONS_SOLV);
			}
		}
		return spg;
	}
	
	private void SetFrozen(PrecedenceGraph g, RoundContext conx) {
		// 1. build a scc graph
		MutableGraph<SCCNode> scc_g = GraphBuilder.directed().allowsSelfLoops(false).build();	
		for (SCCNode scc : conx.nonfrozen_scc_list) {
			assert scc.frozen == false;
			scc_g.addNode(scc);
		}

		// add edgse to scc_g
		// FIXME: performance issue, can be faster
		for (EndpointPair<TxnNode> e : g.allEdges()) {
			long src_id = e.source().getTxnid();
			long dst_id = e.target().getTxnid();
			if (src_id == VeriConstants.INIT_TXN_ID) {continue;} // skip init txn
			
			SCCNode scc1 = conx.tid2scc.get(src_id);
			SCCNode scc2 = conx.tid2scc.get(dst_id);
			if (scc1 != scc2) {
				if (!scc1.frozen && !scc2.frozen) {
					scc_g.putEdge(scc1, scc2);
				} else {
					// some CHECKS: either both frozen, or the source is frozen
					assert (scc1.frozen && scc2.frozen) ||
					       (scc1.frozen && !scc2.frozen);
				}
			}
		}
		assert !Graphs.hasCycle(scc_g);
		ChengLogger.println("  #no_future_scc=" + conx.nonfrozen_scc_list.size());
		
		// 2. Now, all the sccs in the graph are non-frozen
		// Start to set the frozen flag
		Set<SCCNode> cur_frozen_scc = new HashSet<SCCNode>();
		for (SCCNode scc : scc_g.nodes()) {
			assert scc.frozen == false; // scc in scc graph should be non-frozen
			boolean frozen = true;
			for (TxnNode tx : scc.txns) {
				if (tx.getVersion() > conx.epoch_agree - 2) {
					frozen = false;
					break;
				}
			}
			scc.frozen = frozen;
			if (scc.frozen) {
				cur_frozen_scc.add(scc	);
			}
		}
	
		// 3. broadcast non-frozen signal to successor sccs
		Set<SCCNode> zero_ins = new HashSet<SCCNode>();
		for (SCCNode scc : scc_g.nodes()) {
			if (scc_g.inDegree(scc) == 0) {
				zero_ins.add(scc);
			}
		}
		Set<SCCNode> opt_frozen_sccs = new HashSet<SCCNode>(cur_frozen_scc);
		while(zero_ins.size() > 0) {
			SCCNode cur = zero_ins.iterator().next();
			
			if (cur.frozen == false) {
				cur_frozen_scc.remove(cur); // remove from the frozen list
			}
			
			for (SCCNode succ : scc_g.successors(cur)) {
				if (cur.frozen == false) { // broadcast if cur is not frozen
					succ.frozen = false;
				}
				if (scc_g.inDegree(succ) == 1) { // will be zero-indegree after removing cur
					zero_ins.add(succ);
				}
			}
			scc_g.removeNode(cur);
			zero_ins.remove(cur);
			// OPT: if we've done on deciding frozen scc; stop
			opt_frozen_sccs.remove(cur);
			if (opt_frozen_sccs.size() == 0) {
				break;
			}
		}

		// 4. set frozen flag to transactions in frozen scc
		int counter_fz = 0;
		for (SCCNode scc : cur_frozen_scc) {
			assert scc.frozen == true;
			for (TxnNode tx : scc.txns) {
				assert tx.getStatus() == TxnType.COMMIT;
				assert tx.frozen == false; // this scc is a new frozen scc
				tx.frozen = true;
				counter_fz++;
			}
		}
		
		// 5. update conx's frozen/nonfrozen sccs
		conx.frozen_sccs.addAll(cur_frozen_scc);
		conx.nonfrozen_scc_list.removeAll(cur_frozen_scc);
		
		ChengLogger.println("[3]  #frozen=" + counter_fz);
	}


	public boolean isFence(TxnNode t) {
		if (t.getOps().size() > 0 &&
				t.getOps().get(0).isRead &&
				t.getOps().get(0).key_hash == VeriConstants.VERSION_KEY_HASH)
		{
			return true;
		}
		return false;
	}
	
	private boolean isWriteFence(TxnNode t) {
		if (t.getOps().size() == 2 &&
				t.getOps().get(0).isRead &&
				!t.getOps().get(1).isRead &&
				t.getOps().get(0).key_hash == VeriConstants.VERSION_KEY_HASH &&
				t.getOps().get(1).key_hash == VeriConstants.VERSION_KEY_HASH)
		{
			return true;
		}
		return false;
	}
	
	private long verWid(TxnNode t) {
		assert t.getOps().size() == 2;
		assert t.getOps().get(1).isRead == false;
		assert t.getOps().get(1).key_hash == VeriConstants.VERSION_KEY_HASH;
		return t.getOps().get(1).wid;
	}
	
	private Set<Long> writeKeys(TxnNode txn) {
		Set<Long> keys = new HashSet<Long>();
		for (OpNode op : txn.getOps()) {
			if (!op.isRead) {
				keys.add(op.key_hash);
			}
		}
		return keys;
	}
	
	private long getWid(TxnNode txn, long key) {
		for (OpNode op : txn.getOps()) {
			if (!op.isRead && op.key_hash == key) {
				return op.wid;
			}
		}
		assert false;
		return -1L;
	}
	
	protected int AssignEpoch(PrecedenceGraph cg, RoundContext conx) {
		long version_key = VeriConstants.VERSION_KEY_HASH;
		
		// 1. find head wfence for this round
		TxnNode wfence_head = null;
		if (conx.rounds == 1) { // first round
			for (TxnNode txn : cg.allNodes()) {
				if (isFence(txn) && isWriteFence(txn) && txn.get(0).wid == version_key) {
					wfence_head = txn;
				}
			}
		} else { // get epoch_agree txn
			wfence_head = conx.wfences.get(conx.last_epoch_agree - 1);
			assert wfence_head.getVersion() == conx.last_epoch_agree;
		}	
		assert wfence_head != null;
		
		// 2. traverse through the WR-edges find all following wfence txns and rfence txns
		ArrayList<TxnNode> wfence_list = new ArrayList<TxnNode>();
		Set<TxnNode> rfence_txns = new HashSet<TxnNode>();
		TxnNode cur_txn = wfence_head;
		while (true) {
			wfence_list.add(cur_txn); // add current epoch txn to list
			long cur_wid = verWid(cur_txn);
			if (!cg.m_readFromMapping.containsKey(cur_wid)) { // (i) no successors; quit
				break;
			}
			cur_txn = null;
			for (OpNode rop : cg.m_readFromMapping.get(cur_wid)) {
				TxnNode t = cg.getNode(rop.txnid);
				assert isFence(t);
				if (isWriteFence(t)) { // NOTE: treat gc_txn as barrier
					assert cur_txn == null; // there is only one successor epoch txn
					cur_txn = t;
				} else {
					rfence_txns.add(t);
				}
			}
			if (cur_txn == null) { // (ii) no successor wfence; quit
				break;
			}
		}
		
		ChengLogger.println("#wfence: " + wfence_list.size());
		ChengLogger.println("#rfence: " + rfence_txns.size());

		// 3. assign epoch numbers to all the epoch txns
		int cur_epoch = (conx.rounds == 1) ? VeriConstants.FZ_INIT_VERSION : conx.last_epoch_agree; // = wfence_head.getVersion()
		for (TxnNode wfence : wfence_list) {
			if (wfence.getVersion() == VeriConstants.TXN_NULL_VERSION) {
				wfence.setVersion(cur_epoch);
				conx.wfences.add(wfence);
			} else {
				assert wfence.getVersion() == cur_epoch;
			}
			cur_epoch++;
		}
		ChengLogger.println("cur epoch = " + cur_epoch);
		
		// 4. assing epoch to rfence txns
		for (TxnNode rf : rfence_txns) {
			OpNode rop = rf.getOps().get(0);
			assert rf.getOps().size() == 1 && rop.isRead && rop.key_hash == version_key;
			TxnNode prv_epoch = cg.getNode(rop.read_from_txnid);
			assert prv_epoch.getVersion() != VeriConstants.TXN_NULL_VERSION;
			rf.setVersion(prv_epoch.getVersion());
		}
		
		// 5. 
		int epoch_agree = clientVerUpdate(cg, conx);	
		
		return epoch_agree;
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
	
	private int clientVerUpdate(PrecedenceGraph cg, RoundContext conx) {
		int epoch_agree = Integer.MAX_VALUE;
		
		// find the source of each client
		if (conx.client_last_versioned_fence.size() == 0) {
			conx.client_last_versioned_fence = getClientHeads(cg);
		}
		assert conx.client_last_versioned_fence.size() == this.client_list.size();
		ChengLogger.println("#Clients=" + conx.client_last_versioned_fence.size());
		
		// loop for each client
		for (int cid : conx.client_last_versioned_fence.keySet()) {
			TxnNode cur = conx.client_last_versioned_fence.get(cid);
			int ver = VeriConstants.FZ_INIT_VERSION - 1; // first wfence.ver is 1; so, before that, txn.ver == 0
			if (cur.getVersion() != VeriConstants.TXN_NULL_CLIENT_ID) {
				assert isFence(cur); // if not first round, cur is a the last versioned fence for this client
				ver = cur.getVersion();
				assert ver > 0;
			}

			// update normal txns' version
			// UTBABUG: the normal txn's version should be the next version -1, instead of prev version
			// Because, this is possible:
			//    C7: [V=14] -> T_i -> [V=22] ...
			Set<TxnNode> last_epoch_txns = new HashSet<TxnNode>();
			ChengLogger.print(LoggerType.DEBUG, "C=" + cid +": ");
			do {
				if (isFence(cur)) {
					ChengLogger.print(LoggerType.DEBUG, "[" + (isWriteFence(cur)?"W":"R") + cur.getVersion()+"]");
					// if is a version txn, but don't have a version, 
					//  that means one prior wfence hasn't been found; we stop here
					if (cur.getVersion() == VeriConstants.TXN_NULL_VERSION) {
						if (!isWriteFence(cur)) {
							assert !deleted_txnids.contains(cur.getOps().get(0).read_from_txnid);
						}
						last_epoch_txns.clear();
						break;
						// (1) UTBABUG: there might be the case that some writes got skipped and the latter version
						// txns are set to be TXN_FUTURE_VERSION
						// (2) UTBABUG: there are cases where the read version txn comes first and its
						// write version txn hasn't been parsed. We set its version to revisit later

						// we don't have to do anything here for last_epoch_txns, because they
						// were set to TXN_FUTURE_VERION when we see them.
					} else {	
						assert ver < cur.getVersion() || cur == conx.client_last_versioned_fence.get(cid);
						ver = cur.getVersion();
						// here set the normal txn to (ver - 1)
						for (TxnNode n : last_epoch_txns) {
							n.setVersion(ver - 1);
						}
						conx.client_last_versioned_fence.put(cid, cur); // update when XXX
					}
					last_epoch_txns.clear();
				} else { // cur is a normal txn
					cur.prev_version = ver;
					last_epoch_txns.add(cur);
				}
				
				// find the next transaction
				if (cur.getNextClientTxn() == VeriConstants.NULL_TXN_ID) {
					cur = null;
				} else {
					// might be null; when the node is in "m_g"
					cur = cg.getNode(cur.getNextClientTxn());
				}
			} while (cur != null);
			// ver is the version of current client
			epoch_agree = Math.min(epoch_agree, ver);
			
			ChengLogger.println(LoggerType.DEBUG, "C=" + cid +" last saw version is " + ver + "; agreed_Ver=" + epoch_agree);
		}
	
		ChengLogger.println("epoch_agree="+epoch_agree);
		
		return epoch_agree;
	}

	private Set<Long> GetSafeDelTxns(PrecedenceGraph g, RoundContext conx)
	{
		Set<Long> frontier_set = new HashSet<Long>();
		for (Long key : conx.frontier.keySet()) {
			frontier_set.addAll(conx.frontier.get(key));
		}

		// delete transactions that 
		//  (1) is Frozen
		//  (2) no frontier txn in the same scc
		//assert conx.epoch_agree - conx.last_epoch_agree > 2;
		
		Set<Long> safe_dels = new HashSet<Long>();	
		for (SCCNode scc : conx.frozen_sccs) {
			assert scc.frozen;
			boolean del = true;
			for (TxnNode tx : scc.txns) {
				if (frontier_set.contains(tx.getTxnid()) || isFence(tx)) {
					del = false;
				}
			}
			// if this is a frozen SCC and there is no frontier within,
			// then we're good to go
			if (del) {
				for (TxnNode tx : scc.txns) {
					safe_dels.add(tx.getTxnid());
					assert g.containTxnid(tx.getTxnid());
					assert !this.deleted_txnids.contains(tx.getTxnid());
				}
			}
		}

		ChengLogger.println("[3]  #frontier_txns=" + frontier_set.size());
		return safe_dels;
	}
	
	protected void CheckReadFromDelete(PrecedenceGraph g, Set<Long> del_txns) {
		// all the deleted txns should not have any ONGOING predecessors (except for INIT txn)
		for (long tid : del_txns) {
			for (OpNode op : g.getNode(tid).getOps()) {
				if (op.isRead) {
					long prev_txnid = op.read_from_txnid;
					if (prev_txnid == VeriConstants.INIT_TXN_ID) {continue;}
					// previous txn must be either COMMITED or DELETED
					if (g.containTxnid(prev_txnid)) {

						if (g.getNode(prev_txnid).getStatus() != TxnType.COMMIT) {
							ChengLogger.println("previous txn: " + g.getNode(prev_txnid).toString2());
							ChengLogger.println("is previous txn in deleted txn? " + this.deleted_txnids.contains(prev_txnid));
							ChengLogger.println("current txn: " + g.getNode(tid).toString2());
						}
				
						assert g.getNode(prev_txnid).getStatus() == TxnType.COMMIT;
					} else {
						assert this.deleted_txnids.contains(prev_txnid);
					}
				}
			}
		}
	}
	
  // 0: don't know, concurrent
	// 1: i ~-> j
	// 2: j ~-> i
	private int isItoJ(TxnNode node_i, TxnNode node_j, ReachabilityMatrix rm) {
		// (1) if rm != null, use it to check
		if (rm != null) {
			if (rm.reachUnsafe(node_i.getTxnid(), node_j.getTxnid()) == 1) {
				return 1;
			} else if (rm.reachUnsafe(node_j.getTxnid(), node_i.getTxnid()) == 1) {
				return 2;
			}
		}
	
		// (2) use frozen zone
		int prev_i = node_i.prev_version;
		int prev_j = node_j.prev_version;
		// if we don't know which fence comes later; we assume the worst
		int ver_i = (node_i.getVersion() == VeriConstants.TXN_NULL_VERSION) ? Integer.MAX_VALUE : node_i.getVersion();
		int ver_j = (node_j.getVersion() == VeriConstants.TXN_NULL_VERSION) ? Integer.MAX_VALUE : node_j.getVersion();
		
		if (ver_i <= prev_j - 2) {
			return 1;
		} else if (ver_j <= prev_i - 2) {
			return 2;
		}
		
		return 0;
	}
	
	
	protected Map<Long, Set<Long>> LastRoundFrontierUpdate(Map<Long, Set<Long>> frontier, PrecedenceGraph cg, ReachabilityMatrix rm) {
		if (frontier == null) {
			return new HashMap<Long,Set<Long>>();
		}
		
		for (long key : frontier.keySet()) {
			Set<Long> f_set = frontier.get(key);
			if (f_set.size() == 1) { continue; }
			ArrayList<TxnNode> f_nodes = new ArrayList<TxnNode>();
			for (long tid : f_set) {
				f_nodes.add(cg.getNode(tid));
			}
			// check if we can update the frontier
			for (int i = 0; i < f_nodes.size(); i++) {
				for (int j = i + 1; j < f_nodes.size(); j++) {
					int i2j = isItoJ(f_nodes.get(i), f_nodes.get(j), rm);
					switch (i2j) {
					case 1: // 1: i ~-> j, remove i
						frontier.get(key).remove(f_nodes.get(i).getTxnid());
						break;
					case 2: // 2: j ~-> i, remove j
						frontier.get(key).remove(f_nodes.get(j).getTxnid());
						break;
					}
				}
			}
		}
		
		return frontier;
	}
	
	protected Map<Long, Set<Long>> GenFrontier(PrecedenceGraph g, ReachabilityMatrix rm, int epoch_agree, Map<Long, Set<Long>> frontier) {
		assert epoch_agree > 0;
		int fz_version = epoch_agree - 2;
		
		// find the recent txns for each key
		// key => {txn, txn, ...}
		for (TxnNode txn : g.allNodes()) {
			if (txn.getStatus() != TxnType.COMMIT) { continue; }
			if (txn.getVersion() > fz_version || txn.getVersion() == VeriConstants.TXN_NULL_VERSION) { continue; }
			if (isFence(txn)) { continue; } // skip the version txns
			if (txn.frozen) { continue; } // an OPT: skip the fronzen txns; but, there might be frozen txn in old_frontier
			
			// check if txn is in frontier
			Set<Long> keys = writeKeys(txn);
			for (Long k : keys) {
				if (!frontier.containsKey(k)) {
					frontier.put(k, new HashSet<Long>());
					frontier.get(k).add(txn.getTxnid());
					continue;
				}
				
				// handle frozen frontier:
				// if there exists frontier which is frozne, it must be the only frontier for this key
				if (frontier.get(k).size() == 1) {
					TxnNode old_ft_tx = g.getNode(frontier.get(k).iterator().next());
					if (old_ft_tx.frozen) {
						frontier.get(k).clear();
						frontier.get(k).add(txn.getTxnid());
						continue;
					}
				}

				// type:
				// 1: cur_ver ~-> some_frontier    cur_ver get discarded
				// 2: some_frontier ~-> cur_ver    some_frontier get discarded; cur_ver add to frontier
				// 0: cur_ver || all frontier      cur_ver add to frontier
				int type = 0;
				Set<Long> outdated_frontier = new HashSet<Long>();
				for (Long tid : frontier.get(k)) {
					TxnNode some_frontier = g.getNode(tid);
					assert some_frontier.getVersion() <= fz_version;
					int i2j = isItoJ(txn, some_frontier, rm);
					if (i2j == 1) { // i~->j!
						assert type != 2;
						type = 1;
					} else if (i2j == 2) { // j~->i!
						assert type != 1;
						type = 2;
						outdated_frontier.add(tid);
					} else {
						// do nothing
						assert i2j == 0;
					}
				}
				//
				if (type == 0) {
					frontier.get(k).add(txn.getTxnid());
				} else if (type == 2) {
					assert outdated_frontier.size() > 0;
					frontier.get(k).add(txn.getTxnid());
					frontier.get(k).removeAll(outdated_frontier);
				}
			}
		}
		
		return frontier;
	}
	
	private Set<Pair<Long,Long>> GetTransitiveClosureEdges(PrecedenceGraph g, 
			ReachabilityMatrix rm, Set<Long> del_txns, Map<Long, SCCNode> tid2scc) 
	{
		Set<TxnNode> srcs = new HashSet<TxnNode>();
		Set<TxnNode> dsts = new HashSet<TxnNode>();
		Set<TxnNode> dels = new HashSet<TxnNode>();
		for (long d_id : del_txns) {
			TxnNode txn = g.getNode(d_id);
			dels.add(txn);
			srcs.addAll(g.predecessors(txn));
			dsts.addAll(g.successors(txn));
		}
		srcs.removeAll(dels);
		dsts.removeAll(dels);
		srcs.remove(g.getNode(VeriConstants.INIT_TXN_ID));
		dsts.remove(g.getNode(VeriConstants.INIT_TXN_ID));
		
		dels.clear();
		for (TxnNode snode :srcs) {
			if (tid2scc.get(snode.getTxnid()).frozen) {
				dels.add(snode);
			}
		}
		srcs.removeAll(dels);
		dels.clear();
		for (TxnNode dnode : dsts) {
			// successor node might be too new to be included in current round
			if (tid2scc.containsKey(dnode.getTxnid()) &&
					tid2scc.get(dnode.getTxnid()).frozen) {
				dels.add(dnode);
			}
		}
		dsts.removeAll(dels);
		dels.clear();
		
		int counter = 0;
		int bad_luck = 0;
		Set<Pair<Long,Long>> ret = new HashSet();
		for (TxnNode src_node : srcs) {
			for (TxnNode dst_node : dsts) {
				counter++;
				// (1) RM, epoch
				int type = isItoJ(src_node, dst_node, rm);
				if (type == 1) {
					ret.add(new Pair(src_node.getTxnid(), dst_node.getTxnid()));
					continue;
				} else if (type == 0) { // unkown or concurrent
					// (2) if same session or have direct edge
					if (src_node.getClientId() == dst_node.getClientId()
							|| m_g.successors(src_node).contains(dst_node)
							|| m_g.predecessors(src_node).contains(dst_node)) {
						continue;
					}
					// (3)
					bad_luck++;
					if (Graphs.reachableNodes(g.getGraph(), src_node).contains(dst_node)) {
						ret.add(new Pair(src_node.getTxnid(), dst_node.getTxnid()));
					}
				}
			}
		}

		ChengLogger.println("RM size=" + rm.getN());
		ChengLogger.println("Src size=" + srcs.size() + "; dst size=" + dsts.size());
		ChengLogger.println("total comparison = " + counter);
		ChengLogger.println("BAD LUCK counter=" + bad_luck);
		
		return ret;
	}
	
	private void SafeDeletion(PrecedenceGraph g, Set<Long> del_txns, Set<Pair<Long, Long>> tr_edges) {
		for (long tid : del_txns) {
			TxnNode del = g.getNode(tid);
			assert del.frozen;
			assert !isFence(del);
			g.deleteNodeSimple(del);
		}
		
		for (Pair<Long,Long> e : tr_edges) {
			g.addEdge(e.getFirst(), e.getSecond(), EdgeType.DEL_CONNECTED);
		}
	}

	// =========================
	// ===== link rounds =======
	// =========================
	
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

	private Map<Long,Set<Long>> GetRelevantGraphConnected(PrecedenceGraph g, RoundContext conx) {
		// (1) prepare the txns in the Frozen territory 
		Map<Long, Set<TxnNode>> readfrom_frontier_in_new = new HashMap<Long, Set<TxnNode>>(); // {key => a new txn reads from frontier}
		Map<Long, Set<TxnNode>> readfrom_frozen_in_nonfrozen = new HashMap<Long, Set<TxnNode>>(); // {key => a non-new-non-frozen reads from frozen}
		Map<Long, Set<TxnNode>> writes_in_new = new HashMap<Long, Set<TxnNode>>(); // {key=>writes in new_complete_txns}
		Set<TxnNode> touched_frontier_set = new HashSet<TxnNode>(); // txns reading by new_complete_txns
		
		// loop the new txns
		for (TxnNode txn : conx.new_complete_txns) {
			for (OpNode op : txn.getOps()) {
				if (op.isRead) {
					if (op.read_from_txnid == VeriConstants.INIT_TXN_ID) {continue;} // skip init txn
					TxnNode readfrom = g.getNode(op.read_from_txnid);
					assert readfrom != null;
					if (readfrom.frozen) {
						assert conx.frontier.get(op.key_hash).contains(op.read_from_txnid);
						touched_frontier_set.add(readfrom);
						readfrom_frontier_in_new.putIfAbsent(op.key_hash, new HashSet<TxnNode>());
						readfrom_frontier_in_new.get(op.key_hash).add(txn);
					}
				} else { // for write
					writes_in_new.putIfAbsent(op.key_hash, new HashSet<TxnNode>());
					writes_in_new.get(op.key_hash).add(txn);
				}
			}
		}
		
		// loop the old non-frozen txns
		for (TxnNode txn : g.allNodes()) {
			if (txn.frozen) {continue;}
			if (conx.new_complete_txns.contains(txn)) {continue;}
			for (OpNode op : txn.getOps()) {
				if (op.isRead) {
					if (op.read_from_txnid == VeriConstants.INIT_TXN_ID) {continue;}
					TxnNode readfrom = g.getNode(op.read_from_txnid);
					// UTUBABUG: readfrom might be deleted
					if (readfrom == null) {
						assert this.deleted_txnids.contains(op.read_from_txnid);
					}
					if (readfrom == null || readfrom.frozen) {
						readfrom_frozen_in_nonfrozen.putIfAbsent(op.key_hash, new HashSet<TxnNode>());
						readfrom_frozen_in_nonfrozen.get(op.key_hash).add(txn);
					}
				}
			}
		}
		
		// XXX: FIXME: do we need this?? it's correct, but is it necessary?
		// (2) update edges
		// from: read that reads from a frozen txn
		// to: a write in the new on the same key
		Map<Long, Set<Long>> new_edges = new HashMap<Long, Set<Long>>();
		for (long key : writes_in_new.keySet()) {
			Set<TxnNode> writes = writes_in_new.get(key);
			Set<TxnNode> reads1 = readfrom_frontier_in_new.get(key);
			Set<TxnNode> reads2 = readfrom_frozen_in_nonfrozen.get(key);
			Set<TxnNode> reads = new HashSet<TxnNode>();
			if (reads1 != null) {
				reads.addAll(reads1);
			}
			if (reads2 != null) {
				reads.addAll(reads2);
			}
			for (TxnNode r : reads) {
				for (TxnNode w : writes) {
					if (r == w) {continue;} // skip RMW txns
					if (!new_edges.containsKey(r.getTxnid())) {
						new_edges.put(r.getTxnid(), new HashSet<Long>());
					}
					g.addEdge(r.getTxnid(), w.getTxnid(), EdgeType.INIT);
					new_edges.get(r.getTxnid()).add(w.getTxnid());
				}
			}
		}
		
		// (3) catch the SCCs that frozen txns which are read from by new txns are in
		Set<SCCNode> no_longer_frozen = new HashSet<SCCNode>();
		for (TxnNode readfrom : touched_frontier_set) {
			SCCNode scc = conx.tid2scc.get(readfrom.getTxnid());
			assert scc.frozen == true;
			no_longer_frozen.add(scc);
			ChengLogger.println(LoggerType.DEBUG, "frozen scc involved: " + scc.toString());
		}
		// because this scc will be active, remove it from the frozen_list
		conx.frozen_sccs.removeAll(no_longer_frozen);
		Set<TxnNode> involved_frozen = new HashSet<TxnNode>();
		for (SCCNode scc : no_longer_frozen) {
			involved_frozen.addAll(scc.txns);
		}
		ChengLogger.println("#involved_frozen_sccs=" + no_longer_frozen.size() + "#involved_frozen_txns=" + involved_frozen.size());
		
		// (4) add the other non-frozen transactions
		Set<TxnNode> nonfrozen_txns = new HashSet<TxnNode>();
		Set<TxnNode> close_to_epoch_agree = new HashSet<TxnNode>();
		for (TxnNode tx : g.allNodes()) {
			if (!tx.frozen) {
				nonfrozen_txns.add(tx);
				if (tx.prev_version != VeriConstants.TXN_NULL_VERSION && 
						tx.prev_version < conx.last_epoch_agree) {
					close_to_epoch_agree.add(tx);
				}
			}
		}
		
		// (5) setup the subgraph
		Set<TxnNode> all = new HashSet<TxnNode>();
		all.addAll(involved_frozen);
		all.addAll(nonfrozen_txns);
		conx.g_rel = g.subgraphNoClone(all);
		conx.g_nonfrozen = conx.g_rel.subgraphNoClone(nonfrozen_txns);
		
		// ~-> to -> in subg
		int painful_pairs = 0;
	  executor  = Executors.newFixedThreadPool(VeriConstants.THREAD_POOL_SIZE);
		for (TxnNode ftx : involved_frozen) {
			for (TxnNode ctx : close_to_epoch_agree) {
				// (1) check whether the connectivity can be represented using fence reachability
				if (ctx.prev_version - ftx.getVersion() >= 2) { continue; }
				if (conx.rm.reachUnsafe(ctx.getTxnid(), ftx.getTxnid()) == 1) {
					conx.g_rel.addEdge(ctx, ftx, EdgeType.INIT);
					continue;
				}
				int type = conx.rm.reachUnsafe(ftx.getTxnid(), ctx.getTxnid());
				if (type == 1) {
					conx.g_rel.addEdge(ftx, ctx, EdgeType.INIT);
				} else if (type == -1) {
					painful_pairs++;
					ChengLogger.println(LoggerType.DEBUG, "A costly one-to-one reachability procedure is invoked!");
					// check if ftx~->ctx:
					executor.execute(new PainfulPairTask(g, conx, ftx, ctx));
				}
			}
		}
		executor.shutdown();
		try {
			executor.awaitTermination(99999, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		ChengLogger.println("  #painful pairs = " + painful_pairs);
		ChengLogger.println("  #frozen txns = " + involved_frozen.size());
		ChengLogger.println("  #non-frozen-old = " + (nonfrozen_txns.size() - conx.new_complete_txns.size()));
		ChengLogger.println("  #complete-new = " + conx.new_complete_txns.size());
		ChengLogger.println("  #loop = " + involved_frozen.size() + " X " + close_to_epoch_agree.size());
		
		return new_edges;
	}
	
	class PainfulPairTask implements Runnable {
		PrecedenceGraph g;
		RoundContext conx;
		TxnNode ftx;
		TxnNode ctx;
		
		public PainfulPairTask(PrecedenceGraph g, RoundContext conx, TxnNode ftx, TxnNode ctx) {
			this.g = g;
			this.conx = conx;
			this.ftx = ftx;
			this.ctx = ctx;
		}

		public void run() {
			// check if ftx~->ctx:
			if (Graphs.reachableNodes(g.getGraph(), ftx).contains(ctx)) {
				synchronized(conx) {
					conx.g_rel.addEdge(ftx, ctx, EdgeType.INIT);
				}
			}
		}
	}
	
	// =========================
	// ===== parallel audit ====
	// =========================
	
	protected void CheckIndependentCluster(Set<Constraint> cons, ArrayList<Set<TxnNode>> sccs) {
		Map<Long, Integer> tid2sccid = new HashMap<Long,Integer>();
		
		for (int i=0; i<sccs.size(); i++) {
			Set<TxnNode> scc = sccs.get(i);
			// (1) no mix of frozen/non-frozen txns
			boolean frozen = scc.iterator().next().frozen;
			for (TxnNode txn : scc) {	
				assert frozen == txn.frozen;
				tid2sccid.put(txn.getTxnid(), i);
			}
		}

		// (3) txn in one cons must in one scc
		for (Constraint con : cons) {
			long tid = con.chain_1.get(0).getTxnid();
			int sccid = tid2sccid.get(tid);
			for (TxnNode tx : con.chain_1) {
				assert sccid == tid2sccid.get(tx.getTxnid());
			}
			for (TxnNode tx : con.chain_2) {
				assert sccid == tid2sccid.get(tx.getTxnid());
			}
			for (Pair<Long, Long> e: con.edge_set1) {
				assert sccid == tid2sccid.get(e.getFirst());
				assert sccid == tid2sccid.get(e.getSecond());
			}
			for (Pair<Long, Long> e: con.edge_set2) {
				assert sccid == tid2sccid.get(e.getFirst());
				assert sccid == tid2sccid.get(e.getSecond());
			}
		}
	}
	
	
	protected void CheckConstraints(PrecedenceGraph m_g, Set<Constraint> cons) {
		// check if all the elements in one constraints are either (1) all frozen
		// or (2) all non-frozen
		for (Constraint con : cons) {
			boolean frozen = con.chain_1.get(0).frozen;
			boolean good = true;
			
			for (TxnNode tx : con.chain_1) {
				good = good && (frozen == tx.frozen);
			}
			for (TxnNode tx : con.chain_2) {
				good = good && (frozen == tx.frozen);
			}
			for (Pair<Long, Long> e: con.edge_set1) {
				good = good &&  (frozen == m_g.getNode(e.getFirst()).frozen);
				good = good &&  (frozen == m_g.getNode(e.getSecond()).frozen);
			}
			for (Pair<Long, Long> e: con.edge_set2) {
				good = good &&  (frozen == m_g.getNode(e.getFirst()).frozen);
				good = good &&  (frozen == m_g.getNode(e.getSecond()).frozen);
			}
			
			if (!good) {
				ChengLogger.println(LoggerType.ERROR, con.toString(m_g));
				assert false;
			}
		}
	}
	
	private void CheckSCC(PrecedenceGraph cg, RoundContext conx) {
		// CHECK1: all txn belong to some SCCNode
		for (TxnNode tx : cg.allNodes()) {
			SCCNode scc = conx.tid2scc.get(tx.getTxnid());
			assert scc != null;
			assert conx.frozen_sccs.contains(scc) || conx.nonfrozen_scc_list.contains(scc);
		}
		// CHECK2, no two SCCNodes contain the same txn
		Set<TxnNode> tmp_all_txns = new HashSet<TxnNode>();
		for (SCCNode scc : conx.frozen_sccs) {
			for (TxnNode tx : scc.txns) {
				assert !tmp_all_txns.contains(tx);
				tmp_all_txns.add(tx);
			}
		}
		for (SCCNode scc : conx.nonfrozen_scc_list) {
			for (TxnNode tx : scc.txns) {
				assert !tmp_all_txns.contains(tx);
				tmp_all_txns.add(tx);
			}
		}
	}
	
	// XXX: FIXME: how different is this from onshot's GetIndependentClusters?
	private Set<SCCNode> GetIndependentClusters(PrecedenceGraph g_rel, RoundContext conx) {
		PrecedenceGraph spg = GetSuperpositionGraph(g_rel, conx.cons);
		TarjanStronglyConnectedComponents tarjan = new TarjanStronglyConnectedComponents();
		ArrayList<Set<TxnNode>> sccs = tarjan.getSCCs(spg.getGraph(), false);

		if (VeriConstants.HEAVY_VALIDATION_CODE_ON) {
			CheckIndependentCluster(conx.cons, sccs);
		}
		
		Set<SCCNode> scc_list = new HashSet<SCCNode>();
		for (Set<TxnNode> scc : sccs) {
			// skip init txn
			if (scc.size() == 1 && scc.iterator().next().getTxnid() == VeriConstants.INIT_TXN_ID) {continue;}
			
			SCCNode scc_node = new SCCNode(scc);
			scc_node.frozen = scc.iterator().next().frozen; // if frozen, remain froze.
			scc_list.add(scc_node);
			// update tid2scc in context
			for (TxnNode tx : scc) {
				assert tx.frozen == scc_node.frozen; // failed for twitter scaling 50
				conx.tid2scc.put(tx.getTxnid(), scc_node);
			}
		}
		
		return scc_list;
	}

	protected Set<Pair<PrecedenceGraph, Set<Constraint>>> GenSubgraphs(PrecedenceGraph g, Set<Constraint> cons,
			Set<SCCNode> sccs) {
		Set<Pair<PrecedenceGraph, Set<Constraint>>> ret = new HashSet<Pair<PrecedenceGraph, Set<Constraint>>>();
		for (SCCNode scc : sccs) {
			if (scc.size() == 1) { continue; } // skip single element scc
			
			Set<Long> scc_ids = new HashSet<Long>();
			for (TxnNode tx : scc.txns) {
				scc_ids.add(tx.getTxnid());
			}
			
			Set<Constraint> sub_cons = new HashSet<Constraint>();
			for (Constraint con : cons) { // FIXME: performance; can be much faster
				long head_1 = con.chain_1.get(0).getTxnid();
				if (scc_ids.contains(head_1)) {
					sub_cons.add(con);
					assert scc_ids.contains(con.chain_2.get(0).getTxnid());
					assert scc_ids.contains(con.chain_1.get(con.chain_1.size()-1).getTxnid());
					assert scc_ids.contains(con.chain_2.get(con.chain_2.size()-1).getTxnid());
				}
			}
			// NOTE: if there is no constraint, there should be no cycle!
			assert sub_cons.size() > 0;
			
			PrecedenceGraph sub_g = g.subgraph(scc_ids);
			ret.add(new Pair<PrecedenceGraph, Set<Constraint>>(sub_g, sub_cons));
		}
		
		// check invariant: constraints in all sugraphs == all constraints
		int num_cons = 0;
		Set<Constraint> all_subgraph_constraints = new HashSet<Constraint>();
		for (Pair<PrecedenceGraph, Set<Constraint>> subg : ret) {
			all_subgraph_constraints.addAll(subg.getSecond());
			num_cons += subg.getSecond().size();
		}
		assert num_cons == cons.size();
		all_subgraph_constraints.removeAll(cons);
		assert all_subgraph_constraints.size() == 0;
		
		return ret;
	}
	
	// =========================
	// ===== SMT Solver =========
	// =========================

	public static boolean solveConstraints(PrecedenceGraph g, Set<Constraint> cons) {
		SMTEncoderSOSP encoder = new SMTEncoderSOSP(g, cons);
		boolean ret = encoder.Solve();
		if (!ret) {
			// dump the failed transactions
			ArrayList<EndpointPair<TxnNode>> out_edges = new ArrayList<EndpointPair<TxnNode>>();
			ArrayList<Constraint> out_cons = new ArrayList<Constraint>();
			encoder.GetMinUnsatCore(out_edges, out_cons);
			
			// print out
			ChengLogger.println(LoggerType.ERROR, "========= MiniUnsatCore ============");
			ChengLogger.println(LoggerType.ERROR, "  === 1. edges ===");
			Set<TxnNode> unsat_txns = new HashSet<TxnNode>();
			for (EndpointPair<TxnNode> e : out_edges) {
				ChengLogger.println(LoggerType.ERROR, "  " + e.source().toString3() + "\n    -> " + e.target().toString3());
				unsat_txns.add(e.source());
				unsat_txns.add(e.target());
			}
			ChengLogger.println(LoggerType.ERROR, "  === 2. constraints ===");
			for (Constraint c : out_cons) {
				ChengLogger.println(LoggerType.ERROR, "  " + c.toString(g));
				unsat_txns.addAll(c.chain_1);
				unsat_txns.addAll(c.chain_2);
			}
			ChengLogger.println(LoggerType.ERROR, "  === 3. transaction details ===");
			for (TxnNode t : unsat_txns) {
				ChengLogger.println(LoggerType.ERROR, "  " + t.toString2());
			}
		}
		return ret;
	}
	
	// =========================
	// ===== main APIs =========
	// =========================
	
	static class RoundContext {
		int rounds;
		int last_epoch_agree = 0;
		int epoch_agree;
		ReachabilityMatrix rm;
		
		// for incremental complete graph
		PrecedenceGraph c_g = new PrecedenceGraph(); // share the same TxnNode with m_g
		Set<TxnNode> incomplete_reachable = null;
		Set<TxnNode> new_complete_txns = null;
		
		// epochs 
		ArrayList<TxnNode> wfences = new ArrayList<TxnNode>(); // wfence
		Map<Integer, TxnNode> client_last_versioned_fence = new HashMap<Integer, TxnNode>();
		
		// GC
		Map<Long, SCCNode> tid2scc = new HashMap<Long, SCCNode>();
		Set<SCCNode> frozen_sccs = new HashSet<SCCNode>();
		Set<SCCNode> nonfrozen_scc_list;
		Map<Long, Set<Long>> frontier = new HashMap<Long,Set<Long>>();
		Set<Constraint> cons;
		
		PrecedenceGraph g_nonfrozen;
		PrecedenceGraph g_rel;
	}
	
	// XXX: FIXME: port optimization here
	protected PrecedenceGraph CreateKnownGraph(RoundContext conx) {
		ArrayList<File> opfiles = findOpLogInDir(log_dir);
		boolean ret = loadLogs(opfiles, m_g);
		CheckValues(m_g); // check whether all the read/write values match
		if (!ret) assert false; // Reject
		
		// construct the complete subgraph
		Set<TxnNode> incomplete = GetIncompleteTxns(m_g, this.new_txns_this_turn);
		Set<TxnNode> complete_nodes = new HashSet<TxnNode>(m_g.allNodes());
		complete_nodes.removeAll(incomplete);
		PrecedenceGraph complete_g = m_g.subgraphNoClone(complete_nodes);
		// prepare the new_complete_txns = current complete nodes - last round complete nodes
		conx.new_complete_txns.addAll(complete_nodes);
		if (conx.c_g != null) { // this is null in first round
			// UTBABUG: performance bug, can be as long as 24sec for Rubis
			conx.new_complete_txns.removeAll(new HashSet<TxnNode>(conx.c_g.allNodes()));
		}
		
		ChengLogger.println("[1] #Clients=" + this.client_list.size() + "; #new_txns=" + this.new_txns_this_turn.size());
		ChengLogger.println("[1] complete graph: #n=" + complete_g.allNodes().size());
		ChengLogger.println("[1] global graph: #n=" + m_g.allNodes().size());
		
		return complete_g;
	}
	
	private void AddCOEdges(PrecedenceGraph cg, Set<TxnNode> new_nodes) {
		for (TxnNode n : new_nodes) {
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
	
	protected PrecedenceGraph CreateKnownGraph2(RoundContext conx) {
		Profiler prof = Profiler.getInstance();
		
		boolean ret = false;
		ArrayList<File> opfiles = findOpLogInDir(log_dir);
		ret = loadLogs(opfiles, m_g);
		CheckValues(m_g, this.new_txns_this_turn); // check whether all the read/write values match
		if (!ret) { assert false;} // Reject

		// construct the complete subgraph
		Set<TxnNode> cur_incomplete_reachable = GetIncompleteTxns(m_g, this.potential_ongoing_txns);

		// NOTE: complete graph contains INIT TXN
		prof.startTick("getcomplete");
		if (conx.incomplete_reachable == null) { // first round
			Set<TxnNode> complete_nodes = new HashSet<TxnNode>(m_g.allNodes());
			complete_nodes.removeAll(cur_incomplete_reachable);
			conx.c_g = m_g.subgraphNoClone(complete_nodes);
			conx.new_complete_txns = complete_nodes;
		} else { // incrementally growing the complete graph
			// (1) adding complete nodes; nodes = {last round incomplete_reachable + new_txns_this_turn + potential_ongoing_txns}
			Set<TxnNode> new_nodes = new HashSet<TxnNode>();
			new_nodes.addAll(conx.incomplete_reachable); // last round's incomplete reachable
			new_nodes.addAll(this.new_txns_this_turn);
			new_nodes.addAll(this.potential_ongoing_txns);
			
//			System.out.println("m_g=" + m_g.allNodes().size() + " vs. sum=" + (new_nodes.size() + conx.c_g.allNodes().size()) +
//					" [new_nodes=" + new_nodes.size() + "; c_g=" + conx.c_g.allNodes().size()+ "]");
			
			assert m_g.allNodes().size() == new_nodes.size() + conx.c_g.allNodes().size();
			
			Set<TxnNode> new_compl_nodes = new HashSet<TxnNode>(new_nodes);
			new_compl_nodes.removeAll(cur_incomplete_reachable);
			assert new_compl_nodes.size() + cur_incomplete_reachable.size() == new_nodes.size(); // cur_cincomplete_reachable \subset new_nodes
			CheckStaleReads(m_g, conx.frontier, new_compl_nodes, conx.epoch_agree);
			
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
			conx.new_complete_txns = new_compl_nodes;
		}
		conx.incomplete_reachable = cur_incomplete_reachable;

		prof.endTick("getcomplete");

		assert conx.incomplete_reachable.size() + conx.c_g.allNodes().size() == m_g.allNodes().size(); // quick check

		ChengLogger.println("[1] #Clients=" + this.client_list.size() + "; #new_txns=" + this.new_txns_this_turn.size());
		ChengLogger.println("[1] complete graph: #n=" + conx.c_g.allNodes().size());
		ChengLogger.println("[1] global graph: #n=" + m_g.allNodes().size());

		return conx.c_g;
	}

	private void EncodeAndSolve(PrecedenceGraph cg, RoundContext conx) {
		Profiler prof = Profiler.getInstance();
		
		// (1) need the epoch number to be propagate to the TxnNode in g_rel
		//     where the GenConstraints(g_rel) will use
		// (2) An OPT in GenConstraints()/IsConcurrentChains()/IsItoJ(),
		// use epoch numbers to avoid impossible constraints
		// (3) GetRelevantGraph also uses it for maintain the connectivities from frozen txn to relevant txns
		conx.epoch_agree = AssignEpoch(cg, conx);
		
		PrecedenceGraph g_rel = null;
		PrecedenceGraph g_nonfrozen = null;
		
		// NOTE: some frozen sccs get removed from the conx.frozen_sccs
		Map<Long, Set<Long>> new_edges1 = GetRelevantGraphConnected(cg, conx);
		for (long src : new_edges1.keySet()) {
			for (long dst : new_edges1.get(src)) {
				m_g.addEdge(src, dst, EdgeType.CONS_SOLV);
			}
		}
		g_rel = conx.g_rel;
		g_nonfrozen = conx.g_nonfrozen;
		
		conx.cons = GenConstraints(g_rel);
		
		prof.startTick("TMP_PRUNE");
		prof.startTick("TMP_MM");
		// NOTE: even if cons.size()==0, we still need to have "rm" for frontier generation
		conx.rm = ReachabilityMatrix.getReachabilityMatrix(g_nonfrozen.getGraph(), conx.rm);
		prof.endTick("TMP_MM");
		
		if (conx.cons.size() > 0) {
			Map<Long,Set<Long>> new_edges2 = Prune(g_rel, conx.cons, conx.rm);
			// add to the real graph & complete graph
			for (long src : new_edges2.keySet()) {
				for (long dst : new_edges2.get(src)) {
					m_g.addEdge(src, dst, EdgeType.CONS_SOLV);
					cg.addEdge(src, dst, EdgeType.CONS_SOLV);
				}
			}
		}
		prof.endTick("TMP_PRUNE");	

		// detect cycles after pruning
//		boolean hasCycle = Graphs.hasCycle(g_rel.getGraph());
//		if (hasCycle) {
//			DFSCycleDetection.PrintOneCycle(g_rel);
//			assert false;
//		}
		
		if (VeriConstants.HEAVY_VALIDATION_CODE_ON) {
			CheckConstraints(g_rel, conx.cons);
		}

		conx.nonfrozen_scc_list = GetIndependentClusters(g_rel, conx);
		
		if (conx.cons.size() > 0) {
			Set<Pair<PrecedenceGraph, Set<Constraint>>> subpolys = GenSubgraphs(g_rel, conx.cons, conx.nonfrozen_scc_list);
			for (Pair<PrecedenceGraph, Set<Constraint>> poly : subpolys) {
				boolean acyclic = solveConstraints(poly.getFirst(), poly.getSecond());
				if (!acyclic) {
					assert false;
				}
			}
		} else {
			assert false == Graphs.hasCycle(g_rel.getGraph());
		}
		
		// Update the sccs, move frozen scc back
		Set<SCCNode> still_frozen = new HashSet<SCCNode>();
		for (SCCNode sccn : conx.nonfrozen_scc_list) {
			if (sccn.frozen) {
				still_frozen.add(sccn);
			}
		}
		conx.nonfrozen_scc_list.removeAll(still_frozen);
		conx.frozen_sccs.addAll(still_frozen);
		
		if (VeriConstants.HEAVY_VALIDATION_CODE_ON) {
			CheckSCC(cg, conx);
		}
		
		
		ChengLogger.println("[2] #constraintsn=" + conx.cons.size());
		ChengLogger.println("[2] relevant graph: #n=" + g_rel.allNodes().size());
		ChengLogger.println("[2] complete graph: #n=" + cg.allNodes().size());
	}


	private Set<Long> GarbageCollection(PrecedenceGraph cg, RoundContext conx)
	{
		conx.frontier = LastRoundFrontierUpdate(conx.frontier, cg, conx.rm); // we may know more about old frontiers
		conx.frontier = GenFrontier(cg, conx.rm, conx.epoch_agree, conx.frontier);
		assert conx.frontier != null;

		SetFrozen(cg, conx);
		
		Set<Long> del_txns = GetSafeDelTxns(cg, conx);
		CheckReadFromDelete(cg, del_txns); // detect bogus
		// delete from scc lists
		for (long tid : del_txns) {
			SCCNode scc = conx.tid2scc.get(tid);
			assert scc.frozen;
			conx.tid2scc.remove(tid);
			conx.frozen_sccs.remove(scc); // might already be removed
		}
		
		ChengLogger.println("[3] #del_txns=" + del_txns.size());
		return del_txns;
	}


	public boolean continueslyAudit() {
		// need to have TTT for running truncation
		assert VeriConstants.BUNDLE_CONSTRAINTS && VeriConstants.WW_CONSTRAINTS;
		
		Profiler prof = Profiler.getInstance();
		RoundContext conx = new RoundContext();
		
		do {
			round++; // start from round 1
			conx.rounds = round;
			this.ClearCounters();
			prof.clear();
			
			ChengLogger.println("======ROUND[" + round + "]=======");
			prof.startTick("ROUND"+round);
			
			// =============1. Create Known Graph================
			CreateKnownGraph2(conx);
			if (this.new_txns_this_turn.size() == 0) {
				return true;
			}
			
		  // =============2. Encode And Solve================
			EncodeAndSolve(conx.c_g, conx);
			
			// =============3. Garage Collection================
			Set<Long> del_txns = GarbageCollection(conx.c_g, conx);
			Set<Pair<Long, Long>> tr_edges = GetTransitiveClosureEdges(m_g, conx.rm, del_txns, conx.tid2scc);
			SafeDeletion(m_g, del_txns, tr_edges);
			SafeDeletion(conx.c_g, del_txns, tr_edges);
			
			// =============4. Wrap Up================
			// UTBABUG: deleted_txnids is used to skip txns during the CrateGraph!!
			// We must keep updateit; otherwise, there are weird behaviors.
			deleted_txnids.addAll(del_txns);
			new_txns_this_turn.clear();
			conx.last_epoch_agree = conx.epoch_agree;
			conx.new_complete_txns.clear();
			
			prof.endTick("ROUND"+round);
			long runtime = prof.getTime("ROUND"+round);
			ChengLogger.println("Runtime for ROUND[" + round + "] is "+ runtime + "ms");
			ChengLogger.println("    -- GPU: " + prof.getTime("TMP_MM"));
			ChengLogger.println("    -- PRUNE: " + prof.getTime("TMP_PRUNE"));
			
			
			//ChengLogger.println(profResults());
			//ChengLogger.println(statisticsResults());
		} while (true);
	}


	@Override
	public boolean audit() {
		assert false;
		return false;
	}


	@Override
  public int[] count() {
	  return null;
  }

}
