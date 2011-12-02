import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Calendar;

import net.tootallnate.websocket.WebSocketClient;


public class SocketIOTestClient extends WebSocketClient {

	private long lastHeartbeat = 0;
	private boolean connected = false;
	
	public SocketIOTestClient(URI server) {
		super(server);
		System.out.println("Started connection to: " + server);
	}

	@Override
	public void onClose() {
		// TODO Auto-generated method stub
		System.out.println("close!");
	}

	@Override
	public void onIOError(IOException arg0) {
		// TODO Auto-generated method stub
		System.out.println("error: " + arg0);
	}

	@Override
	public void onMessage(String arg0) {
		// TODO Auto-generated method stub
		System.out.println("message!");
	}

	@Override
	public void onOpen() {
		// TODO Auto-generated method stub
		System.out.println("open!");
		this.connected = true;
		
		try {
			this.send("5:::{'name':'hello', 'args':[]}");
			
			this.send("5:::{'name':'chat', 'args':[{'text':'hello'}]}");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		new Thread(new TestClientThread()).start();
	}
	
	public void heartbeat() {
		try {
			System.out.println("Heartbeat!");
			this.send("2:::");
			
			this.lastHeartbeat = Calendar.getInstance().getTimeInMillis();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void chat(String message) {
		try {
			System.out.println("chat: " + message);
			this.send("5:::{'name':'chat', 'args':[{'text':'"+message+"'}]}");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static URI getNewSocketURI(String server) {
		try {
			URL url = new URL("http://" + server + "/socket.io/1/"); 
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();           
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setRequestMethod("POST"); 

			DataOutputStream wr = new DataOutputStream(connection.getOutputStream ());
			wr.flush();
			wr.close();
			
		    BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		    String line = rd.readLine();
		    String hskey = line.split(":")[0];
		    return new URI("ws://" + server + "/socket.io/1/websocket/" + hskey);
		} catch (Exception e) {
			System.out.println("error: " + e);
			return null;
		}
	}
	
	public void disconnect() {
		try {
			this.close();
			this.connected = false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	
	private class TestClientThread implements Runnable {
		
		public void run() {
			while(connected) {				
				if(Calendar.getInstance().getTimeInMillis() - lastHeartbeat > 10000) {
					heartbeat();
				}
				
				chat("hello world!");
				
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		SocketIOTestClient c = new SocketIOTestClient(SocketIOTestClient.getNewSocketURI("localhost:8080"));
		c.connect();

	}

}
