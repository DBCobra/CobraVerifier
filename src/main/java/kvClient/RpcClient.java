package kvClient;


public class RpcClient implements KvInterface{
	private final KvClient kvclient;
	private static RpcClient instance;
		
	// Thread safe efficient singleton
	public static RpcClient getInstance() {
		if (instance == null) {
			synchronized (RpcClient.class) {
				if (instance == null) {
					instance = new RpcClient("ye-cheng.duckdns.org", 8980); // FIXME: ugly hard code
				}
			}
		}
		return instance;
	}

	private RpcClient(String host, int port) {
		kvclient = new KvClient(host, port);
	}

	public Object begin() throws KvException, TxnException {
		return kvclient.beginTxn();
	}

	public boolean commit(Object txn) throws KvException, TxnException {
		assert txn instanceof Long;
		return kvclient.commit((Long)txn);
	}

	public boolean abort(Object txn) throws KvException, TxnException {
		assert txn instanceof Long;
		return kvclient.abort((Long)txn);
	}

	public boolean insert(Object txn, String key, String value) throws KvException, TxnException {
		assert txn instanceof Long;
		return kvclient.insert((Long)txn, key, value);
	}

	public boolean delete(Object txn, String key) throws KvException, TxnException {
		assert txn instanceof Long;
		return kvclient.delete((Long)txn, key);
	}

	public String get(Object txn, String key) throws KvException, TxnException {
		assert txn instanceof Long;
		return kvclient.get((Long)txn, key);
	}

	public boolean set(Object txn, String key, String value) throws KvException, TxnException {
		assert txn instanceof Long;
		return kvclient.set((Long)txn, key, value);
	}

	public boolean rollback(Object txn) {
		assert txn instanceof Long;
		return kvclient.rollback((Long)txn);
	}

	public boolean isalive(Object txn) {
		assert txn instanceof Long;
		return kvclient.isalive((Long)txn);
	}

	public long getTxnId(Object txn) {
		return (Long)txn;
	}
	
	public Object getTxn(long txnid) {
		return txnid;
	}

	public boolean isInstrumented() {
		return false;
	}

}