package florifulgurator.logsocket.javalggr;

import java.io.IOException;
import java.net.URI;

import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

public class WebSocketImpl1 implements IWebSocket {

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
	public boolean connect(URI uri) {
		try {
			sezs = contner.connectToServer(Ws.class, uri);
		} catch (Exception e) {
			System.err.println("!!!- WebSocketImpl1.connect(uri) "+e.getClass().getName()+": "+e.getMessage());
			return false;
		}
		sezsREB = sezs.getBasicRemote();
		return true;
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
		
		@OnMessage
		public void onMessage(String msg, Session sess) { LogSocket.onMessage(msg, sess); }
	
		@OnOpen
		public void onOpen() throws IOException { LogSocket.onOpen(); }
	
		@OnClose
		public void onClose(Session sess) { LogSocket.onClose(sess, null, ""); }

		@OnError
		public void onError(Session sess, Throwable thrbl) { LogSocket.onError(sess, thrbl); }
}

}
