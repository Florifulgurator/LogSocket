package florifulgurator.logsocket.javalggr;

@SuppressWarnings("serial")
public class FilterError extends RuntimeException {
    public FilterError(String s) { super(s); }

	@Override
    public synchronized Throwable fillInStackTrace() { return this; }
}
