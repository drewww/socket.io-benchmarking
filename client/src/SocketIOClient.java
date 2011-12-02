import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import net.tootallnate.websocket.WebSocketClient;


public class SocketIOClient extends WebSocketClient {
	
	protected SocketIOClientEventListener listener;
	protected Map<String, Long> requests = new HashMap<String, Long>();
	
	protected static int nextId = 0;
	
	protected int id;
	
	
	public SocketIOClient(URI server, SocketIOClientEventListener listener) {
		super(server);
		
		this.listener = listener;
		id = nextId;
		
		nextId++;
	}

	@Override
	public void onClose() {
		this.listener.onClose();
	}

	@Override
	public void onIOError(IOException arg0) {
		System.out.println("error: " + arg0);
	}

	@Override
	public void onMessage(String message) {
		long messageArrivedAt = Calendar.getInstance().getTimeInMillis();
		
		int type = new Integer(message.toCharArray()[0]).intValue();
		
		switch(type) {
		case 2:
			this.heartbeat();
			break;
		default:
			// We want to extract the actual message. Going to hack this shit.
			String[] messageParts = message.split(":");
			String lastPart = messageParts[messageParts.length-1];
			String chatPayload = lastPart.substring(1, lastPart.length()-4);
			
			this.listener.onMessage(chatPayload);
			break;
		}
	}

	@Override
	public void onOpen() {
		this.listener.onOpen();
	}
	
	public void heartbeat() {
		try {
			this.send("2:::");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void chat(String message) {
		try {
			String fullMessage = "5:::{\"name\":\"chat\", \"args\":[{\"text\":\""+message+"\"}]}";
			this.send(fullMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void sendTimestampedChat() {
		String message = new Long(Calendar.getInstance().getTimeInMillis()).toString();
		this.chat(message);
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
