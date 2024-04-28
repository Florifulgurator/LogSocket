package florifulgurator.logsocket.javalggr;

import java.io.IOException;
import java.net.URI;

interface IWebSocket {
	void connect(URI uri); // Not waiting for connection!
	boolean isConnecting();
	static int CONN_TRIALS = 20;
	boolean isConnected();
	void sendText(String txt) throws IOException; //synchronized!
	void close();
}
