package florifulgurator.logsocket.javalggr;

import java.io.IOException;
import java.net.URI;
import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

public class WebSocketImpl1 implements IWebSocket {

	private static volatile boolean cnncting = false;
	@Override
	public boolean isConnecting() {return cnncting;};
	private static volatile boolean cnncted = false;
	@Override
	public boolean isConnected() {return cnncted;};

	private static   WebSocketContainer contner = null;
	protected static Session sezs = null; // later == contner.connectToServer(LogSocketWs.class, uri);
	protected static RemoteEndpoint.Basic sezsREB = null;


	public WebSocketImpl1() {
		try {
			contner = ContainerProvider.getWebSocketContainer();
		} catch ( Exception e) {
			System.err.println("!!!- LogSocket_ERROR_2a: "+e.getMessage());
			contner = null;
		}
		if (contner==null) {
			System.err.println("!!!- LogSocket_ERROR_2b: No WebSocketContainer! Likely the web app server is missing.");
		}
	}

	@Override
	synchronized public void connect(URI uri) {
		if (cnncting||cnncted) return;

		// Thread, to not block users of LogSocket >>>>>>>>>>>>>>>>>>>>>>>>>>>>
		LogSocket.exctrService.execute( ()->{
			sezsREB=null; sezs=null; // tabula rasa

			int cntr = 0;
			do {
				if (Thread.interrupted()) {
					cnncting = false; cnncted = false;
					System.out.println("==== WebSocketImpl1.connect("+uri+") Interrupted. NOT CONNECTED.");
					return;
				}
				
				try {
					System.out.println("==== WebSocketImpl1.connect("+uri+") trying to connect");
					// ///////////////////////////////////////////////////////////
					contner.connectToServer(Ws.class, uri);                     //
					// if no server, blocks 2000ms (nowhere documented)         //
					// else IMMEDIATELY calls onOpen! Assign Sessios sezs there //
					// ///////////////////////////////////////////////////////////
					cnncting = false; cnncted = true;
					System.out.println("==== WebSocketImpl1.connect("+uri+") OK! sezs="+sezs);
					return;
					
				} catch (DeploymentException e) {
					System.err.println("!... WebSocketImpl1.connect("+uri+") connection attempt failed: "+e.getMessage());
					try {
						Thread.sleep(cntr*500);	//https://resilience4j.readme.io/docs/getting-started Retries with Exponential Backoff and Jitter
					} catch (InterruptedException e1) {
						cnncting = false; cnncted = false;
						System.out.println("==== WebSocketImpl1.connect("+uri+") Sleep interrupted. NOT CONNECTED.");
						return;
					}
					
					// <<< next try <<<
					
				} catch (Exception e) {
					cnncting = false; cnncted = false;
					System.err.println("!!!. WebSocketImpl1.connect("+uri+") FAILED: "+e.getClass().getName()+" "+e.getMessage());
					return;
				}
		
			} while ( ++cntr <= CONN_TRIALS );
			
			cnncting = false; cnncted = false;
			System.err.println("!!!! WebSocketImpl1.connect("+uri+") GIVING UP");

		} ); // Thread <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	}


	@Override
	synchronized public void sendText(String txt) throws IOException {
		try { sezsREB.sendText(txt); }
		catch (Exception e) {
			System.err.println("!!!. WebSocketImpl1.sendText(txt): "+e.getClass().getName()+" while sending txt=\""+txt+"\" --- "+e.getMessage());
			// FIXME #11811f4b  synchronized added, does it help?
			// Feb 09, 2024 2:32:56 PM org.apache.catalina.startup.Catalina start
			// INFO: Server startup in [1166] milliseconds
			// !--- LogSocketWs: Calling LogSocket.releaseNrBuffer(). Buffer size=1
			// !--- LogSocketWs: nrBuffer emptied.
			// !--- LogSocketWs: Calling LogSocket.releaseLogBuffer(). Buffer size=1
			// !--- LogSocketWs: logBuffer emptied.
			// !!!. WebSocketImpl1.sendText(txt): java.lang.IllegalStateException while sending txt="~Jsp/1#HELLOWORLD&/1_0&1&B !!!!!!!!!!!!!!!! Hello World !!!!!!!!!!!!!!!! (UTF-8 test: 👁↑)" --- The remote endpoint was in state [TEXT_FULL_WRITING] which is an invalid state for called method
		}
	}


	@ClientEndpoint
	public static class Ws {
		
		@OnOpen
		public void onOpen(Session sess) {
			sezs = sess;
			sezsREB = sezs.getBasicRemote();
			cnncting = false; cnncted = true;
			LogSocket.onOpen();
		}
	
		@OnMessage
		public void onMessage(String msg, Session sess) { LogSocket.onMessage(msg, sess); }
	
		@OnClose
		public void onClose(Session sess) {
			LogSocket.onClose(sess, null, "");
			sezsREB=null; sezs=null; cnncted = false;
		}

		@OnError
		public void onError(Session sess, Throwable thrbl) { LogSocket.onError(sess, thrbl); }
	}


	@Override
	public void close() { //TODO/FIXME seriously needed?
		if (sezs!=null) {
			try {
				sezs.close();
			} catch (IOException e) {
				System.err.println("!!.. WebSocketImpl1.close(): "+e.getClass().getName()+" "+e.getMessage());
			}
		} else {
			System.err.println("!!.. WebSocketImpl1.close(): Session is null!");
		}
	}

}
