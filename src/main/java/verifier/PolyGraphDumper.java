package verifier;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

import com.google.common.graph.EndpointPair;

import graph.PrecedenceGraph;
import graph.TxnNode;

public class PolyGraphDumper extends MonoSATVerifierOneshot {

	public PolyGraphDumper(String logfd) {
		super(logfd);
	}

	/* example output
		n:3
		e:0,2
		e:1,2
		c:2,1|1,0
	*/
	public static String DumpPolyGraph_helper(PrecedenceGraph g, Set<Constraint> cons) {
		StringBuilder sb = new StringBuilder();
		
		// nodes
		int n = g.allNodes().size();
		sb.append("n:" + n + "\n");
		
		// edges
		HashMap<Long, Long> txnid2indx = new HashMap<Long, Long>();
		long counter = 0;
		for (TxnNode node : g.allNodes()) {
			txnid2indx.put(node.getTxnid(), counter++);
		}
		assert txnid2indx.size() == n;
		for (EndpointPair<TxnNode> e : g.allEdges()) {
			long tid1 = e.source().getTxnid();
			long tid2 = e.target().getTxnid();
			assert txnid2indx.containsKey(tid1) && txnid2indx.containsKey(tid2);
			sb.append("e:" + txnid2indx.get(tid1) + "," + txnid2indx.get(tid2) + "\n");
		}
		
		// constraints
		for (Constraint con : cons) {
			assert con.chain_1.size() == 1 && con.chain_2.size() == 1;
			assert con.edge_set1.size() == 1 && con.edge_set2.size() == 1;
			long e1_src_tid = con.edge_set1.iterator().next().getFirst();
			long e1_dst_tid = con.edge_set1.iterator().next().getSecond();
			long e2_src_tid = con.edge_set2.iterator().next().getFirst();
			long e2_dst_tid = con.edge_set2.iterator().next().getSecond();
			sb.append("c:" + 
			    txnid2indx.get(e1_src_tid) + "," + txnid2indx.get(e1_dst_tid) + "|" +
			    txnid2indx.get(e2_src_tid) + "," + txnid2indx.get(e2_dst_tid) + "\n"
			);
		}
		
		return sb.toString();
	}
	
	public boolean DumpPolyGraph(String log_file) {
		CreateKnownGraph();
		Set<Constraint> cons = GenConstraints(m_g);
		
		System.out.println("KNOWN graph = " + m_g.toString());
		System.out.println("#constraints = " + cons.size());
		
		String str = DumpPolyGraph_helper(m_g, cons);
		
		try {
			BufferedWriter w = new BufferedWriter(new FileWriter(log_file));
			w.write(str);
			w.close();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
	}

}
