package florifulgurator.logsocket.server;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;


//TODO Fine-grained synchronized / locks for optimized performance.
//TODO ? StringBuilder ? StringJoiner ? Text Blocks ?

@WebListener
@ServerEndpoint("/ws")
public class LogSocketServer implements ServletContextListener  {

	public static ExecutorService  exctrService = Executors.newFixedThreadPool(30);

	// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	// Tomcat can't shut down ExecutorService:
	public void contextInitialized(ServletContextEvent sce) {
		System.out.println(">>>>>>>>>> LogSocketServer: Web application initialization is starting <<<<<<<<<<");
	} 
	public void contextDestroyed(ServletContextEvent sce) { 
		exctrService.shutdownNow();
		System.err.println(">>>>>>>>>> LogSocketServer: ServletContext is about to be shut down <<<<<<<<<<");
	} 
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	// enough of wasteful indentation :-)

public static long                        lastMsgT = -1; // #777237b5

public static record LggRcrd(String longId, String cmdPars) {}
// cmdPars for !NEW_LGGR command, to later compose %NEW_LGGR commands from buffer
public static Map<String,LggRcrd>         lggrList = new ConcurrentHashMap<String,LggRcrd>(100);
//lggr.shortId |=> [lggr.longStr, "%NEW_LGGR (...)" params (...) string]

public static Integer                     lastLogSocketNr = 0;
// LogSocket Session => LogSocket.Nr, determined by SrvrCmd "!GREETS".  ?WeakHashMap? in case onClose(...) fails
public static Map<Session, Integer>       logScktSssns = new ConcurrentHashMap <Session, Integer>(20);
public static Set<Session>                lstnrSssns = ConcurrentHashMap.newKeySet();
public static Boolean	                  hasListeners = false;

public static Queue<String>               srvrBuffer = new ConcurrentLinkedQueue<String>();

public static Map<String, Long>           timersStartT = new ConcurrentHashMap<String, Long>(20); //Clock.T()
public static Map<String, String>         timersDT = new ConcurrentHashMap<String, String>(20); // DT in ms as String

public static Map<String, RandomVar>      randomVars = new ConcurrentHashMap<String, RandomVar>(20);

//https://www.baeldung.com/java-concurrent-map
//Hashtable: 1142.45  SynchronizedHashMap: 1273.89  ConcurrentHashMap: 230.2

// Commands assigned in static {...} block later >>>>>>>>>>>>>>>>>>>>>>>>>>>
interface LggrCmd {	public void exec(String lggrLongStr, String arg); }
public static final Map<String, LggrCmd>  lggrCommands = new ConcurrentHashMap<String, LggrCmd>(3);
interface SrvrCmd {	public void exec(Session sess, String arg) throws Exception; }
public static final Map<String, SrvrCmd>  srvrCommands = new ConcurrentHashMap<String, SrvrCmd>(30);
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

public static final Pattern               blankPttrn = Pattern.compile("\\s+");
public static final Pattern               minusPttrn = Pattern.compile("-");
public static final Pattern               dotPttrn   = Pattern.compile("[.]");



//Test stuff >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	public static boolean debug = false;
	public static boolean TEST0 = false; // for Devtest WebSocketImpl2
	public static boolean TEST1 = false; // Fields public to simplify test. FIXME
	//For TEST1:
	public static String  thisClObjID = "LogSocketServer"; // assigned in constructor => NOT STATIC
	public static String  sessionClObjID = "1st assignment"; // assigned in @OnOpen => NOT STATIC
	public static String  weird = "Hello "; // assigned in @OnMessage => static
	public static int constrCtr = 0; // assigned in constructor => static
	public static int onopenCtr = 0; // assigned in @OnOpen => static
	public static Integer constrCtrB = 0; // static
	public static Integer onopenCtrB = 0; // static
	public static LogSocketServer THIS;

	public LogSocketServer() { 
		LogSocketServer.thisClObjID = shortClObjID(this);
		if(TEST1) {
			LogSocketServer.THIS = this;
			debugMsg(1, "xx.. "+thisClObjID+": CONSTRUCTOR ++constrCtr="+(++constrCtr)+" ++constrCtrB="+(++constrCtrB));
			debugMsg(1, "this.getClass(). ...: " +this.getClass().getCanonicalName());
		}
	}
	protected void finalize() {	System.out.println("xx.. "+thisClObjID+": FINALIZE "); }
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


@OnOpen
synchronized public void onOpen(Session sess, EndpointConfig epConfig) {
// when the endpoint is deployed, there is precisely one EndpointConfig instance 
// holding the configuration information for all the instances of the endpoint class. #DDS p.99
	if (lastMsgT==-1) lastMsgT = Clock.T(); // #777237b5

	//Test stuff >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	if (TEST0) System.out.println("AAAAAAAAAA--- onOpen "+shortClObjID(sess));
	if(TEST1) {
		LogSocketServer.sessionClObjID = shortClObjID(sess);
		debugMsg(1, "@OnOpen "+thisClObjID+" "+shortClObjID(sess)+" ++onopenCtr="+(++onopenCtr)+" ++onopenCtrB="+(++onopenCtrB));
	
		debugMsg(0, "        Session.getUserProperties()="+sess.getUserProperties().entrySet().stream().map(e -> e.getKey()+"="+shortClObjID(e.getValue())).collect( Collectors.joining(", ") ));
		debugMsg(0, "        EndpointConfig.getUserProperties()="+epConfig.getUserProperties().entrySet().stream().map(e -> e.getKey()+"="+shortClObjID(e.getValue())).collect( Collectors.joining(", ") ));
		// org.apache.tomcat.websocket.pojo.PojoEndpoint.methodMapping=PojoMethodMapping@41669527
		debugMsg(0, "FFFFFFF"+LogSocketServer.THIS.getClass().getName());
		//Arrays.stream(LogSocketServer.THIS.getClass().getFields()).forEach(f->debugMsg(1, f.toString()));
		// All fields shown static as declared. But some are actually not!
	}
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
}

@OnClose
synchronized public void onClose(Session sess, CloseReason reason) throws Exception {
	if(TEST0) System.out.println("AAAAAAAAAA--- onClose "+shortClObjID(sess));
	
	if ( logScktSssns.containsKey(sess) ) {
		Integer logScktNr = logScktSssns.remove(sess); // can be null!
		Set<String> delLggrs = new HashSet<String>();
		lggrList.forEach(
			(key,rcrd) -> {
				if ( key.split("_")[0].equals("/"+logScktNr) ) { //key=shortId
					delLggrs.add(key);
					sendMsgToAllListeners("%DEL_LGGR "+key+" "+rcrd.longId, true );
				}
			}
		);
		delLggrs.forEach(k->lggrList.remove(k));
		
		System.out.println(".... "+thisClObjID+" @OnClose: logSocket /"+logScktNr+" "+shortClObjID(sess)+" gone. Close reason: "+ reason.getReasonPhrase());
		
	} else {

		if (lstnrSssns.remove(sess)) {
			if (lstnrSssns.size()==0) hasListeners = false;
			System.out.println(".... "+thisClObjID+" @OnClose: listener %"+getSessIdDec(sess)+" "+shortClObjID(sess)+" gone."
					+ " Close reason: "+ reason.getReasonPhrase());
		} else {
			// FIXME 11811f4b
			System.err.println("!... "+thisClObjID+" @OnClose: PROTOCOL ERROR "+shortClObjID(sess)+" neither logSocket nor listener."
					+" Close reason: "+ reason.getReasonPhrase()
			);
		}
	}
}

@OnError
synchronized public void onError(Session sess, Throwable thr) {
	System.err.println("!!!. "+thisClObjID+" @OnError: "+shortClObjID(sess)+" "+thr.getStackTrace()  ); // .getMessage());
}

// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
@OnMessage // >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
synchronized public void onMessage(Session session, String msg) throws Exception {

	//Test stuff >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	if (TEST0) System.out.println("AAAAAAAAAA--- onMessage "+shortClObjID(session)+" msg="+msg);
	if(TEST1 && !weird.contains(shortClObjID(session))) {
		weird+=shortClObjID(session);
		System.err.println("weird=\""+weird+"\" -- "+thisClObjID+"/"+sessionClObjID
			+"\nonopenCtr="+onopenCtr+" constrCtr="+constrCtr
			+" onopenCtrB="+onopenCtrB+" constrCtrB="+constrCtrB
			+"\nLogSocketServer.THIS="+THIS.toString()+" this="+this.toString()
		);
		//Arrays.stream(THIS.getClass().getFields()).forEach(f->debugMsg(1, ">>> "+f.toString()));
	}
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	
	char ch1 = msg.charAt(0);
	
	if( ch1=='*' ) { // '*' Plain log message, should have no line breaks >>>>>>
		sendMsgToAllListeners("*"+ (-lastMsgT+(lastMsgT=Clock.T())) +"&"+msg.substring(1), false);
		return;
	} else if( ch1=='+' ) { // '+' A) Plain log message with line breaks. Extra treatment by client
	                        //     B) Error msg        >>>>>>
		sendMsgToAllListeners("+"+ (-lastMsgT+(lastMsgT=Clock.T())) +"&"+msg.substring(1), false);
		return;
 	} else if( ch1=='~' ) { // '~' LogSocket-buffered log message, possibly with line breaks. Extra treatment by client
		sendMsgToAllListeners("+&"+msg.substring(1)+ " [LogSocket buffer]", false);
		return;
  	} else if( ch1=='$' ) { // Log messages to be amended or generated by server >>>>>
  		// e.g. "$Srvlt/1#JAVALOOP.1&/1_7&1001 MT_LOG JavaLoopNano" //FIXME "Micro"
   		try {
   		   	String[] splitMsg = blankPttrn.split(msg, 3);
   			try {
   				lggrCommands.get(splitMsg[1]).exec(
   						 (-lastMsgT+(lastMsgT=Clock.T())) +"&"+splitMsg[0].substring(1),
   						 splitMsg[2]
   				);
   				// This is just to keep the formalism in line with server commands (below).
   				// There is just one such command yet, and perhaps there wont be more.
   				return;
   			} catch(Exception e) {
   			   	sendMsgToAllListeners("!ERROR 11 Server: Exception: "+e.getMessage()+" msg=\""+msg+"\"", false);
   			   	return;	
   			}
   		} catch (Exception e) {
   			sendMsgToAllListeners("!ERROR 10 Server: Syntax Error. "+e.getMessage()+" msg=\""+msg+"\"", false);
   			return;
   		}
   	}

   	// Non-log commands >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
   	//  Specific reply to session: sendText(session,...)
	
	String[] splitMsg = blankPttrn.split(msg, 2); // Pattern applied at most 1 times, array length <= 2, last entry contains rest.

	try {
		srvrCommands.get(splitMsg[0]).exec( session, splitMsg.length==1?"":splitMsg[1] );
		return;
	}
	catch(NullPointerException e) {
		sendMsgToAllListeners("!ERROR 13 Server: Unknown command. msg=\""+msg+"\"", false);
		return;
	}
	catch(Exception e) {
		sendMsgToAllListeners("!ERROR 12 Server: Unknown error. Exception message: "+e.getMessage()+" msg=\""+msg+"\"", false);
		return;
	}


}// onMessage(...)  <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

static {
// static block is indeed static (cf. TEST1), called only once:

// Commands used by onMessage(...) >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
lggrCommands.put("$MT_LOG", new LggrCmd() { public void exec(String msgPrefix, String reportName) {
 	RandomVar rnd = randomVars.get(reportName);
 	if(rnd==null) {
 		sendMsgToAllListeners("!ERROR 16 Server: !MT_LOG: Report \""+reportName+"\" not found.", false);
 		return;
 	}
 	// synchronized rnd likely is still busy receiving data from thread launched by !MT_REPORT,
 	// so we postpone things into a thread:
	exctrService.execute( () -> {
 		synchronized(randomVars) {
 			// Avoid extremely unlikely race condition:
 			// The following sendMsgToAllListeners(...) have to come in a row (no other such construct interfering)
 			// so the %MT_LOG client cmd can pick up the already stored text
 			// (i.e. JavaScript extrTxt[extrTxtCtr] = extraText; // #366de29d) to avoid sending it twice:
 			sendMsgToAllListeners("+"+msgPrefix+" MicroTimer "+reportName+" "+rnd.getASCIIart(1,"μs") , true);
  			sendMsgToAllListeners("%MT_SHOW "+reportName+" "+rnd.getASCIIartHeader(), true);
		}
	} );
}
} );
// ------------------------------------------------------------
srvrCommands.put("!MT_REPORT", new SrvrCmd() { public void exec(Session sess, String arg) {
	String[] splitArg = blankPttrn.split(arg, 3);
	Double unit; // Java Lggr sends stuff in 0.1 μs => unit=0.1	// TODO JavaScript lggr (5μs)
	try {
		unit = Double.parseDouble(splitArg[1]);
		if(splitArg.length!=3) throw new Exception("arg length!=3");
	} catch(Exception e) {
		sendMsgToAllListeners("!ERROR 15 Server: !MT_REPORT: Syntax Error: "+e.getMessage(), false);
		return;	
	}
	String reportName = splitArg[0]; // TODO #1f6d462d Check reportName has no whitespace OR: Make sure this wont break it (server).
	RandomVar rnd;

	synchronized(randomVars) {
		if( randomVars.containsKey(reportName) ) {
			rnd = randomVars.get(reportName);
		} else {
			rnd = new RandomVar();
			randomVars.put(reportName, rnd);
		}
		exctrService.execute( () -> {
			rnd.setValsIntString(splitArg[2], unit);
			debugMsg(1, "!MT_REPORT thread="+Thread.currentThread().getName());
		} );
		// RandomVar::setValsIntString is synchronized
	}
}
} );
srvrCommands.put("!HELLO", new SrvrCmd() { synchronized public void exec(Session sess, String arg) {
	//Listener wants to register.
	lstnrSssns.add(sess);
	hasListeners = true;

	//Initialize listener:
	sendText(sess, "%SESSID "+getSessIdDec(sess));//+"-"+shortClObjID(sess) );
	sendText(sess, "%LOGSOCKETS "+logSocketsToString());
	sendText(sess, "%NANOUNIT "+Clock.I_nanoUnit );
	sendText(sess, "%TIMERS "+timersToString());
	lggrList.forEach( (lggShortStr,rcrd) ->
		sendText(sess, "%NEW_LGGR "+lggShortStr+" "+rcrd.longId+(rcrd.cmdPars!=null?" "+rcrd.cmdPars:"") )
	);
	
	if (!srvrBuffer.isEmpty()) {
		sendText(sess, "* Buffered messages while nobody was listening:");
		while(!srvrBuffer.isEmpty()) { sendText(sess, srvrBuffer.remove());	}
		sendText(sess, "* Buffer emptied. Live log messages:");     		 
	}
}
} );
srvrCommands.put("!DEL_LGGR", new SrvrCmd() { synchronized public void exec(Session sess, String argStr) {
	//From Lggr.finalize()
	//TODO from onClose
	sendMsgToAllListeners("%DEL_LGGR " + argStr, true);// msg gets lost when nobody is listening
	String[] args = blankPttrn.split(argStr, 2);
	if( null == lggrList.remove(args[0]) ) {
		System.err.println("!!.. "+thisClObjID+": @onMessage NOT FOUND: "+args[0]);
		sendMsgToAllListeners("!ERROR 4 Server: !DEL_LGGR: Not found: "+args[0], false);	
	}
}
} );
srvrCommands.put("!NEW_LGGR", new SrvrCmd() { synchronized public void exec(Session sess, String argStr) {
	sendMsgToAllListeners("%NEW_LGGR " + argStr, true); 
			// true = not into srvrBuffer. Kept extra (%NEW_LGGR) or gets lost when nobody is listening
	String[] args = blankPttrn.split(argStr, 3);
	if( null != lggrList.put( args[0], new LggRcrd(args[1], args[2]) )  ) {
		System.err.println("!!.. "+thisClObjID+": @onMessage DUPLICATE: "+args[0]);
		sendMsgToAllListeners("!ERROR 3 Server: Duplicate: "+args[0], false);
	}
}
} );
srvrCommands.put("!T_STOP", new SrvrCmd() { synchronized public void exec(Session sess, String name) {
	String s = timersDT.get(name);
	if( s==null || timersStartT.get(name)==-1 ) {
		sendMsgToAllListeners("!ERROR 5 Server: Timer never started", false);
	} else {
		String T = (double)(Clock.T() - timersStartT.get(name))/(double)Clock.I_nanoUnit+"ms";
		timersDT.put(name, (s.isEmpty()) ? T : s+","+T );
		timersStartT.put(name, (long)-1);
		sendMsgToAllListeners("* Timer "+name+" STOP "+T+" <------", false);
		sendMsgToAllListeners("%TIMERS "+timersToString(), true);
	}
}
} );
srvrCommands.put("!T_START", new SrvrCmd() { synchronized public void exec(Session sess, String name) {
	timersStartT.put(name, Clock.T());
	if(!timersDT.containsKey(name)) timersDT.put(name,"");
	sendMsgToAllListeners("* Timer "+name+" START ------>", false);
}
} );
srvrCommands.put("!GREETS", new SrvrCmd() { synchronized public void exec(Session LgScktSssn, String arg) {
	//Initialize logSocket
	//TODO re-use old numbers according to realm
	//TODO check if LgScktSssn is indeed a LogSocket session
	if( logScktSssns.isEmpty() ) {
		lastLogSocketNr = 0;
		//if(debug) debugMsg("xx.. "+thisClObjID+": registerNewLogSocketNr: TABULA RASA");
		if ( !lggrList.isEmpty() ) {
			System.err.println("!!.. "+thisClObjID+": !GREETS: logScktSssns.isEmpty() but lggrList not empty.");
			lggrList.clear();
		}
	} else if( logScktSssns.containsKey(LgScktSssn) ) {
		System.err.println("!!!. "+thisClObjID+": **BUG** !GREETS: "+shortClObjID(LgScktSssn)+" already known.");
	}			
	logScktSssns.put(LgScktSssn, ++lastLogSocketNr);
	
	sendText(LgScktSssn, "/START "+lastLogSocketNr);
}
} );
srvrCommands.put("!GC", new SrvrCmd() { synchronized public void exec(Session sess, String arg) {
	if( isListener(sess) ) {
		sendMsgToAllListeners("* Garbage collection requested by Listener %"+getSessIdDec(sess), true);
	} else if( logScktSssns.containsKey(sess) ) {
		sendMsgToAllListeners("* Garbage collection requested by LogSocket /"+logScktSssns.get(sess), true);
	} else {
		sendMsgToAllListeners("!ERROR 6 Server: "+shortClObjID(sess)+" neither Listener nor LogSocket", false);
	}
	logScktSssns.forEach( (key,val) -> sendText(key, "/GC "+val) ); // val not used by LogSocket
	System.gc();
}
} );
srvrCommands.put("/GC", new SrvrCmd() { synchronized public void exec(Session sess, String arg) {
	sendMsgToAllListeners("/GC "+arg, false);
}
} );
srvrCommands.put("/PING", new SrvrCmd() { synchronized public void exec(Session sess, String arg) {
	if( arg.isEmpty() ) {
		// Forward command to LogSockets (command not necessarily from listener)
		logScktSssns.forEach( (key,val) -> sendText(key, "/PING "+val) ); // val not used by LogSocket 
	} else {
		// Send reply from LogSockets
		sendMsgToAllListeners("* "+arg, false);
	}
}
} );
srvrCommands.put("!THROW", new SrvrCmd() { synchronized public void exec(Session sess, String arg) throws Exception {
	throw new Exception("TEST command !THROW.");
}
} );
srvrCommands.put("!CLOSE", new SrvrCmd() { synchronized public void exec(Session sess, String arg) {
	CloseReason cr = new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Executing command !CLOSE"); 
	try {
		sess.close(cr);
	} catch(IOException e) {
		sendMsgToAllListeners("!ERROR 12 Server: SrvrCmd !CLOSE: "+e.getMessage(), false);
	}
}
} );
srvrCommands.put("!REM", new SrvrCmd() { synchronized public void exec(Session sess, String arg) {
	// Can/should only come from listener
	sendMsgToAllListeners("* Client %"+getSessIdDec(sess)+" says: "+arg, false);
}
} );
srvrCommands.put("!", new SrvrCmd() { synchronized public void exec(Session sess, String arg) {
	sendMsgToAllListeners("! "+arg, false);
}
} );
srvrCommands.put("/", new SrvrCmd() { synchronized public void exec(Session sess, String arg) {
	sendMsgToAllListeners("/ "+arg, false);
}
} );
srvrCommands.put("/ERROR", new SrvrCmd() { synchronized public void exec(Session sess, String arg) {
	sendMsgToAllListeners("/ERROR "+arg, false);
}
} );

}// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


synchronized private static boolean isListener(Session s) {
	//closed or not //FIXME get rid of this
	return (lstnrSssns.contains(s));
}


synchronized private static boolean sendTxtToListener(Session sessSendTo, String msg) {
	// Called by sendMsgToAllListeners(...) and TODO initial handshake
	// No messing with listeners List here!
	boolean sent = false;
	
	if (sessSendTo.isOpen()) {
		try {
			sent = sendText(sessSendTo, msg);
		} catch (Exception e) {
			sent = false;
			//Problem IllegalStateException: The remote endpoint was in state [TEXT_FULL_WRITING] which is an invalid state for called method
			//https://bz.apache.org/bugzilla/show_bug.cgi?id=56026
			String bla = e.getClass().getName()+" while sending to listener %"+getSessIdDec(sessSendTo)+" "+shortClObjID(sessSendTo);
;
			System.err.println("!!!. "+thisClObjID+": sendTxtToListener(...): "+bla+" --- "+e.getMessage());
			//Another IllegalStateException:
			// FIXME 11811f4b
			//.... LogSocketServer@192c7b06: sendText to session=WsSession@11811f4b txt=!GC LogSocketServer garbage collection requested by listener %50
			//.... LogSocketServer@192c7b06 @OnClose: WsSession@4206879
			//.... logSocket /14 WsSession@4206879 gone
			//.... LogSocketServer@192c7b06: sendText to session=WsSession@11811f4b txt=%DEL_LGGR /14_1 JS/14#label2
			//!!!. LogSocketServer@192c7b06: sendTxtToListener(...): java.lang.IllegalStateException while sending to listener %50 WsSession@11811f4b --- Message will not be sent because the WebSocket session has been closed
			//!... LogSocketServer@192c7b06 @OnClose: PROTOCOL ERROR WsSession@11811f4b neither logSocket nor listener
			//.... LogSocketServer@192c7b06: sendMsgToListeners(...) Listener(s) gone. BUFFERING to srvrBuffer
			//.... LogSocketServer@192c7b06 @OnClose: WsSession@11811f4b
		}
	}
	return sent;
}

synchronized private static void sendMsgToAllListeners(String msg, boolean noSrvrBffrng) {
	if(debug) debugMsg(".... "+thisClObjID+": sendMsgToAllListeners  msg="+msg+" hasListeners="+hasListeners);

	if (!hasListeners) { 
		// hasListeners is set ONLY here and by synchronized onMessage(...)
		// When nobody is listening we only buffer log and error messages, but NOT GUI commands
		if (noSrvrBffrng) return; 
		if(debug) debugMsg(".... "+thisClObjID+": sendMsgToAllListeners(...) BUFFERING to srvrBuffer srvrBuffer.size()="+srvrBuffer.size());
		srvrBuffer.add(msg);
		return;
	}

	if (!srvrBuffer.isEmpty()) { // should not happen: "!HELLO" message received despite synchronization
		System.err.println("!!.. "+thisClObjID+": BUG or Tomcat reloading context: sendMsgToAllListeners(...) BUFFER NOT EMPTIED: "+srvrBuffer.size()+" already in."
				+"\n!!!. Continuing buffering, adding msg="+msg
		);
		srvrBuffer.add(msg);
		return; //FIXME This happened indeed. Perhaps Tomcat reloading context: This almost automatically messes things up. 
	}

	Set<Session> errSessions = new HashSet<Session>();
	lstnrSssns.forEach( ssn -> {if (!sendTxtToListener(ssn, msg) ) errSessions.add(ssn);} );
	errSessions.forEach(s->lstnrSssns.remove(s)); //FIXME 11811f4b this can happen before onClose

	if (lstnrSssns.size()==0) {
		if(debug) debugMsg(".... "+thisClObjID+": sendMsgToListeners(...) Listener(s) gone. BUFFERING to srvrBuffer");
		hasListeners = false;		
		if (!noSrvrBffrng) srvrBuffer.add(msg); //We only buffer log and error messages
	}
}

public static Integer getSessIdDec (Session s) {
	return Integer.parseInt(s.getId(),16);
	// WARNING Session.getId() is implementation specific. Here it is a hexadecimal string.
	// PROBLEM: goes to infinity
}

synchronized private static boolean sendText(Session s, String txt) {
	if(debug) debugMsg(".... "+thisClObjID+": sendText to session="+shortClObjID(s)+" txt="+txt);
	//TODO handle error session
	if (s.isOpen()) {
		try {
			s.getBasicRemote().sendText(txt);
			return true;
		} catch (IOException e) {
			if (s.isOpen()) {
				System.err.println(".... "+thisClObjID+": sendText to session="+shortClObjID(s)+" went wrong. txt="+txt );
				return false;
			}
		}
	}
	
	return false;
}
	

private static String logSocketsToString() {
	return logScktSssns.entrySet().stream().map(e -> 
			"/"+e.getValue()+"="+shortClObjID(e.getKey())
		).collect( Collectors.joining(", ") );
}

private static String timersToString() {
	return timersStartT.entrySet().stream().map( e -> 
				e.getKey()+": "+timersDT.get(e.getKey())
			).collect( Collectors.joining(", ") );
	// TODO add "running" if timersStartT.get(e.getKey()==-1);
}

private static String shortClObjID(Object o) {return Stream.of( dotPttrn.split(o.toString())).reduce((first,last)->last).get();}


// Debug stuff >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

private static void debugMsg(String txt) {
	if (!debug) return;
	System.out.println(txt);
}
static void debugMsg(int on, String txt) {
	if (on==1) System.out.println(txt);
}

public static String getCaller(int skp)	{
	// getCaller(0)=="getCaller"
	// getCaller(1)==called
	return StackWalker.getInstance().
		walk(stream -> stream.skip(skp).findFirst().get()).
		getMethodName();
}

// serious END <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<













	
// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Laboratory
// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

public static class TEST {

	
}

}//END class TEST

// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
/* Error collection
 * 
sendMsgToAllListeners("!ERROR 1 Server: \""+msg+"\" ??", false);	
sendMsgToAllListeners("!ERROR 3: Duplicate: "+lggrShortStr, false);
sendMsgToAllListeners("!ERROR 2 Server: \""+msg+"\" ??", false);	
sendMsgToAllListeners("!ERROR 4 Server: !DEL_LGGR: Not found: "+splitMsg[1], false);	
sendMsgToAllListeners("!ERROR 5 Server: Timer never started", false);
sendMsgToAllListeners("!ERROR 6 Server: "+sessionClObjID+"neither Listener nor LogSocket", false);
sendMsgToAllListeners("!ERROR 7 Server: \""+msg+"\" ??", false);
System.err.println("!... "+thisClObjID+" @OnClose: PROTOCOL ERROR "+sessClObjId+" neither logSocket nor listener");
sendMsgToAllListeners("!ERROR 9 Server: "+sessionClObjID+" neither Listener nor LogSocket", false);
sendMsgToAllListeners("!ERROR 10 Server: "+e.getMessage()+" (Syntax Error) msg=\""+msg+"\"", false);
sendMsgToAllListeners("!ERROR 11 Server: "+e.getMessage()+" (Unknown) msg=\""+msg+"\"", false);
sendMsgToAllListeners("!ERROR 12 Server: "+e.getMessage()+" (Cannot tell more...) msg=\""+msg+"\"", false);
sendMsgToAllListeners("!ERROR 13 Server: Unknown command? "+e.getMessage()+" msg=\""+msg+"\"", false);
sendMsgToAllListeners("!ERROR 15 Server: !MT_REPORT: Syntax Error: "+e.getMessage(), false);
sendMsgToAllListeners("!ERROR 16 Server: !MT_LOG: Report \""+reportName+"\" not found.", false);


  * Debug messages deleted from onMessage(Session session, String msg, boolean last)
debugMsg(1, ".... "+thisClObjID+": @onMessage session="+shortClObjID(session)+" msg="+msg+" last="+last);
debugMsg(1, "A--- "+"+"+ (-lastMsgT+(lastMsgT=Clock.T())) +"-"+msg.substring(1));
debugMsg(1, "B--- "+"*"+ (-lastMsgT+(lastMsgT=Clock.T())) +"-"+msg.substring(1));
debugMsg(1, "C--- "+"+"+ (-lastMsgT+(lastMsgT=Clock.T())) +"-"+msg.substring(1));



  */
