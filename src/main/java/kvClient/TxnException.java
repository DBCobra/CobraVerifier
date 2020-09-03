package kvClient;

public class TxnException extends Exception{
	
	private String msg = "TxnException: ";
	
	public TxnException(String msg) {
		this.msg += msg;
	}
	
	public String toString() {
		return msg;
	}

}
