
import java.io.IOException;
import java.util.Scanner;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import florifulgurator.logsocket.javalggr.Lggr;
import florifulgurator.logsocket.javalggr.LogSocket;


@WebServlet(
		urlPatterns = { "/TESTservlet" }, 
		initParams = { @WebInitParam(name = "hurz", value = "mimi", description = "lala")},
		asyncSupported = true )

public class TESTservlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
    SSEBroadcaster broadcaster = new SSEBroadcaster();
	public static ExecutorService  exctrService = Executors.newFixedThreadPool(3);

	private boolean firstLogOK = LogSocket.newLggr("Srvlt", "#TESTservlet", "Throwaway logger in class TESTservlet before constructor")
								 .log("Class declaration of TESTservlet: Hello World! LogSocket ID yet be negotiated with LogSocketServer. Message buffered until then.");
								  //  Lggr.logSocketID might not yet be negotiated with LogSocketServer => ERROR
	private Lggr l1;

	
	
    /**
     * @see HttpServlet#HttpServlet()
     */
    public TESTservlet() {
        super();
        this.l1 = LogSocket.newLggr("Srvlt", "#TESTservlet", "Created in #CONSTRUCTOR of "+LogSocket.shortClObjID(this)+" firstLogOK="+firstLogOK);
    }

	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		l1.log("#INIT this="+this.toString()+" init("+config.toString()+") name="+config.getServletName()+" InitParams: "+initParametersToString(config));
	}

	/**
	 * @see Servlet#destroy()
	 */
	public void destroy() {
		l1.log("#DESTROY this="+this.toString()+" also calls super.destroy() super="+super.toString()+" (!!!)");
        broadcaster.close();
        exctrService.shutdownNow();
        super.destroy();
	}

	/**
	 * @see Servlet#getServletConfig()
	 */
	public ServletConfig getServletConfig() {
		l1.log("#getServletConfig()");
		return null;
	}

	/**
	 * @see Servlet#getServletInfo()
	 */
	public String getServletInfo() {
		l1.log("#getServletInfo()");
		return null; 
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		if (request.getHeader("accept").equals("text/event-stream")) {
			// Request by Javascript EventSource
			l1.logC(10,"logCounter1", "#GET responding to text/event-stream "
					+" doGet("+LogSocket.shortClObjID(request)+", "+LogSocket.shortClObjID(response)+")"
					+" request.getSession()="+LogSocket.shortClObjID(request.getSession())
					+" getRequestedSessionId()="+request.getRequestedSessionId()
			);
			l1.logCM(10,"logCounter1", "Request headers:\n"+requestHeadersToString(request));
			// Two logs repeated 5x each

	        broadcaster.addSubscriber(
	        	new SSETarget(request),
	        	new SSEMessage.Builder().setData("*** Welcome to the test event server ***").build()
	        );

	        l1.log("doGet(...) finished. Subscriber added to broadcaster.");
			
		} else {
			
			l1.log("#GET responding to anybody except text/event-stream");
			l1.log("Request headers:\n"+requestHeadersToString(request));

			response.setStatus(200);
			response.getWriter().append("Headers:\n").append(requestHeadersToString(request)).close();
		}
		response.flushBuffer();
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		l1.logC(10,"logCounter2", "#POST doPost("+LogSocket.shortClObjID(request)+", "+LogSocket.shortClObjID(response)+")"
				+" request.getSession()="+LogSocket.shortClObjID(request.getSession())
				+" getRequestedSessionId()="+request.getRequestedSessionId()
		);
		l1.logCM(10,"logCounter2", "#POST doPost(...) Request headers:\n"+requestHeadersToString(request));


		Scanner scanner = new Scanner(request.getInputStream());
        StringBuilder sb = new StringBuilder();
        while (scanner.hasNextLine()) {
            sb.append(scanner.nextLine());
        }
        
        String[] cmd = sb.toString().split("\\s+", 3);
        
        if ( !cmd[0].equals("CMD:") ) {
        	broadcaster.broadcast("message", sb.toString());
        	l1.log("#POST msg=\""+sb.toString()+"\" given to broadcaster. \""+cmd[0]+"\"");
        	return;
      
        } else {
        	l1.log("#POST cmd=\""+cmd[1]+"\"");
        	if ( cmd[1].equals("javaloop") ) {
        		int i;
        		try { i = Integer.parseInt(cmd[2]); }
        		catch (Exception e ){
        			l1.logErr("#POST Syntax Error: \""+e.getMessage()+"\"");
        			return;
        		}
        		if (i>10000) { l1.logErr("#POST You crazy. Not "+cmd[2]+". -- return"); return;}
         		if (i<200)   { l1.logErr("#POST Not enough: "+cmd[2]+". Should be more than 200. -- return"); return;}
       		
            	Lggr l2 = LogSocket.newLggr("Srvlt", "#JAVALOOP", "TEST: "+cmd[2]+" quick Lorem Ipsums x2 "+LogSocket.shortClObjID(request));
            	String text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
            	              + " -- "
            	              + "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
            	l2.microTimerClear(i-125, 125);//TODO JavaScript microTimer for comparability
            	l2.timerStart("JavaLoop");
        			while(--i >= 0) { l2.log(text); l2.microTimerTick(); }
            	l2.timerStop("JavaLoop");
            	l2.microTimerReport("JavaLoopMicro"); l2.microTimerLogReport("JavaLoopMicro");
            	// Results:
	            	
     		} else if ( cmd[1].equals("thread") ) {
     			l1.log("AAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
     			exctrService.execute( () -> {
     				Lggr l3 = LogSocket.newLggr("Srvlt", "#THREADPOOL", "TESTservlet running thread"+Thread.currentThread().getName()+" "+LogSocket.shortClObjID(request));
     				l3.log("this="+this.toString()+" Thread.currentThread().getName()="+Thread.currentThread().getName());
     			});
     		} else {
     			l1.log("CCCCCCCCCCCCCCCCCCCCCCCCCC");
     		}
        	
        }
//        try {
//        	l1.log("#POST doPost(...) But first 10s of SLEEP.");
//			//Thread.currentThread();
//			Thread.sleep(10000);
//		} catch (InterruptedException e) {
//			l1.logErr("#POST doPost(...) Sleep interrupted! #EXCEPTION:  \""+e.getMessage()+"\");");
//		}
	}

	/**
	 * @see HttpServlet#doPut(HttpServletRequest, HttpServletResponse)
	 */
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		l1.log("doPut("+LogSocket.shortClObjID(request)+", "+LogSocket.shortClObjID(response)+")");
	}

	/**
	 * @see HttpServlet#doDelete(HttpServletRequest, HttpServletResponse)
	 */
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		l1.log("doDelete("+LogSocket.shortClObjID(request)+", "+LogSocket.shortClObjID(response)+")");
	}

	/**
	 * @see HttpServlet#doOptions(HttpServletRequest, HttpServletResponse)
	 */
	protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		l1.log("doOptions("+LogSocket.shortClObjID(request)+", "+LogSocket.shortClObjID(response)+")");
	}

	/**
	 * @see HttpServlet#doTrace(HttpServletRequest, HttpServletResponse)
	 */
	protected void doTrace(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		l1.log("doTrace("+LogSocket.shortClObjID(request)+", "+LogSocket.shortClObjID(response)+")");
	}

	/**
	 * @see HttpServlet#service(HttpServletRequest request, HttpServletResponse response)
	 * If present, gets called to call doGet, doPut etc. manually.
	 */
	/* protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {} */

//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Utils
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

	String requestHeadersToString(HttpServletRequest r) { // getHeaderNames() => Enumeration<String>
		Stream<String> str = StreamSupport.stream(
			Spliterators.spliteratorUnknownSize(r.getHeaderNames().asIterator(), Spliterator.ORDERED),
			false
		);
		return str.map(e -> e+"="+r.getHeader(e)).collect(Collectors.joining("\n"));
	}
	String responseHeadersToString(HttpServletResponse r) { // getHeaderNames() => Collection<String>
		return r.getHeaderNames().stream().map(e -> e+"="+r.getHeader(e)).collect(Collectors.joining("\n"));
	}
	
	String initParametersToString(ServletConfig config) {
		Stream<String> str = StreamSupport.stream(
			Spliterators.spliteratorUnknownSize(config.getInitParameterNames().asIterator(), Spliterator.ORDERED),
			false
		);
		return str.map(e -> e+"="+config.getInitParameter(e)).collect(Collectors.joining("\n"));
	}
}
