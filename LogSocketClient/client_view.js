 /*****************************************************************************
 * client_view.js
 * 
 * Not to suggest a clear-cut MVC pattern (which only works in theory or
 * trivial apps)... client_gui.js might also be named "client_view.js".
 *****************************************************************************/
console.log("client_view.js: Hello World!");


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Output line(s) >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

// Output lines are grouped into chunk DIVs of CHUNK_SIZE lines:
//  1) to speed up browser rendering of e.g. text coloring (TODO #49d13562), line wrapping etc.
//  2) to not block logging for too long

/************************************/
/* var outputEl is the latest chunk */
/* The next output line goes there  */
/************************************/

// Example line: <div class="bgM0">┌<span class="ms">26m </span><span class="pf">Jsp/1#TEST </span>Request headers: host=localhost:8081 <span class="xtS">…▼ </span></div>

// Elements to be cloned into output line:
const outputLineDIV = document.createElement("div");
const prefixSPAN = document.createElement("span"); prefixSPAN.className="pf";
	  prefixSPAN.appendChild(document.createTextNode(" ")); // #68728172
const msSPAN = document.createElement("span"); msSPAN.className="ms"; // "Milliseconds" (actually diverse time units)
	  msSPAN.appendChild(document.createTextNode(" "));
const extraSPAN = document.createElement("span"); extraSPAN.className="xtS"; // Symbol to open/close extratext window
	  extraSPAN.appendChild(document.createTextNode("…▼ ")); // "…👁▼" Some #UTF (e.g. eye) spoil line spacing on Chrome
const extraBTTNS = getPrototypeEl("extraBttns");
function _extraBtnClick(val) {doSend();} // DEL ??
//---
var lastLogLineEl;
var	lastMsNmbr = Number.NaN;
var extraWeakMap = new WeakMap(); // Holds data for extra elements. Weakness for chunk deletion. Doc: "Once an object used as a key has been collected, its corresponding values in any WeakMap become candidates for garbage collection as well — as long as they aren't strongly referred to elsewhere." 
var currExtraButtonSPAN = new Map(); // ExtraButton cmd string -> WeakRef
//---
function logOutput(prfx, T, txt, clr, extraText, extraButton) {
	// clr: Background color "B"=Buffer "F"=Fail "S"=Server "E"=Error "L"=LogSocket "D"=Disco #7e821f32

	const divEl = outputLineDIV.cloneNode(); divEl.className = `bg${clr}${stripeCtr%2}`;
	
	const TEl = msSPAN.cloneNode(true);
	const T_nmbr = Number.parseInt(T); // 0 for T=="". (Server adds string "NaN" for "" #429f6c0a)

	//COPYPASTE #758d3edc function ClockTick2Str(T_nmbr) >>>>>>
	if (T_nmbr>999) { // false for NaN
		const ms  = Math.round(T_nmbr*I_nanoUnit);
		const sec = Math.floor(ms*0.001);
		if (sec!=0) {
			if (sec<10) { // 1 decimal place:
				TEl.prepend(`${sec}.${Math.round((ms-sec*1000)*0.01)}s`);
			} else {
				const min = Math.floor(sec/60);
				if (min!=0) {
					if (min<10) {
						TEl.prepend(`${min}m${sec-min*60}s`);
					} else {
						const hrs = Math.floor(min/60);
						if (hrs!=0)  {
							//TODO days - but enough for the time being :-)
							TEl.prepend(`${hrs}h${min-hrs*60}m`);
						} else {
							TEl.prepend(`${min}m`);
						}
					}
				} //sec>=10 && min==0 =>	
				else {
					TEl.prepend(`${sec}s`); 
				}	
			}
		} //T_nmbr>999 && sec==0 =>
		else {
			TEl.prepend(`${ms}ms`);
		}
	} //T_nmbr<=999 => keep T
	else {
		TEl.prepend(T); //prepend before space-textNode
	}
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	
	//>>>> Sort out log bursts:
	if (T_nmbr < burstGrpThr) {
		divEl.append("│"); // #UTF
		if ( lastMsNmbr >= burstGrpThr)	lastLogLineEl.prepend("┌"); // #UTF
	} else if (lastMsNmbr < burstGrpThr ) {
		lastLogLineEl.firstChild.textContent = "└"; // was "│" before  // #UTF
	}
	lastMsNmbr = T_nmbr;
	lastLogLineEl = divEl;
	//<<<<

	const prfxEl = prefixSPAN.cloneNode(true);
	prfxEl.prepend(prfx); //prepend before space-textNode

	divEl.append(TEl, prfxEl, txt);

	if ( extraText != null ) {
		if ( extraButton ) {
			// extraText is argument for button click, processed via function outputClick(ev)
			let btnSpan = currExtraButtonSPAN.get(extraText)?.deref();
			if ( btnSpan ) {
				// Disable previous buttons from same command
				disableExtraButtons(btnSpan);
			}
			btnSpan = extraBTTNS.cloneNode(true);
			currExtraButtonSPAN.set(extraText, new WeakRef(btnSpan)); // #4d938251
			extraWeakMap.set(btnSpan, extraText);
			divEl.append(btnSpan);
		} else if ( extraText != "") {
			// Add Extratext window symbol. Click processed via function outputClick(ev).
			// Text to be taken care of later (performance!) #6aa1b244 
			const xSmbEl = extraSPAN.cloneNode(true);
			extraWeakMap.set(xSmbEl, extraText);
			divEl.append(xSmbEl);
		}
	}
	
	outputEl.append(divEl);
	if ( ++stripeCtr%CHUNK_SIZE == 0 ) newOutputChunk();
}
//---
function msgOutput(txt, clr, extraClass1) {	// Client message, client/server/logsocket command
	const  divEl = outputLineDIV.cloneNode();
	divEl.className = `bg${clr}${stripeCtr%2}`;
	if (extraClass1) { divEl.classList.add(extraClass1); }

	divEl.append(txt);
	outputEl.append(divEl);
	if ( ++stripeCtr%CHUNK_SIZE == 0 ) newOutputChunk();
}
//---
function errMsgOutput(err, txt, clr) {	// onMessage parse error
	const  divEl = outputLineDIV.cloneNode();
	divEl.className = `bg${clr}${stripeCtr%2}`;
	
	const spEl = prefixSPAN.cloneNode(true);
	spEl.prepend(err); //prepend before space-textNode

	divEl.append(spEl, txt);
	outputEl.append(divEl);
	if ( ++stripeCtr%CHUNK_SIZE == 0 ) newOutputChunk();
}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< Output line(s)


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Output chunks >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

const outputChunkDIV = document.createElement("div"); //#36d00f53
outputChunkDIV.classList.add("msh"); //#4ca41226
if (TEST1) outputChunkDIV.classList.add("chnk"); // Adds thin dashed red line to bottom #5cabad3d

var rlvntBottomChunkEl=null, rlvntTopChunkEl=null;
// Visible and adjacent (i.e. most relevant) chunks to be manipulated first (color, linewrap, show/hide timing, ...).
// Chunks below rlvntBottomChunkEl and above rlvntTopChunkEl come later in async function queue,
// so the event loop might serve more important things before proceeding with the invisible:
var chunkVisibl = new Map(); // was WeakMap // CAVEAT #21849ddf WeakMap not necessary if race conditions with deleteOldestChunk are avoided.
var chunkArr = [];           // List of chunk DIVs. (A WeakMap has no ordered list of keys!)  TODO #2376c42f get rid of chunkArr
//---
function newOutputChunk() {
	outputEl = outputChunkDIV.cloneNode(false); //#36d00f53
	outputParntEl.appendChild(outputEl); // TODO #20eaba80 If performance benefit: Put output chunk element in queue, append to DOM when scrolled down or needed by search.
	chunkIntersectionObserver.observe(outputEl);
	let len = chunkArr.push(outputEl);
	if ( len > MAX_CHUNKS ) {
		if ( deleteOldestChunkAfterPause.notPending ) {
			deleteOldestChunkAfterPause.launch();
			return;
		} else if ( len > MAX_CHUNKS+MAX_CHUNKS_TOL ) {
			deleteOldestChunkAfterPause.cancel()
			deleteOldestChunk(true);
			return;
		}
	}
}
//---
const deleteOldestChunkAfterPause =  new OnceAfterPauseTask( deleteOldestChunk, 20, 0);
//---
function deleteOldestChunk(immediate) {
	if ( chunkArr[0] != outputParntEl.firstElementChild ) {
		console.error("BUG #6e7e7da4 Output chunk buerocracy lost track");
		alertRed("BUG #6e7e7da4<br>Output chunk buerocracy lost track");
	}
	
	if ( !immediate && (GUIworkQueue.len()!=0 || searchWorkQueue.len()!=0) ) { 
		deleteOldestChunkAfterPause.launch();
		return;
	}
	
	deleteOldestChunkAfterPause.cancel(); // should be superfluous
	findRelevantChunks.cancel();		  // CAVEAT #21849ddf

	const n0 = chunkArr.length;
	for ( let n = n0; n > MAX_CHUNKS-MAX_CHUNKS_TOL; n-- ) {
		let delEl = chunkArr.shift();
		chunkVisibl.delete(delEl);
		chunkIntersectionObserver.unobserve(delEl);
		delEl = null;
		outputParntEl.firstElementChild.remove();
	}	
	if (!immediate) {
		alertBlue("Capacity limit:<br>"+ ( n0-n>1 ? (n0-n)+" oldest log chunks" : "Oldest log chunk" ) +" deleted");
	} else {
		alertRed("Logs come too fast:<br>"+ ( n0-n>1 ? (n0-n)+" oldest log chunks" : "Oldest log chunk" ) +" deleted immediately");
	}

	findRelevantChunks.launch();
} 
//---	
const chunkIntersectionObserver = new IntersectionObserver( (entries) => {
	for ( const entry of entries) chunkVisibl.set(entry.target, entry.isIntersecting);
	findRelevantChunks.launch();
});
//---
const findRelevantChunks = new OnceAfterPauseTask( ()=> { // Finds rlvntBottomChunkEl, rlvntTopChunkEl
	var i=0, len=chunkArr.length;

	if ( len<=2 ) {
		rlvntBottomChunkEl = chunkArr[len-1];
		rlvntTopChunkEl = chunkArr[0];
	}
	
	for (; i<len && !chunkVisibl.get(chunkArr[i]); i++) {}  //TODO optimize
	if ( i==len ) {
		// Happens     1) with long lines and horizontal scrolling beyond chunk width
		//                FIXME #3f5a7da8  Use temp DIV with height of chunks and max text width
		// No bug when 2) Scrolled to top with logger list longer than viewport.
		console.warn("findRelevantChunks(): None visible.");
		rlvntTopChunkEl = outputParntEl.firstElementChild; rlvntBottomChunkEl = rlvntTopChunkEl.nextElementSibling; 
		return;
	}
	rlvntTopChunkEl = chunkArr[i==0?i:i-1];
	for (; i<len && chunkVisibl.get(chunkArr[i]); i++) {}
	rlvntBottomChunkEl = chunkArr[i==len?len-1:i];

	////Test for another error #4bcc717c #note8
	//for (; i<len && !chunkVisibl.get(chunkArr[i].deref()); i++) {}
	//if (i<len) {
	//	console.error("findRelevantChunks() BUG2: Set of visible chunks disconnected.", chunks2String());
	//	rlvntBottomChunkEl = null; rlvntTopChunkEl = null;
	//	return;
	//}
}, 20, 10); // Pause 20ms. If relaunched 10ms before timeout, re-pause 20ms
//--- --- ---
// test
function chunks2String() {
	return chunkArr.reduce( (acc,x)	=> acc
			+ ( chunkVisibl.get(x) === void null ?"u":(chunkVisibl.get(x)?"V":"-") )
		, ""
	);
}
// test
function rlvntChunks2String() {
	return chunkArr.reduce( (acc,x)	=> acc
			+ ( x==rlvntTopChunkEl ?"T":(x==rlvntBottomChunkEl?"B":"-") )
		, ""
	);
}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< output chunks

    



// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Logger buerocracy >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Colors and logger list
// #1a14c6d2

//var shortId2numMsgs        // declared elsewhere
var subRealm2numLggrs = new Map();  // used when colorBySubRealm==true;
var subRealm2clr = new Map();       // used when colorBySubRealm==true;
var shortId2T = new Map();
//---
function newLogger(s) { // Command
	const [T, on, shortId, longId] = s.split(" ",4); //#note0
	const comment = s.substring( T.length + longId.length + shortId.length + 5 );
	const realm = longId.split("/", 1)[0]
	const subRealm = longId.split("#", 1)[0];
	
	shortId2T.set(shortId, T);

	// Determine Lggr color >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	var clr = shortId2clr.get(shortId);
	if (clr != null) { console.error(`newLogger(${s}) BUG: overwriting existing color`); } // but null problemo

	if (colorBySubRealm) {
		let n = subRealm2numLggrs.get(subRealm);
		if ( !n ) subRealm2numLggrs.set(subRealm, 1);
		else subRealm2numLggrs.set(subRealm, ++n);

		if ( null==(clr = subRealm2clr.get(subRealm)) )  subRealm2clr.set( subRealm, clr = getNextLggrClr() );
		shortId2clr.set(shortId, clr);
	} else {
		shortId2clr.set(shortId, clr = getNextLggrClr() );
	}
	
	GUIworkQueue.push( newLogger_show, [clr, on, shortId, realm, subRealm, longId, comment] );
}
//---
var lggrClrs = [];	 // #2419b81e
var lggrClrsLength = 0;
var lggrClrNr = 0;  
//---
function getNextLggrClr() {
	var clr = lggrClrs[lggrClrNr];
	lggrClrNr = (++lggrClrNr)%lggrClrsLength;
	//TODO Check if already used and if an unused one is available
	return clr;
}

// Command `!GC_LGGR ${lgr.shortId} ${lgr.longId}`
// Here longId can have ";0" et end #66f78f43
var gcLggrs=[];  // Remember garbage collected loggers for cleanup button
//---
function gcLogger(s) {
	const [shortId, longId] = s.split(" ",2);
	const realm = longId.split("/", 1)[0];
	const subRealm = longId.split("#", 1)[0];

	gcLggrs.push(shortId);
	const clr = shortId2clr.get(shortId);
	shortId2clr.delete(shortId);
	//shortId2T.delete(shortId); still needed #52dead3b
	shortId2numMsgs.delete(shortId);

	// TODO #36e7e6a9 GUI Queue
	// Strike through on GUI logger list >>>>>>>>>>>>>>>>>>
	const el = document.getElementById(`l${shortId}`); 
	if (el) {
		el.classList.add("gc"); 
		document.getElementById("cleanLggrListBtn").style.display = "inline-block";
	}
	else { console.error(`gcLogger(${s}) ERROR1`); }
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

//	// Delete from GUI "Logger Colors" >>>>>>>>>>>>>>>>>>>>
// Why? They are still in the log!

//	var doDelFromClr = true;
//	if (colorBySubRealm) {
//		let n = subRealm2numLggrs.get(subRealm)
//		if ( !n ) {console.error(`gcLogger(${s}) Error or system glitch? Never heard of that logger.`);return;}
//		if ( subRealm2numLggrs.get(subRealm) == 1 ) {
//			subRealm2numLggrs.delete(subRealm);
//		} else {
//			doDelFromClr = false;
//			subRealm2numLggrs.set(subRealm, --n); //DEV #23e526a3 What about silenced loggers?
//		}
//	}
//
//	if (clr==undefined) {
//		console.error(`gcLogger(${s}) BUG3`);
//
//	} else if (doDelFromClr) {
//		var listStr = document.getElementById(`lggrClr${clr}ID`).textContent;
//		const ins = colorBySubRealm? ` ${subRealm} ` : ` ${realm}${shortId} `;
//
//		if ( listStr.indexOf(ins) != -1) {
//			listStr = listStr.replace(ins, "");
//
//			document.getElementById(`lggrClr${clr}ID`).textContent = (listStr=="") ? " -x- " : listStr;  // #7afe07e6
//		}
//	}
//	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
}


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Logger filter 
//  --> client_filter.js

// Logger buerocracy <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< 
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<





function writeSysTimers(s) { // TODO #1538e9e5 Let server send only new timer results, append named list in client.
	if (s.trim()!="") {
		timrsEl.textContent = s;
		timrsBtnEl.style.display = "inline-block";
	}
}


function setSessID(s) {
	s = s.split(" ",1)[0]; // Rest is server WsSession object Id
	if (sessionID != null && sessionID != s) {
		document.getElementById("sessID").innerHTML = "ERROR";
		throw('SessionID error');
	} else {
		sessionID = s;
		document.getElementById("sessID").innerHTML = s;
		alertBlue("... connected.");
	}
}


/* Notes


*/

