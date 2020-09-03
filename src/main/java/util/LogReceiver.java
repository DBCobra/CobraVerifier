package util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LogReceiver {

	private static LogReceiver instance = null;
	
	public static LogReceiver getInstance() {
		if (instance == null) {
			instance = new LogReceiver();
		}
		return instance;
	}
	
	private LogReceiver() {
		int receiver_port = VeriConstants.VERIFIER_PORT;
		try {
			serverSocket = new ServerSocket(receiver_port);
			serverSocket.setReceiveBufferSize(4096 * 1024); // set to 4M
		} catch (IOException e) {
			e.printStackTrace();
			assert false; // FIXME: stop entirely now
		}
		
		client_conns = new ConcurrentHashMap<String, Socket>();
		
		// listener will keep receiving clients' connection
		final LogReceiver r = this;
		listener = new Thread() {
			public void run() {
				while(true) {
					r.waitForNextClient();
				}
			}
		};
		listener.start();
	}
	
	
	// ======== local vars ==========
	ServerSocket serverSocket = null;
	Map<String, Socket> client_conns = null;
	Thread listener = null;

	
	// ======== APIs =======
	
	public void waitForNextClient() {
		 Socket skt = null;
		try {
			skt = serverSocket.accept();
		} catch (IOException e) {
			e.printStackTrace();
			assert false; // FIXME: stop entirely now
		}
		 String name = skt.getRemoteSocketAddress() + "<=>" + skt.getLocalSocketAddress();
		 assert !client_conns.containsKey(name);
		 client_conns.put(name, skt);
	}
	
	public BufferedInputStream getClientStream(String name) {
		assert client_conns.containsKey(name);
		Socket skt = client_conns.get(name);
		try {
			// skt.setReceiveBufferSize(4096 * 1024); // FIXME: for syncing verifier and clients, for now
			ChengLogger.println("Set buffer size to [" + skt.getReceiveBufferSize() + "]");
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		
		BufferedInputStream in = null;
		try {
			in = new BufferedInputStream(skt.getInputStream());
		} catch (IOException e) {
			e.printStackTrace();
			assert false; // FIXME: stop entirely now
		}
		return in;
	}
	
	public Set<String> getClients() {
		return new HashSet<String>(client_conns.keySet());
	}
	
}
