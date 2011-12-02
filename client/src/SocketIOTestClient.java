import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import net.tootallnate.websocket.WebSocketClient;


public class SocketIOTestClient extends WebSocketClient {

	private long lastHeartbeat = 0;
	private boolean connected = false;
	private static boolean threadStarted = false;
	
	
	private static Set<SocketIOTestClient> clients = new HashSet<SocketIOTestClient>(); 
	
	public SocketIOTestClient(URI server) {
		super(server);
		System.out.println("Started connection to: " + server);
		
		SocketIOTestClient.clients.add(this);
		
		if(!threadStarted) {
			threadStarted = true;
			(new Thread(new ChattingThread())).start();
		}
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
		int type = new Integer(arg0.split(":")[0]).intValue();
		
		switch(type) {
		case 2:
			this.heartbeat();
			break;
		default:
//			System.out.println(arg0);
			break;
		}
	}

	@Override
	public void onOpen() {
		// TODO Auto-generated method stub
		System.out.println("open!");
		
		this.hello();
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
			String fullMessage = "5:::{\"name\":\"chat\", \"args\":[{\"text\":\""+message+"\"}]}";
			this.send(fullMessage);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void hello() {
		try {
			this.send("5:::{\"name\":\"hello\", \"args\":[]}");
		} catch (IOException e) {
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
	
	public class ChattingThread implements Runnable {
		public void run() {
			while(true) {
				// Loop through all the clients and make them send a message. We'll worry about rate limiting in a sec.
				for(SocketIOTestClient client : clients) {
					System.out.println("client: " + client);
					client.chat("-" + client.hashCode() + Calendar.getInstance().getTimeInMillis());
				}
				
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

		
		for(int i=0; i<10; i++) {
			SocketIOTestClient c = new SocketIOTestClient(SocketIOTestClient.getNewSocketURI("localhost:8080"));
			c.connect();
		}

	}

}
