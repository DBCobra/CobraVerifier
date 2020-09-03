package algo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;

import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

import graph.TxnNode;


// https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
public class TarjanStronglyConnectedComponents {
	
	private int index = 0;
	private Stack<NodeInfo> stack = new Stack<NodeInfo>();
	private Map<Long,NodeInfo> mapping = new HashMap<Long,NodeInfo>();
	
	private static final int UNDEFINED = -1;
	
	static class NodeInfo {
		public int index;
		public int lowlink;
		public boolean onstack;
		public TxnNode node;
		
		public NodeInfo(TxnNode node) {
			index = UNDEFINED;
			lowlink = UNDEFINED;
			onstack = false;
			this.node = node;
		}
	}
	
	// === non-recursive version ===
	
	static class SymElement {
		public boolean begin;
		public NodeInfo caller;
		public NodeInfo callee;
		public int[] status; /* 1: callee is UNDEFINED; 0: default/otherwise*/
		
		public SymElement(boolean begin, NodeInfo caller, NodeInfo callee, int[] ref_status) {
			this.begin = begin;
			this.caller = caller;
			this.callee = callee;
			this.status = ref_status;
		}
	}
	
	private void strongconnect(Graph<TxnNode> g, NodeInfo vxx, ArrayList<Set<TxnNode>> sccs) {
		Stack<SymElement> simulate_recursive = new Stack<SymElement>();
		int[] status = {0};
		simulate_recursive.push(new SymElement(false, null, vxx, status));
		simulate_recursive.push(new SymElement(true, null, vxx, status));
		Map<NodeInfo, Integer> node_counter = new HashMap<NodeInfo,Integer>();
		
		while(simulate_recursive.size() > 0) {
			SymElement action = simulate_recursive.pop();	
			//System.out.println("---["+action.begin+"] [" + (action.caller != null ? action.caller.node : "null") + "][" + action.callee.node + "]");
			
			// check if symbolic element
			if (action.begin) {
				NodeInfo cur_v = action.callee;
				if (!node_counter.containsKey(cur_v)) {
					node_counter.put(cur_v, new Integer(0));
				}
				node_counter.put(cur_v, node_counter.get(cur_v)+1);
				
				if (cur_v.index == UNDEFINED) {
					cur_v.index = index;
					cur_v.lowlink = index;
					index++;
					stack.push(cur_v);
					cur_v.onstack = true;

					// push all the nodes into the recursive stack
					for (TxnNode n : g.successors(cur_v.node)) {
						NodeInfo w = mapping.get(n.id());
						int[] tmp_status = { 0 };
						simulate_recursive.push(new SymElement(false, cur_v, w, tmp_status));
						simulate_recursive.push(new SymElement(true, cur_v, w, tmp_status));
					}
					// tell the end that this callee is UNDEFINED
					action.status[0] = 1;
				} else if (cur_v.onstack) {
					// NOTE: caller can be null, if it is the first action.
					// However, it should be never occur, since the vxx as input should has index==UNDEFINED
					assert action.caller != null;
					// Hmmm....TODO: think about this carefully...
					action.caller.lowlink = Math.min(action.caller.lowlink, cur_v.index);
				}
			} else {
				node_counter.put(action.callee, node_counter.get(action.callee)-1);
				
				if (action.caller != null && action.status[0] == 1) {
					action.caller.lowlink = Math.min(action.caller.lowlink, action.callee.lowlink);
				}
				
				NodeInfo cur_v = action.callee;
				if (node_counter.get(cur_v)==0 && cur_v.lowlink == cur_v.index && cur_v.onstack) {
					//System.out.println("new SCC! root= " + cur_v.node + ", lowlink=" + cur_v.lowlink + ", index=" + cur_v.index);
					Set<TxnNode> new_scc = new HashSet<TxnNode>();
					NodeInfo w = null;
					do {
						w = stack.pop();
						w.onstack = false;
						new_scc.add(w.node);
						// rule out this node
						node_counter.put(w, -1);
						//System.out.println(" =>" + w.node);
					} while (w != cur_v); // careful, they are ref here, should be fine, but scary
					sccs.add(new_scc);
				}
			}
		}
	}
	
	private void strongconnect_recursive(Graph<TxnNode> g, NodeInfo v, ArrayList<Set<TxnNode>> sccs) {
		v.index = index;
		v.lowlink = index;
		index++;
		stack.push(v);
		v.onstack = true;
		
		for (TxnNode n : g.successors(v.node)) {
			NodeInfo w = mapping.get(n.id());
			if (w.index == UNDEFINED) {
				strongconnect_recursive(g, w, sccs);
				v.lowlink = Math.min(v.lowlink, w.lowlink);
			} else if (w.onstack) {
				// Hmmm....TODO: think about this carefully...
				v.lowlink = Math.min(v.lowlink, w.index);
			}
		}
		
		if (v.lowlink == v.index) {
			Set<TxnNode> new_scc = new HashSet<TxnNode>();
			NodeInfo w = null;
			do {
				w = stack.pop();
				w.onstack = false;
				new_scc.add(w.node);
			} while (w != v); // careful, they are ref here, should be fine, but scary
			sccs.add(new_scc);
		}
	}
	
	public ArrayList<Set<TxnNode>> getSCCs(Graph<TxnNode> g, boolean recursive) {
		ArrayList<Set<TxnNode>> sccs = new ArrayList<Set<TxnNode>>();
		
		// prepare
		index = 0;
		stack.clear();
		mapping.clear();
		for (TxnNode n : g.nodes()) {
			mapping.put(n.id(), new NodeInfo(n));
		}
		
		// loop for all the nodes
		for (TxnNode n : g.nodes()) {
			NodeInfo v = mapping.get(n.id());
			if (v.index == UNDEFINED) {
				if (recursive) {
					strongconnect_recursive(g, v, sccs);
				} else {
					strongconnect(g, v, sccs);
				}
			}
		}
		
		return sccs;
	}
	
	public static void main(String args[]) {
	
		/*
		AdjacentListGraph g = new AdjacentListGraph();
		TxnNode n1 = new GNode(1);
		GNode n2 = new GNode(2);
		GNode n3 = new GNode(3);
		GNode n4 = new GNode(4);
		GNode n5 = new GNode(5);
		g.insertNode(n1);
		g.insertNode(n2);
		g.insertNode(n3);
		g.insertNode(n4);
		g.insertNode(n5);
		g.insertEdge(n1, n2);
		g.insertEdge(n2, n1);
		g.insertEdge(n2, n3);
		g.insertEdge(n3, n4);
		g.insertEdge(n4, n2);
		g.insertEdge(n4, n5);
		System.out.println(g.dumpGraph());
		

		TarjanStronglyConnectedComponents tscc = new TarjanStronglyConnectedComponents();
		ArrayList<Set<GNode>> xx = tscc.getSCCs(g, false);
		System.out.println(xx);
		*/
		

		
		MutableGraph<TxnNode> gg = GraphBuilder.directed().build();
		int num_nodes = 100, num_edges = 500;
		TxnNode[] nodes = new TxnNode[num_nodes];
		for(int i=0; i<num_nodes; i++) {
			nodes[i] = new TxnNode(i);
			gg.addNode(nodes[i]);
		}
		Random rand = new Random();
		for(int i=0; i<num_edges; i++) {
			gg.putEdge(nodes[rand.nextInt(num_nodes)], nodes[rand.nextInt(num_nodes)]);
		}
		
		System.out.println("---1---");
		TarjanStronglyConnectedComponents tscc = new TarjanStronglyConnectedComponents();
		ArrayList<Set<TxnNode>> xx = tscc.getSCCs(gg, true);
		System.out.println(xx);
		
		System.out.println("---2---");
		TarjanStronglyConnectedComponents tscc2 = new TarjanStronglyConnectedComponents();
		ArrayList<Set<TxnNode>> xx2 = tscc2.getSCCs(gg, false);
		System.out.println(xx2);
		
		boolean same = true;
		
		for (Set<TxnNode> set : xx) {
			for (Set<TxnNode> set2 : xx2) {
				// check if set2 == set
				TxnNode first_n = set2.iterator().next();
				if (!set.contains(first_n)) continue;
				for (TxnNode n : set2) {
					if (!set.contains(n)) {
						same = false;
					}
				}
			}
		}
		
		if (same) {
			System.out.println("PASS");
		} else {
			System.out.println("SHIT");
		}
		
		
		
	}

}
