package florifulgurator.logsocket.javalggr;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

//Tomcat shuts down with memory leak WARNING. "... thread named [HttpClient-1-SelectorManager] ..."
public class WebSocketImpl2 implements IWebSocket {
	WebSocket websocket;

	@Override
	public void connect(URI uri) {
//TODO Thread
		CompletableFuture<WebSocket> cfws = HttpClient.newHttpClient().newWebSocketBuilder()
			.buildAsync(uri, new Ws());
	
		try {
			websocket = cfws.join();
			// 1 join() is defined in CompletableFuture whereas get() comes from interface Future
			// 2 join() throws unchecked exception whereas get() throws checked exceptions
			// 3 You can interrupt get() and then throws an InterruptedException
			// 4 get() method allows to specify the maximum wait time
		} catch (Exception e) {
			System.err.println("!!!- LogSocket_ERROR_...X WebSocketImpl2 (TODO) "+e.getMessage());
//TODO	
		}
//TODO
	}

	@Override
	public void sendText(String txt) { websocket.sendText(txt, true).join(); }
	
	
	private static class Ws implements Listener {
	
		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			if (!last) System.err.println("!!!- LogSocket_ERROR_...Y WebSocketImpl2  not last! (TODO: last) ");
			LogSocket.onMessage(""+data, null);  // Mysterious Java fail over f-ing CharSequence
			webSocket.request(1); //TODO explain this
			return null;
			// To control receiving of messages, a WebSocket maintains an internal counter. This counter's value is a number of times the WebSocket has yet to invoke a receive method. While this counter is zero the WebSocket does not invoke receive methods. The counter is incremented by n when request(n) is called. The counter is decremented by one when the WebSocket invokes a receive method. onOpen and onError are not receive methods. WebSocket invokes onOpen prior to any other methods on the listener. WebSocket invokes onOpen at most once. WebSocket may invoke onError at any given time. If the WebSocket invokes onError or onClose, then no further listener's methods will be invoked, no matter the value of the counter. For a newly built WebSocket the counter is zero.
			// https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.html#counter
			// CompletionStages returned from the receive methods have nothing to do with the counter of invocations. Namely, a CompletionStage does not have to be completed in order to receive more invocations of the listener's methods.
			// https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/WebSocket.Listener.html
		}

 		@Override
		public void onOpen(WebSocket webSocket) {
			webSocket.request(1); 
		// "onOpen needs to invoke request(1) on the websocket (invoking the default implementation) in order to receive further invocations."
		// https://stackoverflow.com/a/55464326/3123382
		}
    
		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			LogSocket.onClose(null, statusCode, reason);
			webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
			System.out.println("---- WebSocketImpl2 onClose <<<<<<<<<<<<<<<<<<<<<<<<<");
			return null;
		}
	}

//TODO
	@Override
	public boolean isConnecting() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isConnected() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}
}
