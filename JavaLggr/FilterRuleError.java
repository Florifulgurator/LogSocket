package florifulgurator.logsocket.javalggr;

@SuppressWarnings("serial")
public class FilterRuleError extends RuntimeException {
    public FilterRuleError(String s) { super(s); }

	@Override
    public synchronized Throwable fillInStackTrace() { return this; }
}
