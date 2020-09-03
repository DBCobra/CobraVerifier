package verifier;

import java.util.List;
import java.util.Set;

import graph.PrecedenceGraph;
import graph.TxnNode;
import util.Pair;

public class Constraint {
	public Set<Pair<Long, Long>> edge_set1, edge_set2;
	public List<TxnNode> chain_1, chain_2;

	public Constraint(Set<Pair<Long, Long>> edges1, Set<Pair<Long, Long>> edges2, List<TxnNode> chain1,
			List<TxnNode> chain2) {
		edge_set1 = edges1;
		edge_set2 = edges2;
		chain_1 = chain1;
		chain_2 = chain2;
	}

	public String toString(PrecedenceGraph g) {
		return toString(g, false);
	}

	public String toString(PrecedenceGraph g, boolean detail) {
		StringBuilder sb = new StringBuilder();
		sb.append("Chain 1:\n");
		for (TxnNode tx : chain_1) {
			if (detail) {
				sb.append("  " + tx.toString2() + "\n");
			} else {
				sb.append("  " + tx.toString3() + "\n");
			}
		}
		sb.append("Chain 2:\n");
		for (TxnNode tx : chain_2) {
			if (detail) {
				sb.append("  " + tx.toString2() + "\n");
			} else {
				sb.append("  " + tx.toString3() + "\n");
			}
		}
		sb.append("Edges 1:\n");
		for (Pair<Long, Long> e : edge_set1) {
			TxnNode src = g.getNode(e.getFirst());
			TxnNode dst = g.getNode(e.getSecond());
			sb.append("  " + src.toString3() + "->" + dst.toString3() + "\n");
		}
		sb.append("Edges 2:\n");
		for (Pair<Long, Long> e : edge_set2) {
			TxnNode src = g.getNode(e.getFirst());
			TxnNode dst = g.getNode(e.getSecond());
			sb.append("  " + src.toString3() + "->" + dst.toString3() + "\n");
		}
		return sb.toString();
	}
}
