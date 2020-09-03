package kvClient;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import util.VeriConstants;

public class SqlKV implements KvInterface {

	// singleton per thread
	private static Map<Long, SingleConn> instances = new ConcurrentHashMap<Long, SingleConn>();

	public SingleConn getInstance() {
		long threadId = Thread.currentThread().getId();
		if (!instances.containsKey(threadId)) {
			instances.put(threadId, new SingleConn());
		}
		return instances.get(threadId);
	}

	private static String TABLE_NAME;

	public SqlKV(String tableName) {
		this.TABLE_NAME = tableName;
	}

	public static class SingleConn {
		// ==== connection states of each thread ========
		private Connection conn = null;
		private long currTxnId = -1;
		private long lastTxnId = 10;

		public SingleConn() {
			try {
				Class.forName("org.postgresql.Driver");
				this.conn = DriverManager.getConnection(VeriConstants.DB_URL(), VeriConstants.PG_USERNAME, VeriConstants.PG_PASSWORD);
				this.conn.setAutoCommit(false);
				this.conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
		
		public SingleConn(String url, String username, String passwd) {
			try {
				Class.forName("org.postgresql.Driver");
				this.conn = DriverManager.getConnection(url, username, passwd);
				this.conn.setAutoCommit(false);
				this.conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}

		// =============== kv interface =================
		/**
		 * In JDBC we don't have to specify the start of a transaction So we just
		 * construct a transaction ID. And this is a single thread case, so it's really
		 * simple.
		 */
		public Object begin() throws KvException, TxnException {
//			try {
//				Statement st = conn.createStatement();
//				ResultSet rs = st.executeQuery("select current_setting('transaction_isolation');");
//				while(rs.next()) {
//					System.out.println(rs.getString("current_setting"));
//				}
//			} catch (SQLException e) {
//				e.printStackTrace();
//			}
			assert conn != null;
			assert currTxnId == -1; // last transaction is finished
			currTxnId = lastTxnId + 1;
			return currTxnId;
		}

		public boolean commit(Object txn) throws KvException, TxnException {
			assert conn != null;
			assert (Long) txn == currTxnId && currTxnId != -1 && lastTxnId + 1 == currTxnId;

			try {
				conn.commit();
				lastTxnId = currTxnId;
				currTxnId = -1;
			} catch (SQLException e) {
				throw new TxnException(e.getMessage());
			}
			return true;
		}

		public boolean abort(Object txn) throws KvException, TxnException {
			assert conn != null;
			assert (Long) txn == currTxnId && currTxnId != -1 && lastTxnId + 1 == currTxnId;

			try {
				conn.rollback();
				lastTxnId = currTxnId;
				currTxnId = -1;
			} catch (SQLException e) {
				throw new TxnException(e.getMessage());
			}
			return true;
		}

		public boolean insert(Object txn, String key, String value) throws KvException, TxnException {
			assert conn != null;
			assert (Long) txn == currTxnId && currTxnId != -1 && lastTxnId + 1 == currTxnId;

			PreparedStatement st;
			try {
				st = conn.prepareStatement("INSERT INTO " + TABLE_NAME + " (key, value) VALUES (?, ?)");
				st.setString(1, key);
				st.setString(2, value);
				int updatedRows = st.executeUpdate();
				assert updatedRows == 1;
			} catch (SQLException e) {
				String errMsg = e.getMessage();
				if (errMsg.contains("already exists.")) {
					throw new KvException(errMsg);
				}
				if (errMsg.contains("could not serialize access due to concurrent update")) {
					throw new TxnException(e.getMessage());
				}
				throw new TxnException(e.getMessage());
			}
			return true;
		}

		public boolean delete(Object txn, String key) throws KvException, TxnException {
			assert conn != null;
			assert (Long) txn == currTxnId && currTxnId != -1 && lastTxnId + 1 == currTxnId;

			PreparedStatement st;
			try {
				st = conn.prepareStatement("DELETE FROM " + TABLE_NAME + " WHERE key = ?");
				st.setString(1, key);
				int updatedRows = st.executeUpdate();
				if (updatedRows == 0) {
					return false;
				}
			} catch (SQLException e) {
				String errMsg = e.getMessage();
				if (errMsg.contains("could not serialize access due to concurrent update")) {
					throw new TxnException(e.getMessage());
				}
				throw new TxnException(e.getMessage());
			}
			return true;
		}

		public String get(Object txn, String key) throws KvException, TxnException {
			assert conn != null;
			assert (Long) txn == currTxnId && currTxnId != -1 && lastTxnId + 1 == currTxnId;

			PreparedStatement st;
			String value = null;
			try {
				st = conn.prepareStatement("SELECT value FROM " + TABLE_NAME + " WHERE key = ?");
				st.setString(1, key);
				ResultSet rs = st.executeQuery();
				int rowCount = 0;
				while (rs.next()) {
					rowCount++;
					value = rs.getString("value");
				}
				assert rowCount == 0 || rowCount == 1;
			} catch (SQLException e) {
				String errMsg = e.getMessage();
				if (errMsg.contains("could not serialize access due to read/write dependencies")) {
					throw new TxnException(e.getMessage());
				}
				throw new TxnException(e.getMessage());
			}
			return value;
		}

		public boolean set(Object txn, String key, String value) throws KvException, TxnException {
			assert conn != null;
			assert (Long) txn == currTxnId && currTxnId != -1 && lastTxnId + 1 == currTxnId;

			PreparedStatement st;
			try {
				// Backup code: only update, if key don't exist, then there will be SQLException.
//				st = conn.prepareStatement("UPDATE " + TABLE_NAME + " SET value = ? where key = ?");
//				st.setString(1, value);
//				st.setString(2, key);
				
				// This 'put' can only be done in PostgreSQL 9.5 or newer. In MySQL it becomes:
				// "INSERT INTO table (key, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = ?"
				st = conn.prepareStatement("INSERT INTO " + TABLE_NAME
						+ " (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = ?");
				st.setString(1, key);
				st.setString(2, value);
				st.setString(3, value);
				
				int updatedRows = st.executeUpdate();
				if (updatedRows == 0) {
					throw new KvException("Trying to update a non-existing key: " + key);
				}
				assert updatedRows == 1;
			} catch (SQLException e) {
				String errMsg = e.getMessage();
				if (errMsg.contains("could not serialize access due to")) {
					throw new TxnException(e.getMessage());
				} else if (errMsg.contains("deadlock detected")) {
					throw new TxnException(e.getMessage());
				} else {
					e.printStackTrace();
					throw new TxnException(e.getMessage());
				}
			}
			return true;
		}

		public boolean rollback(Object txn) {
			assert currTxnId == (Long) txn && currTxnId != -1;
			lastTxnId = currTxnId;
			currTxnId = -1;
			try {
				conn.rollback();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			return true;
		}

		public boolean isalive(Object txn) {
			return (Long) txn == currTxnId;
		}
	}

	public Object begin() throws KvException, TxnException {
		return this.getInstance().begin();
	}

	public boolean commit(Object txn) throws KvException, TxnException {
		return this.getInstance().commit(txn);
	}

	public boolean abort(Object txn) throws KvException, TxnException {
		return this.getInstance().abort(txn);
	}

	public boolean insert(Object txn, String key, String value) throws KvException, TxnException {
		return this.getInstance().insert(txn, key, value);
	}

	public boolean delete(Object txn, String key) throws KvException, TxnException {
		return this.getInstance().delete(txn, key);
	}

	public String get(Object txn, String key) throws KvException, TxnException {
		return this.getInstance().get(txn, key);
	}

	public boolean set(Object txn, String key, String value) throws KvException, TxnException {
		return this.getInstance().set(txn, key, value);
	}

	public boolean rollback(Object txn) {
		return this.getInstance().rollback(txn);
	}

	public boolean isalive(Object txn) {
		return this.getInstance().isalive(txn);
	}

	public long getTxnId(Object txn) {
		/**
		 * We use per thread singleton for SQL connection. Each thread must only have no
		 * more than one ongoing transaction. So it is useless to specify txn in the
		 * parameter of all those operations(begin, set, put,...) so the txn is just
		 * transaction id and we can always check whether the passed-in txnid is the
		 * same with the ongoing one in our state.
		 */
		return (Long) txn;
	}

	public Object getTxn(long txnid) {
		return txnid;
	}

	public boolean isInstrumented() {
		return false;
	}

}
