/*****************************************************************************
 * client_cmds.js
 * 
 * Action starts here with sorting out server commands from Lggr messages in
 * the websocket onMessage callback - the receiving end of LogSocketServer.java 
 *****************************************************************************/
console.log("client_cmds.js: Hello World!");


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// function onMessage(evt)  >>>>>>>>>>
//---
// onMessage globals:
var shortId2clr = new Map();
var shortId2numMsgs = new Map();

const numMsgsUpdt = new OnceAfterTimeoutTask(updateNumMsgs, 555);

var lastWasNonLog = false; // 4 variables for the stripe effect - and still not perfect
var stripeCtr=0;
var nonLogNum=0;
var misCount=0;
//... more at #1a14c6d2 Logger buerocracy
//---
function onMessage(evt) {
	// TODO buffer, execute (some) commands async

	const srvrMsg = evt.data;
	const first = srvrMsg.split(" ",1)[0];
	const [T, longId, shortId, numMsgs, flag] = first.split("&",5);
	// leading "*" / "+" sticks at T //flags: #2fa32174

	switch ( srvrMsg.at(0) ) {
		case "*":
			//COPYPASTE #5435c2b0 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
			if ( !longId ) {
				msgOutput(srvrMsg.substring(first.length+1), "S");
				return;
			}
			
			shortId2numMsgs.set(shortId, numMsgs);
			if (numMsgsUpdt.notPending) numMsgsUpdt.launch();
			
			if (lastWasNonLog) {
				lastWasNonLog = false;
				if ((nonLogNum%2)==1 ) { //So the stripes don't vanish when an uneven number of hidden non-log got inbetween
					stripeCtr++; nonLogNum++; misCount++;
				}
			}
			// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
			logOutput( longId, T.substring(1),
				srvrMsg.substring(first.length+1),
				( flag ? flag : shortId2clr.get(shortId) )
			);
			return;
			
		case "+":
			//COPYPASTE #5435c2b0 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
			if ( !longId ) {
				msgOutput(srvrMsg.substring(first.length+1), "S");
				return;
			}
			
			shortId2numMsgs.set(shortId, numMsgs);
			if (numMsgsUpdt.notPending) numMsgsUpdt.launch();
			
			if (lastWasNonLog) {
				lastWasNonLog = false;
				if ((nonLogNum%2)==1 ) { //So the stripes don't vanish when an uneven number of hidden non-log got inbetween
					stripeCtr++; nonLogNum++; misCount++;
				}
			}
			// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
			const part1 = srvrMsg.substring(first.length+1).split("\n",1)[0]; //TODO? normalize /\r?\n/ => /n
			logOutput( longId, T.substring(1),
				part1+" ",
				( flag ? flag : shortId2clr.get(shortId) ),
				srvrMsg.substring(first.length+part1.length+2) // part2
			);
			return;

		case "@":
			// Not logged #R: hand shake, clock sync
			switch ( srvrMsg.split(" ",1)[0] ) {
				case "@READY?":
					GUIworkQueue.push( doSend, "!READY" );
					return; // #R<<<<
					
				// Clock sync from client side >>>>>>>>>>>>>>>>>>>>>>
				case "@TING":
					if (tong==null) tong = T_ms();
					           else tong1 = T_ms();
					doSend("!TONG");
					return; // #R<<<<
					
				case "@TONG":
					ting1 = T_ms();
					doSend("!TING");
					return; // #R<<<<

				case "@RSLT":
					Clock_SyncDaemon.receiveTTResult(srvrMsg.substring(6));
					return; // #R<<<<
					
				// Clock sync from server side >>>>>>>>>>>>>>>>>>>>>>
				case "@SING":
					if (tong===null) {
						tong = T_ms();
						doSend("!SONG");
					} else {
						tong1 = T_ms();
						ting = T_ms();
						doSend("!SING");
					}
					return; // #R<<<<
					
				case "@SONG":
					if (ting1==null) {
						ting1 = T_ms();
						doSend("!SING");
					} else {
						doSend("!RSLT "+tong+" "+tong1+" "+ting+" "+ting1);
						ting = null; ting1 = null; tong = null; tong1 = null; 

					}
					return; // #R<<<<
			}
			
		case "%":
			switch ( srvrMsg.split(" ",1)[0] ) {
				// TODO #1b29f250 If better performance: Maps of cmds, use optional chaining (?.)
			    //   "123456789
				case "%NEW_LGGR":
					newLogger(srvrMsg.substring(10));
					break;
				case "%GC_LGGR":
					GUIworkQueue.push( gcLogger, srvrMsg.substring(9));
					break;
				case "%STOPPED":
					// Loggers that were active, but got stopped
					GUIworkQueue.push( stppdLggrs, srvrMsg.substring(9));
					break;
				//   "1234567890123
				case "%FILTER1_ADD":
					filter1_add(srvrMsg.substring(13)); // already does GUIworkQueue itself
					break;
				case "%FIRST_T":
					firstT = Number.parseInt(srvrMsg.substring(9));
					break; 
				case "%TIMERS":
					setTimeout(()=>{writeSysTimers(srvrMsg.substring(8));},10);
					break; 			
				case "%CLOCK":
					GUIworkQueue.push( initClock, srvrMsg.substring(7).split(" ",2));
					break;
				case "%SESSID":
					GUIworkQueue.push( setSessID, srvrMsg.substring(8));
					break;
				case "%T0CORR":
					GUIworkQueue.push( T0corrFromSrvr, srvrMsg.substring(8).split(" ",6));
					break;
				
				default:
					errMsgOutput("UNKNOWN CLIENT COMMAND", srvrMsg, "E");
					return;
			}
			countNonLogMsg();
			msgOutput(srvrMsg, "S", "cmd"); // Add class "cmd" for hiding srvrMsg
			return;
			
		case "!":	
			break;
		case "/":
			break;
		default:
			errMsgOutput("PREFIX CHAR ERROR", srvrMsg, "E");
			return;
	} // END switch ( srvrMsg.at(0) )

	switch ( srvrMsg.split(" ",1)[0].substring(1 )) {
		case "GC":
			if ( logGC ) {
				//countNonLogMsg();
				msgOutput(srvrMsg, "L");
			}
			return;
			
		case "ERROR":
			msgOutput(srvrMsg, "E");
			return;

		case "ALERT_B": //Short extra message in the alert window
			alertBlue(srvrMsg); //TODO? Scroll to log
			return;
		case "ALERT_R":
			alertRed(srvrMsg);
			return;
			
		case "": // general feedback:
			msgOutput(srvrMsg.substring(2), srvrMsg.at(0)=="/"?"L":"S");
			return;
	}
	errMsgOutput("UNKNOWN COMMAND", srvrMsg, "E");

	return;
}
//---
function countNonLogMsg() {
	if (!lastWasNonLog) {
		lastWasNonLog = true;
		if (numMsgsUpdt.notPending) numMsgsUpdt.launch();
	}
	nonLogNum++;
}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< function onMessage(evt)


function doSend(mymessage) {
	if (websocket.readyState == 1) {
		websocket.send(mymessage);
		return true;
	} else {
		alertRed("Socket " + wsStateNames[websocket.readyState] + ".");
		msgOutput("Socket " + wsStateNames[websocket.readyState] + ". Message NOT sent: " + mymessage, "F");
		return false;
	}
}


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
var websocket;
const wsStateNames = ["CONNECTING", "OPEN", "CLOSING", "CLOSED"];
var connectionTried = 0;
//---
function onOpen(evt) {
	connectionTried = 0;
	msgOutput(`WebSocket OPEN: ${(evt.data?evt.data+" ":"")}wsUri=${wsUri}`, "D");
	setTimeout(	()=>{ websocket.send(`!HELLO`);}, 50);
}
//---
function onError(evt) {
	if (connectionTried) {
		alertRed(`WebSocketServer: No reply.<br>Trying again (${++connectionTried})`);
		if (connectionTried>2) return; //Don't bother log anymore
	}
	msgOutput(`WebSocket ERROR: evt.data=${evt.data} wsUri=${wsUri}`, "E");
}
//---
function onClose(evt) {
	if (connectionTried || evt.code == 1006 ) {
		if (connectionTried>50) {
			connectionTried=0;
			alertRed("WebSocketServer: No reply.<br>Giving up.<br>Try reloading page later.");
		} else {
			setTimeout(	connect, 1000);
			return;
		}
	}
	msgOutput(`WebSocket CLOSE: code=${evt.code}, reason=${evt.reason}, data=${evt.data}`, "D");
}
//---
function connect() {
	alertBlue("Connecting...");
	//alertBlue("... connected.") sent by setSessID(...) //sessionID!=null indicates a clean connection

	try {
		websocket = new WebSocket(wsUri);
	} catch (ignore) {} //onError does job
	if (!connectionTried) connectionTried = 1;
	websocket.onopen = onOpen;
	websocket.onclose = onClose;
	websocket.onerror = onError;
	websocket.onmessage = onMessage;
}
