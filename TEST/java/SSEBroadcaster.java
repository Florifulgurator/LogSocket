

import java.io.IOException;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.w3c.dom.events.EventTarget;

import florifulgurator.logsocket.javalggr.Lggr;
import florifulgurator.logsocket.javalggr.LogSocket;


/**
 * This class implements a one-to-many connection for broadcasting messages across multiple subscribers.
 *
 * @author <a href="http://github.com/mariomac">Mario Macías</a>
 */
public class SSEBroadcaster {
	private Lggr l3 = LogSocket.newLggr("Jv", "#SSEBroadcaster");

    protected Queue<SSETarget> targets = new ConcurrentLinkedQueue<>();

	/**
	 * <p>Adds a subscriber from a <code>connectionRequest</code> that contains the information to allow sending back
	 * information to the subsbriber (e.g. an <code>HttpServletRequest</code> for servlets or <code>HttpServerRequest</code>
	 * for VertX)</p>
	 *
	 * @param eventTarget an event target to be subscribed to the broadcast messages
	 *
	 * @throws IOException if there was an error during the acknowledge process between broadcaster and subscriber
	 */
	public void addSubscriber(SSETarget eventTarget) throws IOException {
        targets.add(eventTarget.ok().open());
    }

	/**
	 * <p>Adds a subscriber to the broadcaster from a <code>connectionRequest</code> reference that contains the information to allow sending back
	 * information to the subsbriber (e.g. an <code>HttpServletRequest</code> for servlets or <code>HttpServerRequest</code>
	 * for VertX).</p>
	 *
	 *
	 *
	 * @param eventTarget an event target to be subscribed to the broadcast messages
	 * @param welcomeMessage The welcome message
	 * @throws IOException if there was an error during the acknowledge process between broadcaster and subscriber, or
	 *         if the subscriber immediately closed the connection before receiving the welcome message
	 */
	public void addSubscriber(SSETarget eventTarget, SSEMessage welcomeMessage) throws IOException {
        targets.add(eventTarget.ok().open().send(welcomeMessage));
    }

	/**
	 * Get total count of subscribers. Actual number of active subscribers may be less that this.
	 * @return the size of the Set holding the subscribers
	 */
	public int getSubscriberCount() {
		return targets.size();
	}

	/**
	 * Returns true if subscriber count is greater than zero
	 * @return true if subscriber count is greater than zero
	 */
	public boolean hasSubscribers() {
		return getSubscriberCount() > 0;
	}

	/**
	 * <p>Broadcasts a {@link MessageEvent} to all the subscribers, containing only 'event' and 'data' fields.</p>
	 *
	 * <p>This method relies on the {@link EventTarget#send(MessageEvent)} method. If this method throws an
	 * {@link IOException}, the broadcaster assumes the subscriber went offline and silently detaches it
	 * from the collection of subscribers.</p>
	 *
	 * @param event The descriptor of the 'event' field.
	 * @param data The content of the 'data' field.
	 */
	public void broadcast(String event, String data) {
		broadcast(new SSEMessage.Builder()
				.setEvent(event)
				.setData(data)
				.build());
	}

	/**
	 * <p>Broadcasts a {@link MessageEvent} to the subscribers.</p>
	 *
	 * <p>This method relies on the {@link EventTarget#send(MessageEvent)} method. If this method throws an
	 * {@link IOException}, the broadcaster assumes the subscriber went offline and silently detaches it
	 * from the collection of subscribers.</p>
	 *
	 * @param messageEvent The instance that encapsulates all the desired fields for the {@link SSEMessage}
	 */
	public void broadcast(SSEMessage messageEvent) {
		l3.logM("#broadcast messageEvent="+messageEvent.toString());
        for (Iterator<SSETarget> it = targets.iterator(); it.hasNext(); ) {
        	SSETarget dispatcher = it.next();
            try {
                dispatcher.send(messageEvent);
            } catch (IOException e) {
            	l3.logErr("#broadcast#EXCEPTION:  \""+e.getMessage()+"\"");
                // Client disconnected. Removing from targets
                it.remove();
            }
        }
    }

	/**
	 * Closes all the connections between the broadcaster and the subscribers, and detaches all of them from the
	 * collection of subscribers.
	 */
	public void close() {
		l3.log("#close");
        for (SSETarget d : targets) {
            try {
                d.close();
            } catch (Exception e) {
        		l3.log("#close#EXCEPTION: \""+e.getMessage()+"\"");
            }
        }
        targets.clear();
    }
}

