/*****************************************************************************
 * client_cmds.js
 * Rule of thumb: longId, shortId only here
 * 
 *****************************************************************************/
// TODO delete subRealm2numLggrs ?


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// onMessage() globals and helper function
// More under #LoggerData
var lggrClrs = [];	 // #2419b81e  //FIXME rename to subRealm...
var lggrClrsNum = 0; //lggrClrs.length
var lggrClrNr = 0;  
var shortId2clr = {};
var shortId2numMsgs = {};

var numMsgsUpdtCllbck =     {notSet:true, cllbck: updateNumLogMsgs, ms: 333};
var updateNonLogNumCllbck = {notSet:true, cllbck: updateNonLogNum, ms: 333};

var lastWasNonLog = false; // 4 variables for the stripe effect....
var logMsgNum=0;
var nonLogNum=0;
var misCount=0;

function setTimeoutOnce(onceFctn) {
	onceFctn.notSet = false;
	setTimeout(
		() => { onceFctn.cllbck(); onceFctn.notSet=true; },
		onceFctn.ms 
	);
}
//---
function onMessage(evt) {
	//if (test) console.log("onMessage srvrMsg="+srvrMsg);
	// TODO if srvrMsg is empty
	// TODO 1) buffer, 2) execute (some) commands async with setTimeout

	const srvrMsg = evt.data;
	const firstChar = srvrMsg.at(0);
	const first = srvrMsg.split(" ",1)[0];
	const [T, longId, shortId, numMsgs, flag] = first.split("&",5); // leading star at T //flags: #2fa32174

	if ( firstChar == "*" ) {
		parseWrite(false);
		return;
	}
	if ( firstChar == "+" ) {
		parseWrite(true);
		return;
	}
	//---
	function parseWrite(plus) {
		if ( !longId ) {
			writeToScreen(null, null, srvrMsg.substring(first.length+1), "Server");
			return;
		}
		
		shortId2numMsgs[shortId] = numMsgs;
		if (numMsgsUpdtCllbck.notSet) setTimeoutOnce(numMsgsUpdtCllbck);
		
		if (lastWasNonLog) {
			lastWasNonLog = false;
			if ((nonLogNum%2)==1 ) { //So the stripes don't vanish when an uneven number of hidden non-log got inbetween
				logMsgNum++;
				nonLogNum++;
				misCount++;
			}
		}

		if (plus) {
			const part1 = srvrMsg.substring(first.length+1).split("\n",1)[0]; //TODO? normalize /\r?\n/ => /n
			const part2 = srvrMsg.substring(first.length+part1.length+2);
			writeToScreen(longId, T.substring(1),
				part1+" ",
				( flag=="E" ? "Error" : (flag=="B"? "Buffer" : shortId2clr[shortId] )),
				null, part2
			);
			return;
		}
		
		writeToScreen( longId, T.substring(1),
			srvrMsg.substring(first.length+1),
			(flag ? "Error" : shortId2clr[shortId])
		);
	} // END function parseWrite


	var clr = "Error"; //output background color

	if (firstChar=="%") {
	//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	//A command from server to here
		clr = "Disco"; // default color

		switch ( srvrMsg.split(" ",1)[0] ) { //TODO perhaps use object

			case "%NEW_LGGR":
				newLogger(srvrMsg.substring(10));
				break; 	//Goto CONTINUE	
		
			case "%DEL_LGGR":
				delLogger(srvrMsg.substring(10));
				break;

			case "%LOGSOCKETS":
				lgScktsEl.innerText = srvrMsg.substring(12);
				break; 

			case "%TIMERS":
				timrsEl.innerText = srvrMsg.substring(8);
				break; 
			
			case "%NANOUNIT":
				setNanoUnit(srvrMsg.substring(10));
				break;

			case "%SESSID":
				setSessID(srvrMsg.substring(8));
				break;

/*			case "%EVAL":
				var result; //of eval
				try {
					eval(srvrMsg.substring(6))
					clr = "Error"; // or Leak
					if (result) writeToScreen("", "", result="+result, "Error");
				} catch (e) {
					writeToScreen("%EVAL", "", "ERROR! message=\""+e+"\" result="+result, "Errr");
				}
				break;

			case "%EVAL2":
				try {
					eval(srvrMsg.substring(7));
					// do not writeToScreen
				} catch (e) {
					writeToScreen("%EVAL2", "", srvrMsg.substring(7), "Error")
					writeToScreen("%EVAL2", "", "ERROR message=\"" + e + "\"", "Errr");
					console.error("ERROR! message=\"" + e + "\"");
				}
				return; 
*/			
		}
				
	//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	} else { // if (firstChar=="%")
		//TODO 
		if ( srvrMsg.split(" ",1)[0].substring(1)=="ERROR" ) {
			 writeToScreen(null, null, srvrMsg, clr);
			 return;
		}
		if ( firstChar=="!" ) { clr = "Server"; }
		else if ( firstChar=="/" ) { clr = "LgSckt"; }
		
		if (showGC && "GC"==srvrMsg.split(" ",1)[0].substring(1) ) {
			writeToScreen(null, null, srvrMsg, clr);
			return;
		}

		writeToScreen("UNKNOWN MESSAGE:", "", srvrMsg, "Error");
		return;
	}
	
	//CONTINUE:
	if (!lastWasNonLog) {
		lastWasNonLog = true;
		if (updateNonLogNumCllbck.notSet) setTimeoutOnce(updateNonLogNumCllbck);
	}
	nonLogNum++;
		
	writeToScreen(null, null, srvrMsg, clr, "cmd"); //Add class "cmd" for hiding srvrMsg
}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// >>>> Here comes the output line:
var extrTxtCtr = 0;
var extrTxt = [];
//--
function writeToScreen(prfx, T, txt, clr, extraClass1, extraText) {
	//console.log( "writeToScreen("+prfx+", "+T+", "+txt+", "+clr+", "+extraClass1+", "+extraText+")" );
	const  divEl = outputDIV.cloneNode();
	divEl.className = `bg${clr}${logMsgNum%2}`;
	if (extraClass1) { divEl.classList.add(extraClass1); }
	// extraClass1 "cmd" is for hidden lines.
	// Counter nonLogNum takes care that the stripes dont get messed up.
	// TODO 1) put nonLogNum here 2) put "cmd" here 3) better naming

	if (prfx) {
		const spEl = prefixSPAN.cloneNode(true);
		spEl.prepend(document.createTextNode(prfx)); //prepend before space-textNode

		if (T) {
			const TNmbr = Number(T);
			if (TNmbr>999) {
				const ms  = Math.round(TNmbr*nanoUnit);
				const sec = Math.floor(ms/1000);
				if (sec!=0) {
					if (sec<10) { // 1 decimal place:
						T = `${sec}.${Math.round((ms-sec*1000)/100)}s`;
					} else {
						const min = Math.floor(sec/60);
						if (min!=0) {
							if (min<10) {
								T = `${min}m${sec-min*60}s`;
							} else {
								const hrs = Math.floor(min/60);
								if (hrs!=0)  {
									//TODO days - but enough for the time being :-)
									T = `${hrs}h${min-hrs*60}m`;
								} else {
									T = `${min}m`;
								}
							}
						} //sec>=10 && min==0 =>	
						else {
							T = `${sec}s`; 
						}	
					}
				} //TNmbr>999 && sec==0 =>
				else {
					T = `${ms}ms`;
				}
			} //TNmbr<=999 => keep T
			
			const TEl = msSPAN.cloneNode(true);
			TEl.prepend(T); //prepend before space-textNode
			
			//>>>> Sort out log bursts:
			if (TNmbr < burstGrpThr) {
				divEl.appendChild(document.createTextNode("│")); //UTF
				if ( lastMsNmbr >= burstGrpThr)	lastOUtpDivEl.prepend("┌");
			} else if (lastMsNmbr < burstGrpThr ) {
				lastOUtpDivEl.firstChild.textContent = "└"; // was "│" before
			}
			lastMsNmbr = TNmbr;
			lastOUtpDivEl = divEl;
			divEl.appendChild(TEl);
			//<<<<

		} //<<<< if (T)		
		divEl.appendChild(spEl);
		
	} //<<<< if (prfx) 
	divEl.appendChild(document.createTextNode(txt));

	if (extraText) { //TODO function appendExtrTxtPopup(divEl, extraText)
		spEl = extraSPAN.cloneNode(true); // #7073166e

		extrTxtCtr++;
		spEl.classList.add(`ex${extrTxtCtr}`);
		extrTxt[extrTxtCtr] = extraText; // #366de29d
		
		divEl.appendChild(spEl);
	}
	
	outputEl.appendChild(divEl);
	logMsgNum++;
}
// END output line <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


function setNanoUnit(I_nU) {
	nanoUnit =  1/I_nU;
	I_nanoUnit = Number.parseInt(I_nU);
	nanoUnitStr = "?!!?";

	//COPYPASTE docu client_cmds.js
	switch (I_nanoUnit) {
		case 10: nanoUnitStr = "&#8530;ms"; break; // 100000ns == 1/10ms
		case 100:  nanoUnitStr = "10&mu;s"; break;   // 10000ns == 10μs
	}

	//document.getElementById("millisID").lastChild.innerHTML = nanoUnitStr;
}
    
function send_comment(txt) { doSend("!REM "+txt); }

function doSend(mymessage) {
	var st = websocket.readyState;
	if (st == 1) {
		websocket.send(mymessage);
	} else {
		writeToScreen("", "", "Socket " + wsStateNames[st] + ". Message NOT sent: " + mymessage, "Errr");
	}
}

function setSessID(s) {
	if (sessionID != null && sessionID != s) {
		document.getElementById("sessID").innerHTML = "ERROR";
		throw('SessionID error');
	} else {
		sessionID = s;
		document.getElementById("sessID").innerHTML = "%"+s;
	}
}



// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Logger buerocracy >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// #LoggerData
var dltdLggrs=[];	          // #dltdLggrs.push
//var shortId2numMsgs = {};  // declared elsewhere
var subRealm2numLggrs = {};  // used when colorBySubRealm==true;
var clr2listStr = {}; // init at #5329f239
                      // clr -> "" or blank-separated list with leading and trailing blank
                      // == document.getElementById(`lggrClr${clr}ID`).innerText
//---
function newLogger(s) { // Command
	const [shortId, longId] = s.split(" ",2);
	const comment = s.substring(longId.length + shortId.length +2);
	const realm = longId.split("/", 1)[0]
	const subRealm = longId.split("#", 1)[0];
	
	
	// Determine Lggr color >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	var clr = shortId2clr[shortId];
	if (clr != null) { console.error(`newLogger(${s}) BUG: overwriting existing color`); } // but null problemo

	if (colorBySubRealm) {
		if (subRealm2numLggrs[subRealm]==undefined) subRealm2numLggrs[subRealm]=1;
		else subRealm2numLggrs[subRealm]++;

		if ( null==(clr = subRealm2clr[subRealm]) ) {
			clr = subRealm2clr[subRealm] = getNextLggrClr();
		}
		shortId2clr[shortId] = clr;
	} else {
		clr = shortId2clr[shortId] = getNextLggrClr();
	}
	
	// Insert in GUI "Logger Colors" >>>>>>>>>>>>>>>>>>>>>>
	var listStr = clr2listStr[clr]; // !=undefined by init_colors() #5329f239
	const ins = colorBySubRealm? ` ${subRealm} ` : ` ${realm}${shortId} `;
	
	if (listStr==undefined) {
		console.error("newLogger("+s+") BUG: clr="+clr+" listStr="+listStr+" ins=\""+ins+"\"");
		listStr = "";
	}
	
	if (!listStr.includes(ins)) {
		clr2listStr[clr] = listStr+ins;
		document.getElementById(`lggrClr${clr}ID`).innerText = listStr+ins;
	}
	
	// Append to GUI logger list >>>>>>>>>>>>>>>>>>>>>>>>>> 
	lggrListEl.appendChild(lggrListTR.cloneNode(true));       // HTML id="lggrTblID"
	
	let col2 = lggrListEl.lastChild.querySelector(":nth-child(2)");
	col2.id = `l${shortId}`; col2.classList.add(`bg${clr}0`);
	col2.appendChild(document.createTextNode(longId));

	lggrListEl.lastChild.querySelector(":nth-child(1)").appendChild(document.createTextNode("   ")); // 3 chars, see below. CSS!
	lggrListEl.lastChild.querySelector(":nth-child(3)").appendChild(document.createTextNode(comment));
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
}
//---
function getNextLggrClr() {
	var clr = lggrClrs[lggrClrNr];
	lggrClrNr = (++lggrClrNr)%lggrClrsNum;
	//TODO Check if already used and if an unused one is available
	return clr;
}
//---
function delLogger(s) { // GUI
	//Command `!DEL_LGGR ${lgr.shortId} ${lgr.longId}`
	const [shortId, longId] = s.split(" ",2);
	const realm = longId.split("/", 1)[0];
	const subRealm = longId.split("#", 1)[0];

	// Strike through on GUI logger list >>>>>>>>>>>>>>>>>>
	var el = document.getElementById(`l${shortId}`); 
	if (el) {
		el.style.textDecoration = "line-through";
		dltdLggrs.push(`l${shortId}`);	// #dltdLggrs.push
		document.getElementById("clDlLgrsID").style.display = "block";
	}
	else { console.error(`delLogger(${s}) ERROR1`); }
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	// Delete from GUI "Logger Colors" >>>>>>>>>>>>>>>>>>>>
	var doDelFromClr = true;
	if (colorBySubRealm) {
		if (subRealm2numLggrs[subRealm]==undefined) {console.error(`delLogger(${s}) Error or system glitch? Never heard of that logger.`);return;}
		if (subRealm2numLggrs[subRealm]==1) {
			delete subRealm2numLggrs[subRealm];
		} else {
			doDelFromClr = false;
			subRealm2numLggrs[subRealm]--;
		}
	}

	const clr = shortId2clr[shortId];
	if (clr==undefined) {
		console.error(`delLogger(${s}) BUG3`);

	} else if (doDelFromClr) {
		var listStr = clr2listStr[clr];
		const ins = colorBySubRealm? ` ${subRealm} ` : ` ${realm}${shortId} `;

		if ( listStr.indexOf(ins) != -1) {
			listStr = listStr.replace(ins, "");

			document.getElementById(`lggrClr${clr}ID`).innerText = (listStr=="") ? " -x- " : listStr;  // #7afe07e6
			clr2listStr[clr] = listStr;
		}
	}
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	delete shortId2clr[shortId];
}
//---
function cleanupDltdLggrs() { // GUI
	dltdLggrs.forEach( (id) => {
		lggrListEl.removeChild( document.getElementById(id).parentNode );
		delete shortId2numMsgs[id];
	});
	dltdLggrs=[];
	document.getElementById("clDlLgrsID").style.display = "none";
}
//---
function updateNumLogMsgs() { // GUI
	Object.entries(shortId2numMsgs).forEach( (en) => {
		if ( el = document.getElementById(`l${en[0]}`) )
			el.previousElementSibling.innerText = en[1].padStart(3," ") // 3 chars, see above. CSS!
	});
}
//---
function updateNonLogNum() { // GUI
	document.getElementById("showCmdsID").lastChild.innerText = `(${nonLogNum-misCount})`;
}
// Logger buerocracy <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< 
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

