console.log("client_clock.js: Hello World! 9");

// "A man with a watch knows what time it is. A man with two watches is never sure."

var I_nanoUnit, nanoUnit;

// Return client clock ticks in server clock tick units (1/100ms == 10μs)  #140aef3e
// T() corresponds to server-side function Clock.T() which is the System Time Normal
function T() { return Math.floor( Q*(performance.now()-T0) *nanoUnit ); }

var T0 = performance.now(); // high resolution timestamp in milliseconds.
// It represents the time elapsed since Performance.timeOrigin (the time when navigation has started in window contexts, or the time when the worker is run in Worker and ServiceWorker contexts).

var Q = 1.0 + 0.0 / (24*60*60*1000.0); // 1.0 + clock drift in ms per 24h.
// TODO #613da861 Clock synchronization: estimate Q
// T0 correction already works: precision <10μs with occasional outliers up to 50μs (why?)

// ~ "a typical quartz clock or wristwatch will gain or lose 15 seconds per 30 days" https://en.wikipedia.org/wiki/Quartz_clock
//   1μs/s == 86.4ms/d  500ms/d == 5.8μs/s  
//
// ~ Industry example: https://deuterontech.com/clock-synchronization/

// Full precision time in ms, like Java Clock.T_ms(), used only for clock synchronization or tests:
var T_ms = ()=> {return Q*(performance.now()-T0);}

//-----------------------------------------------------------------------------

// Command %CLOCK
function initClock([nU, srvr_T_ms]) {
	nanoUnit = Number.parseInt(nU); // nanoUnit hardwired to 100 in server clock. #70249bfa
	I_nanoUnit = 1.0/nanoUnit;
	let nanoUnitStr = "?!!?";
	switch (nanoUnit) { // DOCU #140aef3e
		case 10: nanoUnitStr = "&#8530;ms"; break; // 100000ns == 1/10ms
		case 100: nanoUnitStr = "10&mu;s"; break;  //  10000ns == 10μs
	}
	document.getElementById("CTtxtID").innerHTML = nanoUnitStr;
	
	T0 = T0 - Number.parseFloat(srvr_T_ms) - T_ms(); // wrong by a few handfuls of milliseconds
//return;//DEV	
	setTimeout(Clock_SyncDaemon.T0correction,  2000); // TODO #66eb4a7b Not yet a daemon
	setTimeout(Clock_SyncDaemon.T0correction,  4000); 
	setTimeout(Clock_SyncDaemon.T0correction,  10000); // Just for fun...
	setTimeout(Clock_SyncDaemon.T0correction,  100000); // 100s
	setInterval(Clock_SyncDaemon.T0correction, 1000000); // 16m
}

// Command %T0CORR: Result of clock sync by server, using same algorithm and names as here.
function T0corrFromSrvr([status, _T0corr, _avgC, _avgS, ttRounds, ttTime_ms]) { //#e7c370d7
	switch (status) {
		case "OK":
			let corrT0 = - Number.parseFloat(_T0corr); // #e7c370d7
			if (Number.isNaN(corrT0)) {	alertRed("T0corrFromSrvr(...) BUG"); return; }
			
			T0 = T0 - corrT0;  
			
			document.getElementById("T0corrID").innerHTML = round1(1000*corrT0)+"&mu;s";
			document.getElementById("T0corrSID").classList.remove("hidden");
			document.getElementById("T0corrCID").classList.add("hidden");
			document.getElementById("amrtCID").innerHTML = round3(_avgS)+"ms";// !!! cf. Clock.java CAVEAT #e7c370d7
			document.getElementById("amrtSID").innerHTML = round3(_avgC)+"ms";
			alertGreen("Clock correction by server: "+round1(1000*corrT0)+"&mu;s");
			console.log("tingong rounds="+ttRounds+", tingtong time="+ttTime_ms+"ms");
			break;

		case "ERROR":
			alertRed("Clock sync by server: "+status+"<br/>after "+T0+" tingtong rounds");
			break;
		case "STOPPED":
			alertBlue("Clock sync by server: "+status+"<br/>after "+T0+" tingtong rounds");
			break;
	}
	
	document.getElementById("syClClBtn").disabled=false; // #2ebbea73
}

//-----------------------------------------------------------------------------


//

// Tingtong >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Clock pingpong is the basic clock laboratory procedure
//
var ting=null, ting1=null, tong=null, tong1=null; 
// Same _vars on server in static class Clock: _tong, _tong1, _ting, _ting1
// 
// Clnt T_ms()              Srvr T_ms()              Clnt T_ms()              Srvr T_ms()
//      (ting) ---!TING--->     (_tong) ---@TONG--->     (ting1) ---!TING--->    (_tong1)
//
// Srvr T_ms()              Clnt T_ms()              Srvr T_ms()              Clnt T_ms() 
//     (_ting) ---@TING--->      (tong) ---!TONG--->    (_ting1) ---@TING--->     (tong1) ---!TONG--->
//
// Srvr   ---@RSLT---> Clock_SyncDaemon.receiveTTResult(_tong,_tong1,_ting,_ting1)
// 
//                                                               

// C = (ting1-ting + tong1-tong)/2  == Avg client msg roundtrip time
// S = (_tong1-_tong + _ting1-_ting)/2  == Avg server msg roundtrip time (Quality test: should be quite the same)

// E = (_tong-ting + _tong1-ting1 + _ting1-tong)/3  == Avg srvr-clnt time diff
// F = (ting1-_tong + tong-_ting + tong1-_ting1)/3  == Avg clnt-srvr time diff 

// If R_cs is the message travel time from client to server, and R_sc from server to client
// and X is ( server Clock.T_ms() - client T_ms() ) assumed to be called simultaneously (ask Einstein for problematics)
// then (E-F)/2 == X + (R_cs-R_sc)/2


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
var Clock_SyncDaemon =  ( () => {
// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Similar in Clock.java
// TODO #66eb4a7b Quartz drift, daemon 

// Implemented in the conceptually perhaps not simplest manner:
// Instead of feeding data from server into a plain callback function, 
// a generator function* is used that waits for data to receive via the yield operator.
// (So actually it is a receiver, not a generator. I wanted to try that out.)
// This reverse-perspective style doesn't need much more text than the simple solution,
// and might be more flexible for ongoing development.
//
// The Java equivalent implementation uses a thread and a higher-functional generator/receiver
// "Seq" that waits for a CompletableFuture (another functional programming thing I wanted to try).


var tingtongReceiver = null;
var T0correctns = [];
var stopping = true;
var started = false;

async function T0correction() {
	// Hardwired algorithm parameters:  #P1 ... #P6
	if (started) {console.error("T0correction already running"); return Number.NaN;}

	started = true;
	stopping = false;
	T0correctns = [];
	var tingtongRounds = 0, avgC, avgS;
	var ttt_ms = T_ms();

	try {
		tingtongRounds = await new Promise( (resolve, reject) => {
			tingtongReceiver = makeTingtongResultReceiver(200, resolve, reject); // #P1
			tingtongReceiver.next(); // init // #N a value passed to the first invocation of next(value) is always ignored.
			if (!startTingtongRound()) reject(new Error("Not even started."));
		});
		
		ttt_ms = T_ms()-ttt_ms;

	} catch (error) {
		if (stopping)
			alertRed("Clock sync stopped:<br/>"+error.message); // Just to be sure. Should be a bug when stopped this way.
		else
			alertRed("Clock sync failed:<br/>"+error.message);
		return Number.NaN; //really stopped

	} finally {
		tingtongReceiver = null;
	}

	if (tingtongRounds>170) { // #P2
		T0correctns.sort((a,b) => a[0]-b[0]); // Sort by total tingtong time tong1-ting.
		//Use those corrections whose total tingtong time is in the better 70%.
		//TODO? Reject correction if time at 70% is higher than avg. Looks like outliers can reduce correction precision 10x
		const usedLen = Math.max(1, Math.floor(T0correctns.length*0.7)); // #P3

		// Try that in Java:  :-D
		var [corrT0, avgC, avgS] = T0correctns.slice(0,usedLen)
			.reduce(
				(acc,x) => [ acc[0]+x[4], acc[1]+x[1], acc[2]+x[2] ],
				[0,0,0] 
			);
		corrT0 /=usedLen; avgC /= usedLen; avgS /= usedLen;
		
		// Clock synchronization, first step: /////
		T0 = T0 - corrT0;                        //
		// \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\

		document.getElementById("T0corrID").innerHTML = round1(1000*corrT0)+"&mu;s";
		document.getElementById("T0corrCID").classList.remove("hidden");
		document.getElementById("T0corrSID").classList.add("hidden");
		document.getElementById("amrtCID").innerHTML = round3(avgC)+"ms";
		document.getElementById("amrtSID").innerHTML = round3(avgS)+"ms";
		alertGreen("Clock correction "+round1(1000*corrT0)+"&mu;s");

		console.log("T0 correction: "+ Math.round(10000*corrT0)/10+"μs",
			"avgMsgRoundtripT_Clnt="+round1(avgC*1000)+"μs.",
			"avgMsgRoundtripT_Srvr="+round1(avgS*1000)+"μs.",
			"Total tingtong time: "+Math.round(ttt_ms)+"ms"
		);
		
		started = false;
		
	} else {
		if (!stopping) alertBlue("Clock sync interrupted<br/>by log activity.");
		else alertRed("Bug? #3fc360db"); //TODO WHY?
		started = false;
	}
	
	document.getElementById("syClSvBtn").disabled=false; // #2ebbea73
}


function* makeTingtongResultReceiver(repeats, resolvePromise, rejectPr) {
	var i=0;
	setTimeout( ()=> { if(i==0) {rejectPr(new Error("Tingtong timeout."));} }, 777 ); // #P6
	yield; // #N 
	
	do {
		// ///////////////////////////
		const result = yield;       // <-- data from @TT_RSLT via receiveTTResult(str)
		// \\\\\\\\\\\\\\\\\\\\\\\\\\\
		if (result=="stop #4fc370cc") break;
		
		const [a, b, c, d] = result.split(" ");
		const _tong=Number.parseFloat(a), _tong1=Number.parseFloat(b), _ting=Number.parseFloat(c), _ting1=Number.parseFloat(d);
		if   (_tong==Number.NaN ||        _tong1==Number.NaN ||        _ting==Number.NaN ||        _ting1==Number.NaN ) {
			rejectPr(new Error("NaN in "+result));  // TODO: TEST it
			return;
		}

		i++;
		T0correctns.push( [
			tong1-ting,
			(ting1-ting + tong1-tong)/2,  // C
			(_tong1-_tong + _ting1-_ting)/2, // S
			(tong1 + ting)/2.0, // T Choice of absolute time for least squares #613da861
			0.5*( (_tong-ting + _tong1-ting1 + _ting1-tong)/3.0 - (ting1-_tong + tong-_ting + tong1-_ting1)/3.0 ) // D = (E-F)/2
		] );
	}
	while ( --repeats && numMsgsUpdt.notPending ); // Logging is more important
	
	ting = null; ting1 = null; tong = null; tong1 = null; 

	resolvePromise(i);
}

function startTingtongRound() {
	ting1 = null; tong = null; tong1 = null; 
	ting = T_ms();
	return doSend("!TING");
}

function stop() { //unused
	stopping = true;
	if (tingtongReceiver) tingtongReceiver.next("stop #4fc370cc");
}

//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
// "public methods" of Clock_SyncDaemon
return 	{ 
	receiveTTResult: (str) => {  // cmd @RSLT
		if ( tingtongReceiver ) {
			if (!tingtongReceiver.next(str).done ) setTimeout( startTingtongRound, 0 );
			// else done all!
		} else if (!stopping) console.error("receiveTTResult BUG");
	},
	T0correction: T0correction
};	
// END of var Clock_SyncDaemon = ( () => { ...	<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
})();
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
