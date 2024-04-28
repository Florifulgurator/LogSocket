package florifulgurator.logsocket.javalggr;

import java.io.IOException;
import java.net.URI;

//Hokuspokus for quick implementation/test of different Ws Client implementations
interface IWebSocket {
	boolean connect(URI uri);
	void sendText(String txt) throws IOException;
}
