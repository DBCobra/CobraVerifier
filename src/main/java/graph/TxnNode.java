package graph;

import java.util.ArrayList;

import util.VeriConstants;

public class TxnNode extends AbsNode{

	public enum TxnType {
		ONGOING, COMMIT, ABORT, STALE,
	};

	// attributes
	TxnType type = TxnType.ONGOING;
	ArrayList<OpNode> ops = new ArrayList<OpNode>();
	long begin_timestamp = VeriConstants.TXN_NULL_TS;
	long commit_timestamp = VeriConstants.TXN_NULL_TS;
	long next_client_txn = VeriConstants.NULL_TXN_ID;
	long prev_client_txn = VeriConstants.NULL_TXN_ID;
	int client_id = VeriConstants.TXN_NULL_CLIENT_ID;
	int version = VeriConstants.TXN_NULL_VERSION;
	public int prev_version = VeriConstants.TXN_NULL_VERSION;
	public boolean frozen = false;  // used also as monitoring

	
	public TxnNode(long id) {
		super(id);
	}
	
	// clone function
	public TxnNode(TxnNode n) {
		super(n.id());
		type = n.type;
		begin_timestamp = n.begin_timestamp;
		commit_timestamp = n.commit_timestamp;
		next_client_txn = n.next_client_txn;
		prev_client_txn = n.prev_client_txn;
		client_id = n.client_id;
		version = n.version;
		prev_version = n.prev_version;
		// clone all the ops
		ops = new ArrayList<OpNode>();
		for (OpNode op : n.ops) {
			ops.add(new OpNode(op));
		}
	}
	
	public long getTxnid() {
		return id();
	}

	public TxnType getStatus() {
		return type;
	}

	public void setStatus(TxnType t) {
		type = t;
	}

	public int size() {
		return ops.size();
	}

	public OpNode get(int i) {
		return ops.get(i);
	}
	
	public void setClientId(int cid) {
		assert client_id == VeriConstants.TXN_NULL_CLIENT_ID;
		client_id = cid;
	}
	
	public int getClientId() {
		return client_id;
	}
	
	public int getVersion() {
		return version;
	}
	
	public void setVersion(int v) {
		version = v;
	}
	
	public long getNextClientTxn() {
		return next_client_txn;
	}
	
	public void setNextClientTxn(long next) {
		next_client_txn = next;
	}
	
	public long getPrevClientTxn() {
		return prev_client_txn;
	}
	
	public void setPrevClientTxn(long prev) {
		prev_client_txn = prev;
	}

	public void setBeginTimestamp(long ts) {
		assert begin_timestamp == VeriConstants.TXN_NULL_TS;
		begin_timestamp = ts;
	}
	
	public long getBeginTimestamp() {
		return begin_timestamp;
	}
	
	public long getCommitTimestamp() {
		return commit_timestamp;
	}
	
	public void commit(long ts) {
		type = TxnType.COMMIT;
		commit_timestamp = ts;
	}

	public void abort() {
		type = TxnType.ABORT;
	}

	public void appendOp(OpNode op) {
		ops.add(op);
	}

	public ArrayList<OpNode> getOps() {
		return ops;
	}
	
	public String toString() {
		return "Txn[" + Long.toHexString(id()) + "][FZ:" + frozen + "][C:" + client_id + "-" + commit_timestamp + "][status:" + type + "][pV:" + prev_version + "][V:"
				+ version + "][prev:" + Long.toHexString(prev_client_txn) + "][next:" + Long.toHexString(next_client_txn) + "]";
	}
	
	public String toString3() {
		return "Txn[" + Long.toHexString(id()) + "][FZ:" + frozen + "][C:" + client_id + "-" + commit_timestamp + "][pV:" + prev_version + "][V:"
				+ version + "]";
	}

	public String toString2() {
		StringBuilder sb = new StringBuilder();
		sb.append("Txn[" + Long.toHexString(id()) + "][FZ:" + frozen + "][C:" + client_id + "-" + commit_timestamp + "][status:" + type + "][pV:" + prev_version
				+ "][V:" + version + "][prev:" + Long.toHexString(prev_client_txn) + "][next:"
				+ Long.toHexString(next_client_txn) + "] {\n");
		for (OpNode op : ops) {
			sb.append("    " + op + "\n");
		}
		sb.append("}\n");
		return sb.toString();
	}
}
