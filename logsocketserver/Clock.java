package florifulgurator.logsocket.server;

// "A man with a watch knows what time it is. A man with two watches is never sure."

import static florifulgurator.logsocket.utils.MGutils.*;
import florifulgurator.logsocket.yseq.Seq;
import florifulgurator.logsocket.yseq.Seq.SeqStopException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.websocket.Session;

// Excellent article on System.nanoTime() performance and Linux implementation details: https://www.javaadvent.com/2019/12/measuring-time-from-java-to-kernel-and-back.html
// Calls Posix::clock_gettime(CLOCK_MONOTONIC, &tp), returns jlong(tp.tv_sec) * (1000 * 1000 * 1000) + jlong(tp.tv_nsec)
// It is not more costly than System.currentTimeMillis() when the OS uses vDSO to bypass system calls.

public class Clock {
	public static final long   T0 = System.nanoTime(); // close to max safe integer in JavaScript
	                                                   // Windows: Last 2 digits of System.nanoTime() either 99 or 00.
	public static  int         nanoUnit = 100;         // 100: 1Tick == 10μs==1/100ms || 10: 1Tick==1/10ms
	                                                   // Likely forever hardwired to 100. Dependency: CAVEAT #70249bfa
	public static  double      milliNanoUnit = nanoUnit/1000000.0;

	// DOCU #140aef3e
	// LogSocket system time T comes as integer number of server "clock ticks"
	// T()/nanoUnit = ms
	public static final long T() { return Math.round((System.nanoTime()-T0) *milliNanoUnit); }

	// Full precision time in ms. Use only in clock synchronization, diagnostics, tests
	public static final double T_ms() { return Q*(System.nanoTime()-T0) /1000000.0; }
	
	// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	// TEST: Add artificial clock/quartz drift to test clock synchronization on 1 machine.
	public static final double Q = 1.0;// + 111.111 / (24*60*60*1000.0) ≈ 0.00000128
	//  1.0 + clock drift of 111.111 ms per 24h == 4.629ms/h == 77.2μs/min == 1.3μs/s
	//  Typical would be 0.5s per 24h == 5.8μs/s == 1μs/172ms
	
	// Used for clock synch ting-tong from both sides: 
	public static Double tong = null;
	public static Double tong1 = null;
	public static Double ting = null;
	public static Double ting1 = null;
		

// ----------------------------------------------------------------------------------------
// Translated from client_clock.js
// TODO #2ebbea73 Block/await client-side sync
// TODO #66eb4a7b Quartz drift, daemon 

public static class SyncDaemon {
	private static boolean DEV = false;
	
	private static Session sess; // other side with a clock

	private static CompletableFuture<String> cfuture;
	private static boolean T0correction_started = false;
	private static long lastSrvrMsgT; // to stop T0correction when log activity


	public static synchronized void T0correction(Session s, long lastMsgT) { // Launches thread and returns
		final int PAR1=200; final int PAR2=170;	final double PAR3=0.7;
		
		if (T0correction_started) {
			LogSocketServer.sendText(sess, "! T0correction("+shortClObjID(s)+") already started");
			return;
		}
		System.out.println(".... starting T0correction("+shortClObjID(s)+")");

		T0correction_started = true;
		sess = s;
		lastSrvrMsgT = lastMsgT;

		System.out.println("==== T0correction launching thread");//DIAGN
			
// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
LogSocketServer.exctrService.execute( () -> { // >>>>>>>>>>>>>>>>>>
	System.out.println("==== T0correction thread BEGIN");//DIAGN

	if (!startTingtongRound()) {
		cfuture.cancel(true);
		T0correction_started = false;
		System.err.println("!!!. LogSocketServer.Clock: T0correction("+shortClObjID(sess)+") not even started");
		LogSocketServer.sendText(sess, "!ERROR 18 LogSocketServer.Clock: T0correction("+shortClObjID(s)+") not even started");
		System.out.println("==== T0correction thread END1");//DIAGN
		return;
	};

	String status="ERROR";
	boolean[] stopped = {false}; // effectively final :-)
	double ttt_ms = T_ms();
	ArrayList<CSTD> tingtongValues = tingtongReceiver(stopped) // Seq.java: A simple lazy-evaluation functional iterator/stream/generator/receiver
		.take(PAR1)
		.map(
		r -> new CSTD(
			(r.tong1 - r.ting),
			(r.ting1 - r.ting  + r.tong1 - r.tong)/2.0,  //C
			(r._tong1- r._tong + r._ting1- r._ting)/2.0, //S
			(r.tong1 + r.ting)/2.0,  // T Choice of absolute time for least squares #613da861
			0.5*( (r._tong - r.ting  + r._tong1- r.ting1 + r._ting1- r.tong)/3.0 - (r.ting1 - r._tong + r.tong  - r._ting + r.tong1 - r._ting1)/3.0 )  // D = (E-F)/2
		)
	// /////////////////
	).toArrayList();  // <-- data from @RSLT via receiveTTResult(str)
	// \\\\\\\\\\\\\\\\\
	
	ting = null; ting1 = null; tong = null; tong1 = null; 
		
	ttt_ms = T_ms()-ttt_ms;
	int tingtongRounds = tingtongValues.size();
	
	if (stopped[0]) status = "STOPPED";

	if (tingtongRounds<PAR2) {
		LogSocketServer.sendText(sess, "%T0CORR "+status+" "+tingtongRounds);
		System.out.println("==== T0correction: No result");
		System.out.println("==== T0correction thread END2");//DIAGN
		T0correction_started = false;
		//... stopping?
		return;
	}

	int usedLen = (int) Math.max(1, Math.floor(tingtongRounds*PAR3));
	tingtongValues.sort(Comparator.comparing( r -> r.t_t ));
	
	double _avgC[] = {0.0}; // effectively final
	double _avgS[] = {0.0};
	double _avgCS[] = {0.0};
	double _T0corr[] = {0.0};

	// #E xperimental - For orthogonal least squares:
	double _x[] = {0.0}; // double _y[] = {0.0}; == _T0corr

	// No way to efficiently shrink (discard tail of) an ArrayList:
	// Generally recommended: tingtongValues.subList(usedLen, tingtongValues.size()).clear(), which calls protected void removeRange(int fromIndex, int toIndex), 
	// which gets a list iterator positioned before fromIndex, and repeatedly calls ListIterator.next followed by ListIterator.remove until the entire range has been removed.
	tingtongValues.subList(0,usedLen)
		.forEach( r -> {
			_avgC[0] += r.C; _avgS[0] += r.S;
			_avgCS[0] += r.C/r.S;
			_T0corr[0] += r.D;
			_x[0] += r.T; // #E
		} );
	_avgC[0] /= usedLen; _avgS[0] /= usedLen; _avgCS[0] /= usedLen; _T0corr[0] /= usedLen;
	_x[0] /= usedLen; // #E

	LogSocketServer.sendText(sess, "%T0CORR OK "+round6(_T0corr[0])+" "+round3(_avgC[0])+" "+round3(_avgS[0]) //#e7c370d7
		+" "+tingtongRounds+" "+round3(ttt_ms) // for client console.log
	);

	if (DEV) {	 // #E
			System.out.println("==== T0 correction: "+ _T0corr[0]);
			System.out.println("==== Avg msg roundtrip time: Server: "+round3(_avgC[0])+"ms, client: "+round3(_avgS[0]));
			System.out.println("==== Total tingtong time "+round3( ttt_ms)+"ms tingtongRounds="+tingtongRounds);
	
			// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
			// #613da861 Get better T0corr plus rough estimate of Q by orthogonal least squares method:
			// Formula in https://en.wikipedia.org/wiki/Deming_regression#Solution (delta=1)
			final double _x_ = _x[0]; //Performance/clarity
			final double _y_ = _T0corr[0];
			double Sxx[] = {0.0}; double Sxy[] = {0.0};	double Syy[] = {0.0};
			tingtongValues.subList(0,usedLen)
				.forEach( r -> {
					Sxx[0] += square(r.T-_x_);
					Syy[0] += square(r.D-_y_);
					Sxy[0] += (r.T-_x_)*(r.D-_y_);
				} );
			double beta_1 = 0.5*( Syy[0]-Sxx[0] + Math.sqrt( square(Syy[0]-Sxx[0]) + 4.0*square(Sxy[0]) ) ) /Sxy[0];
			double beta_0 = _y_ - beta_1*_x_; //instead of T0corr
			double alpha_1 = Sxy[0]/Sxx[0]; // ordinary (non orthogonal) least squares ~ beta_1
	
			System.out.println("==== Q="+ (1.0 + beta_1) + " beta_0="+beta_0+" _x_="+_x_+" alpha_1="+alpha_1);
	
			// #613da861
			// Result: A) T0corr is actually better than beta_0! (No wonder: Squaring amplifies the larger errors.)
			//     but B) beta_1 estimation of clock drift seems already quite good (total tingtong time 315.21ms only).
			//            Client clock Q=1.2222222222 => beta_1=0.2222329021718387
			
	} //<<<<<<<DEV
	
	T0correction_started = false;
	//... stopping?
	System.out.println("==== T0correction thread END3");//DIAGN
}); // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
// Thread <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	
		System.out.println("==== T0correction END");//DIAGN
	} // T0correction <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


	// Fields of tingtongReceiver:
	private final static int PAR6 = 777;
	private static final Pattern  blankPttrn = Pattern.compile("\\s+");
	private static record Tttttttt(double tong, double tong1, double ting, double ting1, double _tong, double _tong1, double _ting, double _ting1) {};
	private static record CSTD(double t_t, double C, double S, double T, double D) {};
	// t_t = tong1-ting
	// Quoth client_clock.js:
	// C = (ting1-ting + tong1-tong)/2  == Avg client msg roundtrip time           [Here: SERVER!] CAVEAT #e7c370d7
	// S = (_tong1-_tong + _ting1-_ting)/2  == Avg server msg roundtrip time       [Here: CLIENT!]
	// E = (_tong-ting + _tong1-ting1 + _ting1-tong)/3  == Avg srvr-clnt time diff
	// F = (ting1-_tong + tong-_ting + tong1-_ting1)/3  == Avg clnt-srvr time diff 
	//
	// If R_cs is the message travel time from client to server, and R_sc from server to client
	// and X is ( server Clock.T_ms() - client T_ms() ) assumed to be called simultaneously
	// then (E-F)/2 == X + (R_cs-R_sc)/2

	public static Seq<Tttttttt> tingtongReceiver(boolean[] stopped) {
		return c -> {
			while (true) {
				try {
					String[] received = blankPttrn.split(
						cfuture.get(PAR6, TimeUnit.MILLISECONDS),
						4 );

					c.accept(
						new Tttttttt(
							tong, tong1, ting, ting1,
							Double.valueOf(received[0]), Double.valueOf(received[1]), Double.valueOf(received[2]), Double.valueOf(received[3])
					));

					//Thread.sleep( 1 );
					//No sleep: Total tingtong time 430ms
					//Thread.sleep(2): 1092ms  ==> sleeps 3.3ms actually!
					
					if ( !startTingtongRound() ) {
						cfuture.cancel(true);
						System.err.println("!!!. tingtongReceiver aborted: LogSocketServer could not send command.");
						Seq.stop();
					}

				} catch (SeqStopException e) {
					break;
				} catch (Exception e) {
					if (e.getMessage().equals("stop #4fc370cc")) {
						stopped[0]=true;
						break;
					} else {
						LogSocketServer.sendText(sess, "!ERROR 20 LogSocketServer.Clock: tingtongReceiver exception: "+e.getClass().getName()+" message: "+ e.getMessage());
						System.err.println("!!!. tingtongReceiver exception "+e.getClass().getName()+" message: "+ e.getMessage());
						break;
						// ??! #2ebbea73-->
						// tingtongReceiver exception java.lang.NullPointerException message: Cannot invoke "java.lang.Double.doubleValue()" because "florifulgurator.logsocket.server.Clock.tong" is null
					}
				}
			}
		};
	}

	private static boolean startTingtongRound() {
		cfuture = new CompletableFuture<String>();
		ting1 = null; tong = null; tong1 = null; 
		ting = T_ms();
		return LogSocketServer.sendText(sess, "@SING");
	}
			
	public static void receiveTTResult(String res, long lastMsgT) {
		if ( cfuture==null) {
			System.err.println("!!.. receiveTTResult("+res+") No future");
			return;
		}
		if ( lastMsgT != lastSrvrMsgT) {
			cfuture.completeExceptionally(new Exception("stop #4fc370cc"));
			return;
		}
		cfuture.complete(res);
	}
	

	} //  class SyncDaemon <<<<<<<<<<<<<<<
} // class Clock <<<<<<<<<<<<<<<<<<<<<<