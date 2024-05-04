/**
 *  LogSocket.js
 *  1-File parallel to 2-File LogSocket.java, Lggr.java
 *  An exercise in comparative JS/Java linguistics :-)
 */

console.log("LogSocket.js: Hello World! 77");

if ( typeof LogSocket != "undefined" ) console.warn("LogSocket already exists");

// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
var LogSocket = LogSocket || ( () => {
// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// "Private namespace": Closure of an anonymous immediately invoked function.
// "Public methods" are in the returned object assigned to var LogSocket.

var Nr;  // Unique ID generated by LogSocketServer & set via "/START Nr"
var wsUri = "ws://localhost:8080/logsocket/ws"; // DOCU #57977fd8 LogSocketServer URI HARDWIRED
var websocket;
var shutDown = false;
var isOpen = false; // == true after /START was OK
var numLggrs = 0;	//Count of created Loggers.
var realmLabel2LggrList = new Map();
var logBuffer = [];
var logBuffered = false;
var nrBuffer = [];
var nrBuffered = false;
var filter1 = new Set(); //DEV #6cb5e491

const illglLblChrsPttrn = new RegExp("[^#@A-Za-z0-9_]", "g");

const shortId2PromiseRslv = new Map(); // For logs with buttons. shortId2CFuture in LogSocket.java

const finalizeRgstr = new FinalizationRegistry( val => {finalizeLggr(val);} ); // Lggr garbage collection



//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Basic WebSocket infrastructure
var websocket;
var connectionTried = 0;
//---
function onOpen(evt) {
	connectionTried = 0;
	console.log(`WebSocket OPEN: ${(evt.data?evt.data+" ":"")}wsUri=${wsUri}`);

	websocket.send("!GREETS");
}
//---
function onError(evt) {
	// If an error occurs while attempting to connect, first a simple event with the name error is sent to the WebSocket object (thereby invoking its onerror handler),
	// and then the CloseEvent is sent to the WebSocket object (thereby invoking its onclose handler) to indicate the reason for the connection's closing.
	if (connectionTried) {
		console.log(`WebSocketServer: No reply. Trying again (${++connectionTried})`);
		if (connectionTried>2) return; //Don't bother log anymore
	}
	console.error(`WebSocket ERROR: evt.data=${evt.data} wsUri=${wsUri}`);
}
//---
function onClose(evt) {
	if (connectionTried || evt.code == 1006 ) {
		if (connectionTried>50) {
			//connectionTried=0; 	// TODO #3ebe778e reconnect, buffer
			console.log("WebSocketServer: No reply. Giving up. Try reloading page later.");
		} else {
			setTimeout(	connect, 1000);
			return;
		}
	}
	
	let CloseReason = closeEvtCode.get(evt.code) ? closeEvtCode.get(evt.code) : "UNKNOWN";
	console.log(`WebSocket onClose: evt.wasClean==${evt.wasClean} evt.code==${evt.code} CloseReason: "${CloseReason}"`);

	shutDown = true; isOpen = false; websocket = null;
	//wasClean == True if the connection closed cleanly, false otherwise.
	//https://devdocs.io/dom/closeevent/code
}
//---
function connect() { // Connecting only when used.
	if (websocket) { console.error("WebSocket already there."); return; }
	console.log("Connecting...");

	try {
		websocket = new WebSocket(wsUri);
		// Async. Unlike Java (* depending which implementation) this will automatically attempt to open the connection to the server
		// Unlike in Java (*), a websocket.send(...) right here would not work.
		// The first send(...) happens in event listener onOpen(...). This would not work in Java.
	} catch (ignore) {} //onError does job
	
	if (!connectionTried) connectionTried = 1;
	websocket.onopen = onOpen;
	websocket.onclose = onClose;
	websocket.onerror = onError;
	websocket.onmessage = onMessage;
}
//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


function onMessage(evt) {
	var msg = evt.data;
	var cmd = msg.split(" ",1)[0]; // #note0
	var arg = msg.substring(cmd.length+1);
	
	switch (cmd) {
	case "/START":
		var n = parseInt(arg);
		if (isNaN(n)) {
			console.error("Not a number in "+msg);
			//TODO close websocket
			return;
		}
		if (Nr!=null && Nr!=n) console.error("LogSocket_ERROR_14: "+msg+" but already LogSocket.Nr="+Nr);
		Nr = n;
		isOpen = true;
		if (nrBuffered ) { releaseNrBuffer(); }
		if (logBuffered ) { releaseLogBuffer(); }
		return;

	case "/PING":
		websocket.send("%/ LogSocket /"+Nr+" (JavaScript) is alive! "+numLggrs+" loggers created."); // TODO #495e57b8
		return;

	case "/GC":
		// TODO consistency check: arg==LogSocket.Nr ?
		if (!window.gc) {
			websocket.send("%/ LogSocket.js /"+Nr+": Garbage collection function window.gc() missing: Start Chrome with --js-flags=\"--expose-gc\" ?");
		} else {
			window.gc();
		}
		return;
		
	case "/FILTER1_ADD":
		for ( const rule of arg.split(" ")) {
			filter1.add(rule);
			filter1_applyRule(rule);
		}
		return;

	case "/CFT":
		let spl = arg.split(" ", 2);
		let arr = shortId2PromiseRslv.get(spl[0]);
		if ( arr != null ) {
			let [reslv, rejct] = arr;
			shortId2PromiseRslv.set(spl[0], null);
			reslv(spl[1]);
		}
		else  websocket.send("%/ALERT_R Button click:<br/>Promise is null!");
		return;
				
	}
	
	complain("LogSocket_ERROR_10: Unknown message=\""+msg+"\"");
}



// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Logger constructor >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// See lggr.java for more/other explanations
function Lggr(rlm, lbl, i2, commnt, n) {
	this.realm = rlm;
	this.label = lbl;
	this.n2 = i2;     // realm+label+n2 give Logger ID, put together in this.longId
	this.nr = n;      // Counted by LogSocket. Incl. LogSocket.Nr gives short no-information ID for Listener GUI.
	                  // nr==0 : realm+label listed as stopped before creation. => no longId, shortId, no finalization registry //DEV #6cb5e491
	this.on = false;  // Here isser/setter makes sense #5dc4f4a6
	this.numMsgs = 0;
	this.longId;      // Long ID
	this.shortId;     // ==undefined if there is no logSocket.Nr - longId then has a preliminary value!=""
				      // if !=undefined longId is definitive
	this.comment = commnt;

	if (n!=0) { // >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
		//FIXME / DEV #6cb5e491 #5dc4f4a6 breaks when nr==0. Use prototype
		this.on = true;
		makeLggrIdStrings(this);

		this.repeatCounters = new Map();
	
		this.toString = function() {return this.longId; };  // Should not be used
		
		this.log = function(msg) { if (this.on) {return log(this, msg);} };
		this.logErr = function(msg) { if (this.on) {return logErr(this, msg);} };
		this.logPrms = function(msg) { if (this.on) {return logPrms(this, msg);} else {return Promise.resolve("");} };

		this.logC = function(n, logCntrID, msg) {
			// Log max. n messages as counted by counter logCntrID
			if (!this.on) { return false; }
			var count = this.repeatCounters.get(logCntrID);
			if (count==null) count=0;
			if (count < n) {
				this.repeatCounters.set(logCntrID, ++count);
				return log(this, `[${count}/${n}] ${msg}`);
			}
			return false;
		};
		 
		this.timerStart = function(timerName) { if (this.on) {return timerStartStop(this, timerName, true);} else {return false;} };
		this.timerStop = function(timerName) { if (this.on) {return timerStartStop(this, timerName, false);} else {return false;} };
	
		// ...
		//TODO more functions from Java
	} // if (n!=0) <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
}
// Logger constructor <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


function finalizeLggr(str) {
	if (!isOpen) return;
	websocket.send("!GC_LGGR " + str);
}

function makeLggrIdStrings(lgr) { 
	if ( lgr.shortId!=undefined) {return;}
	if ( lgr.n2!=0 ) { lgr.longId = `${lgr.realm}/${Nr}${lgr.label};${lgr.n2}`;
	} else           { lgr.longId = `${lgr.realm}/${Nr}${lgr.label}`; }

	if (Nr) {
		lgr.shortId = `/${Nr}_${lgr.nr}`;
	}
}

function makeKnown(lgr) {
	try {
		if (!isOpen) throw "Websocket not ready";

		finalizeRgstr.register(lgr, `${lgr.shortId} ${lgr.longId}`);
		websocket.send(`!NEW_LGGR ${lgr.on?"1":"0"} ${lgr.shortId} ${lgr.longId} ${(lgr.comment!=null ? lgr.comment : "")}`); // see also GC_LGGR

	} catch (e) { // TODO not catching websocket.send!
		console.error("---- LogSocket_ERROR_5 Exception: ", e);
		lgr.comment = "LogSocket_ERROR_5";
		lgr.on = false;
		shutDown = true; isOpen = false;
		return false;
	}
	return true;
}


function releaseNrBuffer() {
	if (Nr==null) { throw "LogSocket_ERROR_15: FATAL BUG. Nr should not be null"; }
	if (!nrBuffered) return;
	nrBuffered = false;
	while (nrBuffer.length!=0) {
		let bffrdLggr = nrBuffer.shift();
		bffrdLggr.comment += " [NR_BUF]";
		makeLggrIdStrings(bffrdLggr);
		makeKnown(bffrdLggr);
	}
}


function notBuffered(lgr, msg) {
	if (Nr == undefined) {
		logBuffer.push( [lgr,msg] );
		logBuffered = true;
		return false;
	}

	if (logBuffered) {
		// The Nr==null problem concerned every Lggr. 
		releaseLogBuffer();
	}
	return true;
}

function releaseLogBuffer() {
	logBuffered = false;
	while ( logBuffer.length!=0 ) {
		let pair = logBuffer.shift();
		let bffrdLggr = pair[0];
		makeLggrIdStrings(bffrdLggr);
		websocket.send(`-${bffrdLggr.longId}&${bffrdLggr.shortId}&${++bffrdLggr.numMsgs}&B ${pair[1]}`); // #c29e692
	}
}

	
function complain(txt) {
	console.error("!!-- "+txt);
	try {
		websocket.send("%/ERROR /"+Nr+" "+txt);
	} catch (e) {
		// Maybe that was the reason for the complaint
	}

}
	
// Logging services: >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// TODO logM, logCmd from Java

function log(lgr, msg) {
	if (shutDown) {
		lgr.on = false; //TODO all other loggers
		return false;
	}
	if ( notBuffered(lgr, msg) ) {	websocket.send(`*${lgr.longId}&${lgr.shortId}&${++lgr.numMsgs} ${msg}`); }
	return true;
}

function logErr(lgr, msg) {
	if (shutDown) {
		lgr.on = false; //TODO all other loggers
		return false;
	}
	if ( notBuffered(lgr, msg) ) {	websocket.send(`+${lgr.longId}&${lgr.shortId}&${++lgr.numMsgs}&E ${msg}`); }
	return true;
}

function logPrms(lgr, msg) { // logCFtr in LogSocket.java
	if (shutDown) {
		lgr.on = false; //TODO all other loggers
		return Promise.reject(new Error("LogSocket is shut down."));
	}
	if (Nr != null) { // not buffering
		let arr = shortId2PromiseRslv.get(lgr.shortId);  // shortId2CFuture in LogSocket.java
		if ( arr != null ) {
			let [reslv, rejct] = arr;
			rejct(new Error("logPrms: Old Promise rejected in favour of a new one."));
		}
		prms = new Promise( (resolve, reject) => { shortId2PromiseRslv.set(lgr.shortId, [resolve, reject]); });
		websocket.send( `+${lgr.longId}&${lgr.shortId}&${++lgr.numMsgs}&P ${msg.split("\n",1)[0]}\n/CFT ${lgr.shortId}` );
			// Here (not elswhere) we make sure there are no \n in msg
			// /CFT ... is the command used in client response, which adds the HTML value of the clicked button
		return prms;
	}
	return Promise.reject(new Error("LogSocket is buffering."));
}




// Global timers managed by LogSocketServer
function timerStartStop(lgr, timerName, start) {
	if (shutDown) {
		lgr.on = false; //TODO all other loggers
		isOpen = false;
		return false;
	}
	const msg = (start ? "!T_START " : "!T_STOP ") + timerName;
	if ( notBuffered(lgr, msg) ) { websocket.send( msg );}
	return true;
}


function sendCmd(lgr, cmd) {
	if (shutDown) {
		lgr.on = false; //TODO all other loggers
		isOpen = false;
		return false;
	}
	if ( notBuffered(lgr, cmd) ) { websocket.send(cmd);	}
	return true;
}


function filter1_applyRule(realmLabel) {
	
	if (realmLabel.charAt(0)=='*') {//All realms
		complain("TODO #6776bf39 filter1 rule="+realmLabel);
		return;
	}
	if (realmLabel.charAt(realmLabel.length-1)=='*') {//All labels
		complain("TODO #6776bf39 filter1 rule="+realmLabel);
		return;
	}

	let lggrList = realmLabel2LggrList.get(realmLabel);
	if (lggrList==null) return;

	let cmdArgs="";
	let gone=0, stopped=0, alreadystopped=0;
	
	for (weakRef of lggrList) {
		let l = weakRef.deref();
		if ( l!=null ) {
			if ( l.on ) {
				l.on=false;
				stopped++;
			} else {
				alreadystopped++;
			}
			cmdArgs += " "+l.shortId;
			
		} else gone++;
	}
	
	if ( cmdArgs!="" ) {
		websocket.send("!STOPPED"+cmdArgs );
		websocket.send("%/ LogSocket /"+Nr+": Filter rule \""+realmLabel+"\" stopped "+stopped+" logger instance"+(stopped==1?"":"s")
	                +" - "+alreadystopped+" already stopped, "+gone+" gone." );
		//TODO #7594d994 client consistency test
	}
}

const closeEvtCode = new Map(); // Eextended version in NOTES.txt #4b98b431
closeEvtCode.set(1000, "Normal Closure");
closeEvtCode.set(1001, "Going Away");
closeEvtCode.set(1002, "Protocol error");
closeEvtCode.set(1003, "Unsupported Data");
closeEvtCode.set(1004, "Reserved");
closeEvtCode.set(1005, "No Status Rcvd");
closeEvtCode.set(1006, "Abnormal Closure");
closeEvtCode.set(1007, "Invalid frame payload data");
closeEvtCode.set(1008, "Policy Violation");
closeEvtCode.set(1009, "Message Too Big");
closeEvtCode.set(1010, "Mandatory Extension");
closeEvtCode.set(1011, "Internal Error");
closeEvtCode.set(1012, "Service Restart"); //Not working, 1001 instead
closeEvtCode.set(1013, "Try Again Later");
closeEvtCode.set(1014, "Bad Gateway");
closeEvtCode.set(1015, "TLS handshake");

//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
// Assign to global var LogSocket:
return 	{ 
	newLggr: function(realm, label, comment) {
		if (!websocket) connect();

		if( comment==undefined ) comment="";
		
		let checkedLabel = label;
		if( label==undefined || label=="" ) {
			checkedLabel = "#NOLABEL";
		} else if ( label.at(0)!="#" ) {
			checkedLabel = "#"+label; //else bug!
		}
		checkedLabel = checkedLabel.replaceAll(illglLblChrsPttrn, ""); 

		if ( filter1.has(realm+checkedLabel) ) { //DEV #6cb5e491
			return new Lggr(realm, checkedLabel, 0, "", 0);
		}

		let lggrList = realmLabel2LggrList.get(realm+checkedLabel);
		let n2;
		if ( lggrList === void null ) {
			realmLabel2LggrList.set(realm+checkedLabel, (lggrList=[]) );
			n2 = 0;
		} else {
			n2 = lggrList.length;
		}

		let newLggr = new Lggr(realm, checkedLabel, n2, comment.trim()+( checkedLabel==label ? "" : " [LABEL CORRECTED]" ), ++numLggrs);
		lggrList.push(new WeakRef(newLggr));
		
		if (Nr==null) { // E.g. websocket not yet ready
			nrBuffer.push(newLggr);
			nrBuffered = true;
		} else { // Since Nr exists, websocket works (except glitch)
			makeKnown(newLggr);
		}
		
		return newLggr;
	},
	
	closeEvtCode: closeEvtCode
};
// END of var LogSocket = ( () => { ...	<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
})();
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<



// General use utils >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

// https://stackoverflow.com/questions/11547672/how-to-stringify-event-object
LogSocket.stringify = function(object, depth=0, max_depth=5) {

	if (depth > max_depth) return 'Object';

	const obj = {};
	for (let key in object) {
		let value = object[key];
		if (value instanceof Node)
			// specify which properties you want to see from the node
			value = { id: value.id };
		else if (value instanceof Window) value = 'Window';
		else if (value instanceof Object) value = LogSocket.stringify(value, depth + 1, max_depth);
		obj[key] = value;
	}

	return depth ? obj : JSON.stringify(obj);
}
