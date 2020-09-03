package graph;

import verifier.AbstractVerifier;

public class OpNode extends AbsNode {
	
	public boolean isRead;
	public long txnid;
	public long wid;
	public long read_from_txnid;
	public long key_hash;
	public long val_hash;
	public int pos;
	public String key = null;
	
	
	public OpNode(boolean isRead, long txnid, String key, long val_hash, long wid, long ptid,int pos) {
		super(0);
		assert false; // used by online GCer
	}
	
	public OpNode(boolean isRead, long txnid, long key_hash,  long val_hash, long wid, long ptid,int pos) {
		super(isRead ? 
				((txnid << Integer.BYTES*8) + pos) :
				wid); // FIXME: should guarantee the uniqueness! Assume read/write to one key in one txn
		assert pos != 0;
		
		this.isRead = isRead;
		this.txnid = txnid;
		this.key_hash = key_hash;
		this.val_hash = val_hash;
		this.wid = wid;
		this.read_from_txnid = ptid;
		this.pos = pos;
	}
	
	// the clone function
	public OpNode(OpNode op) {
		this(op.isRead, op.txnid, op.key_hash, op.val_hash, op.wid, op.read_from_txnid, op.pos);
	}
	
	public String toString() {
		return "Op[R=" + isRead + "][txnid=" + Long.toHexString(txnid) + "][wid=" + Long.toHexString(wid) + "][prv_txnid="
				+ Long.toHexString(read_from_txnid) + "][keyhash=" + Long.toHexString(key_hash) + "][val="
				+ Long.toHexString(val_hash) + "]";
	}


}
