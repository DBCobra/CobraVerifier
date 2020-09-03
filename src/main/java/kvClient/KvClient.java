package kvClient;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import kvClient.pb.AbortArg;
import kvClient.pb.BeginArg;
import kvClient.pb.CommitArg;
import kvClient.pb.DeleteArg;
import kvClient.pb.ErrorType;
import kvClient.pb.GetArg;
import kvClient.pb.InsertArg;
import kvClient.pb.IsAliveArg;
import kvClient.pb.Key;
import kvClient.pb.KeyValue;
import kvClient.pb.KvServiceGrpc;
import kvClient.pb.Response;
import kvClient.pb.ResponseKv;
import kvClient.pb.RollbackArg;
import kvClient.pb.ServiceError;
import kvClient.pb.SetArg;
import kvClient.pb.KvServiceGrpc.KvServiceBlockingStub;
import kvClient.pb.KvServiceGrpc.KvServiceStub;

public class KvClient {
	private static final Logger logger = Logger.getLogger(KvClient.class.getName());

	private final ManagedChannel channel;
	private final KvServiceBlockingStub blockingStub;
	private final KvServiceStub asyncStub; // async calls might be used in open loop testing

	public KvClient(String host, int port) {
		this(ManagedChannelBuilder.forAddress(host, port).usePlaintext());
	}

	public KvClient(ManagedChannelBuilder<?> channelBuilder) {
		channel = channelBuilder.build();
		blockingStub = KvServiceGrpc.newBlockingStub(channel);
		asyncStub = KvServiceGrpc.newStub(channel);
	}

	public void shutdown() throws InterruptedException {
		channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
	}

	// =============== Txn related interfaces ====================

	public long beginTxn() throws TxnException, KvException {
		info("*** begin Transaction");
		BeginArg request = BeginArg.newBuilder().build();
		Response response;

		try {
			response = blockingStub.begin(request);
		} catch (StatusRuntimeException e) {
			warning("RPC failed : {0}", e.getStatus());
			throw new TxnException("RPC failed");
		}

		if (response.hasE()) {
			throwException(response.getE());
		}
		return response.getTxnid();
	}

	public boolean commit(long txnid) throws TxnException, KvException {
		info("*** txn {0} commit", txnid);
		CommitArg request = CommitArg.newBuilder().setTxnid(txnid).build();
		Response response;

		try {
			response = blockingStub.commit(request);
		} catch (StatusRuntimeException e) {
			warning("RPC failed : {0}", e.getStatus());
			throw new TxnException("RPC failed");
		}

		if (response.hasE()) {
			throwException(response.getE());
		}
		return response.getRes();
	}

	public boolean abort(long txnid) throws KvException, TxnException {
		info("*** txn {0} abort", txnid);
		AbortArg request = AbortArg.newBuilder().setTxnid(txnid).build();
		Response response;

		try {
			response = blockingStub.abort(request);
		} catch (StatusRuntimeException e) {
			warning("RPC failed : {0}", e.getStatus());
			throw new TxnException("RPC failed");
		}

		if (response.hasE()) {
			throwException(response.getE());
		}
		return response.getRes();
	}

	public boolean rollback(long txnid) {
		info("*** txn {0} rollback", txnid);
		RollbackArg request = RollbackArg.newBuilder().setTxnid(txnid).build();
		Response response;

		try {
			response = blockingStub.rollback(request);
		} catch (StatusRuntimeException e) {
			warning("RPC failed : {0}", e.getStatus());
			return false;
		}

		if (response.hasE()) {
			assert false;
		}
		return response.getRes();
	}

	public boolean isalive(long txnid) {
		info("*** txn {0} isalive?", txnid);
		IsAliveArg request = IsAliveArg.newBuilder().setTxnid(txnid).build();
		Response response;

		try {
			response = blockingStub.isAlive(request);
		} catch (StatusRuntimeException e) {
			warning("RPC failed : {0}", e.getStatus());
			return false;
		}

		if (response.hasE()) {
			return false;// XXX
		}
		return response.getRes();
	}	
	// ================= KVstore related interfaces =======================

	public boolean insert(long txnid, String key, String val) throws TxnException, KvException {
		info("*** insert {0}, {1}", key, val);
		KeyValue kv = KeyValue.newBuilder().setKey(key).setValue(val).build();
		InsertArg request = InsertArg.newBuilder().setKv(kv).setTxnid(txnid).build();
		Response response;
		try {
			response = blockingStub.insert(request);
		} catch (StatusRuntimeException e) {
			warning("RPC failed : {0}", e.getStatus());
			throw new TxnException("");
		}
		if (response.hasE()) {
			throwException(response.getE());
		}
		info("*** insert success!");
		return response.getRes();

	}
	
	public boolean set(long txnid, String key, String val) throws KvException, TxnException {
		info("*** set {0}, {1}", key, val);
		KeyValue kv = KeyValue.newBuilder().setKey(key).setValue(val).build();
		SetArg request = SetArg.newBuilder().setKv(kv).setTxnid(txnid).build();
		Response response;
		try {
			response = blockingStub.set(request);
		} catch (StatusRuntimeException e) {
			warning("RPC failed : {0}", e.getStatus());
			throw new TxnException("");
		}
		if (response.hasE()) {
			throwException(response.getE());
		}
		info("*** set success!");
		return response.getRes();
	}
	
	public String get(long txnid, String key) throws KvException, TxnException {
		info("*** get {0}", key);
		Key k = Key.newBuilder().setKey(key).build();
		GetArg request = GetArg.newBuilder().setKey(k).setTxnid(txnid).build();
		ResponseKv response;
		try {
			response = blockingStub.get(request);
		} catch (StatusRuntimeException e) {
			warning("RPC failed : {0}", e.getStatus());
			throw new TxnException("");
		}
		if (response.hasE()) {
			throwException(response.getE());
		} else if (response.hasK()) {
			// ugly implementation: return null means the key doesn't exist
			return null;
		}
		info("*** get success! " + response.getKv().getValue());
		assert response.getKv().getKey().equals(key);
		return response.getKv().getValue();
	}

	public boolean delete(long txnid, String key) throws KvException, TxnException {
		info("*** delete {0}", key);
		Key k = Key.newBuilder().setKey(key).build();
		DeleteArg request = DeleteArg.newBuilder().setKey(k).setTxnid(txnid).build();
		Response response;
		try {
			response = blockingStub.delete(request);
		} catch (StatusRuntimeException e) {
			warning("RPC failed : {0}", e.getStatus());
			throw new TxnException("");
		}
		if (response.hasE()) {
			throwException(response.getE());
		}
		info("*** delete success! ");
		return response.getRes();
	}
	
	// =============== utils ===============

	private void info(String msg, Object... params) {
//		logger.log(Level.INFO, msg, params);
	}

	private void warning(String msg, Object... params) {
		logger.log(Level.WARNING, msg, params);
	}

	private void throwException(ServiceError e) throws KvException, TxnException {
		if (e.getEt() == ErrorType.TxnError) {
			throw new TxnException(e.getMsg());
		} else if (e.getEt() == ErrorType.KvError) {
			throw new KvException(e.getMsg());
		} else {
			System.out.println("Error: unsupported error type from KvServer: " + e.getEt());
			assert false;
		}
	}
	
}
