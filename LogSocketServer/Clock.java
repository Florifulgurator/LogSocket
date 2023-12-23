package florifulgurator.logsocket.server;

import java.util.concurrent.Callable;

public class Clock {
	private static final long T0 = System.nanoTime();    //Last 2 digits worthless: Either 99 or 00
	public static  int        I_nanoUnit = 100;          // 100: 1Tick == 10microSecs=1/100ms || 10: 1Tick==1/10ms
	public static  double     nanoUnit = 1.0/I_nanoUnit; // #1298bf37
	public static  double     I_micro_nanoUnit = I_nanoUnit/1000000.0;

	// T()*nanoUnit = ms
	public static  long T()    { return Math.round((System.nanoTime()-T0) *I_micro_nanoUnit); }
	public static  long T_ms() { return Math.round((System.nanoTime()-T0) /1000000.0); }
	public static String formatT(long T) {return T+"-TODO";} //TODO
	
	public static class SyncStatDaemon implements Callable<String> {
		private String id;
		
		public SyncStatDaemon(String str) {
		//setDaemon(true);
			LogSocketServer.debugMsg(1, ";;;; SyncStatDemon#SyncStatDemon(str) str="+str);
			this.id = str;
		}
		@Override
		public String call() throws Exception {
			LogSocketServer.debugMsg(1, ";;;; SyncStatDemon#call() id="+id);
//TODO
			Thread.yield();
			return id;
		}
			
	}
}