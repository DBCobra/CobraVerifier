package verifier;

import java.util.Set;

import graph.TxnNode;

public class SCCNode {
	public Set<TxnNode> txns;
	public boolean frozen = false;
	
	public SCCNode(Set<TxnNode> set) {
		txns = set;
	}
	
	public String toString() {
		return "SCC[" + Long.toHexString(txns.iterator().next().getTxnid()) + "][frozen=" + frozen + "][size=" + txns.size() + "]";
	}
	
	public String dumpTxns() {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		for (TxnNode tx : txns) {
			sb.append(Long.toHexString(tx.getTxnid())+", ");
		}
		sb.append("}");
		return sb.toString();
	}
	
	public int size() {
		return txns.size();
	}
}
