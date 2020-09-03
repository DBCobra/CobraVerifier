package kvClient;

public interface KvInterface {
	
	public Object begin() throws KvException,TxnException;
	public boolean commit(Object txn) throws KvException,TxnException;
	public boolean abort(Object txn) throws KvException,TxnException;
	public boolean insert(Object txn, String key, String value) throws KvException,TxnException;
	public boolean delete(Object txn, String key) throws KvException,TxnException;
	public String get(Object txn, String key) throws KvException,TxnException;
	public boolean set(Object txn, String key, String value) throws KvException,TxnException;
	
	// the client is responsible to call rollback() when it catches a TxnExecption
	// and if the client wants to manually abort a txn, then only abort() is needed to call.
	public boolean rollback(Object txn);
	public boolean isalive(Object txn);
	public long getTxnId(Object txn);
	public Object getTxn(long txnid);
	public boolean isInstrumented();
	
}