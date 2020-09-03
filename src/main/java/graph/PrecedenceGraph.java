package graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraph;
import com.google.common.graph.ValueGraphBuilder;

import gpu.ReachabilityMatrix;
import util.ChengLogger;
import util.Profiler;
import util.VeriConstants;
import util.VeriConstants.LoggerType;


// a wrapper for graph
public class PrecedenceGraph {
	
	private MutableGraph<TxnNode> g;
	// mapping txnid=>node
	private HashMap<Long,TxnNode> tindex;
	// read-from dependency, wid => {OpNode, ...}
	public Map<Long, Set<OpNode>> m_readFromMapping = null;
	
	// remember all the WW dependencies
	// NOTE: this is previous *write id* to current *write id* (not txn id)
	private HashMap<Long,Long> wwpairs;
	private HashMap<Long,Long> rev_wwpairs; // in order to efficiently delete a node

	
	
	// ======Constructors========
	
	public PrecedenceGraph() {
		g = GraphBuilder.directed().allowsSelfLoops(false).build();
		tindex = new HashMap<Long, TxnNode>();
		m_readFromMapping = new HashMap<Long,Set<OpNode>>();
		wwpairs = new HashMap<Long,Long>();
		rev_wwpairs = new HashMap<Long,Long>();
	}
	
	// the clone function
	public PrecedenceGraph(PrecedenceGraph old_graph) {
		// get an empty graph
		g = GraphBuilder.directed().allowsSelfLoops(false)
				.expectedNodeCount(old_graph.g.nodes().size())
				.expectedNodeCount(old_graph.g.edges().size())
				.build();
		
		// rebuild the nodes
		tindex = new HashMap<Long, TxnNode>();
		for (long txnid : old_graph.tindex.keySet()) {
			// clone the txnNode using new node from "g"
			TxnNode txn_new = new TxnNode(old_graph.tindex.get(txnid));
			// add it to graph
			g.addNode(txn_new);
			// add it to the current precedence graph
			tindex.put(txnid, txn_new);
		}
		
		// rebuild the edges
		for (EndpointPair<TxnNode> e : old_graph.allEdges()) {
			TxnNode new_src = tindex.get(e.source().getTxnid());
			TxnNode new_dst = tindex.get(e.target().getTxnid());
			// FIXME: how to retrieve value of the edge?
			g.putEdge(new_src, new_dst);
		}
		
		// rebuild the read-from mapping
		m_readFromMapping = new HashMap<Long,Set<OpNode>>();
		for (long wid : old_graph.m_readFromMapping.keySet()) {
			m_readFromMapping.put(wid, new HashSet<OpNode>(old_graph.m_readFromMapping.get(wid)));
		}
		
		// rebuild the wwpairs
		wwpairs = new HashMap<Long,Long>(old_graph.wwpairs);
		rev_wwpairs = new HashMap<Long,Long>(old_graph.rev_wwpairs);
	}
	
	// FIXME: UGLY CODE!
	// the no-clone clone function
	public PrecedenceGraph(PrecedenceGraph old_graph, boolean clone) {
		// get an empty graph
		g = GraphBuilder.directed().allowsSelfLoops(false)
				.expectedNodeCount(old_graph.g.nodes().size())
				.expectedNodeCount(old_graph.g.edges().size())
				.build();
		
		// rebuild the nodes
		tindex = new HashMap<Long, TxnNode>();
		for (long txnid : old_graph.tindex.keySet()) {
			// clone the txnNode using new node from "g"
			TxnNode txn = old_graph.tindex.get(txnid);
			// add it to graph
			g.addNode(txn);
			// add it to the current precedence graph
			tindex.put(txnid, txn);
		}
		
		// rebuild the edges
		for (EndpointPair<TxnNode> e : old_graph.allEdges()) {
			TxnNode new_src = tindex.get(e.source().getTxnid());
			TxnNode new_dst = tindex.get(e.target().getTxnid());
			// FIXME: how to retrieve value of the edge?
			g.putEdge(new_src, new_dst);
		}
		
		// rebuild the read-from mapping
		m_readFromMapping = new HashMap<Long,Set<OpNode>>();
		for (long wid : old_graph.m_readFromMapping.keySet()) {
			m_readFromMapping.put(wid, new HashSet<OpNode>(old_graph.m_readFromMapping.get(wid)));
		}
		
		// rebuild the wwpairs
		wwpairs = new HashMap<Long,Long>(old_graph.wwpairs);
		rev_wwpairs = new HashMap<Long,Long>(old_graph.rev_wwpairs);
	}
	
	// ======Graph=======
	
	public MutableGraph<TxnNode> getGraph() {
		return g;
	}
	

	// =======Nodes=========
	
	public Collection<TxnNode> allNodes() {
		return tindex.values();
	}
	
	public Set<Long> allTxnids() {
		return tindex.keySet();
	}
	
	public boolean containTxnid(long id) {
		return tindex.containsKey(id);
	}
	
	public TxnNode getNode(long id) {
		if (tindex.containsKey(id)) {
			return tindex.get(id);
		}
		return null;
	}
	
	public void addTxnNode(TxnNode n) {
		// because of the inconsistency of logs, we may create an empty node,
		assert !tindex.containsKey(n.getTxnid());
		g.addNode(n);
		tindex.put(n.getTxnid(), n);
	}
	
	public Set<TxnNode> successors(TxnNode n) {
		return g.successors(n);
	}
	
	public Set<TxnNode> predecessors(TxnNode n) {
		return g.predecessors(n);
	}
	
	public int numPredecessor(TxnNode n) {
		return g.inDegree(n);
	}
	
	public int numSuccessor(TxnNode n) {
		return g.outDegree(n);
	}
	
	public void deleteNodeSimple(TxnNode n) {
		assert n.getTxnid() != VeriConstants.INIT_TXN_ID;
		
		// NOTE: we need also to update the prev/next client txns!
		long prev_tid = n.getPrevClientTxn();
		long next_tid = n.getNextClientTxn();
		if (prev_tid != VeriConstants.NULL_TXN_ID) {
			assert tindex.containsKey(prev_tid);
			getNode(prev_tid).setNextClientTxn(next_tid);
		}
		if (next_tid != VeriConstants.NULL_TXN_ID) {
			assert tindex.containsKey(next_tid);
			getNode(next_tid).setPrevClientTxn(prev_tid);
		}
		// for all ops, remove from m_readFromMapping
		for (OpNode op : n.getOps()) {	
			if (op.isRead) {
				long prev_wid = op.wid;	
				if (m_readFromMapping.containsKey(prev_wid)) {			
					boolean done = m_readFromMapping.get(prev_wid).remove(op);
					if(!done) {
						//System.out.println(n.toString2());
						assert op.read_from_txnid == VeriConstants.INIT_TXN_ID; // FIXME: why?
					}
					// if empty, remove this
					if (m_readFromMapping.get(prev_wid).size() == 0) {
						m_readFromMapping.remove(prev_wid);
					}
				}
			} else {
				// remove the write directly
				m_readFromMapping.remove(op.wid);
			}
		}
		// connect wwpair, if possible
		//  w1-ww->w2-ww->w3  ==[delete w2]==> w1-ww->w3
		for (OpNode op : n.getOps()) {
			if (!op.isRead) {
				long wid = op.wid;
				Long prev_wid = rev_wwpairs.get(wid);
				Long next_wid = wwpairs.get(wid);
				
				if (prev_wid != null) {
					assert wwpairs.get(prev_wid) == wid;
					wwpairs.remove(prev_wid);
					rev_wwpairs.remove(wid);
				}
				if (next_wid != null) {
					assert rev_wwpairs.get(next_wid) == wid;
					wwpairs.remove(wid);
					rev_wwpairs.remove(next_wid);
				}
				// FIXME: is it possible that w1-ww->w2, but in precedence graph w1 cannot reach w2?
				if (prev_wid != null && next_wid != null) {
					wwpairs.put(prev_wid, next_wid);
					rev_wwpairs.put(next_wid, prev_wid);
				}			
			}
		}
		// remove from the graph
		g.removeNode(n);
		tindex.remove(n.getTxnid());
	}
	
	public void deleteNodeConnected(TxnNode n) {
		Profiler prof = Profiler.getInstance();
	
		// connect the immediate predecessors and immediate successors
		for (TxnNode pred : predecessors(n)) {
			// all txns are the successors of INIT
			if (pred.getTxnid() == VeriConstants.INIT_TXN_ID) {continue;}
			for (TxnNode succ : successors(n)) {
				this.addEdge(pred, succ, EdgeType.DEL_CONNECTED);
			}
		}

		// delete the node from the Precedencegraph
		deleteNodeSimple(n);
	}
	
	// =====Edges=====
	
	public Set<EndpointPair<TxnNode>> allEdges() {
		return g.edges();
	}
	
	public void addEdge(long fr, long to, EdgeType et) {
		assert tindex.containsKey(fr) && tindex.containsKey(to);
		TxnNode src = tindex.get(fr);
		TxnNode dst = tindex.get(to);
		g.putEdge(src, dst);
	}
	
	public void addEdge(TxnNode fr, TxnNode to, EdgeType et) {
		assert tindex.containsKey(fr.getTxnid()) && tindex.containsKey(to.getTxnid());
		g.putEdge(fr, to);
	}
	
	// =====WR dependency=====
	
	public Set<OpNode> getReadFromNodes(long wid) {
		if (m_readFromMapping.containsKey(wid)) {
			return m_readFromMapping.get(wid);
		}
		return null;
	}
	
	// =====WW dependency=====
	
	public Map<Long, Long> getWWpairs() {
		return wwpairs;
	}
	
	public Map<Long, Long> getRevWWparis() {
		return rev_wwpairs;
	}
	
	public long getWW(long wid) {
		if (wwpairs.containsKey(wid)) {
			return wwpairs.get(wid);
		}
		return VeriConstants.MISSING_WRITE_ID;
	}
	
	public long getPrevWW(long wid) {
		if (rev_wwpairs.containsKey(wid)) {
			return rev_wwpairs.get(wid);
		}
		return VeriConstants.MISSING_WRITE_ID;
	}
	
	public void addWW(long prev_wid, long cur_wid, long key) {
		if (prev_wid == VeriConstants.MISSING_WRITE_ID || prev_wid == VeriConstants.NULL_WRITE_ID) return;
		// NOTE: prev_wid == INIT_WRITE_ID should be included here
		
		if (wwpairs.containsKey(prev_wid)) {
			if (cur_wid != wwpairs.get(prev_wid)) {
				ChengLogger.println(LoggerType.ERROR, Long.toHexString(prev_wid) +
						"=> cur_wid[" + Long.toHexString(cur_wid) +
						"], prev_nxt_wid[" + Long.toHexString(wwpairs.get(prev_wid))+"]");
				// details
				
				// find txnid for these wids
				long prev_nxt_wid = wwpairs.get(prev_wid);
				TxnNode prev_node = null;  // prev_wid
				TxnNode one_succ = null;    // prev_nxt_wid
				TxnNode another_succ = null; // cur_wid
				// FIXME: we have a duplicated wid problem
				for (TxnNode txn : g.nodes()) {
					for (OpNode op : txn.getOps()) {
						if (op.isRead) {continue;}
						if (op.wid == prev_wid) {
							//assert prev_node == null;
							prev_node = txn;
							ChengLogger.println(LoggerType.ERROR, "prev write\n  " + prev_node.toString2());
						}
						if (op.wid == prev_nxt_wid) {
							//assert one_succ == null;
							one_succ = txn;
							ChengLogger.println(LoggerType.ERROR, "one successor\n  " + one_succ.toString2());
						}
						if (op.wid == cur_wid) {
							//assert another_succ == null;
							another_succ = txn;
							ChengLogger.println(LoggerType.ERROR, "another successor\n  " + another_succ.toString2());
						}
					}
				}
			}
			assert cur_wid == wwpairs.get(prev_wid);
			assert prev_wid == rev_wwpairs.get(cur_wid);
		} else {
			wwpairs.put(prev_wid, cur_wid);
			rev_wwpairs.put(cur_wid, prev_wid);
		}
	}
	
	// =====subgraph=====
	/*
	// mapping txnid=>node
	private HashMap<Long,TxnNode> txns;
	
	// remember all the WW dependencies
	// NOTE: this is previous *write id* to current *write id* (not txn id)
	private HashMap<Long,Long> wwpairs;
	*/
	
	public PrecedenceGraph subgraph(Set<Long> sub_txnids) {
		PrecedenceGraph pg = new PrecedenceGraph();
		Set<Long> wid_set = new HashSet<Long>();
		Map<Long, OpNode> read_set = new HashMap<Long, OpNode>();
		
		// add all vertices
		for (long txnid : sub_txnids) {
			// clone the txnNode using new node from "g"
			TxnNode txn_new = new TxnNode(this.tindex.get(txnid));
			pg.addTxnNode(txn_new);
			// construct read_set/wid_set, for latter use
			for (OpNode op : txn_new.getOps()) {
				if (op.isRead) {				
					assert !read_set.containsKey(op.id()); // op id should not duplicate
					read_set.put(op.id(), op);
				} else {
					wid_set.add(op.wid);
				}
			}
		}
		
		// add all edges
		for (long txnid : sub_txnids) {
			// we only care the outdegree for each node
			TxnNode fr = this.tindex.get(txnid);
			for (TxnNode to : g.successors(fr)) {
				long to_id = to.getTxnid();
				// if the other side is also in the subgraph, add one edge
				if (sub_txnids.contains(to_id)) {
					// UTBABUG: use the subgraph's own nodes
					TxnNode new_fr = pg.getNode(fr.id());
					TxnNode new_to = pg.getNode(to.id());
					// FIXME: EdgeType problem
					pg.addEdge(new_fr, new_to, EdgeType.INIT);
				}
			}
		}
		
		// construct readFromMapping, wwpairs and rev_wwpairs
		for (long txnid : sub_txnids) {
			for (OpNode op : pg.getNode(txnid).getOps()) {
				if (!op.isRead) {
					// construct readFromMapping
					if (m_readFromMapping.containsKey(op.wid)) {
						assert !pg.m_readFromMapping.containsKey(op.wid); // should never meet this wid
						Set<OpNode> tmp_readset = new HashSet<OpNode>();
						for (OpNode rop : m_readFromMapping.get(op.wid)) {
							// UTBABUG: "pg" and "this" have different OpNodes.
							if (read_set.keySet().contains(rop.id())) {
								OpNode pg_rop = read_set.get(rop.id());
								tmp_readset.add(pg_rop);
							}
						}
						if (tmp_readset.size() > 0) {
							pg.m_readFromMapping.put(op.wid, tmp_readset);
						}
					}
					// update WW pairs
					// add a WW pair when both the fr-write and to-write are in current subgraph
					if (this.wwpairs.containsKey(op.wid)) {
						long to_wid = this.wwpairs.get(op.wid);
						if (wid_set.contains(to_wid)) {
							pg.wwpairs.put(op.wid, to_wid);
							pg.rev_wwpairs.put(to_wid, op.wid);
						}
					}
				}
			}
		}
		
		if (VeriConstants.HEAVY_VALIDATION_CODE_ON) {
			pg.ValidateGraph(true);
		}

		return pg;
	}
	

	public PrecedenceGraph subgraphNoClone(Collection<TxnNode> sub_txns) {
		PrecedenceGraph pg = new PrecedenceGraph();
		
		// construct the subgraph
		pg.g = Graphs.inducedSubgraph(this.g, sub_txns);

		// (1) construct tidex
		// (2) construct the OpId vs. TxnNode
		Set<Long> wid_set = new HashSet<Long>();
		Map<Long, OpNode> read_set = new HashMap<Long, OpNode>();
		for (TxnNode txn : sub_txns) {
			pg.tindex.put(txn.getTxnid(), txn);
			// construct read_set/wid_set, for latter use
			for (OpNode op : txn.getOps()) {
				if (op.isRead) {				
					assert !read_set.containsKey(op.id()); // op id should not duplicate
					read_set.put(op.id(), op);
				} else {
					wid_set.add(op.wid);
				}
			}
		}

		// (3) construct readFromMapping, wwpairs and rev_wwpairs
		for (TxnNode txn : sub_txns) {
			for (OpNode op : txn.getOps()) {
				if (!op.isRead) {
					// construct readFromMapping
					if (m_readFromMapping.containsKey(op.wid)) {	
						assert !pg.m_readFromMapping.containsKey(op.wid); // should never meet this wid
						Set<OpNode> tmp_readset = new HashSet<OpNode>();
						for (OpNode rop : m_readFromMapping.get(op.wid)) {
							if (read_set.keySet().contains(rop.id())) {
								OpNode pg_rop = read_set.get(rop.id());
								tmp_readset.add(pg_rop);
							}
						}
						if (tmp_readset.size() > 0) {
							pg.m_readFromMapping.put(op.wid, tmp_readset);
						}
					}
					// update WW pairs
					// add a WW pair when both the fr-write and to-write are in current subgraph
					if (this.wwpairs.containsKey(op.wid)) {
						long to_wid = this.wwpairs.get(op.wid);
						if (wid_set.contains(to_wid)) {
							pg.wwpairs.put(op.wid, to_wid);
							pg.rev_wwpairs.put(to_wid, op.wid);
						}
					}
				}
			}
		}
		
		if (VeriConstants.HEAVY_VALIDATION_CODE_ON) {
			pg.ValidateGraph(true);
		}

		return pg;
	}
	
	public void ValidateGraph(boolean subgraph) {
		// make sure no write wid == INIT_WID
		assert !m_readFromMapping.containsKey(VeriConstants.INIT_WRITE_ID);
		// make sure no null-txn
		assert !this.containTxnid(VeriConstants.NULL_TXN_ID);
		// make sure that "g" is consistent with (<~>) "tindex"
		assert tindex.size() == g.nodes().size();
		for (TxnNode n : g.nodes()) {
			long tid = n.getTxnid();
			assert n == tindex.get(tid);
		}
		Set<Long> all_wids = new HashSet<Long>();
		// make sure "m_readFromMapping" <~> "g"
		// all read operations are in "m_readFromMapping"
		// NOTE: "m_readFromMapping" can be inconsistent with "g" for writes
		//   there might be writes in "m_readFromMapping",
		//   but the txn in "g" is an empty one
		for (TxnNode n : g.nodes()) {
			for (OpNode op : n.getOps()) {
				if (op.isRead) {
					long prev_wid = op.wid;
					// this graph might not contain the transaction
					// because of deletion or subgraph
					if (m_readFromMapping.containsKey(prev_wid)) {
						Set<OpNode> list = m_readFromMapping.get(prev_wid);
						assert list.contains(op);
					}
				} else {
					all_wids.add(op.wid);
				}
			}
		}
		// make sure "wwpairs" <~> "rev_wwpairs" <~> "g"
		assert wwpairs.size() == rev_wwpairs.size();
		for (long prv : wwpairs.keySet()) {
			long nxt = wwpairs.get(prv);
			assert rev_wwpairs.get(nxt) == prv;
			assert all_wids.contains(nxt);
			// NOTE: this is not necessarily true, because we can see the read, but not the
			// write this read reads-from.
			//assert all_wids.contains(prv);
		}
		
		if (subgraph) return;
		
		// make sure INIT exists
		assert this.containTxnid(VeriConstants.INIT_TXN_ID);
		// make sure "TxnNode.pre/next" <~> "g"
		// txns belong to one client should in one chain
		Map<Integer,TxnNode> first_txn = new HashMap<Integer, TxnNode>();
		Map<Integer, Integer> num_per_client = new HashMap<Integer, Integer>();
		for (TxnNode n : this.allNodes()) {
			int cid = n.getClientId();
			if (!first_txn.containsKey(cid)) {
				num_per_client.put(cid, 1);
				TxnNode tmp = n;
				while(tmp.getPrevClientTxn() != VeriConstants.NULL_TXN_ID) {
					tmp = tindex.get(tmp.getPrevClientTxn());
				}
				first_txn.put(cid, tmp);
			} else {
				num_per_client.put(cid, num_per_client.get(cid) + 1);
			}
		}
		// #nodes in "g" <~> num of txn
		int total_txns = 0;
		for (int num_tx_in_c : num_per_client.values()) {
			total_txns += num_tx_in_c;
		}
		assert total_txns == this.allNodes().size();
		
		// one client should have one chain
		for (TxnNode f : first_txn.values()) {
			int cid = f.getClientId();
			if (cid == VeriConstants.TXN_NULL_CLIENT_ID) continue;
			int counter = 1;
			TxnNode tmp = f;
			while(tmp.getNextClientTxn() != VeriConstants.NULL_TXN_ID) {
				counter++;
				tmp = tindex.get(tmp.getNextClientTxn());
			}
			
			
			
			if (counter != num_per_client.get(cid)) {
				System.out.println("counte => " + counter + ",   num_per_client=>" + num_per_client.get(cid));
				System.out.println("  cid = " + cid);
				System.out.println("KNOWN first=> " + f);
				tmp = f;
				while(tmp.getNextClientTxn() != VeriConstants.NULL_TXN_ID) {
					tmp = tindex.get(tmp.getNextClientTxn());
					System.out.println("KNOWN => " + tmp);
				}
				//
				for (TxnNode n : allNodes()) {
					if (n.client_id == cid) {
						System.out.println("ALL => " + n);
					}
				}
			}
			
			
			
			
			
			assert counter == num_per_client.get(cid);
		}
	}
	
	public String toString() {
		StringBuilder ret = new StringBuilder();
		ret.append("PrecedenceGraph. #txn=" + allNodes().size() + " #edges=" + allEdges().size() + "\n");
		ret.append("                 #ww_pairs=" + wwpairs.size() + "\n");
		ret.append("                 #w_has_read=" + m_readFromMapping.size() + "\n");
		return ret.toString();
	}
	
	public String toString2() {
		return g.toString();
	}
	
	// complete the incomplete precedence graph
	public static PrecedenceGraph CompletePrecedenceGraph(
			PrecedenceGraph in_pg,
			ArrayList<LinkedList<OpNode>> write_orders)
	{
		// clone graph
		PrecedenceGraph comp_pg = new PrecedenceGraph(in_pg);
		
		// add WW-edge for each WW-pair
		for (LinkedList<OpNode> wlist : write_orders) {
			long key = wlist.get(0).key_hash;
			OpNode prev = null, cur = null;
			for (OpNode wnode : wlist) {
				assert key == wnode.key_hash;
				if (prev == null) {
					prev = wnode;
					continue;
				}
				cur = wnode;
				
				long prev_txnid = prev.txnid;
				long prev_wid = prev.wid;
				long cur_txnid = cur.txnid;
				
				// add WW-edge for the txns
				if (prev_txnid != cur_txnid) {
					comp_pg.addEdge(prev_txnid, cur_txnid, EdgeType.WW);
				}
				
				// if there are R-op reads-from prev write, should add RW-edge
				if (in_pg.m_readFromMapping.containsKey(prev_wid)) {
					for (OpNode op : in_pg.m_readFromMapping.get(prev_wid)) {	
						// UTBABUG: readFromMapping can have nodes beyond current subgraph
						// FIXME: readFromMapping should be combined with PrecedenceGraph!
						if (comp_pg.getNode(op.txnid)!=null && // if subgraph has this node
								op.txnid != cur_txnid)             // if they do not belong to the same txn
						{
							comp_pg.addEdge(op.txnid, cur_txnid, EdgeType.RW);
						}
					}
				}
				
				prev = cur;
			}
		}
		
		return comp_pg;
	}
	
}
