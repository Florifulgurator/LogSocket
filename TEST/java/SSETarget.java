

// Derived from @author <a href="http://github.com/mariomac">Mario Macías</a>

/*
┌29m3s JvS/1#TESTservlet.1 #POST doPost(RequestFacade@65edb6ef, ResponseFacade@61d23e8a)
│1 Jv/1#SSEBroadcaster #broadcast messageEvent=event: message …👁▼
│1 Jv/1#SSETarget #SEND messageEvent.toString()=event: message …👁▼
│4 Jv/1#SSEBroadcaster #broadcast#EXCEPTION:  "java.io.IOException: An established connection was aborted by the software in your host machine"
│1 Jv/1#SSETarget.AsyncListenerImpl #AsyncListenerImpl onError AsyncEvent@23f015a4
│0 Jv/1#SSETarget.1 #SEND messageEvent.toString()=event: message …👁▼
│1 Jv/1#SSETarget.AsyncListenerImpl #AsyncListenerImpl onComplete AsyncEvent@53a7ae31
│0 Jv/1#SSETarget.AsyncListenerImpl.1 #AsyncListenerImpl onError AsyncEvent@4c3d181c
│1 Jv/1#SSETarget.AsyncListenerImpl.1 #AsyncListenerImpl onComplete AsyncEvent@70a7b330
│0 Jv/1#SSEBroadcaster #broadcast#EXCEPTION:  "java.io.IOException: An established connection was aborted by the software in your host machine"
*/

import java.io.IOException;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import florifulgurator.logsocket.javalggr.Lggr;
import florifulgurator.logsocket.javalggr.LogSocket;

/**
 * SSE dispatcher for one-to-one connections from Server to client-side subscriber
 */
public class SSETarget {
	private Lggr l1 = LogSocket.newLggr("Jv", "#SSETarget", "Glorified javax.servlet.AsyncContext");

	private final AsyncContext asyncContext;

    /**
     * Builds a new dispatcher from an {@link HttpServletRequest} object.
     * @param request The {@link HttpServletRequest} reference, as sent by the subscriber.
     */
    public SSETarget(HttpServletRequest request) {
    	asyncContext = request.startAsync();
        asyncContext.setTimeout(0);
        asyncContext.addListener(new AsyncListenerImpl());

        l1.log("#CONSTRUCTOR called with request="+LogSocket.shortClObjID(request));
    }

    /**
     * If the connection is accepted, the server sends the 200 (OK) status message, plus the next HTTP headers:
     * <pre>
     *     Content-type: text/event-stream;charset=utf-8
     *     Cache-Control: no-cache
     *     Connection: keep-alive
     * </pre>
     * @return The same {@link SSETarget} object that received the method call
     */
	public SSETarget ok() {
        HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();

        response.setStatus(200);
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Connection","keep-alive");

    	l1.log("#HTTPHEADER SSETarget.ok() (HttpServletResponse)asyncContext.getResponse()="+LogSocket.shortClObjID(response));
    	return this;
    }

    /**
     * Responds to the client-side subscriber that the connection has been open
     *
     * @return The same {@link SSETarget} object that received the method call
     * @throws IOException if there was an error writing into the response's {@link java.io.OutputStream}. This may be
     * a common exception: e.g. it will be thrown when the SSE subscriber closes the connection
     */
    public SSETarget open() throws IOException {
    	l1.log("#OPEN");

        HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();
        response.getOutputStream().print("event: open\n\n");
        response.getOutputStream().flush();

        return this;
    }

    /**
     * Sends a {@link SSEMessage} to the subscriber, containing only 'event' and 'data' fields.
     * @param event The descriptor of the 'event' field.
     * @param data The content of the 'data' field.
     * @return The same {@link SSETarget} object that received the method call
     * @throws IOException if there was an error writing into the response's {@link java.io.OutputStream}. This may be
     * a common exception: e.g. it will be thrown when the SSE subscriber closes the connection
     */
    public SSETarget send(String event, String data) throws IOException {
    	l1.log("#SEND event="+event+" data="+data);

        HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();
        response.getOutputStream().print(
                new SSEMessage.Builder()
                    .setData(data)
                    .setEvent(event)
                    .build()
                    .toString()
        );
        response.getOutputStream().flush();
        return this;
    }

    /**
     * Sends a {@link SSEMessage} to the subscriber
     * @param messageEvent The instance that encapsulates all the desired fields for the {@link SSEMessage}
     * @return The same {@link SSETarget} object that received the method call
     * @throws IOException if there was an error writing into the response's {@link java.io.OutputStream}. This may be
     * a common exception: e.g. it will be thrown when the SSE subscriber closes the connection
     */
    public SSETarget send(SSEMessage messageEvent) throws IOException {
    	l1.logM("#SEND messageEvent.toString()="+messageEvent.toString()+"------------");

		HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();
        response.getOutputStream().print(messageEvent.toString());
		response.getOutputStream().flush();
        return this;
    }

    private boolean completed = false;

    /**
     * Closes the connection between the server and the client.
     */
    public void close() {  //TODO not called
    	l1.log("#CLOSE completed="+completed);
        if(!completed) {
            completed = true;
            asyncContext.complete();
        }
    }

    private class AsyncListenerImpl implements AsyncListener {
    	private Lggr l2 = LogSocket.newLggr("Jv", "#SSETarget#AsyncListenerImpl");
        @Override
        public void onComplete(AsyncEvent event) throws IOException {
        	l2.log("#AsyncListenerImpl onComplete "+LogSocket.shortClObjID(event));
            completed = true;
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
        	l2.log("#AsyncListenerImpl onTimeout "+LogSocket.shortClObjID(event));
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
        	l2.logErr("#AsyncListenerImpl onError "+LogSocket.shortClObjID(event));
        }

        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
        	l2.log("#AsyncListenerImpl onStartAsync "+LogSocket.shortClObjID(event));
        }
    }
}
