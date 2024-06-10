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
	// TODO Single-letter commands, when all is consolidated. Replace switch with array of lambdas.
	
	const srvrMsg = evt.data;
	const first = srvrMsg.split(" ",1)[0];
	const [T, longId, shortId, numMsgs, flag] = first.split("&",5);
	// leading "*" / "+" sticks at T  // if nonempty, flag determines special color:  #2fa32174

	// Some copy pasta spaghetti for better performance, some due to laziness
	switch ( srvrMsg.at(0) ) {
		case "*":
			//COPYPASTE #5435c2b0 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
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
				( flag ? flag : shortId2clr.get(shortId) ) //#2fa32174 
			);
			return;
			
		case "+":
			//COPYPASTE #5435c2b0 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
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
			if (flag!="P")
				logOutput( longId, T.substring(1),
					part1+" ",
					( flag ? flag : shortId2clr.get(shortId) ), //#2fa32174 
					srvrMsg.substring(first.length+part1.length+2) // part2 for ExtraTextWin
				);
			else
				logOutput( longId, T.substring(1),
					part1+" ",
					shortId2clr.get(shortId), 
					srvrMsg.substring(first.length+part1.length+2), // part2 as click arg
					true // make button
				);
			return;

		case "@": 
			// Not logged #R: hand shake, clock sync
			// TODO use "#": better position in ASCII table
			switch ( first ) {
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
			switch ( first ) {
			    //   "123456789
				case "%NEW_LGGR":
					newLogger(srvrMsg.substring(10));
					countNonLogMsg();
					msgOutput(srvrMsg, "L", "cmd"); // Add class "cmd" for hiding srvrMsg
					return;
				case "%GC_LGGR":
					GUIworkQueue.push( gcLogger, srvrMsg.substring(9));
					countNonLogMsg();
					msgOutput(srvrMsg, "L", cmdGC?"cmd":null);
					return;
				case "%SILENCED":
					// Loggers that were active, but got stopped
					GUIworkQueue.push( silencedLggrs, srvrMsg.substring(10));
					countNonLogMsg();
					msgOutput(srvrMsg, "L", "cmd"); // Add class "cmd" for hiding srvrMsg
					return;
				//   "1234567890123
				case "%FILTER1_ADD":
					filter1_add(srvrMsg.substring(13)); // already does GUIworkQueue itself
					countNonLogMsg();
					msgOutput(srvrMsg, "S", "cmd");
					return;
				case "%FIRST_T":
					firstT = Number.parseInt(srvrMsg.substring(9));
					countNonLogMsg();
					msgOutput(srvrMsg, "S", "cmd");
					return; 
				case "%TIMERS":
					setTimeout(()=>{writeSysTimers(srvrMsg.substring(8));},10);
					countNonLogMsg();
					msgOutput(srvrMsg, "S", "cmd");
					return; 			
				case "%CLOCK":
					GUIworkQueue.push( initClock, srvrMsg.substring(7).split(" ",2));
					countNonLogMsg();
					msgOutput(srvrMsg, "S", "cmd");
					return;
				case "%SESSID":
					GUIworkQueue.push( setSessID, srvrMsg.substring(8));
					countNonLogMsg();
					msgOutput(srvrMsg, "S", "cmd");
					return;
				case "%T0CORR":
					GUIworkQueue.push( T0corrFromSrvr, srvrMsg.substring(8).split(" ",6));
					countNonLogMsg();
					msgOutput(srvrMsg, "S", "cmd");
					return;
				case "%%":
					// general feedback/comment from other client:
					msgOutput(srvrMsg.substring(1), "D");
					return;

				case "%!ERROR":
				case "%/ERROR":
					msgOutput(srvrMsg.substring(1), "E");
					return;
				case "%/DIAGN":
					if (TEST1) msgOutput(srvrMsg.substring(1), "L");
					return;			
				case "%!DIAGN":
					if (TEST1) msgOutput(srvrMsg.substring(1), "S");
					return;			
				//   "1234567890123
				case "%!ALERT_B": //Short extra message in the alert window
				case "%/ALERT_B": 
					alertBlue(srvrMsg.substring(10)); //TODO? Scroll to log
					return;
				case "%!ALERT_R":
				case "%/ALERT_R":
					alertRed(srvrMsg.substring(10));
					return;
							
				case "%!": // general feedback:
				case "%/":
					msgOutput(srvrMsg.substring(2), srvrMsg.at(1)=="/"?"L":"S");
					return;
					
				default:
					errMsgOutput("UNKNOWN COMMAND", srvrMsg, "E");
					return;
			} //switch ( first )

		default:
			errMsgOutput("PREFIX CHAR ERROR", srvrMsg, "E");
			return;
	} // END switch ( srvrMsg.at(0) )
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
