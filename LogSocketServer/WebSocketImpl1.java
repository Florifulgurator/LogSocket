package javalggr;

import java.io.IOException;
import java.net.URI;

import javax.websocket.ClientEndpoint;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
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
			System.err.println("!!!- LogSocket_ERROR_...A (TODO) "+e.getMessage());
			return false;
		}
		sezsREB = sezs.getBasicRemote();
		return true;
	}

	@Override
	public void sendText(String txt) throws IOException { sezsREB.sendText(txt); }
		

	@ClientEndpoint
	public static class Ws {
		
		@OnMessage
		public void onMessage(String msg, Session sess) { LogSocket.onMessage(msg, sess); }
	
		@OnOpen
		public void onOpen() throws IOException { LogSocket.onOpen(); }
	
		@OnClose
		public void onClose(Session sess) { LogSocket.onClose(sess, null, ""); }
}

}
