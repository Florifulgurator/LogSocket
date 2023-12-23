package javalggr;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.stream.Collectors;

public class Lggr {
	
	//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	// First 3 fields give an information-rich Logger ID.
	// Unique for the associated LogSocket.
	// Including LogSocket.Nr gives global uniqueness.
	public String realm = "Jv";        // Default realm: Jv=Java
	public String label;               // 
	public Integer n2;                 // Realm+Label duplicate number, determined by LogSocket
	// Caveats: 1) One LogSocket might serve several realms, e.g. Java-logic, Java-servlet, Java-JSP
	//          2) Distinct LogSockets might represent the same JavaScript page, e.g. in separate browser windows.
	//          3) Duplicate labels can occur by user choice (resp. error) or by repeated creation at the same place,
	//             e.g. in a procedure.
	// Therefore we can only filter for realm and label, and cut off duplicates.
	//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	public Integer nr;     	   // Counted by LogSocket. Incl. LogSocket.Nr gives short no-information ID for Listener GUI.

	public boolean on = true;  // Logging switched on/off
	public int numMsgs = 0;    // Counted by LogSocket
	public String longId;
	public String shortId;    // "/"+LogSocket.Nr+"_"+nr null until handshake completed
	public String comment;
	public Hashtable<String,Integer> repeatCounters; // log max. n messages as counted by counter logCntrID
	
	public int nanoTimerWarmupCntr;
	public int nanoTimerTickCntr;
	public int nanoTimerMaxTicks;
	public long[] nanoTimerTickDiffs;
	public long nanoTimerLast;
		

	
	
	
	protected Lggr(String rlm, String lbl, Integer i2, String commnt, Integer n) {
		//System.out.println("xxx- Lggr: CONSTRUCTOR rlm="+rlm+" lbl="+lbl+" n2"+i2+" nr"+n);
		realm = rlm; label = lbl; n2 = i2; comment = commnt; nr = n;
		repeatCounters = new Hashtable<String,Integer> ();
		makeLggrIdStrings();
	}

	protected void makeLggrIdStrings() {
		if ( shortId!=null) {
			//System.out.println("makeLggrIdStrings DOING NOTHING shortId=" + shortId + " longId=" + longId);
			return;
		}
		
		if ( n2!=0 ) { longId = realm + "/" + LogSocket.Nr + label + "." + n2.toString();
		} else       { longId = realm + "/" + LogSocket.Nr + label; }
		
		if (LogSocket.Nr!=null) { shortId = "/"+LogSocket.Nr+"_"+nr; }
	}
	
	public String toString() { return longId; } // Should not be used
	
	protected void finalize() { //TODO if Nr==null
		String s = shortId + " "+ realm + "/" + LogSocket.Nr;
		LogSocket.sendCmd(this, "/GC Garbage collection: Finalizing Logger " + s);
		LogSocket.sendCmd(this, "!DEL_LGGR " + s);
	}

	
	// Logger services: >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	
	public boolean log (String msg) { //TODO performance: directly call LogSocket.log(Lggr lgr, String msg, char firstChar, int flag)
		if (on) return LogSocket.log(this, msg); else return false;
	}

	public boolean logErr (String msg) {  //TODO: Javascript
		if (on) return LogSocket.logErr(this, msg); else return false;
	}
	
	public boolean logM (String msg) { //TODO: Javascript
		if (on) return LogSocket.logM(this, msg); else return false;
	}

	public boolean logC (Integer n, String logCntrID, String msg) {
		// log max. n messages as counted by counter logCntrID
		if (!on) return false;
		int count = 1;
		if (repeatCounters.containsKey(logCntrID)) {
			count = repeatCounters.get(logCntrID);
			if (++count >= n) on=false;
		}
		repeatCounters.put(logCntrID, count);
		return LogSocket.log(this, "["+count+"/"+n+"] "+msg);
	}

	public boolean logCM (Integer n, String logCntrID, String msg) {
		//TODO: Javascript
		if (!on) return false;
		int count = 1;
		if (repeatCounters.containsKey(logCntrID)) {
			count = repeatCounters.get(logCntrID);
			if (++count >= n) on=false;
		}
		repeatCounters.put(logCntrID, count);
		return LogSocket.logM(this, "["+count+"/"+n+"] "+msg);
	}

	public boolean logOnce (String msg) {
		if (on) { on=false;	return LogSocket.log(this, "[1/1] "+msg); } else return false;
	}

	// Global named timers managed by LogSocketServer
	public boolean timerStart (String timerName) {
			if (on) return LogSocket.timerStartStop(this, timerName, true); else return false;
	}
	public boolean timerStop (String timerName) {
			if (on) return LogSocket.timerStartStop(this, timerName, false); else return false;
	}
	
	//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	// Local unnamed timer for many time measurements between calls to nanoTimerTick(), after a
	// a number of Warm-up ticks. Initialized by nanoTimerClear(int maxTicks, int warmupTicks).
	// At the end the results get sent to server in one swoop by nanoTimerReport(String reportName).
	// Server accumulates these results in a named "random variable"
	// which can be emptied by nanoTimerClearReport(String reportName).
	// Upon nanoTimerLogReport(String reportName) resp. nanoTimerPostReport(String reportName)
	// the server draws an ASCII art diagram of the distribution of values and either puts it in the log
	// or TODO posts it in the log header.
	// TODO #1f6d462d Check reportName has no whitespace OR: Make sure this wont break it (server).

	public void nanoTimerClear(int maxTicks, int warmupTicks) {
		if (maxTicks<=0) {
			LogSocket.sendCmd(this, "/ERROR nanoTimerClear("+maxTicks+","+warmupTicks+")");
			maxTicks = 0; warmupTicks = 0;
		}
		nanoTimerTickDiffs = new long[maxTicks];
		nanoTimerMaxTicks = maxTicks;
		nanoTimerWarmupCntr = warmupTicks;
		nanoTimerLast = System.nanoTime();
		nanoTimerTickCntr = 0;
	}
	//---	
	public void nanoTimerTick() {
		if(nanoTimerWarmupCntr <= 0 ) {
			if(nanoTimerTickCntr < nanoTimerMaxTicks) {
				nanoTimerTickDiffs[nanoTimerTickCntr++] = -nanoTimerLast + (nanoTimerLast = System.nanoTime());
			}
		} else {
			nanoTimerWarmupCntr--;
			nanoTimerLast = System.nanoTime();
		}
	}
	//---	
	public void nanoTimerClearReport(String reportName) {
		if (isBadReportName(reportName))  return ;
		LogSocket.sendCmd(this, "!MT_NEW "+reportName);
	}
	//---	
	// Send results in 1/10 microseconds, to spare bandwidth. Server converts to microseconds.
	// (The last 2 digits of nanotime are worthless, cf. Clock.java)
	public int nanoTimerReport(String reportName) {
		if (isBadReportName(reportName))  return 0;
		LogSocket.sendCmd(this, "!MT_REPORT "+reportName+" 0.1 "
		   + Arrays.stream(nanoTimerTickDiffs, 0, nanoTimerTickCntr)
		   		.map( x-> Math.round((float)x/100.0) )
		     .mapToObj(String::valueOf).collect(Collectors.joining(" "))
		 );
		return nanoTimerTickCntr;
	}
	//---	
	public void nanoTimerLogReport(String reportName) {
		if (isBadReportName(reportName))  return;
		LogSocket.logCmd(this, "MT_LOG "+reportName);
	}
	//---	
	public boolean isBadReportName(String reportName) {
		if (reportName.contains(" ")) {
			logErr("Report name \""+reportName+"\" contains whitespace. Doing nothing.");
			return true;
		}
		return false;
	}
}
