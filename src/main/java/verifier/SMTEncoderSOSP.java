package verifier;

import static monosat.Logic.and;
import static monosat.Logic.assertTrue;
import static monosat.Logic.not;
import static monosat.Logic.or;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

public class SMTEncoderSOSP {
	
	private PrecedenceGraph g;
	private Set<Constraint> cons;
	private boolean solved = false;
	private Set<Pair<Long,Long>> chosen_edges;
	
	public SMTEncoderSOSP(PrecedenceGraph g, Set<Constraint> cons) {
		this.g = g;
		this.cons = cons;
	}
	
	public boolean Solve() {
		solved = solvePolyg(g, cons);
		return solved;
	}
	
	public Set<Pair<Long,Long>> GetSolutionEdges() {
		assert solved;
		return chosen_edges;
	}
	
	public void GetMinUnsatCore(List<EndpointPair<TxnNode>> out_edges, List<Constraint> out_cons) {
		SMTEncoder smt = new SMTEncoder(g, cons);
		smt.GetMinUnsatCore(out_edges, out_cons);
	}
	
	
	// ========= SOSP solution ========
	
	private Pair<Lit,Lit> addConstraint(Constraint c, Graph monog, Map<Long,Integer> txnid2mnode, Solver s) {
		Lit var_true = new Lit(s);
		assertTrue(var_true);
		
		Lit l_all = var_true;
		Lit l_nothing = var_true;
		for (Pair<Long,Long> e : c.edge_set1) {
			int src = txnid2mnode.get(e.getFirst());
			int dst = txnid2mnode.get(e.getSecond());
			Lit e_var = monog.addEdge(src, dst);
			l_all = and(l_all, e_var);
			l_nothing = and(l_nothing, not(e_var));
		}
		
		Lit r_all = var_true;
		Lit r_nothing = var_true;
		for (Pair<Long,Long> e : c.edge_set2) {
			int src = txnid2mnode.get(e.getFirst());
			int dst = txnid2mnode.get(e.getSecond());
			Lit e_var = monog.addEdge(src, dst);
			r_all = and(r_all, e_var);
			r_nothing = and(r_nothing, not(e_var));
		}
		
		assertTrue(or(l_all, l_nothing));
		assertTrue(or(r_all, r_nothing));
		assertTrue(or(
				and(l_all, r_nothing),
				and(r_all, l_nothing)
				));
		
		return new Pair<Lit,Lit>(l_all, r_all);
	}
	
	private boolean  solvePolyg(PrecedenceGraph g, Set<Constraint> cons) {
		// https://github.com/sambayless/monosat/blob/master/examples/java/Tutorial.java#L161
		Profiler prof = Profiler.getInstance();
		Solver s = new Solver();
		
		prof.startTick(VeriConstants.PROF_MONOSAT_1);
		Graph monog = new Graph(s);
		
		// add nodes
		Map<Long,Integer> txnid2mnode = new HashMap<Long,Integer>();
		for (TxnNode t : g.allNodes()) {
			int n = monog.addNode();
			txnid2mnode.put(t.getTxnid(), n);
		}
		
		// add edges
		for (EndpointPair<TxnNode> e : g.allEdges()) {
			int src = txnid2mnode.get(e.source().getTxnid());
			int dst = txnid2mnode.get(e.target().getTxnid());
			Lit mono_edge = monog.addEdge(src, dst);
			assertTrue(mono_edge); // this edge should be true
		}
		
		// add constraints
		ArrayList<Constraint> cons_arr = new ArrayList<Constraint>(cons); // ordered list
		ArrayList<Pair<Lit,Lit>> cons_lits = new ArrayList<Pair<Lit,Lit>>();
		for (Constraint c : cons_arr) {
			 Pair<Lit, Lit> lcon = addConstraint(c, monog, txnid2mnode, s);
			 cons_lits.add(lcon);
		}
		
		// add acyclic
		assertTrue(monog.acyclic());
		
		prof.endTick(VeriConstants.PROF_MONOSAT_1);
		
		prof.startTick(VeriConstants.PROF_MONOSAT_2);
		boolean ret = s.solve();
		prof.endTick(VeriConstants.PROF_MONOSAT_2);
		
		// remember results
		if (ret) {
			chosen_edges = new HashSet<Pair<Long,Long>>();
			
			for (int i=0; i<cons_arr.size(); i++) {
				Pair<Lit,Lit> cons_ret = cons_lits.get(i);
				Constraint con = cons_arr.get(i);
				
				boolean is_left = cons_ret.getFirst().value();
				boolean is_right = cons_ret.getSecond().value();
				assert (is_left || is_right) && (!is_left || !is_right);
				
				chosen_edges.addAll(is_left ? con.edge_set1 : con.edge_set2);
			}
		}
		
		return ret;
	}

}
