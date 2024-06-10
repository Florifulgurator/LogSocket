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
import java.util.concurrent.LinkedBlockingQueue;
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
		
		if (saveFilter1) { //TODO #7f344280 //DEV #23e526a3 #6cb5e491
			props.setProperty( "filter1", filter1.stream().collect(Collectors.joining(" ")) );
			try { props.store(new FileWriter(rootPath+"LogSocketServer.properties"), null); }
			catch (IOException e) {	System.err.println("!!!! LogSocketServer.properties likely not saved. Exception: "+e.getClass().getName()+" "+e.getMessage()); }
		}
		
		System.err.println(">>>>>>>>>> LogSocketServer: ServletContext is about to be shut down <<<<<<<<<<");
	} 
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	// enough of wasteful indentation :-)

public static ExecutorService              exctrService = Executors.newFixedThreadPool(10);

public static record LggrRcrd(String longId, String comment, boolean[] on, long T, Session sess) {}
public static Map<String,LggrRcrd>        lggrMap = new ConcurrentHashMap<>(100); // Lggr.shortId |=> LggrRcrd
public static Comparator<String>          shortIdComprtr = (l1, l2) -> (int)Math.signum(lggrMap.get(l1).T - lggrMap.get(l2).T);

public static Integer                     lastLogSocketNr = 0;
public static MyBiMap <Session, Integer>  lgScktSssn2Nr = new MyBiMap <>(); // TODO?  ?WeakHashMap? in case onClose(...) fails
public static Set<Session>                lstnrSssns = ConcurrentHashMap.newKeySet(); // #3ffba3b The only listeners we have right now are LogSocketClients
public static Boolean	                  hasListeners = false;

public static Queue<String>               srvrBuffer = new ConcurrentLinkedQueue<>();

private static LinkedHashSet<String>      filter1 = new LinkedHashSet<>(); // Insertion order. Working filter in LogSocket might be ordered differently  //DEV #6cb5e491
private static boolean                    saveFilter1 = false;
protected static final LinkedBlockingQueue<String>
                                          fltrHlprQ = new LinkedBlockingQueue<>();

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

// TODO #7e23cb75 frwrdCommands seperate from srvrCommands
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
		sendMsgToAllListeners("%! @OnClose: logSocket /"+logScktNr+" "+shortClObjID(sess)+" gone. Close reason: "+ reason.getReasonPhrase(), false );
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
		
	} else {
		if ( lstnrSssns.remove(sess) ) {
			sendMsgToAllListeners("%! @OnClose: listener "+getSessIdDec(sess)+" "+shortClObjID(sess)+" gone.", true );
			if ( lstnrSssns.isEmpty() ) hasListeners = false;
			
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
	if(msg.isEmpty()) { sendMsgToAllListeners("%!ERROR 21 LogSocketServer: Empty msg from "+shortClObjID(session), false); return; }

	switch( msg.charAt(0) ) {
	
	case '*':
		// '*' Plain log message, should have no line breaks.
		sendMsgToAllListeners("*"+ (-lastMsgT+(lastMsgT=Clock.T())) +"&"+msg.substring(1), false);
		return;
		
	case '+':
		// '+' Extra treatment by client:
		//     A) Plain log message with line breaks. Text after first \n goes into extraText window.
		//     B) Flag "E": Like A), colored red by client (error message)
		//     C) Flag "P": Log message with buttons: Text after first \n is click callback.
		sendMsgToAllListeners("+"+ (-lastMsgT+(lastMsgT=Clock.T())) +"&"+msg.substring(1), false);
		return;
		
	case '-':
 		// '-' LogSocket-buffered log message, possibly with line breaks.
 		//     Extra treatment by client, see '+'
 		// FIXME TODO #536c8c47 flag and firstChar get lost when buffered
		sendMsgToAllListeners("+NaN&"+msg.substring(1)+ " [LOG_BUF]", false); // #429f6c0a
		return;
		
	case '$':
		// '$' Log messages to be amended or generated by server
  		//     e.g. "$Srvlt/1#JAVALOOP.1&/1_7&1001 MT_LOG JavaLoopMicro"
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
   			   	sendMsgToAllListeners("%!ERROR 11 LogSocketServer: Exception: "+e.getClass().getName()+" "+e.getMessage()+" msg=\""+msg+"\"", false);
   			   	return;	
   			}
   		} catch (Exception e) {
   			sendMsgToAllListeners("%!ERROR 10 LogSocketServer: Syntax Error. "+e.getMessage()+" msg=\""+msg+"\"", false);
   			return;
   		}

	case '.':
		// Message from new Lggr to be filtered by server with prepared filter rule(s)
		//DEV #34b72a7
		onMessage(session, msg.substring(1)+" //DEV #34b72a7");
		return;
   	}

   	// Non-log commands >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	String[] splitMsg = blankPttrn.split(msg, 2); 
	SrvrCmd cmd = srvrCommands.get(splitMsg[0]);
	if (cmd!=null) {
		try {
			cmd.exec( session, splitMsg.length==1?"":splitMsg[1] );
			return;
		} catch(Exception e) {
			sendMsgToAllListeners("%!ERROR 12 LogSocketServer: Exception while executing command: "+e.getClass().getName()+" "+e.getMessage()+" msg=\""+msg+"\" session="+shortClObjID(session), false);
			return;
		}
	} else {
		switch( msg.charAt(0) ) {
		case '%':
			sendMsgToAllListeners(msg, false);
			return;
		case '/':
			if( sendMsgToAllLogSockets(msg) == 0 ) sendText(session, "%! No LogSockets connected.");
			return;
		}
	}
	
	sendMsgToAllListeners("%!ERROR 13 LogSocketServer: Unknown command: msg=\""+msg+"\" session="+shortClObjID(session), false);

}// onMessage(...)  <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


static { // static block >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// static block is indeed static (cf. TEST1), called only once.

// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Commands used by onMessage(...) >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

srvrCommands.put("!HELLO", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	//Listener wants to register.

	sendText(sess, "%SESSID %"+getSessIdDec(sess)+" "+shortClObjID(sess));
	sendText(sess, "%FIRST_T "+firstLgScktT);
	sendText(sess, "%TIMERS "+timersToString()); // TODO #1538e9e5 Let server send only new timer results, append named list in client.
	sendText(sess, "%CLOCK "+Clock.nanoUnit +" "+Clock.T_ms());
	
	synchronized(lggrMap) {
		// Send lggrs to client, sorted by their timestamps
		// TODO #495e57b8 get loggers (incl. numMsgs) from LogSockets /PING, check with lggrMap
		lggrMap.keySet().stream().sorted(shortIdComprtr).forEach( shortId -> {
			LggrRcrd rcrd = lggrMap.get(shortId);
			sendText(sess, "%NEW_LGGR " + rcrd.T + " "+(rcrd.on[0]?"1 ":"0 ") + shortId + " " + rcrd.longId + " " + rcrd.comment ); // TODO #495e57b8 numMsgs
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
		sendText(sess, "%! Server-buffered messages while nobody was listening:");
		while(!srvrBuffer.isEmpty()) { sendText(sess, srvrBuffer.remove());	}
		sendText(sess, "%! Buffer emptied. Live log messages:");     		 
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
srvrCommands.put("!NEW_LGGR", new SrvrCmd() { public void exec(Session sess, String argStr)
{
//		websocket.sendText("!NEW_LGGR "
// // argStr:
//				+ Long.toHexString(lgr.labelsCode) + " "
//				+ (lgr.on?"1 ":"0 ")
//				+ lgr.shortId + " "
//				+ lgr.longId
//				+ (lgr.comment!=null ? " "+lgr.comment : "")); // see also GC_LGGR

	if(firstLgScktTisNew) {
		sendMsgToAllListeners("%FIRST_T "+firstLgScktT, true);
		firstLgScktTisNew = false;
	}

	long T = Clock.T();
	// Add timestamp, remove labelsCode:
	sendMsgToAllListeners("%NEW_LGGR " +T+ " " + blankPttrn.split(argStr, 2)[1], true); // true = not into srvrBuffer. Kept extra (%NEW_LGGR) or gets lost when nobody is listening

	String[] args = blankPttrn.split(argStr, 5);
	
	if (args[0].equals("0")) {
		fltrHlprQ.add(argStr); //DEV #34b72a7
	}
	
	boolean existed;
	synchronized(lggrMap) {
		//public static record LggrRcrd(String longId, String comment, boolean[] on, long T, Session sess) {}
		existed = lggrMap.put( args[2], new LggrRcrd( args[3], args[4], new boolean[]{"1".equals(args[1])}, T, sess ) ) != null;
	}
	if( existed ) {
		System.err.println("!!.. "+thisClObjID+": @onMessage DUPLICATE: "+args[0]);
		sendMsgToAllListeners("%!ERROR 3 LogSocketServer: Duplicate: "+args[0], false);
	}
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
		sendMsgToAllListeners("%!ERROR 4 LogSocketServer: !GC_LGGR: Not found: \""+args[0]+"\"", false);	
	}
}
} );
// ---
srvrCommands.put("!T_STOP", new SrvrCmd() { synchronized public void exec(Session sess, String name)
{
	String s = timersDT.get(name);
	if( s==null || timersStartT.get(name)==-1 ) {
		sendMsgToAllListeners("%!ERROR 5 LogSocketServer: Timer never started", false);
	} else {
		String T = (double)(Clock.T() - timersStartT.get(name))/(double)Clock.nanoUnit+"ms";
		timersDT.put(name, (s.isEmpty()) ? T : s+", "+T );
		timersStartT.put(name, (long)-1);
		sendMsgToAllListeners("%! Timer "+name+" STOP "+T+" <------", false);
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
	sendMsgToAllListeners("%! Timer "+name+" START ------>", false);
}
} );
// ---
srvrCommands.put("!GC", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	if( lstnrSssns.contains(sess) ) {
		sendMsgToAllListeners("%! Garbage collection requested by Client %"+getSessIdDec(sess), true);
	} else if( lgScktSssn2Nr.containsKey(sess) ) {
		sendMsgToAllListeners("%/ Garbage collection requested by LogSocket /"+lgScktSssn2Nr.get(sess), true);
	} else {
		sendMsgToAllListeners("%!ERROR 6 LogSocketServer: "+shortClObjID(sess)+" neither Listener nor LogSocket", false);
	}
	lgScktSssn2Nr.k_v.forEach( (key,val) -> sendText(key, "/GC "+val) ); // val not used by LogSocket
}
} );
// ---
srvrCommands.put("/CFT", new SrvrCmd() { synchronized public void exec(Session sess, String arg)
{
	// Forward command only to concerned LogSocket
	Session lgScktSssn = lggrMap.get(blankPttrn.split(arg, 2)[0]).sess;
	if (lgScktSssn!=null) sendText(lgScktSssn, "/CFT "+arg);
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
			if (rcrd!=null) { rcrd.on[0] = false;
			} else {
				sendMsgToAllListeners("%!ERROR 23 LogSocketServer: Lost track of Lggr "+shortId, false);
			}
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
		sendMsgToAllListeners("%!ERROR 19 LogSocketServer: SrvrCmd !CLOSE: "+e.getMessage(), false);
	}
}
} );
// --- 
// Client side clock sync per timestamp ping pong >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
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
// --- Server side clock sync, mirroring client side algorithm
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
// Client side clock sync per clock ping pong <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

// ------------------------------------------------------------
lggrCommands.put("MT_LOG", new LggrCmd() { public void exec(String msgPrefix, String reportName) {
 	RandomVar rnd = randomVars.get(reportName);
 	if(rnd==null) {
 		sendMsgToAllListeners("%!ERROR 16 LogSocketServer: !MT_LOG: Report \""+reportName+"\" not found.", false);
 		return;
 	}
 	// synchronized rnd likely is still busy receiving data from thread launched by !MT_REPORT,
 	// so we postpone things into a thread:
	exctrService.execute( () -> {
 		synchronized(randomVars) {
 			// TODO: Avoid extremely unlikely race condition:
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
		sendMsgToAllListeners("%!ERROR 15 LogSocketServer: !MT_REPORT: Syntax Error: "+e.getMessage(), false);
		return;	
	}
	String reportName = splitArg[0];
	RandomVar rnd;

	synchronized(randomVars) {
		if( randomVars.containsKey(reportName) ) {
			rnd = randomVars.get(reportName);
		} else {
			rnd = new RandomVar(reportName);
			randomVars.put(reportName, rnd);
		}
		exctrService.execute( () -> {
			rnd.setValsIntString(splitArg[2], unit);
		} );
		// RandomVar::setValsIntString is synchronized
	}
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
	if (props.getProperty("filter1") == null) {
		props.setProperty("filter1", "");
	} else {
		if ( props.getProperty("filter1").trim() != "" ) filter1.addAll( Arrays.asList(blankPttrn.split(props.getProperty("filter1").trim())) );
	}

} catch (FileNotFoundException e) {
	try {
		props.setProperty("filter1", "");
		props.store(new FileWriter(rootPath+"LogSocketServer.properties"), ">>>>>>>>>> Creating file LogSocketServer.properties <<<<<<<<<<");

	} catch (IOException e1) {
		System.err.println("!!!! Cannot create file "+rootPath+"LogSocketServer.properties");
	}

} catch (Exception e) {
	e.printStackTrace();
}


FilterHelper.start();

}// static block <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<



synchronized static boolean sendText(Session s, String txt) {
	if (s.isOpen()) {
		try {
			s.getBasicRemote().sendText(txt);
			return true;
		} catch (Exception e) {
			String bla = e.getClass().getName()+" while sending to listener %"+getSessIdDec(s)+" "+shortClObjID(s)+" Session.isOpen()="+s.isOpen();
			System.err.println("!!!. "+thisClObjID+": sendText(...): "+bla+" --- "+e.getMessage());
		}
	}
	return false;
}
//---
synchronized private static void sendMsgToAllListeners(String msg, boolean noSrvrBffrng) {
	if (!hasListeners) { 
		// hasListeners is set ONLY here and by synchronized onMessage(...)
		// When nobody is listening we only buffer log and error messages, but NOT GUI commands
		if (noSrvrBffrng) return; 
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
	lstnrSssns.forEach( s -> { if (!sendText(s, msg) ) errSessions.add(s); } );
	errSessions.forEach(s->lstnrSssns.remove(s)); //FIXME 11811f4b this can happen before onClose

	if (lstnrSssns.size()==0) {
		hasListeners = false;		
		if (!noSrvrBffrng) srvrBuffer.add(msg); //We only buffer log and error messages
	}
}
//---
private static int sendMsgToAllLogSockets(String msg) {
	int[] count = {0};
	lgScktSssn2Nr.k_v.forEach( (key,val) -> { sendText(key, msg); count[0]++;} );
	return count[0];
}


public static Integer getSessIdDec (Session s) {
	return Integer.parseInt(s.getId(),16);
	// WARNING Session.getId() is implementation specific. Here it is a hexadecimal string.
	// PROBLEM: goes to infinity
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


@SuppressWarnings("unused")
private static void debugMsg(String txt) {
	if (!debug) return;
	System.out.println(txt);
}
static void debugMsg(int on, String txt) {
	if (on==1) System.out.println(txt);
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
sendMsgToAllListeners("%!ERROR 1 LogSocketServer: \""+msg+"\" ??", false);	
sendMsgToAllListeners("%!ERROR 3: Duplicate: "+lggrShortStr, false);
sendMsgToAllListeners("%!ERROR 2 LogSocketServer: \""+msg+"\" ??", false);	
sendMsgToAllListeners("%!ERROR 4 LogSocketServer: !DEL_LGGR: Not found: "+splitMsg[1], false);	
sendMsgToAllListeners("%!ERROR 5 LogSocketServer: Timer never started", false);
sendMsgToAllListeners("%!ERROR 6 LogSocketServer: "+sessionClObjID+"neither Listener nor LogSocket", false);
sendMsgToAllListeners("%!ERROR 7 LogSocketServer: \""+msg+"\" ??", false);
System.err.println("!... "+thisClObjID+" @OnClose: PROTOCOL ERROR "+sessClObjId+" neither logSocket nor listener");
sendMsgToAllListeners("%!ERROR 9 LogSocketServer: "+sessionClObjID+" neither Listener nor LogSocket", false);
sendMsgToAllListeners("%!ERROR 10 LogSocketServer: "+e.getMessage()+" (Syntax Error) msg=\""+msg+"\"", false);
sendMsgToAllListeners("%!ERROR 11 LogSocketServer: "+e.getMessage()+" (Unknown) msg=\""+msg+"\"", false);
sendMsgToAllListeners("%!ERROR 12 LogSocketServer: Exception while executing command: "+e.getClass().getName()+" "+e.getMessage()+" msg=\""+msg+"\" session="+shortClObjID(session), false);
sendMsgToAllListeners("%!ERROR 13 LogSocketServer: Unknown command? "+e.getMessage()+" msg=\""+msg+"\"", false);
sendMsgToAllListeners("%!ERROR 15 LogSocketServer: !MT_REPORT: Syntax Error: "+e.getMessage(), false);
sendMsgToAllListeners("%!ERROR 16 LogSocketServer: !MT_LOG: Report \""+reportName+"\" not found.", false);
sendMsgToAllListeners("%!ERROR 17 LogSocketServer: /STOP_L: "+e.getMessage(), false);
sendText(sess, "%!ERROR 18 LogSocketServer.Clock: T0correction("+LogSocketServer.shortClObjID(s)+") not even started");
sendMsgToAllListeners("%!ERROR 19 LogSocketServer: SrvrCmd !CLOSE: "+e.getMessage(), false);
LogSocketServer.sendText(sess, "!ERROR 20 LogSocketServer.Clock: tingtongReceiver exception "+e.getClass().getName()+" message: "+ e.getMessage());
sendMsgToAllListeners("%!ERROR 21 LogSocketServer: Empty msg from "+shortClObjID(session), false);
sendMsgToAllListeners("%!ERROR 22 LogSocketServer: /CFT Exception: "+e.getClass().getName()+" "+e.getMessage()+" arg="+arg, false);
sendMsgToAllListeners("%!ERROR 23 LogSocketServer: Lost track of Lggr "+shortId, false);



  */
