package verifier;

import static monosat.Logic.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.graph.EndpointPair;

import graph.PrecedenceGraph;
import graph.TxnNode;
import monosat.Graph;
import monosat.Lit;
import monosat.Solver;
import util.Pair;
import util.Profiler;
import util.VeriConstants;

public class SMTEncoder {
	
	Solver solver;
	Map<Long,Integer> txnid2mnode;
	Map<Lit, EndpointPair<TxnNode>> edge_lits;
	Map<Lit, Constraint> cons_lits;
	Lit acyclic_lit;
	
	public SMTEncoder(PrecedenceGraph g, Set<Constraint> cons) {
		solver = new Solver();
		// txnid => node
		txnid2mnode = new HashMap<Long,Integer>();
		// edge's lit => real edge
		edge_lits = new HashMap<Lit, EndpointPair<TxnNode>>();
		// constraint lit => real constraint
		cons_lits = new HashMap<Lit, Constraint>();
		
		
		init(g, cons);
	}

	private Lit consLit(Constraint c, Graph monog, Map<Long,Integer> txnid2mnode) {		
		Lit l_all = Lit.True;
		Lit l_nothing = Lit.True;
		for (Pair<Long,Long> e : c.edge_set1) {
			int src = txnid2mnode.get(e.getFirst());
			int dst = txnid2mnode.get(e.getSecond());
			Lit e_var = monog.addEdge(src, dst);
			l_all = and(l_all, e_var);
			l_nothing = and(l_nothing, not(e_var));
		}
		
		Lit r_all = Lit.True;
		Lit r_nothing = Lit.True;
		for (Pair<Long,Long> e : c.edge_set2) {
			int src = txnid2mnode.get(e.getFirst());
			int dst = txnid2mnode.get(e.getSecond());
			Lit e_var = monog.addEdge(src, dst);
			r_all = and(r_all, e_var);
			r_nothing = and(r_nothing, not(e_var));
		}
		
		Lit cons_lit = or(and(l_all, r_nothing), and(r_all, l_nothing));
		
		return and(cons_lit, Lit.True); // cons must be sat
	}
	
	private void init(PrecedenceGraph g, Set<Constraint> cons) {
		Profiler prof = Profiler.getInstance();
		prof.startTick(VeriConstants.PROF_MONOSAT_1);
		
		Graph monog = new Graph(solver);
		
		// add nodes
		for (TxnNode t : g.allNodes()) {
			int n = monog.addNode();
			txnid2mnode.put(t.getTxnid(), n);
		}
		
		// edge Lits
		for (EndpointPair<TxnNode> e : g.allEdges()) {
			int src = txnid2mnode.get(e.source().getTxnid());
			int dst = txnid2mnode.get(e.target().getTxnid());
			Lit edge = monog.addEdge(src, dst);
			Lit edge_true = and(edge, Lit.True); // this edge is in the known graph; hence should be true
			edge_lits.put(edge_true, e);
		}
		
		// constraint Lits
		for (Constraint c : cons) {
			Lit cons_lit = consLit(c, monog, txnid2mnode);
			cons_lits.put(cons_lit, c);
		}
		
		// add acyclic lit
		acyclic_lit = and(monog.acyclic(), Lit.True);
		
		prof.endTick(VeriConstants.PROF_MONOSAT_1);
	}
	
	public boolean Solve() {
		Profiler prof = Profiler.getInstance();
		prof.startTick(VeriConstants.PROF_MONOSAT_2);
		
		// prepare Lits
		ArrayList<Lit> lits = new ArrayList<Lit>();
		lits.add(acyclic_lit);
		lits.addAll(edge_lits.keySet());
		lits.addAll(cons_lits.keySet());
		
		boolean ret = solver.solve(lits);
		prof.endTick(VeriConstants.PROF_MONOSAT_2);
		
		return ret;
	}
	
	public void GetMinUnsatCore(List<EndpointPair<TxnNode>> out_edges, List<Constraint> out_cons) {
		assert out_edges.isEmpty() && out_cons.isEmpty();
		
		// prepare Lits
		ArrayList<Lit> lits = new ArrayList<Lit>();
		lits.add(acyclic_lit);
		lits.addAll(edge_lits.keySet());
		lits.addAll(cons_lits.keySet());
		
		List<Lit> core = solver.minimizeUnsatCore(lits);
		
		// link to edges and constraints
		for (Lit l : core) {
			if (edge_lits.containsKey(l)) {
				out_edges.add(edge_lits.get(l));
				continue;
			}
			if (cons_lits.containsKey(l)) {
				out_cons.add(cons_lits.get(l));
				continue;
			}
			assert l == acyclic_lit;
		}
	}
	

}
