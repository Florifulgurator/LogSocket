package javalggr;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

//import javax.websocket.ContainerProvider;
//import javax.websocket.DeploymentException;
//import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
//import javax.websocket.WebSocketContainer;



public class LogSocket {
// Singleton: Private constructor.
//
// We make sure there is no superfluous Lggr object reference, to allow garbage collection when out of use.

private static IWebSocket                websocket = null; // WebSocketImpl1 javax.websocket // #6b31fbdc
                                                           // WebSocketImpl2 java.net.http.WebSocket
private static boolean debug = false;

protected static Integer                 Nr = null;  // Unique ID generated by LogSocketServer & set via "/START Nr"
private static URI                       uri = URI.create("ws://localhost:8080/logsocket/ws");
protected static boolean                 isClosed = false;
protected static boolean                 serverClosed = false; //Only set by LogSocketWs.onClose() // TODO Javascript WHY?

private static Integer                   numLggrs = 0;	//Count of created Loggers

private static Hashtable<String,Integer> realmLabel2lastLggrN2 = new Hashtable<String,Integer>();

// Java 14 record instead of class brimborium
public static record LggrStringPair(Lggr lgr, String str) {}
protected static Queue<LggrStringPair>   logBuffer = new LinkedList<LggrStringPair>();
protected static boolean                 logBuffered = false;

protected static Queue<Lggr>             nrBuffer = new LinkedList<Lggr>();
protected static boolean                 nrBuffered = false;

//private static Set<Integer> ignoreLggrNr = new HashSet<Integer>(); TODO Javascript, then uncomment
private static Set<String>               realms = new HashSet<String>();

public static final Pattern              blankPttrn = Pattern.compile("\\s+");

// Singleton. No constructor necessary.
private LogSocket() {}

public static void onOpen() throws IOException {
	//WebSocketImpl1:
	// websocket.sendText("!GREETS LogSocketWs="+LogSocket.shortClObjID(LogSocket.sezs));
	// makes bug. WebSocketImpl2 hangs. ?? due to synchronized?? Initial greeting done in connect(). Works differently in Javascript.
}

public static void onClose(Session sess, Integer statuscode, String reason) {
	System.out.println("---- LogSocket: onClose sess="+shortClObjID(sess)+" statuscode="+statuscode+" reason="+reason);
	isClosed = true;
	serverClosed = true; // TODO Javascript
}

static public void onMessage(String msg, Session sess) {
	String[] cmd = blankPttrn.split(msg, 2);

	if (cmd[0].equals("/START")) {
		Integer n;
		try {
			n = Integer.parseInt(cmd[1]);
		} catch (NumberFormatException e) {
			n=9999; // debug Nr
			complain("LogSocketWs_ERROR_13: /START Argument not a number string. Setting LogSocket.Nr=9999.");
		} catch (Exception e) {
			n=7777;
			complain("LogSocketWs_ERROR_12: /START Argument missing. Setting LogSocket.Nr=7777.");
		}
		if (Nr!=null && !Nr.equals(n)) complain("LogSocketWs_ERROR_14: "+msg+" but already LogSocket.Nr="+Nr);
		Nr = n;

		if (nrBuffered ) {
			System.out.println("!--- LogSocketWs: Calling LogSocket.releaseNrBuffer(). Buffer size="+nrBuffer.size());
			releaseNrBuffer();
			System.out.println("!--- LogSocketWs: nrBuffer emptied.");
		}
		if (logBuffered ) {
			System.out.println("!--- LogSocketWs: Calling LogSocket.releaseLogBuffer(). Buffer size="+logBuffer.size());
			releaseLogBuffer();
			System.out.println("!--- LogSocketWs: logBuffer emptied.");
		}

		return;
   	}

	if (cmd[0].equals("/PING")) {
		// TODO consistency check: arg==LogSocket.Nr ?
		try {
			websocket.sendText("/PING "+"LogSocket /"+Nr+" is alive!" );
		} catch (IOException e) {
			System.err.println("!!!- LogSocket_ERROR_... (TODO) "+e.getMessage());
			isClosed = true;
		}
		return;
	}

	if (cmd[0].equals("/GC")) {
		// TODO consistency check: arg==LogSocket.Nr ?
		System.gc();
		return;
	}
	
	complain("LogSocketWs_ERROR_10: Unknown message=\""+msg+"\"");
}


private static void connect() {
	if (websocket!=null) {
		System.err.println("---- LogSocket_ERROR_6: Bug/FIXME: WebSocket already there.");
		return;
	}	
	websocket = new WebSocketImpl1(); // !!!!!!!!!!!!!!!!!!!!!!!!!! #6b31fbdc
	isClosed = !websocket.connect(uri);
	
	if (!isClosed) {
		try {
			websocket.sendText("!GREETS");
		} catch (Exception e) {
			System.err.println("!!!- LogSocket_ERROR_... (TODO) "+e.getMessage());
			isClosed = true;
		}
	}
}
	

public static Lggr newLggr(Object o, String realm, String label) {
		return newLggr(realm, label+"#"+shortClObjID(o) , o.toString());  //TODO JS
}
public static Lggr newLggr(String realm, String label) {
	return newLggr(realm, label, "");
}

public static Lggr newLggr(String realm, String label, String comment) { // label e.g. #bla1#BLA77, which is two labels that can be filtered separately.
	if ( websocket==null ) connect();

	if (label.isEmpty()) {
		label = "#INFO";
	} else if (label.charAt(0)!='#') {
		label = "#"+label; // else bug!
		//TODO complain
		//TODO filter whitespace
	}

	realms.add(realm);
	
	Integer lastLggrN2 = realmLabel2lastLggrN2.get(realm+label);
	Integer n2;
	if ( lastLggrN2!=null ) {
		n2 = lastLggrN2 + 1;
	} else {
		n2 = 0;
	}
	
	Lggr newLggr = new Lggr(realm, label, n2, comment.trim(), numLggrs++);
	
	realmLabel2lastLggrN2.put(realm+label,newLggr.n2); 
	
	if (Nr==null) {
		nrBuffer.add(newLggr);
		nrBuffered = true;
	} else if (!isClosed) { //FIXME #A JavaScript
		try {
			websocket.sendText(NEW_LGGR_cmd(newLggr));
		} catch (Exception e) {
			System.err.println("---- LogSocket_ERROR_4 IOException: "+e.getMessage());
			isClosed = true;// #A
			newLggr.comment = "LogSocket_ERROR_4 Exception: "+e.getMessage(); // #A
		}
	}
	
	return newLggr;
}

private static String NEW_LGGR_cmd(Lggr lgr) { // see also DEL_LGGR
	return "!NEW_LGGR " + lgr.shortId + " " + lgr.longId + (lgr.comment!=null ? " "+lgr.comment : "");
}

static void releaseNrBuffer() {
	if (Nr==null) { System.err.println("!!!- LogSocket_ERROR_15: FATAL BUG. Nr should not be null"); }
	if (!nrBuffered) return;
	nrBuffered = false;
	while (!nrBuffer.isEmpty()) {
		Lggr bffrdLggr = nrBuffer.remove();
		bffrdLggr.comment = "[NR_BUF] "+bffrdLggr.comment;
		bffrdLggr.makeLggrIdStrings();
		try {
			websocket.sendText(NEW_LGGR_cmd(bffrdLggr));
		} catch (Exception e) {
			System.err.println("---- LogSocket_ERROR_5 Exception: "+e.getMessage());
		}
	}
}


public static boolean notFiltered(Lggr lgr, String msg) {
	// First fast step of filtering >>>>>>>>>
	// Major TODO !
	if ( isClosed ) {
		//debugMsg(1,"msg dropped: "+msg);
		return false;
	}
	// if ( ignoreLggrNr.contains(lgr.nr) ) {	return false;	} TODO Javascript, then uncomment.
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	return true;
}

public static boolean notBuffered(Lggr lgr, String msg) {
	if (Nr == null) {
		//debugMsg(0,"!--- LogSocket.log(): BUFFERING to logBuffer while waiting for LogSocket.Nr");
		logBuffer.add( new LggrStringPair(lgr,msg)); // TODO flag
		logBuffered = true;
		return false;
	}
	if (logBuffered) {
		//debugMsg(0,"!--- LogSocket.log(): Calling releaseLogBuffer(). Buffer size="+logBuffer.size());
		// The Nr==null problem concerned every Lggr of this LogSocket, so
		// the ordering remains correct for all messages of this LogSocket ONLY.
		releaseLogBuffer();
		//debugMsg(0,"!--- LogSocket.log(): logBuffer emptied.");
	}
	return true;
}

static void releaseLogBuffer() {
	if (Nr==null) { System.err.println("---- LogSocket_ERROR_14: FATAL BUG. Nr should not be null"); return;}

	logBuffered = false;
	while (!logBuffer.isEmpty()) {
		LggrStringPair pair = logBuffer.remove();
		Lggr bffrdLggr = pair.lgr;
		bffrdLggr.makeLggrIdStrings();
		log(bffrdLggr, pair.str, '~', 2); // #c29e692
	}
}

//--------------------
private static void complain(String txt) { //TODO JavaScript
	System.err.println("!!-- "+txt);
	try {
		websocket.sendText("/ERROR "+txt);
	} catch (IOException e) {
		// Maybe that was the reason for the complaint
	}
}

// Logging services: >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

public static boolean log(Lggr lgr, String msg) { return log(lgr, msg, '*', 0); }
public static boolean logM(Lggr lgr, String msg) { return log(lgr, msg, '+', 0); } //TODO Javascript
public static boolean logErr(Lggr lgr, String msg) { return log(lgr, msg, '+', 1); } //TODO Javascript
//hardwired #c29e692  logBuf(Lggr lgr, String msg) { return log(lgr, msg, '~', 2); } //DONE Javascript
public static boolean logCmd(Lggr lgr, String cmd) { return log(lgr, cmd, '$', 0); } //TODO Javascript

private static final String[] flag2Str = {" ", "&E ", "&B "}; //for performance: flag as it appears in sendText(...) incl. surroundings 
// #2fa32174   0 == nothing, 1 == Error  2 == Buffered //TODO 3 == Timer

//TODO Refactor copy-paste
public static boolean log(Lggr lgr, String msg, char firstChar, int flag) {
	if ( notFiltered(lgr, msg) ) {
		if ( notBuffered(lgr, msg) ) {
			try {
				websocket.sendText( firstChar+lgr.longId+"&"+lgr.shortId+"&"+(++lgr.numMsgs)+flag2Str[flag]+msg );
			} catch (IOException e) {
				System.err.println("!!!- LogSocket_ERROR_... (TODO) "+e.getMessage());
				isClosed = true;
				return false;
			}
		} // else: notBuffered(lgr, msg) does logBuffer.add( new LggrStringPair(lgr,msg));
		return true;
	} 
	return false;
}

// Global timers managed by LogSocketServer
public static boolean timerStartStop(Lggr lgr, String timerName, boolean start) {
	if (timerName.contains(" ")) {
		logErr(lgr, "Bad timer name \""+timerName+"\". Doing nothing.");
		return false;
	}
	String msg = (start ? "!T_START " : "!T_STOP ") + timerName;
	if ( notFiltered(lgr, msg) ) {
		if ( notBuffered(lgr, msg) ) {
			try {
				websocket.sendText( msg );
			} catch (IOException e) {
				System.err.println("!!!- LogSocket_ERROR_... (TODO) "+e.getMessage());
				isClosed = true;
			}
		}
		return true;
	} 
	return false;
}

//Internal command
public static boolean sendCmd(Lggr lgr, String cmd) {
	if ( notFiltered(lgr, cmd) ) {
		if ( notBuffered(lgr, cmd) ) {
			try {
				websocket.sendText( cmd );
			} catch (IOException e) {
				System.err.println("!!!- LogSocket_ERROR_... (TODO) "+e.getMessage());
				isClosed = true;
			}
		}
		return true;
	} 
	return false;
}


// Logging utilities >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
public static String getCaller(int skp)	{
	// getCaller(0)=="getCaller"
	// getCaller(1)==called
	return StackWalker.getInstance().
		walk(stream -> stream.skip(skp).findFirst().get()).
		getMethodName();
}

public static String shortClObjID(Object o) {
	return (o==null)?"null":Stream.of(o.toString().split("[.]")).reduce((first,last)->last).get();
}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


@SuppressWarnings("unused")
private static void debugMsg(int except, String txt) {
	if (!debug && except==0 ) return;
	System.out.println(txt);
}


}

/*
"!!!- LogSocket_ERROR_2: No WebSocketContainer! API gives no reason, just a null. Likely the web app server is missing."
"---- LogSocket_ERROR_3 Exception: "+e.getMessage()
"---- LogSocket_ERROR_4 IOException: "+e.getMessage()
"!!!- LogSocket_ERROR_15: FATAL BUG. Nr should not be null"
"---- LogSocket_ERROR_5 Exception: "+e.getMessage()
"---- LogSocket_ERROR_14: FATAL BUG. Nr should not be null"
"!--- LogSocket_ERROR_13: session is null"
"!!-- LogSocket_ERROR_7: Exception: "+e.getMessage()
"---- LogSocket_ERROR_8: Exception:"+e.getMessage()
 */
