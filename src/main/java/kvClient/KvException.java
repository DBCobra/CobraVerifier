package kvClient;

public class KvException extends Exception {
	private String msg = "";

	public KvException() {
		this("");
	}

	public KvException(String msg) {
		this.msg = msg;
	}

	public String toString() {
		return msg;
	}

}
