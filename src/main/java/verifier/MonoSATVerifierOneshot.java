package verifier;

import gpu.ReachabilityMatrix;
import graph.EdgeType;
import graph.OpNode;
import graph.PrecedenceGraph;
import graph.TxnNode;
import graph.TxnNode.TxnType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import monosat.*;
import static monosat.Logic.*;

public class MonoSATVerifierOneshot extends AbstractLogVerifier {

	public MonoSATVerifierOneshot(String logfd) {
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
		
		// write combine
		if (VeriConstants.WW_CONSTRAINTS) {
		  CombineWrites(chains, g.getWWpairs(), wid2txnid, wid2key);
		}
		
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
					// for strict ser
					if (VeriConstants.TIME_ORDER_ON) {
						if (haveTimeOrder(chainset.get(i), chainset.get(j))) {
							continue;
						}
					}
					// UTBABUG: if chain[i] ~-> chain[j], it doesn't mean we can skip the constraint,
					// because the reachabilities we know might not include what read-txns  should be placed!
					// Coalescing
					if (VeriConstants.BUNDLE_CONSTRAINTS) {
						Constraint con = Coalesce(chainset.get(i), chainset.get(j), key, g.m_readFromMapping, wid2txnid);
						cons.add(con);
					} else {
						List<Constraint> con_lst = NoCoalesce(chainset.get(i), chainset.get(j), key, g.m_readFromMapping, wid2txnid);
						cons.addAll(con_lst);
					}
				}
			}
		}
		
		return cons;
	}
	
	private boolean haveTimeOrder(List<TxnNode> chain_1, List<TxnNode> chain_2) {
		assert VeriConstants.TIME_ORDER_ON;
		
		TxnNode head_1 = chain_1.get(0);
		TxnNode head_2 = chain_2.get(0);
		TxnNode tail_1 = chain_1.get(chain_1.size()-1);
		TxnNode tail_2 = chain_2.get(chain_2.size()-1);
		
		// tail_1.commit + drift < head_2.begin => chain_1 < chain_2
		if (tail_1.getCommitTimestamp() + VeriConstants.TIME_DRIFT_THRESHOLD < head_2.getBeginTimestamp() ||
				tail_2.getCommitTimestamp() + VeriConstants.TIME_DRIFT_THRESHOLD < head_1.getBeginTimestamp())
		{
			return true;
		}
		return false;
	}
	
	private Constraint Coalesce(List<TxnNode> chain_1, List<TxnNode> chain_2, Long key,
			Map<Long, Set<OpNode>> readfrom, Map<Long, Long> wid2txnid)
	{
		Set<Pair<Long, Long>> edge_set1 = GenChainToChainEdge(chain_1, chain_2, key, readfrom, wid2txnid);
		Set<Pair<Long, Long>> edge_set2 = GenChainToChainEdge(chain_2, chain_1, key, readfrom, wid2txnid);
		return new Constraint(edge_set1, edge_set2, chain_1, chain_2);
	}
	
	private List<Constraint> NoCoalesce(List<TxnNode> chain_1, List<TxnNode> chain_2, Long key,
			Map<Long, Set<OpNode>> readfrom, Map<Long, Long> wid2txnid)
	{
		ArrayList<Constraint> ret = new ArrayList<Constraint>();
		long head_1 = chain_1.get(0).getTxnid();
		long head_2 = chain_2.get(0).getTxnid();
		long tail_1 = chain_1.get(chain_1.size()-1).getTxnid();
		long tail_2 = chain_2.get(chain_2.size()-1).getTxnid();
		// (1) construct constraints: <read_from_tail_1 -> head_2, tail_2 -> head_1>
		Set<Pair<Long, Long>> edge_set1 = GenChainToChainEdge(chain_1, chain_2, key, readfrom, wid2txnid);
		Pair<Long,Long> sec_edge1 = new Pair<Long,Long>(tail_2, head_1);
		for (Pair<Long, Long> e : edge_set1) {
			Set<Pair<Long,Long>> set1 = new HashSet<Pair<Long,Long>>();
			set1.add(e);
			Set<Pair<Long,Long>> set2 = new HashSet<Pair<Long,Long>>();
			set2.add(sec_edge1);
			ret.add(new Constraint(set1, set2, chain_1, chain_2));
		}
		
	  // (2) construct constraints: <read_from_tail_2 -> head_1, tail_1 -> head_2>
		Set<Pair<Long,Long>> edge_set2 = GenChainToChainEdge(chain_2, chain_1, key, readfrom, wid2txnid);
		Pair<Long,Long> sec_edge2 = new Pair<Long,Long>(tail_1, head_2);
		for (Pair<Long, Long> e :edge_set2) {
			Set<Pair<Long,Long>> set1 = new HashSet<Pair<Long,Long>>();
			set1.add(e);
			Set<Pair<Long,Long>> set2 = new HashSet<Pair<Long,Long>>();
			set2.add(sec_edge2);
			ret.add(new Constraint(set1, set2, chain_2, chain_1));
		}
		return ret;
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
		
		long tail_1 = chain_1.get(chain_1.size()-1).getTxnid();
		long head_2 = chain_2.get(0).getTxnid();
		
		Set<Pair<Long,Long>> ret = new HashSet<Pair<Long,Long>>();
		if (!readfrom.containsKey(tail_1_wid)) {
			assert tail_1 != head_2;
			ret.add(new Pair<Long,Long>(tail_1, head_2));
			return ret;
		}
		
		assert readfrom.get(tail_1_wid).size() > 0;
		for (OpNode op : readfrom.get(tail_1_wid)) {
			long rtx = op.txnid;
			// it is possible (without WW_CONSTRAINTS) that a reading-from transaction (rtx) and
			// another write (head_2) are the same txn (a successive write)
			if (rtx != head_2) {
				ret.add(new Pair<Long,Long>(rtx, head_2));
			}
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
				boolean hasCycle = DFSCycleDetection.hasCycleHybrid(g.getGraph());
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


	private PrecedenceGraph GetSuperpositionGraph(PrecedenceGraph m_g, Set<Constraint> cons) {
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
	
	public boolean isFence(TxnNode t) {
		if (t.getOps().size() > 0 &&
				t.getOps().get(0).isRead &&
				t.getOps().get(0).key_hash == VeriConstants.VERSION_KEY_HASH)
		{
			return true;
		}
		return false;
	}
	
	protected boolean isWriteFence(TxnNode t) {
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

	
	protected Set<Long> writeKeys(TxnNode txn) {
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
	
  // 0: concurrent
	// 1: i ~-> j
	// 2: j ~-> i
	private int isItoJ(TxnNode node_i, TxnNode node_j, ReachabilityMatrix rm) {
		assert rm != null;
		if (rm.reach(node_i.getTxnid(), node_j.getTxnid())) {
			return 1;
		} else if (rm.reach(node_j.getTxnid(), node_i.getTxnid())) {
			return 2;
		}
		return 0;
	}
	
	
	public boolean CheckIncomplete(PrecedenceGraph g) {
		boolean complete = true;
		for (TxnNode txn : g.allNodes()) {
			if (txn.getStatus() != TxnType.COMMIT && txn.getTxnid() != VeriConstants.INIT_TXN_ID) {
				System.out.println(txn.toString2());
				complete = false;
			}
		}
		return complete;
	}

	// =========================
	// ===== parallel audit ====
	// =========================
	
	private void CheckIndependentCluster(Set<Constraint> cons, ArrayList<Set<TxnNode>> sccs) {
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
	
	protected Set<SCCNode> GetIndependentClusters(PrecedenceGraph g_rel, Set<Constraint> cons) {
		PrecedenceGraph spg = GetSuperpositionGraph(g_rel, cons);
		TarjanStronglyConnectedComponents tarjan = new TarjanStronglyConnectedComponents();
		ArrayList<Set<TxnNode>> sccs = tarjan.getSCCs(spg.getGraph(), false);

		if (VeriConstants.HEAVY_VALIDATION_CODE_ON) {
			CheckIndependentCluster(cons, sccs);
		}
		
		Set<SCCNode> scc_list = new HashSet<SCCNode>();
		for (Set<TxnNode> scc : sccs) {
			SCCNode scc_node = new SCCNode(scc);
			scc_list.add(scc_node);
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

	public static boolean solveConstraints(PrecedenceGraph g, Set<Constraint> cons, Set<Pair<Long,Long>> solution) {
		SMTEncoderSOSP encoder = new SMTEncoderSOSP(g, cons);
		//SMTEncoder encoder = new SMTEncoder(g, cons);
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
		
		if (ret && solution != null) { // if we have a solution
			solution.addAll(encoder.GetSolutionEdges());
		}
		
		
		return ret;
	}
	
	// =========================
	// ===== main APIs =========
	// =========================
	
	protected PrecedenceGraph CreateKnownGraph() {
		ArrayList<File> opfiles = findOpLogInDir(log_dir);
		boolean ret = loadLogs(opfiles, m_g);
		CheckValues(m_g); // check whether all the read/write values match
		if (!ret) assert false; // Reject
		
		// check incomplete
		boolean pass = CheckIncomplete(m_g);
		if (!pass) {
			ChengLogger.println(LoggerType.ERROR, "REJECT: The history is incomplete!");
			assert false;
		}
		
		ChengLogger.println("[1] #Clients=" + this.client_list.size());
		ChengLogger.println("[1] global graph: #n=" + m_g.allNodes().size());
		return m_g;
	}
	
	private boolean EncodeAndSolve() {
		Profiler prof = Profiler.getInstance();

		prof.startTick("ONESHOT_GEN_CONS");
		Set<Constraint> cons = GenConstraints(m_g);
		prof.endTick("ONESHOT_GEN_CONS");
		
		prof.startTick("ONESHOT_PRUNE");
		// NOTE: for TPCC, cons.size()==0, we skip this
		if (VeriConstants.INFER_RELATION_ON) {
			if (cons.size() > 0) {
				prof.startTick("ONESHOT_MM");
				ReachabilityMatrix rm = ReachabilityMatrix.getReachabilityMatrix(m_g.getGraph(), null);
				prof.endTick("ONESHOT_MM");

				Map<Long, Set<Long>> new_edges = Prune(m_g, cons, rm);
				// add to the real graph & complete graph
				for (long src : new_edges.keySet()) {
					for (long dst : new_edges.get(src)) {
						m_g.addEdge(src, dst, EdgeType.CONS_SOLV);
					}
				}

				// detect cycles after pruning
				boolean hasCycle = DFSCycleDetection.hasCycleHybrid(m_g.getGraph());
				if (hasCycle) {
					DFSCycleDetection.PrintOneCycle(m_g);
					prof.endTick("ONESHOT_PRUNE");
					return false;
				}
			}
		}
		prof.endTick("ONESHOT_PRUNE");	

		// TODO: put into HEAVY_CHECK
		if (VeriConstants.HEAVY_VALIDATION_CODE_ON) {
			CheckConstraints(m_g, cons);
		}

		prof.startTick("ONESHOT_SOLVE");
		boolean pass = true;
		if (VeriConstants.PCSG_ON) {
			Set<SCCNode> scc_list = GetIndependentClusters(m_g, cons);
			Set<Pair<PrecedenceGraph, Set<Constraint>>> subpolys = GenSubgraphs(m_g, cons, scc_list);
			for (Pair<PrecedenceGraph, Set<Constraint>> poly : subpolys) {
				boolean acyclic = solveConstraints(poly.getFirst(), poly.getSecond(), null);
				if (!acyclic) {pass =false; break;}
			}
		} else {
			pass = solveConstraints(m_g, cons, null);
		}
		prof.endTick("ONESHOT_SOLVE");
		return pass;
	}

	public boolean continueslyAudit() {
		assert false;
		return false;
	}


	@Override
	public boolean audit() {
		VeriConstants.BATCH_TX_VERI_SIZE = Integer.MAX_VALUE;
		Profiler prof = Profiler.getInstance();

		// (1)
		prof.startTick("ONESHOT_CONS");
		CreateKnownGraph();
		prof.endTick("ONESHOT_CONS");
		
		// (2)
		boolean pass = EncodeAndSolve();
		
		long cons = prof.getTime("ONESHOT_CONS") + prof.getTime("ONESHOT_GEN_CONS");
		long prune = prof.getTime("ONESHOT_PRUNE");
		long solve = prof.getTime("ONESHOT_SOLVE");
		
		ChengLogger.println("  construct: " + cons + "ms");
		ChengLogger.println("  prune: " + prune + "ms");
		ChengLogger.println("  solve: " + solve + "ms");
		return pass;
	}


	@Override
  public int[] count() {
	  return null;
  }

}
