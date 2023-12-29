
// Cut short from @author <a href="http://github.com/mariomac">Mario Macías</a>

/**
 * This class encapsulates a SSE Message. It may specify the next optional fields:
 *
 * <ul>
 *     <li>event: an arbitrary name describing an event type (e.g. "message", "error", "newEvent", etc...)</li>
 *     <li>data: string data to be transmitted with the event</li>
 *     <li>id: a string that provides an unique identifier for this event</li>
 *     <li>retry: a number representing the timeout (in milliseconds) that the client must wait before
 *         reconnecting again to the server, after the event has been received</li>
 * </ul>
 */
public class SSEMessage {
	private final String msg; //That's all!

	public final String toString() { return msg; }
	private SSEMessage(String msg) { this.msg = msg; }

	/**
	 * Helper class used to build a {@link SSEMessage} instance.
	 */
	public static class Builder {
		private String data = null;
		private String event = null;
		private Integer retry = 7777;
		private String id = "myFirstTest";

		public Builder setData(String data) { this.data = data; return this; }
		public Builder setEvent(String event) { this.event = event; return this; }
		public Builder setRetry(int retry) { this.retry = retry; return this; }
		public Builder setId(String id) { this.id = id; return this; }

		public SSEMessage build() {
			StringBuilder sb = new StringBuilder();
			if(event != null) {
				sb.append("event: ").append(event.replace("\n", "")).append('\n');
			}
			if(data != null) {
				for(String s : data.split("\n")) {
					sb.append("data: ").append(s).append('\n');
				}
			}
			if(retry != null) {
				sb.append("retry: ").append(retry).append('\n');
			}
			if(id != null) {
				sb.append("id: ").append(id.replace("\n","")).append('\n');
			}

			// an empty line dispatches the event
			sb.append('\n');
			return new SSEMessage(sb.toString());
		}
	}

}
