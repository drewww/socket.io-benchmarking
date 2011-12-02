import java.io.IOException;


public interface SocketIOClientEventListener {

	public void onError(IOException e);
	public void onMessage(String type);
	public void onClose();
	public void onOpen();
	
}
