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
		
		switch(message.toCharArray()[0]) {
		case '2':
			this.heartbeat();
			break;
		case '5':
			// We want to extract the actual message. Going to hack this shit.
			String[] messageParts = message.split(":");
			String lastPart = messageParts[messageParts.length-1];
			String chatPayload = lastPart.substring(1, lastPart.length()-4);
			
			long roundtripTime;
			String[] payloadParts = chatPayload.split(",");
			if(new Integer(this.id).toString().compareTo(payloadParts[0])==0) {
				roundtripTime = messageArrivedAt - new Long(payloadParts[1]);
				this.listener.messageArrivedWithRoundtrip(roundtripTime);
			}

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
			String fullMessage = "3:::{\"name\":\"chat\", \"args\":[{\"text\":\""+message+"\"}]}";
			this.send(fullMessage);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void sendTimestampedChat() {
		String message = this.id + "," + new Long(Calendar.getInstance().getTimeInMillis()).toString();
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
			
			// first talk to the load balancer to ask which server we should be communicating with
			URL url = new URL("http://" + server + "/"); 
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();           
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setRequestMethod("GET"); 

			
		    BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		    String socketHost = rd.readLine();
			
			// now talk to the actual socket server
			url = new URL("http://" + socketHost + "/socket.io/1/"); 
			connection = (HttpURLConnection) url.openConnection();           
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setRequestMethod("POST"); 
			
			DataOutputStream wr = new DataOutputStream(connection.getOutputStream ());
			wr.flush();
			wr.close();
			
		    rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		    String line = rd.readLine();
		    String hskey = line.split(":")[0];
		    System.out.println("hskey: " + hskey);
		    return new URI("ws://" + socketHost + "/socket.io/1/websocket/" + hskey);
		} catch (Exception e) {
			System.out.println("error: " + e);
			return null;
		}
	}
}
