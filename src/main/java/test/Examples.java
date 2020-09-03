package test;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.common.graph.Graphs;

import algo.DFSCycleDetection;
import gpu.ReachabilityMatrix;
import graph.EdgeType;
import graph.OpNode;
import graph.PrecedenceGraph;
import graph.TxnNode;
import monosat.Graph;
import monosat.Lit;
import monosat.Solver;
import util.Profiler;

import static monosat.Logic.assertTrue;

public class Examples {
	
	
	public static PrecedenceGraph example1(Map<Long,ArrayList<OpNode>> readFromMapping) {
		PrecedenceGraph pg = new PrecedenceGraph();
		
		String A = "key1", B= "key2", C="key3";
		int txnid = 0;
		long wid = 0;
		long[][] ts = {
				{0xdeadbeef, 0xdeadbeef}, // Abandon
				{1,5},
				{2,6},
				{3,7},
				{4,11},
				{8,12},
				{9,13},
				{10,14},
		};
		int begin = 0, commit = 1;
		long no_prev_txnid =0;
		int timestamp=0;
		// W
		// cur_txn.appendOp(new OpNode(false,txnid,key_hash,wid));
		// R
		// cur_txn.appendOp(new OpNode(true,txnid,key_hash,wid,prev_txnid));

		// T1
		TxnNode t = new TxnNode(++txnid);
		t.setBeginTimestamp(ts[txnid][begin]);
		t.appendOp(new OpNode(false, txnid, A, 0L, ++wid, no_prev_txnid, ++timestamp)); // 1
		t.appendOp(new OpNode(false, txnid, B, 0L,++wid, no_prev_txnid, ++timestamp)); // 2
		t.commit(ts[txnid][commit]);
		pg.addTxnNode(t);
		
		// T2
		t = new TxnNode(++txnid);
		t.setBeginTimestamp(ts[txnid][begin]);
		OpNode r1 = new OpNode(true, txnid, A, 0L, 1, 1, 0);
		t.appendOp(r1);
		OpNode r2 = new OpNode(true, txnid, B, 0L, 3, 3, 0);
		t.appendOp(r2);
		t.commit(ts[txnid][commit]);
		pg.addTxnNode(t);
		
		// T3
		t = new TxnNode(++txnid);
		t.setBeginTimestamp(ts[txnid][begin]);
		t.appendOp(new OpNode(false, txnid, B, 0L, ++wid, no_prev_txnid, ++timestamp)); // 3
		t.appendOp(new OpNode(false, txnid, C, 0L, ++wid, no_prev_txnid, ++timestamp)); // 4
		t.commit(ts[txnid][commit]);
		pg.addTxnNode(t);
		
		// T4
		t = new TxnNode(++txnid);
		t.setBeginTimestamp(ts[txnid][begin]);
		OpNode r3 = new OpNode(true, txnid, C, 0L, 4, 3, 0);
		t.appendOp(r3);
		t.appendOp(new OpNode(false, txnid, C, 0L, ++wid, no_prev_txnid, ++timestamp)); // 5
		t.commit(ts[txnid][commit]);
		pg.addTxnNode(t);
		
		// T5
		t = new TxnNode(++txnid);
		t.setBeginTimestamp(ts[txnid][begin]);
		t.appendOp(new OpNode(false, txnid, A, 0L, ++wid, no_prev_txnid, ++timestamp)); // 6
		t.appendOp(new OpNode(false, txnid, B, 0L, ++wid, no_prev_txnid, ++timestamp)); // 7
		t.commit(ts[txnid][commit]);
		pg.addTxnNode(t);
		
		// T6
		t = new TxnNode(++txnid);
		t.setBeginTimestamp(ts[txnid][begin]);
		OpNode r4 = new OpNode(true, txnid, A, 0L, 8, 7, 0);
		t.appendOp(r4);
		OpNode r5 = new OpNode(true, txnid, B, 0L, 7, 5, 0);
		t.appendOp(r5);
		t.commit(ts[txnid][commit]);
		pg.addTxnNode(t);
		
		// T7
		t = new TxnNode(++txnid);
		t.setBeginTimestamp(ts[txnid][begin]);
		t.appendOp(new OpNode(false, txnid, A, 0L, ++wid, no_prev_txnid, ++timestamp)); // 8
		OpNode r6 = new OpNode(true, txnid, C, 0L, 5, 4, 0);
		t.appendOp(r6);
		t.commit(ts[txnid][commit]);
		pg.addTxnNode(t);
		
		// mapping	
		readFromMapping.put(1L, new ArrayList<OpNode>());
		readFromMapping.put(3L, new ArrayList<OpNode>());
		readFromMapping.put(4L, new ArrayList<OpNode>());
		readFromMapping.put(5L, new ArrayList<OpNode>());
		readFromMapping.put(7L, new ArrayList<OpNode>());
		readFromMapping.put(8L, new ArrayList<OpNode>());
		readFromMapping.get(1L).add(r1);
		readFromMapping.get(3L).add(r2);
		readFromMapping.get(4L).add(r3);
		readFromMapping.get(8L).add(r4);
		readFromMapping.get(7L).add(r5);
		readFromMapping.get(5L).add(r6);

		
		// edges
		pg.addEdge(1, 2, EdgeType.WR);
		pg.addEdge(3, 2, EdgeType.WR);
		pg.addEdge(3, 4, EdgeType.WR);
		pg.addEdge(4, 7, EdgeType.WR);
		pg.addEdge(5, 6, EdgeType.WR);
		pg.addEdge(7, 6, EdgeType.WR);
		//
		pg.addEdge(2, 5, EdgeType.TO);
		pg.addEdge(2, 7, EdgeType.TO);

		return pg;
	}
	
	public static PrecedenceGraph example2(Map<Long,ArrayList<OpNode>> readFromMapping) {
		PrecedenceGraph pg = new PrecedenceGraph();
		
		String A = "key1", B="key2", C="key3";
		int txnid = 0;
		long wid = 0;
		long[][] ts = {
				{0xdeadbeef, 0xdeadbeef}, // Abandon
				{1,100},
				{2,101},
				{3,102},
				{4,103},
				{104,105},
		};
		int begin = 0, commit = 1;
		long no_prev_txnid =0;
		int timestamp=0;
		// W
		// cur_txn.appendOp(new OpNode(false,txnid,key_hash,wid));
		// R
		// cur_txn.appendOp(new OpNode(true,txnid,key_hash,wid,prev_txnid));

		// T1
		TxnNode t = new TxnNode(++txnid);
		t.setBeginTimestamp(ts[txnid][begin]);
		t.appendOp(new OpNode(false, txnid, A, 0L, ++wid, no_prev_txnid, ++timestamp)); // 1
		t.commit(ts[txnid][commit]);
		pg.addTxnNode(t);
		
		// T2
		t = new TxnNode(++txnid);
		t.setBeginTimestamp(ts[txnid][begin]);
		t.appendOp(new OpNode(false, txnid, A, 0L, ++wid, no_prev_txnid, ++timestamp)); // 2
		t.commit(ts[txnid][commit]);
		pg.addTxnNode(t);
		
		// T3
		t = new TxnNode(++txnid);
		t.setBeginTimestamp(ts[txnid][begin]);
		t.appendOp(new OpNode(false, txnid, A, 0L, ++wid, no_prev_txnid, ++timestamp)); // 3
		t.commit(ts[txnid][commit]);
		pg.addTxnNode(t);
		
		// T4
		t = new TxnNode(++txnid);
		t.setBeginTimestamp(ts[txnid][begin]);
		t.appendOp(new OpNode(false, txnid, A, 0L, ++wid, no_prev_txnid, ++timestamp)); // 4
		t.commit(ts[txnid][commit]);
		pg.addTxnNode(t);
		
		// T5
		t = new TxnNode(++txnid);
		t.setBeginTimestamp(ts[txnid][begin]);
		t.appendOp(new OpNode(false, txnid, A, 0L, ++wid, no_prev_txnid, ++timestamp)); // 5
		t.commit(ts[txnid][commit]);
		pg.addTxnNode(t);
		
		pg.addEdge(1, 5, EdgeType.TO);
		pg.addEdge(2, 5, EdgeType.TO);
		pg.addEdge(3, 5, EdgeType.TO);
		pg.addEdge(4, 5, EdgeType.TO);
		
		return pg;
	}
	
	public static void main3(String args[]) {
		Map<Long,ArrayList<OpNode>> readFromMapping = new HashMap<Long,ArrayList<OpNode>>();
		PrecedenceGraph pg = example1(readFromMapping);
		System.out.println(pg.toString2());
	}
	
	public static void main2(String args[]) {
		Solver s = new Solver();
		Graph g = new Graph(s);
		int n1 = g.addNode();
		int n2 = g.addNode();
		int n3 = g.addNode();
		
		g.addEdge(n1, n2);
		g.addEdge(n2, n3);
		assertTrue(g.acyclic());
		
		boolean ret = s.solve();
		
		System.out.print(ret);
	}
	
	public static void main(String args[]) {
		PrecedenceGraph g = new PrecedenceGraph();
		
		int nodes = 10000;
		for (int i = 0; i < nodes; i++) {
			g.addTxnNode(new TxnNode(i));
		}
		for (int i = 0; i < nodes - 2; i++) {
			g.addEdge(i, i + 1, null);
			// g.addEdge(i, i+2, null);
		}
		
		Profiler p = Profiler.getInstance();
		p.startTick("my");
		if (DFSCycleDetection.CycleDetection(g.getGraph())) {
			System.out.println("CYC");
		} else {
			System.out.println("ACYC");
		}
		p.endTick("my");
		
		p.startTick("google");
		if(Graphs.hasCycle(g.getGraph())) { // OVERFLOW
			System.out.println("CYC");
		} else {
			System.out.println("ACYC");
		}
		p.endTick("google");
		System.out.println("my " + p.getTime("my") + "; google " + p.getTime("google"));
	}

}
