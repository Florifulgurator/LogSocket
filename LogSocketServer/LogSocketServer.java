package florifulgurator.logsocket.server;

import static florifulgurator.logsocket.utils.MGutils.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
//     Synchronized static methods are synchronized on the class object...
//     but not everything declared static remains static! cf. #srvr_TEST1


@ServerEndpoint("/ws")  // javax.websocket.server
@WebListener            // javax.servlet.annotation for ServletContextListener to shut down ExecutorService and save properties
public class LogSocketServer implements ServletContextListener  {

	// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	public void contextInitialized(ServletContextEvent sce) {
		System.out.println(">>>>>>>>>> LogSocketServer: Web application initialization is starting <<<<<<<<<<");
	} 
	public void contextDestroyed(ServletContextEvent sce) { 
		exctrService.shutdownNow(); // Tomcat can't shut down ExecutorService:
		
		if (saveFilter1) { //TODO #7f344280
			props.setProperty( "filter1", filter1.stream().collect(Collectors.joining(" ")) );
			try { props.store(new FileWriter(rootPath+"LogSocketServer.properties"), null); }
			catch (IOException e) {	System.err.println("!!!! LogSocketServer.properties likely not saved. Exception: "+e.getClass().getName()+" "+e.getMessage()); }
		}
		
		System.err.println(">>>>>>>>>> LogSocketServer: ServletContext is about to be shut down <<<<<<<<<<");
	} 
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	// enough of wasteful indentation :-)

public static ExecutorService              exctrService = Executors.newFixedThreadPool(10);

public static record LggrRcrd(String longId, String comment, boolean on, boolean ignored, long T) {} //Records are immutable data classes
public static Map<String,LggrRcrd>        lggrMap = new ConcurrentHashMap<>(100); // lggr.shortId |=> LggrRcrd
public static Comparator<String>          shortIdComprtr = (l1, l2) -> (int)Math.signum(lggrMap.get(l1).T - lggrMap.get(l2).T);

public static Integer                     lastLogSocketNr = 0;
public static MyBiMap <Session, Integer>  lgScktSssn2Nr = new MyBiMap <>(); // TODO?  ?WeakHashMap? in case onClose(...) fails
public static Set<Session>                lstnrSssns = ConcurrentHashMap.newKeySet();
public static Boolean	                  hasListeners = false;

public static Queue<String>               srvrBuffer = new ConcurrentLinkedQueue<>();

private static LinkedHashSet<String>      filter1 = new LinkedHashSet<>(); // insertion order to keep things tidy //DEV #6cb5e491
private static boolean                    saveFilter1 = true;

public static Map<String, Long>           timersStartT = new ConcurrentHashMap<>(20); //Clock.T()
public static Map<String, String>         timersDT = new ConcurrentHashMap<>(20); // DT in ms as String

public static Map<String, RandomVar>      randomVars = new ConcurrentHashMap<>(20);

//https://www.baeldung.com/java-concurrent-map
//Hashtable: 1142.45  SynchronizedHashMap: 1273.89  ConcurrentHashMap: 230.2   (But: For serious performance benchmarking use JMH "All else is a waste of time")


// Commands assigned in static {...} block later >>>>>>>>>>>>>>>>>>>>>>>>>>>
interface LggrCmd {	public void exec(String lggrLongStr, String arg); }
public static final Map<String, LggrCmd>  lggrCommands = new ConcurrentHashMap<>(3);

interface SrvrCmd {	public void exec(Session sess, String arg) throws Exception; }
public static final Map<String, SrvrCmd>  srvrCommands = new ConcurrentHashMap<>(30);
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

private static long                       lastMsgT = -1; // #777237b5
private static long                       firstLgScktT = 0;
private static boolean                    firstLgScktTisNew = true;

private static final Pattern              blankPttrn = Pattern.compile("\\s+");

private static String                     rootPath = null;
private static Properties                 props = new Properties();


//Test stuff >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	// Most "public" due to old test. // TODO private/public
	public static boolean debug = false;
//  #srvr_TEST1 result: Not a singleton! Due to Tomcat "reflection attack"?
	public static String  thisClObjID = "LogSocketServer"; // assigned in constructor => NOT STATIC
//	public static String  sessionClObjID = "1st assignment"; // assigned in @OnOpen => NOT STATIC
//	public static String  weird = "Hello "; // assigned in @OnMessage => static
// TODO: Test if @OnMessage is static method #srvr_TEST2
//	public LogSocketServer() { 
//		LogSocketServer.thisClObjID = shortClObjID(this);
//	}
//  J.Bloch: Effective Java, on singletons: "As of release 1.5, there is a third approach to implementing singletons. Simply make an enum type with one element: [...] This approach is functionally equivalent to the public field approach, except that it is more concise, provides the serialization machinery for free, and provides an ironclad guarantee against multiple instantiation, even in the face of sophisticated serialization or reflection attacks. While this approach has yet to be widely adopted, a single-element enum type is the best way to implement a singleton."
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< Test stuff


@OnOpen
synchronized public void onOpen(Session sess, EndpointConfig epConfig) {
// when the endpoint is deployed, there is precisely one EndpointConfig instance 
// holding the configuration information for all the instances of the endpoint class. #DDS p.99
	if (lastMsgT==-1) lastMsgT = Clock.T(); // #777237b5
}

@OnClose
public void onClose(Session sess, CloseReason reason) throws Exception {
	if ( lgScktSssn2Nr.containsKey(sess) ) {
		Integer logScktNr = lgScktSssn2Nr.remove(sess); // can be null!
		Set<String> delLggrs = new HashSet<String>();
		synchronized(lggrMap) {
			lggrMap.forEach(
				(key,rcrd) -> {
					if ( key.split("_")[0].equals("/"+logScktNr) ) { //key=shortId
						delLggrs.add(key);
						sendMsgToAllListeners("%GC_LGGR "+key+" "+rcrd.longId, true );
					}
				}
			);
			delLggrs.forEach(k->lggrMap.remove(k));
		}
		
		System.out.println(".... "+thisClObjID+" @OnClose: logSocket /"+logScktNr+" "+shortClObjID(sess)+" gone. Close reason: "+ reason.getReasonPhrase());
		
	} else {
		if ( lstnrSssns.remove(sess) ) {
			if ( lstnrSssns.isEmpty() ) hasListeners = false;
			
			System.out.println(".... "+thisClObjID+" @OnClose: listener %"+getSessIdDec(sess)+" "+shortClObjID(sess)+" gone."
					+ " Close reason: "+ reason.getReasonPhrase());
		} else {
			// FIXME #11811f4b
			System.err.println("!... "+thisClObjID+" @OnClose: PROTOCOL ERROR "+shortClObjID(sess)+" neither logSocket nor listener."
					+" Close reason: "+ reason.getReasonPhrase()
			);
		}
	}
}

@OnError
synchronized public void onError(Session sess, Throwable thr) {
	System.err.println("!!!. "+thisClObjID+" @OnError: "+shortClObjID(sess)+" "+thr.getMessage() ); // .getMessage());
}

// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
@OnMessage // >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
synchronized public void onMessage(Session session, String msg) throws Exception {
// synchronized static methods are synchronized on the class object
	char ch1 = msg.charAt(0);
	
	if( ch1=='*' ) { // '*' Plain log message, should have no line breaks >>>>>>
		sendMsgToAllListeners("*"+ (-lastMsgT+(lastMsgT=Clock.T())) +"&"+msg.substring(1), false);
		return;
	} else if( ch1=='+' ) { // '+' A) Plain log message with line breaks. Extra treatment by client
	                        //     B) Error msg        >>>>>>
		sendMsgToAllListeners("+"+ (-lastMsgT+(lastMsgT=Clock.T())) +"&"+msg.substring(1), false);
		return;
 	} else if( ch1=='~' ) { // '~' LogSocket-buffered log message, possibly with line breaks. Extra treatment by client
		sendMsgToAllListeners("+NaN&"+msg.substring(1)+ " [LOG_BUF]", false); // #429f6c0a
		return;
  	} else if( ch1=='$' ) { // Log messages to be amended or generated by server >>>>>
  		// e.g. "$Srvlt/1#JAVALOOP.1&/1_7&1001 MT_LOG JavaLoopMicro"
   		try {
   		   	String[] splitMsg = blankPttrn.split(msg, 3);
   			try {
   				lggrCommands.get(splitMsg[1]).exec(
   						 (-lastMsgT+(lastMsgT=Clock.T())) +"&"+splitMsg[0].substring(1),
   						 splitMsg[2]
   				);
   				// This is just to keep the formalism in line with server commands (below).
   				// There is just one such command yet, and likely there wont be more.
   				return;
   			} catch(Exception e) {
   			   	sendMsgToAllListeners("!ERROR 11 LogSocketServer: Exception: "+e.getClass().getName()+" "+e.getMessage()+" msg=\""+msg+"\"", false);
   			   	return;	
   			}
   		} catch (Exception e) {
   			sendMsgToAllListeners("!ERROR 10 LogSocketServer: Syntax Error. "+e.getMessage()+" msg=\""+msg+"\"", false);
   			return;
   		}
   	}

   	// Non-log commands >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
   	//  Specific reply to session: sendText(session,...)
	
	String[] splitMsg = blankPttrn.split(msg, 2); // Pattern applied at most 1 times, array length <= 2, last entry contains rest.
	SrvrCmd cmd = srvrCommands.get(splitMsg[0]);
	try {
		cmd.exec( session, splitMsg.length==1?"":splitMsg[1] );
	} 
	catch(NullPointerException e) {
		sendMsgToAllListeners("!ERROR 13 LogSocketServer: Unknown command \""+splitMsg[0]+"\" msg=\""+msg+"\" session="+shortClObjID(session), false);
		return;
	}
	catch(Exception e) {
		sendMsgToAllListeners("!ERROR 12 LogSocketServer: Exception while executing command: "+e.getClass().getName()+" "+e.getMessage()+" msg=\""+msg+"\" session="+shortClObjID(session), false);
		return;
	}


}// onMessage(...)  <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


static { // static block >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// static block is indeed static (cf. TEST1), called only once.

// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Commands used by onMessage(...) >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

srvrCommands.put("!HELLO", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	//Listener wants to register.

	sendText(sess, "%SESSID "+getSessIdDec(sess)+" "+shortClObjID(sess));
	sendText(sess, "%FIRST_T "+firstLgScktT);
	sendText(sess, "%TIMERS "+timersToString()); // TODO #1538e9e5 Let server send only new timer results, append named list in client.
	sendText(sess, "%CLOCK "+Clock.nanoUnit +" "+Clock.T_ms());
	
	synchronized(lggrMap) {
		// Send lggrs to client, sorted by their timestamps
		// TODO #495e57b8 get loggers (incl. numMsgs) from LogSockets /PING, check with lggrMap
		lggrMap.keySet().stream().sorted(shortIdComprtr).forEach( shortId -> {
			LggrRcrd rcrd = lggrMap.get(shortId);
			if (!rcrd.ignored) {
				sendText(sess, "%NEW_LGGR " + rcrd.T + " "+(rcrd.on?"1 ":"0 ") + shortId + " " + rcrd.longId + " " + rcrd.comment ); // TODO #495e57b8 numMsgs
			} // else *E*xistence ignored
		});
	}	
	if (!filter1.isEmpty()) sendText(sess, "%FILTER1_ADD "+filter1.stream().collect(Collectors.joining(" ")));
	
	// Wait until listener has digested and replies with "!READY":
	sendText(sess, "@READY?");
}
} );
// ---
srvrCommands.put("!READY", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	lstnrSssns.add(sess);
	hasListeners = true;
	
	if (!srvrBuffer.isEmpty()) {
		sendText(sess, "* Server-buffered messages while nobody was listening:");
		while(!srvrBuffer.isEmpty()) { sendText(sess, srvrBuffer.remove());	}
		sendText(sess, "* Buffer emptied. Live log messages:");     		 
	}
}
} );
// ---
srvrCommands.put("!GREETS", new SrvrCmd() { synchronized public void exec(Session LgScktSssn, String arg)
{
	//Initialize logSocket
	//TODO re-use old numbers according to realm
	//TODO check if LgScktSssn is indeed a LogSocket session
	if( lgScktSssn2Nr.isEmpty() ) {
		lastLogSocketNr = 0;
		firstLgScktT = Clock.T(); firstLgScktTisNew = true;
	
		if ( !lggrMap.isEmpty() ) {
			System.err.println("!!.. "+thisClObjID+": !GREETS: lgScktSssn2Nr.isEmpty() but lggrMap not empty.");
			lggrMap.clear();
		}
	} else {
		if( lgScktSssn2Nr.containsKey(LgScktSssn) ) System.err.println("!!!. "+thisClObjID+": **BUG** !GREETS: "+shortClObjID(LgScktSssn)+" already known.");
	}
				
	lgScktSssn2Nr.put(LgScktSssn, ++lastLogSocketNr);
	//TODO send filter1
	sendText(LgScktSssn, "/START "+lastLogSocketNr);
}
} );
// ---
srvrCommands.put("!GC_LGGR", new SrvrCmd() { public void exec(Session sess, String argStr)
{
	//From Lggr.finalize()
	sendMsgToAllListeners("%GC_LGGR " + argStr, true);// msg gets lost when nobody is listening
	String[] args = blankPttrn.split(argStr, 2);
	boolean existed;
	synchronized(lggrMap) {
		existed = lggrMap.remove(args[0]) != null;
	}
	if( !existed ) {
		System.err.println("!!.. "+thisClObjID+": @onMessage !GC_LGGR ... NOT FOUND: "+args[0]);
		sendMsgToAllListeners("!ERROR 4 LogSocketServer: !GC_LGGR: Not found: \""+args[0]+"\"", false);	
	}
}
} );
// ---
srvrCommands.put("!NEW_LGGR", new SrvrCmd() { public void exec(Session sess, String argStr)
{////DEV #23e526a3 Lggr.on
	if(firstLgScktTisNew) {
		sendMsgToAllListeners("%FIRST_T "+firstLgScktT, true);
		firstLgScktTisNew = false;
	}
	long T = Clock.T();
	sendMsgToAllListeners("%NEW_LGGR " +T+ " " + argStr, true); // true = not into srvrBuffer. Kept extra (%NEW_LGGR) or gets lost when nobody is listening
	String[] args = blankPttrn.split(argStr, 4);
	boolean existed;
	synchronized(lggrMap) {
		existed = lggrMap.put( args[1], new LggrRcrd( args[2], args[3], "1".equals(args[0])?true:false, false, T ) ) != null;
	}
	
	if( existed ) {
		System.err.println("!!.. "+thisClObjID+": @onMessage DUPLICATE: "+args[0]);
		sendMsgToAllListeners("!ERROR 3 LogSocketServer: Duplicate: "+args[0], false);
	}
}
} );
// ---
srvrCommands.put("!T_STOP", new SrvrCmd() { synchronized public void exec(Session sess, String name)
{
	String s = timersDT.get(name);
	if( s==null || timersStartT.get(name)==-1 ) {
		sendMsgToAllListeners("!ERROR 5 LogSocketServer: Timer never started", false);
	} else {
		String T = (double)(Clock.T() - timersStartT.get(name))/(double)Clock.nanoUnit+"ms";
		timersDT.put(name, (s.isEmpty()) ? T : s+", "+T );
		timersStartT.put(name, (long)-1);
		sendMsgToAllListeners("* Timer "+name+" STOP "+T+" <------", false);
		// TODO #1538e9e5 Let server send only new timer results, append named list in client.
		sendMsgToAllListeners("%TIMERS "+timersToString(), true);
	}
}
} );
// ---
srvrCommands.put("!T_START", new SrvrCmd() { synchronized public void exec(Session sess, String name)
{
	timersStartT.put(name, Clock.T());
	if(!timersDT.containsKey(name)) timersDT.put(name,"");
	sendMsgToAllListeners("* Timer "+name+" START ------>", false);
}
} );
// ---
srvrCommands.put("!GC", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	if( lstnrSssns.contains(sess) ) {
		// #3ffba3b The only listener we have right now is the LogSocket Client
		sendMsgToAllListeners("* Garbage collection requested by Client %"+getSessIdDec(sess), true);
	} else if( lgScktSssn2Nr.containsKey(sess) ) {
		sendMsgToAllListeners("* Garbage collection requested by LogSocket /"+lgScktSssn2Nr.get(sess), true);
	} else {
		sendMsgToAllListeners("!ERROR 6 LogSocketServer: "+shortClObjID(sess)+" neither Listener nor LogSocket", false);
	}
	lgScktSssn2Nr.k_v.forEach( (key,val) -> sendText(key, "/GC "+val) ); // val not used by LogSocket
	System.gc();
}
} );
// ---
srvrCommands.put("/GC", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	sendMsgToAllListeners("/GC "+arg, false);
}
} );
// ---
srvrCommands.put("/PING", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	if( arg.isEmpty() ) {
		// Forward command to LogSockets (command not necessarily from listener)
		lgScktSssn2Nr.k_v.forEach( (key,val) -> sendText(key, "/PING "+val) ); // val not used by LogSocket
		if (lgScktSssn2Nr.k_v.isEmpty()) sendText(sess, "! No LogSockets connected.");
	} else {
		// Send reply from LogSockets
		sendMsgToAllListeners("* "+arg, false);
	}
}
} );
// ---
srvrCommands.put("/FILTER", new SrvrCmd() { public void exec(Session sess, String arg) //JS: doSend(`/FILTER ${realm} /${LgScktNr} ${label} ;*`);
{ 
	String[] args = null;
	Integer LgScktNr = null;
	String realmLabel = null;
	
	try {
		args = blankPttrn.split(arg, 4);
		realmLabel = args[0]+args[2]; 
		LgScktNr = Integer.parseInt(args[1].substring(1));
	}
	catch (NumberFormatException e) {}
	catch (Exception e) {
		sendMsgToAllListeners("!ERROR 17 LogSocketServer: /FILTER Exception: "+e.getClass().getName()+" "+e.getMessage()+" arg="+arg, false);
		return;
	}

	synchronized(filter1) {
		if (LgScktNr==null) {
			// Global filter: All LogSockets, stored at Server, Clients, and LogSockets
			filter1.add(realmLabel);
			String effFinal = "/FILTER1_ADD "+realmLabel;
			lgScktSssn2Nr.k_v.forEach( (key,val) -> sendText(key, effFinal) );
			sendMsgToAllListeners("%FILTER1_ADD "+realmLabel, true);
			// Receivers might have to check for duplicates. (E.g. Client #7ad7d40e )
	
		} else {
			// Run time filter, TODO
			Session lgScktSssn = lgScktSssn2Nr.v_k.get(LgScktNr);
			sendText(lgScktSssn, "/FILTER2_ADD "+arg);
			sendMsgToAllListeners("%FILTER2_ADD "+arg, false); // Job of LogSocket
			sendMsgToAllListeners("! Major TODO :-)", false);
		}
	}
}
} );
// ---
srvrCommands.put("!FILTER1_REMOVE", new SrvrCmd() { public void exec(Session sess, String arg)
{ 
	synchronized(filter1) {
		filter1.remove(arg);
		lgScktSssn2Nr.k_v.forEach( (key,val) -> sendText(key,"/FILTER1_REMOVE "+arg) );
		sendMsgToAllListeners("%FILTER1_REMOVE "+arg, false);
	}
}
} );
// ---
srvrCommands.put("!SILENCED", new SrvrCmd() { public void exec(Session sess, String arg)
{
	synchronized(lggrMap) {
		for (String shortId : blankPttrn.split(arg) ) {
			LggrRcrd rcrd = lggrMap.get(shortId);
			lggrMap.put(shortId, new LggrRcrd(rcrd.longId, rcrd.comment, false, true, rcrd.T));
		}
	}
	sendMsgToAllListeners("%SILENCED "+arg, false);
	//TODO #7594d994 client consistency test
}
} );
// ---
srvrCommands.put("!CLOSE", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	CloseReason cr = new CloseReason(CloseReason.CloseCodes.GOING_AWAY, "Executing command !CLOSE"); 
	try {
		sess.close(cr);
	} catch(IOException e) {
		sendMsgToAllListeners("!ERROR 12 LogSocketServer: SrvrCmd !CLOSE: "+e.getMessage(), false);
	}
}
} );
// --- Client side clock sync per clock ping pong
srvrCommands.put("!TING", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	if ( Clock.tong==null ) {
		Clock.tong = Clock.T_ms();
		sendText(sess,"@TONG");
	} else {
		Clock.tong1 = Clock.T_ms();
		// There could be a pause here
		
		// Now server-client tingtong:
		Clock.ting = Clock.T_ms();
		sendText(sess,"@TING");
	}
}
} );
srvrCommands.put("!TONG", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	if ( Clock.ting1==null ) {
		Clock.ting1 = Clock.T_ms();
		sendText(sess,"@TING");
	} else {
		sendText(sess,"@RSLT "+Clock.tong+" "+Clock.tong1+" "+Clock.ting+" "+Clock.ting1);
		Clock.ting1 = null; Clock.ting1 = null; Clock.tong = null; Clock.tong1 = null; 
	} 
}
} );
// --- Server side clock sync, mirroring client side allgorithm
srvrCommands.put("!SYNC", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	Clock.SyncDaemon.T0correction(sess, lastMsgT); // Launches thread and returns
	// Thread sends reply "%T0CORR ... ... ..."
}
} );
// ---
srvrCommands.put("!SING", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	if (Clock.tong==null) Clock.tong = Clock.T_ms();
	                else  Clock.tong1 = Clock.T_ms();
	 sendText(sess,"@SONG");
}
} );
srvrCommands.put("!SONG", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	Clock.ting1 = Clock.T_ms();
	sendText(sess,"@SING");
}
} );
srvrCommands.put("!RSLT", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	Clock.SyncDaemon.receiveTTResult(arg, lastMsgT);
}
} );

// ------------------------------------------------------------
lggrCommands.put("MT_LOG", new LggrCmd() { public void exec(String msgPrefix, String reportName) {
 	RandomVar rnd = randomVars.get(reportName);
 	if(rnd==null) {
 		sendMsgToAllListeners("!ERROR 16 LogSocketServer: !MT_LOG: Report \""+reportName+"\" not found.", false);
 		return;
 	}
 	// synchronized rnd likely is still busy receiving data from thread launched by !MT_REPORT,
 	// so we postpone things into a thread:
	exctrService.execute( () -> {
 		synchronized(randomVars) {
 			// FIXME: Avoid extremely unlikely race condition:
 			// The following sendMsgToAllListeners(...) have to come in a row (no other such construct interfering)
 			sendMsgToAllListeners("+"+msgPrefix+" MicroTimer "+reportName+" "+rnd.getASCIIart(1,"μs") , true);
  			sendMsgToAllListeners("%MT_SHOW "+reportName+" "+rnd.getASCIIartHeader(), true);
		}
	} );
}
} );
// ------------------------------------------------------------
srvrCommands.put("!MT_REPORT", new SrvrCmd() { public void exec(Session sess, String arg)
{
	String[] splitArg = blankPttrn.split(arg, 3);
	Double unit; // Java Lggr sends stuff in 0.1 μs => unit=0.1	// TODO JavaScript lggr (5μs)
	try {
		unit = Double.parseDouble(splitArg[1]);
		if(splitArg.length!=3) throw new Exception("arg length!=3");
	} catch(Exception e) {
		sendMsgToAllListeners("!ERROR 15 LogSocketServer: !MT_REPORT: Syntax Error: "+e.getMessage(), false);
		return;	
	}
	String reportName = splitArg[0];
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
		} );
		// RandomVar::setValsIntString is synchronized
	}
}
} );
// ---
srvrCommands.put("!REM", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	sendMsgToAllListeners("* Client %"+getSessIdDec(sess)+" says: "+arg, false); // Can/should only come from listener
}
} );
// ---
srvrCommands.put("!", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	sendMsgToAllListeners("! "+arg, false);
}
} );
// ---
srvrCommands.put("/", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	sendMsgToAllListeners("/ "+arg, false);
}
} );
// ---
srvrCommands.put("/ERROR", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	sendMsgToAllListeners("/ERROR "+arg, false);
}
} );
// --- TEST commands
srvrCommands.put("!THROW", new SrvrCmd() { synchronized public void exec(Session sess, String arg) throws Exception
{
	throw new Exception("Exception message of TEST command !THROW.");
}
} );


System.out.println(".... "+srvrCommands.keySet().size()+" srvrCommands");

// Commands used by onMessage(...) <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
System.out.println(".... LogSocketServer path="+rootPath);

try {
	props.load(new FileReader(rootPath+"LogSocketServer.properties"));
	System.out.println(".... Loaded file "+rootPath+"LogSocketServer.properties");
	filter1.addAll( Arrays.asList(blankPttrn.split(props.getProperty("filter1"))) );

} catch (FileNotFoundException e) {
	try {
		props.setProperty("filter1", "");
		props.store(new FileWriter(rootPath+"LogSocketServer.properties"), ">>>>>>>>>> Creating file LogSocketServer.properties <<<<<<<<<<");

	} catch (IOException e1) {
		System.err.println("!!!! Cannot create file "+rootPath+"LogSocketServer.properties");
		e1.printStackTrace();
	}

} catch (IOException e) {
	e.printStackTrace();
}



}// static block <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<




synchronized private static boolean sendTxtToListener(Session sessSendTo, String msg) {
// #3ffba3b DOCU *** The only listeners we have right now are the LogSocket Clients ***
// Called by sendMsgToAllListeners(...) and TODO initial handshake
// No messing with listeners List here!
	boolean sent = false;
	
	if (sessSendTo.isOpen()) {
		try {
			sent = sendText(sessSendTo, msg);
		} catch (Exception e) {
			sent = false;
			// FIXME #11811f4b occasional IllegalStateException
			// IllegalStateException: The remote endpoint was in state [TEXT_FULL_WRITING] which is an invalid state for called method
			//https://bz.apache.org/bugzilla/show_bug.cgi?id=56026
			String bla = e.getClass().getName()+" while sending to listener %"+getSessIdDec(sessSendTo)+" "+shortClObjID(sessSendTo);
			System.err.println("!!!. "+thisClObjID+": sendTxtToListener(...): "+bla+" --- "+e.getMessage());
			//Another IllegalStateException:
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
// #3ffba3b DOCU *** The only listeners we have right now are the LogSocket Clients ***
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
		return;
	}

	Set<Session> errSessions = new HashSet<Session>(); //TODO not needed
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

synchronized static boolean sendText(Session s, String txt) {
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
	

@SuppressWarnings("unused")
private static String logSocketsToString() {
	return lgScktSssn2Nr.entrySet().stream().map(e -> 
			 "/"+e.getValue()+"="+shortClObjID(e.getKey())
		   ).collect( Collectors.joining(", ") );
}

private static String timersToString() {
	return timersStartT.entrySet().stream().map( e -> 
	         e.getKey()+": "+timersDT.get(e.getKey())
		   ).collect( Collectors.joining(", ") );
	// TODO add "running" if timersStartT.get(e.getKey()==-1);
}




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



public static class  MyBiMap<K, V> { // "nested class"
	public  Map<K,V> k_v = new ConcurrentHashMap<K,V>(20);
	private Map<V,K> v_k = new ConcurrentHashMap<V,K>(20);
	
	public synchronized V   put(K key, V value)     { v_k.put(value, key); return k_v.put(key, value); }
	public V                get(K key)              { return k_v.get(key); }
	public boolean          containsKey(Object key) { return k_v.containsKey(key); }
	public synchronized V   remove(Object key)      { V v=k_v.remove(key); v_k.remove(v); return v; }
	public boolean          isEmpty()               { return k_v.isEmpty();	}
	public Set<Entry<K, V>> entrySet()              { return k_v.entrySet(); }
}









	
// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Laboratory
// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

public static class TEST {

	
}

}//END class TEST





// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
/* Error collection
 * 
sendMsgToAllListeners("!ERROR 1 LogSocketServer: \""+msg+"\" ??", false);	
sendMsgToAllListeners("!ERROR 3: Duplicate: "+lggrShortStr, false);
sendMsgToAllListeners("!ERROR 2 LogSocketServer: \""+msg+"\" ??", false);	
sendMsgToAllListeners("!ERROR 4 LogSocketServer: !DEL_LGGR: Not found: "+splitMsg[1], false);	
sendMsgToAllListeners("!ERROR 5 LogSocketServer: Timer never started", false);
sendMsgToAllListeners("!ERROR 6 LogSocketServer: "+sessionClObjID+"neither Listener nor LogSocket", false);
sendMsgToAllListeners("!ERROR 7 LogSocketServer: \""+msg+"\" ??", false);
System.err.println("!... "+thisClObjID+" @OnClose: PROTOCOL ERROR "+sessClObjId+" neither logSocket nor listener");
sendMsgToAllListeners("!ERROR 9 LogSocketServer: "+sessionClObjID+" neither Listener nor LogSocket", false);
sendMsgToAllListeners("!ERROR 10 LogSocketServer: "+e.getMessage()+" (Syntax Error) msg=\""+msg+"\"", false);
sendMsgToAllListeners("!ERROR 11 LogSocketServer: "+e.getMessage()+" (Unknown) msg=\""+msg+"\"", false);
sendMsgToAllListeners("!ERROR 12 LogSocketServer: "+e.getMessage()+" (Cannot tell more...) msg=\""+msg+"\"", false);
sendMsgToAllListeners("!ERROR 13 LogSocketServer: Unknown command? "+e.getMessage()+" msg=\""+msg+"\"", false);
sendMsgToAllListeners("!ERROR 15 LogSocketServer: !MT_REPORT: Syntax Error: "+e.getMessage(), false);
sendMsgToAllListeners("!ERROR 16 LogSocketServer: !MT_LOG: Report \""+reportName+"\" not found.", false);
sendMsgToAllListeners("!ERROR 17 LogSocketServer: /STOP_L: "+e.getMessage(), false);
sendText(sess, "!ERROR 18 LogSocketServer.Clock: T0correction("+LogSocketServer.shortClObjID(s)+") not even started");
"!ERROR 19 );
LogSocketServer.sendText(sess, "!ERROR 20 LogSocketServer.Clock: tingtongReceiver exception "+e.getClass().getName()+" message: "+ e.getMessage());




  */
