import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;


public class SocketIOLoadTester extends Thread implements SocketIOClientEventListener {

	public static final int STARTING_MESSAGES_PER_SECOND_RATE = 1;
	public static final int SECONDS_TO_TEST_EACH_LOAD_STATE = 10;
	public static final int MESSAGES_PER_SECOND_RAMP = 1;
	public static final int SECONDS_BETWEEN_TESTS = 1;
	
	public static final int POST_TEST_RECEPTION_TIMEOUT_WINDOW = 20000;
	
	protected Set<SocketIOClient> clients = new HashSet<SocketIOClient>();
	
	protected int concurrency;
	
	protected int currentMessagesPerSecond = STARTING_MESSAGES_PER_SECOND_RATE;
	
	protected boolean lostConnection = false;
	
	protected Integer numConnectionsMade = 0;
	
	protected List<Long> roundtripTimes;
	private boolean postTestTimeout;
	
	protected SocketIOLoadTester(int concurrency) {
		this.concurrency = concurrency;
	}
	
	public synchronized void run() {
		
		// Start the connections. Wait for all of them to connect then we go.
		for(int i=0; i<this.concurrency; i++) {
			SocketIOClient client = new SocketIOClient(SocketIOClient.getNewSocketURI("roar.media.mit.edu:8080"), this);
			this.clients.add(client);
			client.connect();
		}
		
		try {
			this.wait();
		} catch (InterruptedException e) {
			System.out.println("Interrupted!");
		}
		System.out.println("Woken up - time to start load test!");
		this.startLoadTest();
		return;
	}
	
	protected void startLoadTest() {
		// Actually run the test.
		// Protocol is spend 3 seconds at each load level, then ramp up messages per second.
		while(!lostConnection) {
			System.out.print(concurrency + " connections at " + currentMessagesPerSecond + ": ");
			
			this.roundtripTimes = new ArrayList<Long>(SECONDS_TO_TEST_EACH_LOAD_STATE * currentMessagesPerSecond);
			
			for(int i=0; i<SECONDS_TO_TEST_EACH_LOAD_STATE; i++) {
				this.triggerChatMessagesOverTime(currentMessagesPerSecond, 1000);
			}
			
			// At this point, all messages have been sent so we should wait until they've all been received.
			this.postTestTimeout = true;
			synchronized(this) {
				try {
					this.wait(POST_TEST_RECEPTION_TIMEOUT_WINDOW);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			if(this.postTestTimeout) {
				System.out.println(" failed - not all messages received in " + POST_TEST_RECEPTION_TIMEOUT_WINDOW + "ms");
				break;
			} else {
				this.processRoundtripStats();
				
				// TODO Do a check here - if we saw a mean roundtrip time above 100ms or so, that's the congestion point and we should record that as the "knee" of the curve, basically.
			}
			currentMessagesPerSecond += MESSAGES_PER_SECOND_RAMP;
			try {
				Thread.sleep(SECONDS_BETWEEN_TESTS*1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
		System.out.println("Shutting down.");
		return;
	}
	
	protected void triggerChatMessagesOverTime(int totalMessages, long ms) {
//		long timeAtStart = System.currentTimeMillis();

		long msPerSend = ms / totalMessages;
		
		// We basically want to guarantee that this method is going to take exactly a second to run, so the messages are spread out across the full second. 
		
		Iterator<SocketIOClient> clientsIterator = this.clients.iterator();
		for(int i=0; i<totalMessages; i++) {
			long messageStartTime = System.currentTimeMillis();
			
			SocketIOClient client = clientsIterator.next();
			client.sendTimestampedChat();
			
			if(!clientsIterator.hasNext()) {
				clientsIterator = clients.iterator();
			}
			
			if(System.currentTimeMillis() - messageStartTime < msPerSend) {
				try {
					Thread.sleep(msPerSend - (System.currentTimeMillis() - messageStartTime));
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				// TODO If we were smart, we would start shaving time off the NEXT wait to make up for this.
				// That's also what we would do to help account for the drift - if total time taken is larger
				// than what it should be, wait less on the next one.
				System.err.println("Somehow took longer to send than we budgeted time for.");
			}
		}
		
		// Saw a little bit of drift here, but I'm going to say it's okay for now. Should take a look at it later. Didn't seem 100% monotonically increasing
		// although I did see some general positive drift as the number of messages increased. Might have to do with integer wait values and rounding?
//		System.out.println("Time duration at end: " + (System.currentTimeMillis() - timeAtStart) + " (target: " + ms + ")");
	}
	
	protected SummaryStatistics processRoundtripStats() {
		SummaryStatistics stats = new SummaryStatistics();
		
		for(Long roundtripTime : this.roundtripTimes) {
			stats.addValue(roundtripTime);
		}
		
		System.out.format(" n: %5d min: %8.0f  mean: %8.0f   max: %8.0f   stdev: %8.0f\n", stats.getN(), stats.getMin(), stats.getMean(), stats.getMax(), stats.getStandardDeviation());
		
		return stats;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// Just start the thread.
		
		SocketIOLoadTester tester = new SocketIOLoadTester(2000);
		tester.start();
	}

	@Override
	public void onError(IOException e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onMessage(String message) {
		//		System.out.println("message: " + message);
	}
	
	

	@Override
	public void onClose() {
		lostConnection = true;
		System.out.println(" failed!");
		System.out.println("Lost a connection. Shutting down.");
	}

	@Override
	public void onOpen() {
		synchronized(this) {
			numConnectionsMade++;
			if(numConnectionsMade.compareTo(concurrency)==0) {
				System.out.println("All " + concurrency + " clients connected successfully.");
				// Turn the main testing thread back on. We don't want to accidentally
				// be executing on some clients main thread.
				this.notifyAll();
			}
		}
	}

	@Override
	public void messageArrivedWithRoundtrip(long roundtripTime) {
		this.roundtripTimes.add(roundtripTime);
		
		if(this.roundtripTimes.size() == SECONDS_TO_TEST_EACH_LOAD_STATE * currentMessagesPerSecond) {
			synchronized(this) {
				this.postTestTimeout = false;
				this.notifyAll();
			}
		}
	}

}
