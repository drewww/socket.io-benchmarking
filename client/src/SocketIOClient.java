import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import net.tootallnate.websocket.WebSocketClient;


public class SocketIOClient extends WebSocketClient {
	
	SocketIOClientEventListener listener;
	
	public SocketIOClient(URI server, SocketIOClientEventListener listener) {
		super(server);
		
		this.listener = listener;
	}

	@Override
	public void onClose() {
		// TODO Auto-generated method stub
		System.out.println("close!");
		
		this.listener.onClose();
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
			// TODO Make this the real message type, or something. This will get changed when we
			// handle message turnaround times properly anyway. 
			this.listener.onMessage("5");
			break;
		}
	}

	@Override
	public void onOpen() {
		// TODO Auto-generated method stub
		this.listener.onOpen();
	}
	
	public void heartbeat() {
		try {
			this.send("2:::");
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
}
