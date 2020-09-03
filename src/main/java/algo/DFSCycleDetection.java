package algo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

import graph.PrecedenceGraph;
import graph.TxnNode;
import util.ChengLogger;
import util.VeriConstants.LoggerType;
import verifier.Constraint;
import verifier.PolyGraphDumper;

public class DFSCycleDetection {
	
	// a cycle this detector last found
	private static ArrayList<Long> last_cycle = null;
	
	public static ArrayList<Long> getLastCycle() {
		return last_cycle;
	}
	
	//============Ungly impl==================
	public static class Symbol extends TxnNode {
		public long monkId;
		public Symbol(long id) {
			super(id);
			monkId = id;
		}
		public long id() {
			return -1;
		}
		public String toString() {
			return super.toString()+"-S";
		}
	};
	// ==============================
	
	private static ArrayList<Symbol> getCycle(Stack<TxnNode> stack, HashMap<Long, Boolean> on_path, TxnNode next_node) {
		ArrayList<Symbol> cycle = new ArrayList<Symbol>();
		while(true) {
			if (stack.peek().id() != -1) {
				// pop twice
				long id = stack.pop().id();
				TxnNode sym_node = stack.pop();
				assert sym_node.id() == -1; // must be a symbol
				assert ((Symbol)sym_node).monkId == id; // must be the same with prev node
			} else {
				// a symobl on top of stack
				Symbol sym_node = (Symbol)stack.pop();
				assert on_path.containsKey(sym_node.monkId); // a single symobl must be on the path
				cycle.add(sym_node);
				if (sym_node.monkId == next_node.id()) {
					break;
				}
			}
		}
		// reverse the array
		ArrayList<Symbol> ret = new ArrayList<Symbol>();
		for (int i=cycle.size()-1; i>=0; i--) {
			ret.add(cycle.get(i));
		}
		assert ret.get(0).monkId == next_node.id();
		return ret;
	}
	
	/*
	  1  procedure DFS-iterative(G,v):
		2      let S be a stack
		3      S.push(v)
		4      while S is not empty
		5          v = S.pop()
		6          if v is not labeled as discovered:
		7              label v as discovered
		8              for all edges from v to w in G.adjacentEdges(v) do 
		9                  S.push(w)
		*/
	public static boolean DFS(Graph<TxnNode> g, TxnNode node, HashMap<Long, Boolean> visited)	{
		if (visited.get(node.id())) {return false;}
		// The invariant is:
		// all the node with only Symbol (not real node) on the stack should be in the path (in onPath).
		HashMap<Long, Boolean> onPath = new HashMap<Long,Boolean>(); // Maybe too costly?
		Stack<TxnNode> stack = new Stack<TxnNode>(); // Maybe too costly?
		stack.push(new Symbol(node.id()));
		stack.push(node);
		assert(visited.get(node.id()) == false);
		
		while (!stack.isEmpty()) {
			TxnNode v = stack.pop();
			// if symbolic, update the path information
			if (v.id() == -1) {
				// this is symbolic
				long sym_id = ((Symbol)v).monkId;
				// this node leave the path
				onPath.put(sym_id, false);
				continue;
			}
			
			// record this node on the path
			onPath.put(v.id(), true);
			
			if (!visited.get(v.id())) {
				visited.put(v.id(), true);
				Set<TxnNode> successors = g.successors(v);
				for (TxnNode w : successors) {
					
					if (onPath.containsKey(w.id()) && onPath.get(w.id())==true) {
						// get the cycle
						ArrayList<Symbol> cycle = getCycle(stack, onPath, w);
						// store the cycle
						last_cycle = new ArrayList<Long>();
						for (Symbol s: cycle) {
							last_cycle.add(s.monkId);
						}
						last_cycle.add(w.id());
						// we found a cycle
						return true;
					}
					
					if (!visited.get(w.id())) {
						stack.push(new Symbol(w.id()));
						stack.push(w);
					}
				}
			}
	
		}
		return false;
	}
	
	public static boolean hasCycleHybrid(Graph<TxnNode> g) {
		try {
			boolean hasCycle = Graphs.hasCycle(g);
			return hasCycle;
		} catch (StackOverflowError e) { // Guava might stack overflow
			return DFSCycleDetection.CycleDetection(g);
		}
	}
	
	public static boolean CycleDetection(Graph<TxnNode> g) {
		
		HashMap<Long, Boolean> visited = new HashMap<Long, Boolean>();
		
		// O(n) to init visited and find the source
		for (TxnNode n : g.nodes()) {
			// update the visit status to false
			visited.put(n.id(), false);
		}
		
		// do DFS on each of 0-in-gress node
		for (TxnNode node : g.nodes())  {
			boolean hasCycle = DFS(g, node, visited);
			if (hasCycle) return true;
		}
		return false;
	}
	
	public static void PrintOneCycle(PrecedenceGraph g) {
		last_cycle = null;
		boolean hascycle = CycleDetection(g.getGraph());
		if (hascycle != Graphs.hasCycle(g.getGraph())) {
			System.out.println("GOOGLE disagree: " + Graphs.hasCycle(g.getGraph()));		
			assert false;
		}
		assert hascycle;
		assert last_cycle != null;
		
		ChengLogger.println(LoggerType.ERROR, "========= Cycle in the known graph ============");
		ChengLogger.println(LoggerType.ERROR, "  === 1. cycle ==");
		ArrayList<TxnNode> txns = new ArrayList<TxnNode>();
		StringBuilder sb = new StringBuilder("  ");
		for (long tid : last_cycle) {
			txns.add(g.getNode(tid));
			sb.append("T[" + Long.toHexString(tid) + "] -> ");
		}
		sb.append("END");
		ChengLogger.println(LoggerType.ERROR, sb.toString());
		ChengLogger.println(LoggerType.ERROR, "  === 2. transaction details ==");
		for (TxnNode t : txns) {
			ChengLogger.println(LoggerType.ERROR, "  " + t.toString2());
		}
	}
	
	
	/*
	ArrayList<TxnNode> list;
	HashMap<TxnNode, Integer> t2id;
	
  // This function is a variation of DFSUytil() in  
  // https://www.geeksforgeeks.org/archives/18212 
  private boolean isCyclicUtil(int i, boolean[] visited, 
                                    boolean[] recStack, Graph<TxnNode> g)  
  { 
        
      // Mark the current node as visited and 
      // part of recursion stack 
      if (recStack[i]) 
          return true; 

      if (visited[i]) 
          return false; 
            
      visited[i] = true; 

      recStack[i] = true; 
      TxnNode cur = list.get(i);
      
      
      List<Integer> children = new ArrayList<Integer>();
      for (TxnNode succ : g.successors(cur)) {
      		children.add(t2id.get(succ));
      }
        
      for (Integer c: children) 
          if (isCyclicUtil(c, visited, recStack, g)) 
              return true; 
                
      recStack[i] = false; 

      return false; 
  } 

  // Returns true if the graph contains a  
  // cycle, else false. 
  // This function is a variation of DFS() in  
  // https://www.geeksforgeeks.org/archives/18212 
  private boolean isCyclic(Graph<TxnNode> g)  
  { 
        int n = g.nodes().size();
       list = new ArrayList<TxnNode>(g.nodes());
       t2id = new HashMap<TxnNode,Integer>();
       for (int i=0; i<list.size(); i++) {
      	 	t2id.put(list.get(i), i);
       }
      // Mark all the vertices as not visited and 
      // not part of recursion stack 
      boolean[] visited = new boolean[n]; 
      boolean[] recStack = new boolean[n]; 
        
        
      // Call the recursive helper function to 
      // detect cycle in different DFS trees 
      for (int i = 0; i < n; i++) 
          if (isCyclicUtil(i, visited, recStack, g)) 
              return true; 

      return false; 
  }
  */
}
