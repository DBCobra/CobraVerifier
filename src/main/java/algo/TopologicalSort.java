package algo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

public class TopologicalSort {
	
	// Assumption: there is no cycle!
	public static <T> ArrayList<T> toposort(MutableGraph<T> g) {
		assert !Graphs.hasCycle(g);
		ArrayList<T> ret = new ArrayList<T>();

		// maintain all the in-degrees for all nodes
		Set<T> candidates = new HashSet<T>();
		Map<T, Integer> indegrees = new HashMap<T, Integer>();
		for (T node : g.nodes()) {
			int indegree = g.inDegree(node);
			indegrees.put(node, indegree);
			if (indegree == 0) {
				candidates.add(node);
			}
		}
		assert candidates.size() > 0;

		while (candidates.size() > 0) {
			// (1) get one write with no in-degree, update current candidates
			T candidate = candidates.iterator().next();
			candidates.remove(candidate);
			ret.add(candidate);
	
			// NOTE: an optimization, use readonly raw node for performance
			for (T succ : g.successors(candidate)) {
				int new_indegree = indegrees.get(succ) - 1;
				assert new_indegree >= 0;
				indegrees.put(succ, new_indegree);		
				if (new_indegree == 0) {
					candidates.add(succ);
				}
			}
		}

		assert ret.size() == g.nodes().size();
		return ret;
	}

}
