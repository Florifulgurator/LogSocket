package florifulgurator.logsocket.javalggr;

import java.lang.ref.Cleaner;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class Lggr {
	
	//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	// First 3 fields give an information-rich Logger ID.
	// Unique for the associated LogSocket.
	// Including LogSocket.Nr gives global uniqueness.
	public String realm = "Jv";   // Default realm: Jv=Java
	public String label;          // A label always starts with "#" and may contain sub-labels
	public int n2;                // Realm+Label duplicate number, determined by LogSocket
	//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	public int nr;     	   	       // Counted by LogSocket. Incl. LogSocket.Nr gives unique no-information shortId for Listener GUI.
	                               // nr==0 : realm+label listed as stopped/filtered before creation. => no longId, shortId, no finalization registry //DEV #6cb5e491
	public boolean on = false;     // Logger messages switched on/off: A) when filtered "M" or when B) max log msg count reached
	public int numMsgs = 0;        // Counted by LogSocket
	public String longId;	         // Human readable informational unique Id, for syntax see LogSocket#makeLggrIdStrings
	public String shortId = null;  // "/"+LogSocket.Nr+"_"+nr
	public String comment;
	
	Cleaner.Cleanable cleanable = null;       // ==null when filtered "E" 

	long labelsCode = 0L;                     // !=0L when registered for filter and cleanup of weak references
	public boolean dontMakeKnown = false;     // When filtered "E" before makeKnown(): "Silent" cleanup #5f5d8a51
	LogSocket.LggrRefRcrd lggrRefRcrd = null;
	public String filterResult = null;				// null== Filter not yet applied, ""== not filtered, "E","M"== filter result
	
	// Special logging functionalities:
	public Hashtable<String,Integer> repeatCounters; // log max. n messages as counted by counter logCntrID
	
	public int microTimerWarmupCntr;
	public int microTimerTickCntr;
	public int microTimerMaxTicks;
	public long[] microTimerTickDiffs;
	public long microTimerLast;
		
	
	protected Lggr(String rlm, String lbl, Integer i2, long lblsCode, String commnt, Integer n) {
		realm = rlm; label = lbl; n2 = i2; comment = commnt; nr = n; labelsCode = lblsCode;
		
		if (nr!=0) {  //DEV #6cb5e491
			on=true; //TODO JS
			repeatCounters = new Hashtable<String,Integer> ();
			LogSocket.makeLggrIdStrings(this);
		}
	}

	public String toString() { return longId; } // Should not be used
	
	
	// Logger services:
	
	public boolean log (String msg) { //TODO performance: directly call LogSocket.log(Lggr lgr, String msg, char firstChar, int flag)
		if (on) return LogSocket.log(this, msg); else return false;
	}

	public boolean logErr (String msg) { if (on) return LogSocket.logErr(this, msg); else return false;	}
	
	public boolean logM (String msg) { //TODO: Javascript
		if (on) return LogSocket.logM(this, msg); else return false;
	}

	public boolean logC (Integer n, String logCntrID, String msg) {
		// Log max. n messages as counted by counter logCntrID
		if (!on) return false;
		Integer count = repeatCounters.get(logCntrID);
		if (count==null) count=0;
		if (count < n) {
			repeatCounters.put(logCntrID, ++count);
			return LogSocket.log(this, "["+count+"/"+n+"] "+msg);
		}
		return false;
	}

	public boolean logCM (Integer n, String logCntrID, String msg) {
		//TODO: Javascript
		if (!on) return false;
		Integer count = repeatCounters.get(logCntrID);
		if (count==null) count=0;
		if (count < n) {
			repeatCounters.put(logCntrID, ++count);
			return LogSocket.logM(this, "["+count+"/"+n+"] "+msg);
		}
		return false;
	}

	public boolean logOnce (String msg) { // Unlike logC/logCM turns off complete logger.
		if (on) { on=false;	return LogSocket.log(this, "[1/1] "+msg); } else return false;
	}

	public CompletableFuture<String> logCFtr (String msg) {
		if (!on) return CompletableFuture.completedFuture("");
		return LogSocket.logCFtr(this, msg);
	}

	// Global named timers managed by LogSocketServer
	public boolean timerStart (String timerName) {
		if (on) return LogSocket.timerStartStop(this, timerName, true); else return false;
	}
	public boolean timerStop (String timerName) {
		if (on) return LogSocket.timerStartStop(this, timerName, false); else return false;
	}
	
	// DOCU #95f0f06 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	// Local unnamed timer for many time measurements between calls to microTimerTick(), after a
	// a number of Warm-up ticks. Initialized by microTimerClear(int maxTicks, int warmupTicks).
	// At the end the results get sent to server in one swoop by microTimerReport(String reportName).
	// Server accumulates these results in a named "random variable"
	// which can be emptied by microTimerClearReport(String reportName).
	// Upon microTimerLogReport(String reportName) resp. microTimerPostReport(String reportName)
	// the server draws an ASCII art diagram of the distribution of values and either puts it in
	// the log or TODO posts it in the log header.

	public void microTimerClear(int maxTicks, int warmupTicks) {
		if (maxTicks<=0) {
			LogSocket.sendCmd(this, "%/ERROR microTimerClear("+maxTicks+","+warmupTicks+")");
			maxTicks = 0; warmupTicks = 0;
		}
		microTimerTickDiffs = new long[maxTicks];
		microTimerMaxTicks = maxTicks;
		microTimerWarmupCntr = warmupTicks;
		microTimerLast = System.nanoTime();
		microTimerTickCntr = 0;
	}
	//---	
	public void microTimerTick() {
		if(microTimerWarmupCntr <= 0 ) {
			if(microTimerTickCntr < microTimerMaxTicks) {
				microTimerTickDiffs[microTimerTickCntr++] = -microTimerLast + (microTimerLast = System.nanoTime());
			}
		} else {
			microTimerWarmupCntr--;
			microTimerLast = System.nanoTime();
		}
	}
	//---	
	public void microTimerClearReport(String reportName) {
		if (isBadReportName(reportName))  return ;
		LogSocket.sendCmd(this, "!MT_NEW "+reportName);
	}
	//---	
	// Send results in 1/10 microseconds, to spare bandwidth. Server converts to microseconds.
	// (The last 2 digits of nanotime are worthless, cf. Clock.java)
	public int microTimerReport(String reportName) {
		if (isBadReportName(reportName))  return 0;
		LogSocket.sendCmd(this, "!MT_REPORT "+reportName+" 0.1 "
		   + Arrays.stream(microTimerTickDiffs, 0, microTimerTickCntr)
		   		   .map( x-> Math.round((float)x/100.0) )
		           .mapToObj(String::valueOf).collect(Collectors.joining(" "))
		 );
		return microTimerTickCntr;
	}
	//---	
	public void microTimerLogReport(String reportName) {
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
