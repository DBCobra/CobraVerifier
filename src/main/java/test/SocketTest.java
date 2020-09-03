package test;

import java.io.IOException;

import util.LogReceiver;
import util.LogSender;

public class SocketTest {
	
	static int num_clients = 5;
	
	public static void main(String args[]) {
		
		LogReceiver s = LogReceiver.getInstance(); 
		
		// init  clients
		LogSender[] clients = new LogSender[num_clients];
		for (int i = 0; i<num_clients; i++) {
			clients[i] = new LogSender("localhost", 10086);
		}
		
		// talk
		for (int i = 0; i<num_clients; i++) {
			String msg = "this is client-" + i;
			clients[i].write(msg.getBytes());
			clients[i].flush();
		}
		
		System.out.println("All connected");
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		System.out.println("finish waiting");
		
		
		for (String name : s.getClients()) {
			System.out.println("---" + name);
			byte[] b = new byte[1024];
			try {
				s.getClientStream(name).read(b);
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("   " + new String(b));
		}
		
		
		System.out.println("DONE");
		
	}

}
