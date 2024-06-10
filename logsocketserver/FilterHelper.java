package florifulgurator.logsocket.server;

/**
 * 
 */
public class FilterHelper {

	public static synchronized void start() {
		// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
		LogSocketServer.exctrService.execute( () -> { // >>>>>>>>>>>>>>>>>>
			System.out.println("==== FilterHelper thread BEGIN");//DIAGN
			String cmd;
			
			while (!Thread.interrupted()) {
				try {
					cmd = LogSocketServer.fltrHlprQ.take();
System.out.println("==== FilterHelper DEV ... "+cmd);//DEV
					
					
					
				} catch (InterruptedException e) {
					break;
				}
			}			
			System.out.println("==== FilterHelper thread END");//DIAGN
		}); // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
		// Thread <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	}
	
	@SuppressWarnings("serial")
	static class FilterHelperError extends RuntimeException {
		public FilterHelperError(String s) { super(s); }
		@Override
		public synchronized Throwable fillInStackTrace() { return this; }
	}

}
