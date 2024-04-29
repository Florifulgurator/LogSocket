/*****************************************************************************
 * client_gui.js
 *
 * Mostly concerned with user interactions. 
 *****************************************************************************/

console.log("client_gui.js: Hello World! 10");


var GUIworkQueue = MakeAsyncQueue("GUIworkQueue");
// CAVEAT #654a9561 Make sure GUIworkQueue does not do things that can interfere with searchWorkQueue


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
//  Responsiveness: Do things first with visible chunks and direct neighbors.
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

function launchHardGUIwork(task, disabledClickEl, taskDescr) {
	const now = T_ms(); //DIAGN
	updateGUIsync();
	// #34b1c95d not total COPYPASTE:
	GUIworkQueue.push( awaitPendingTaskAsynFn(deleteOldestChunkAfterPause) );
	GUIworkQueue.push( awaitPendingTaskAsynFn(findRelevantChunks) );
	GUIworkQueue.push( task, [rlvntBottomChunkEl, rlvntTopChunkEl], 10 );
	if (outputParntEl.firstElementChild != rlvntTopChunkEl)    GUIworkQueue.push( task, [rlvntTopChunkEl.previousElementSibling, outputParntEl.firstElementChild] );
	if (outputParntEl.lastElementChild != rlvntBottomChunkEl)  GUIworkQueue.push( task, [outputParntEl.lastElementChild,   rlvntBottomChunkEl.nextElementSibling] );

	GUIworkQueue.push( ()=>	disabledClickEl.disabled = false);
	GUIworkQueue.push( (t)=>console.log(taskDescr+" in "+round(T_ms()-t)+"ms (except browser rendering)"), now);  //DIAGN COPYPASTE #499503cc
}



//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Some General User Events
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

function updateGUIsync() { //What can be done quickly
	hideContextMenus_.launch();
	placeExtraTxtWin_.launch();
}
const placeExtraTxtWin_ = new OnceThenPauseTask(placeExtraTxtWin, 200); //don't repeat within 200ms
const hideContextMenus_ = new OnceThenPauseTask(hideContextMenus, 200);


function onResize() { updateGUIsync(); }
(new ResizeObserver(updateGUIsync)).observe(loggersEl);

function onScroll() { updateGUIsync(); }

function onSelect(ev) {
	var txt;
	if ( (txt=window.getSelection().toString().trim()).length>1 && txt.length<=MAX_SRCHTXTLEN ) {
		searchWorkQueue.push(highlight, txt, 50);
		searchEl.value = txt;
	} else {
		searchEl.value = "";
	}
}

function outputClick(ev) {
	hideContextMenus();
	const el = ev.target;
	//if ( el.classList.contains('hl') ) { removeColor(el); return; } // #41f603b5 CSS hl removed
	if ( el.className.indexOf("hl")!=-1 ) {		                      // this should suffice...
		removeColor(el);
		return;
	} 
	if ( el.classList.contains('xtS') ) {
		toggleExtraTxtWin(el);
		return;
	}
	if ( el.classList.contains('smallbtn') ) {
		const btnSpan = el.parentNode;
		if ( btnSpan.classList.contains('xtB') ) {
			btnSpan.classList.add("pn"); //switch off buttons
			for ( let el=btnSpan.firstElementChild ; el; el=el.nextElementSibling ) el.disabled=true;
			const cmd = extraWeakMap.get(btnSpan);
			if ( cmd!=null )
				doSend(`${cmd} ${el.value}`);
			else
				alertRed("BUG #4da5410b");
		}
		return;
	}
}

function outputCtxtMenu(ev) {
	hideContextMenus();
	const el = ev.target;
	if ( el.classList.contains('xtW') ) { hideExtraTxtWin(); return; }

	if ( el.className.indexOf("hl")!=-1)  { contextMenu1(ev); return; }
	if ( el.classList.contains('pf') || el.classList.contains('lg') ) { contextMenu3(ev); return; }
	if ( el.classList.contains('xtS') ) { toggleExtraTxtWin(el); return; }
	
}


// Resize >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// * onResize() doesn't fire when scrollbars appear
// * When you run out of patience with CSS
const widthTestEl = document.getElementById("wdthTst");
var outputWidth=100;
//---
const afterWidthTestElResize = new OnceAfterTimeoutTask( ()=>{
	outputWidth = Math.floor(1+ widthTestEl.getBoundingClientRect().width);
	loggersEl.style.maxWidth = Math.floor(outputWidth-2*EMP)+"px";  // #6e83e5b3
	updateGUIsync(); // doesn't hurt...
},33);
//---
(new ResizeObserver(()=>{afterWidthTestElResize.launch()})).observe(widthTestEl); // Cf. error msg. in OnceAfterPauseTask defn.


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// ... Functions directly attached to elements in HTML code are prepended by _
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

function _clrsButtonClick() { toggleDrpdwnDspl(clrsDivEl); }
function _menuButtonClick() { toggleDrpdwnDspl(menuDivEl); }
function _lggrsButtonClick() { loggersEl.classList.add("frame2Tr"); toggleDrpdwnDspl(loggersEl); } 
//---
function toggleDrpdwnDspl(winEl) {
	updateGUIsync(); 
	if ( winEl.style.display == "none" ) {
		winEl.style.display="block";
		setTimeout(()=>{winEl.style.opacity=1;}, 1); // #25035ec2 opacity transition and display change problem
		winEl.symEl.innerHTML="&#9746;";
	} else {
		winEl.style.opacity=0;
		winEl.style.display="none";
		winEl.symEl.innerHTML="&#9776;";	
	}
}




//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// "Menu" dropdown
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

function _search()      { if( searchEl.value.length>1 ) highlight(searchEl.value.substring(0,MAX_SRCHTXTLEN)); }

function _garbageColl()    { doSend("!GC"); if(window.gc) window.gc(); }
function _comment()        { doSend("%% Client "+sessionID+" says: "+textID.value); }
function _pingLogSockets() { doSend("/PING"); }
function _caseSensi()      { caseSensitive = document.getElementById("caseID").checked; }
function _cmdGC()          { cmdGC = document.getElementById("cmdGCID").checked; }

function _synchClocks() {
	document.getElementById("syClSvBtn").disabled=true; // #2ebbea73
	GUIworkQueue.push(Clock_SyncDaemon.T0correction, null, 50);
} 
function _synchClocksS() {
	document.getElementById("syClClBtn").disabled=true; // #2ebbea73
	doSend("!SYNC"); 
}


function _showCmds() { // TODO #  Hard work machinery maybe not needed here, as there aren't many cmds
	const chkd = document.getElementById("showCmdsID").firstChild.checked;
	cssRule_cmd.style.display =	chkd ? "block" : "none";
	updateGUIsync();
}

function pasteSearch(ev) {
	let txt = ev.clipboardData.getData("text");
	if ( (txt=txt.trim()).length) {
		searchEl.value = txt.substring(0,MAX_SRCHTXTLEN);
		_search();
	}
}

//-------------------------------------------------------------
function _millis() {
	const mmm = document.getElementById("millisID").firstChild;
	mmm.disabled = true;
	if (mmm.checked) {
		outputChunkDIV.classList.remove("msh");
		launchHardGUIwork( showTiming, mmm, "Show timing on");
	} else {
		outputChunkDIV.classList.add("msh");
		launchHardGUIwork( hideTiming, mmm, "Show timing off");
	}
}
//---
function showTiming([firstChunk, lastChunk]) {
	for ( let chunkEl=firstChunk; chunkEl; chunkEl = (chunkEl==lastChunk)?null:chunkEl.previousElementSibling )
		chunkEl.classList.remove("msh"); // msh == "milliseconds hidden"
}
//---
function hideTiming([firstChunk, lastChunk]) {
	for ( let chunkEl=firstChunk; chunkEl; chunkEl = (chunkEl==lastChunk)?null:chunkEl.previousElementSibling )
		chunkEl.classList.add("msh");
}

//-------------------------------------------------------------
function _linewrap() {
	const lll = document.getElementById("linewrapID").firstChild;
	lll.disabled = true;
	if (lll.checked) {
		outputChunkDIV.style.whiteSpace = "pre-wrap";
		outputChunkDIV.style.wordBreak = "break-all";
		launchHardGUIwork( linewrapChunks, lll, "Linewrap on");
	} else {
		outputChunkDIV.style.whiteSpace = "pre"; // no linewrap #7d185fc2 at init
		launchHardGUIwork( unLinewrapChunks, lll, "Linewrap off");
	}
}
//---
function linewrapChunks([first, last]) {
	for ( let chunkEl=first; chunkEl; chunkEl = (chunkEl==last)?null:chunkEl.previousElementSibling ) {
		// For TESTing performance. Then:
		// TODO #6103a6ee 1) use CSS class .lnw,  2) unify _millis() and _linewrap() 
		chunkEl.style.whiteSpace = "pre-wrap";
		chunkEl.style.wordBreak = "break-all";
	}
}
//---
function unLinewrapChunks([first, last]) {
	for ( let chunkEl=first; chunkEl; chunkEl = (chunkEl==last)?null:chunkEl.previousElementSibling )
		chunkEl.style.whiteSpace = "pre"; // no linewrap #7d185fc2 at init
}

// TODO #6103a6ee Unify _millis() and _linewrap() 
//-------------------------------------------------------


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// "Colors" dropdown
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

// Highlight color selection
var hlColrsNum=0, hlxColrsNum=0; // set in init() 
var userHighlColr="";    // If integer string, mark selection with user color `hlx${userHighlColr}`
var highlightCounter=0;  // ... else use automatic color `hl${highlightCounter}`
//---
function hlColrsClick(ev) {
	const clickedEl = ev.target;
	if ( clickedEl.classList.contains('boxed') ) { return; }

	const cln = clickedEl.className;
	var swtch = 0;
	if (cln.match(/^hl\d$/) ) { swtch = 1; }
	else if (cln.match(/^hlx\d$/) ) { swtch = 2; }

	if (swtch == 0) return;
	
	for (var i=0; i<hlColrsNum; i++) { document.getElementById("hl"+i+"ID").classList.remove("boxed"); }
	for (var i=0; i<hlxColrsNum; i++) { document.getElementById("hlx"+i+"ID").classList.remove("boxed"); }

	if (swtch == 1) {
		highlightCounter = parseInt(cln.substring(2));
		document.getElementById("hl"+highlightCounter+"ID").classList.add("boxed");
		userHighlColr = "";
		return;
	}
	// swtch==2
	userHighlColr = cln.substring(3);
	document.getElementById("hlx"+userHighlColr+"ID").classList.add("boxed");
}



//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// "Loggers" dropdown special effect
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

// At first, the Loggers window (loggersEl) is displayed below the header with border CSS frame1.
//    -> The header has a "LogSocket ~ Client ~" title element (titleEl) at top left.
//    -> The "Loggers" button (lggrsButtonEl) is hidden, as it would collide with titleEl.
// #A When loggersEl gets scrolled out of viewport,
//    1) remove it from HTML flow, set display:none and make it CSS position:fixed (posLT).
//    2) display lggrsButtonEl, so loggersEl gets displayed/hidden upon click, with border CSS frame2 similar to the other dropdown windows
// #B When lggrsButtonEl collides with titleEl (scrolling back up again)
//    1) display loggersEl in flow below header, as in the beginning
//    2) hide lggrsButtonEl
//
var lggrsButtonEl = document.getElementById("lggrsButtonID");
var titleEl = document.getElementById("titleID");

(new IntersectionObserver( (entries, observer)=>{
	const entry = entries[0];

	if ( entry.target == loggersEl && !entry.isIntersecting ) { //#A
		observer.unobserve(loggersEl);

		lggrsButtonEl.style.display="inline";
		loggersEl.symEl.innerHTML="&#9776;"; // Seriously. Button symbol stored at loggersEl.
		loggersEl.style.maxHeight = Math.floor(visualViewport.height - lggrsButtonEl.getBoundingClientRect().bottom -25)+"px";; // COPYPASTE #72736288
		loggersEl.style.display="none";

		if (loggersEl.posLTdone === void null) {
			cssRule_posLT.style.top = Math.floor(lggrsButtonEl.getBoundingClientRect().bottom +5)+"px"; // #3aedf5bc  COPYPASTE #72736288
			loggersEl.posLTdone=1;
		}
		loggersEl.classList.remove("frame1");
		loggersEl.classList.add("posLT", "frame2");
		loggersEl.style.opacity=0;

		observer.observe(titleEl);
		return;
	}
	if ( entry.target == titleEl && entry.isIntersecting ) { //#B
		observer.unobserve(titleEl);
		
		lggrsButtonEl.style.display="none"; // CSS opacity transition not working! (parent position fixed ?)
		loggersEl.classList.remove("posLT", "frame2", "frame2Tr");
		loggersEl.classList.add("frame1");
		loggersEl.style.opacity=0;
		setTimeout(()=>{loggersEl.style.opacity=1;}, 1);
		loggersEl.style.maxHeight = null;
		loggersEl.style.display="inline-block";
		
		observer.observe(loggersEl);
		return;
	}
}
)).observe(loggersEl);
//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< "Loggers" dropdown special effect


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Logger list 
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

function _cleanupDltdLggrs() {
	gcLggrs.forEach( (shortId) => {
		lggrListEl.removeChild( document.getElementById(`l${shortId}`).parentNode );
		shortId2T.delete(shortId);
	});
	gcLggrs=[];  // TODO: forget gcLggrs, clean up by CSS class gc, ign
	
	lastListedShortId = "";
	for ( let trEl=lggrListEl.firstElementChild; trEl; trEl=trEl.nextElementSibling ) {
		shortId = trEl.querySelector(":nth-child(2)").id.substring(1);
		trEl.querySelector(":nth-child(3)").textContent
			= ClockTick2Str( lastListedShortId=="" ? shortId2T.get(shortId)-firstT : shortId2T.get(shortId)-shortId2T.get(lastListedShortId) );
		lastListedShortId = shortId;			
	}

	document.getElementById("cleanLggrListBtn").style.display = "none";
	updateGUIsync();
}
//---
function _cleanupTimers() {
	timrsEl.textContent=" -x-";
	timrsBtnEl.style.display = "none";
}

function updateNumMsgs() {
	shortId2numMsgs.forEach( (value, key) => {
		if ( el = document.getElementById(`l${key}`) )
			el.previousElementSibling.textContent = value;
	});
	// document.getElementById("logMsgNumID").textContent = stripeCtr-misCount-nonLogNum;
	document.getElementById("showCmdsID").lastChild.textContent = `(${nonLogNum-misCount})`;

}


// GUI part of function newLogger(s) / %NEW_LGGR
var lastListedShortId = "";
const lggrListTR = document.createElement("tr");
	  lggrListTR.innerHTML = "<td style='text-align:right;padding-right:0.3rem;'></td><td class='lg'></td><td class='ms' style='padding:0 0.3rem 0 0.2rem;'></td><td></td>";
//---
function newLogger_show([clr, on, shortId, realm,  subRealm, longId, comment]) {
	
	if (on) { // Insert in "Logger Colors" >>>>>>>>>>>>>>>>
		let listStr = document.getElementById(`lggrClr${clr}ID`).textContent;
		const ins = colorBySubRealm? ` ${subRealm} ` : ` ${realm}${shortId} `;
			
		if (!listStr.includes(ins)) {
			if (listStr== " --- " || listStr== " -x- ") listStr = ""; // #7afe07e6
			document.getElementById(`lggrClr${clr}ID`).textContent = listStr+ins;
		}
	}
	
	// Append to logger list >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	let newTR = lggrListTR.cloneNode(true);
	let col2 = newTR.querySelector(":nth-child(2)");
	col2.id = `l${shortId}`; col2.classList.add(`bg${clr}0`);
	if (!on) col2.classList.add("sil");  //DOCU #2540b06f  Logger Status
	col2.setAttribute("title", `Short ID: ${shortId}`);
	col2.appendChild(document.createTextNode(longId));

	newTR.querySelector(":nth-child(1)").appendChild(document.createTextNode(""));
	newTR.querySelector(":nth-child(3)").appendChild(document.createTextNode(
		ClockTick2Str(lastListedShortId=="" ? shortId2T.get(shortId)-firstT : shortId2T.get(shortId)-shortId2T.get(lastListedShortId)))  // #52dead3b lastListedShortId could already be GCed
	);
	newTR.querySelector(":nth-child(4)").appendChild(document.createTextNode(comment));

	lastListedShortId = shortId;
	lggrListEl.appendChild(newTR);
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
}


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Logger filter list 
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>







var filter1ActiveRow = null;
//---
function filter1_changeEvt(ev) {
	filter1ActiveRow = ev.target.parentNode.parentNode;

	// So the button doesn't slip away:
	ev.target.style.width  = Math.round(ev.target.getBoundingClientRect().width)+"px";
	
	// New and original selected values:
	let newRealm = filter1ActiveRow.querySelector("select.r").value.trim();
	let newLabel = filter1ActiveRow.querySelector("select.l").value.trim();
	let origRealm = filter1ActiveRow.querySelector("select.r option[orig]").textContent.trim();
	let origLabel = filter1ActiveRow.querySelector("select.l option[orig]").textContent.trim();

console.log(origRealm,origLabel);

	// Add/remove buttons:
	let btnSpan = filter1ActiveRow.querySelector("span.b");

	if ( newRealm==origRealm && newLabel==origLabel) {
		// Nothing to do: remove all buttons
		//DEV	btnSpan.innerHTML = "";
		return;
	}

	if ( null == btnSpan.querySelector("input.cncl") ) //DEV btnSpan.appendChild(fltrCnclBTN.cloneNode());

	if (newRealm=="*" && newLabel=="*") {
		//DEV		btnSpan.prepend(fltrRemvBTN.cloneNode());
	}
	else //DEV	btnSpan.querySelector("input.rmv")?.remove();
	
	
	// Pointer events only on row: (until cancel)
	fltrListEl.classList.add("pn");       // pointer-events: none;
	filter1ActiveRow.classList.add("pa"); // pointer-events: all;
	

		
//		doSend(`!FILTER1_REMOVE ${origRealm}${origLabel}`);
//		alertBlue(`Removed filter rule:<br/>${origRealm}${origLabel}<br/>TODO: Resurrect filtered loggers.`);
//		return;
}
//---
function _filter1CnclBtnClick() { // Cancel button: Revert to option[orig], allow pointer events elsewhere again
	filter1ActiveRow.querySelector("span.b").innerHTML = "";
	fltrListEl.classList.remove("pn");       // pointer-events: none;
	filter1ActiveRow.classList.remove("pa"); // pointer-events: all;
}

function _filter1RmvBtnClick() { console.log("_filter1RmvBtnClick");
}






//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< Logger functions




//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
//  Extratext Window, extraButtons
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


function disableExtraButtons(btnSpan) {
	GUIworkQueue.push( () => {
		btnSpan.classList.add("pn");
		for ( let el=btnSpan.firstElementChild ; el; el=el.nextElementSibling ) el.disabled=true;
	});
}
//TODO #4d938251

// Only one Extratext Window open at a time
var currXtrTxtWinSymbEl = null; // Click-symbol of currently open extratext window
var xtrTxtWinFullWidth = 0;
const extraTxtWinDIV = document.createElement("div"); extraTxtWinDIV.className="xtW";
//---
function hideExtraTxtWin(notReally) {
	if (currXtrTxtWinSymbEl) {
		let xtWinEl = currXtrTxtWinSymbEl.firstElementChild; 
		currXtrTxtWinSymbEl.firstChild.textContent = "…▼ "; // #UTF
		xtWinEl.style.opacity = 0;
		xtWinEl.style.display = "none";
		if (!notReally) currXtrTxtWinSymbEl = null;
	}
}
//---
function toggleExtraTxtWin(clickedSymEl) { // caller: outputClick
	var xtWinEl;

	if ( currXtrTxtWinSymbEl==clickedSymEl ) {
		hideExtraTxtWin();
		return;
	} // seriously...:
	hideExtraTxtWin();
	
	currXtrTxtWinSymbEl = clickedSymEl;
	currXtrTxtWinSymbEl.firstChild.textContent = "…☒"; // #UTF
	const bgClass = clickedSymEl.parentNode.className.split(" ").find( (c) => c.match(/^bg[A-Z]\d$/) );

	xtWinEl = currXtrTxtWinSymbEl.firstElementChild;

	if (!xtWinEl) { //#6aa1b244
		xtWinEl = extraTxtWinDIV.cloneNode();
		xtWinEl.className = `xtW ${bgClass}`;


		xtWinEl.appendChild(document.createTextNode(extraWeakMap.get(clickedSymEl)));
		currXtrTxtWinSymbEl.append(xtWinEl); 

		xtWinEl.style.display = "inline-block"; //Dimensions valid only after display!
		// Now we use extraWeakMap to store window width.
		extraWeakMap.delete(clickedSymEl);
		extraWeakMap.set( xtWinEl, xtWinEl.getBoundingClientRect().width ); // it seriously has decimal places
	}
	
	placeExtraTxtWin(xtWinEl);
}
//---
function placeExtraTxtWin(xtWinEl) { //FIXME #6660f095 Classic: Avoid horiz scrollbars when vertical scrollbars from manual vertical resize appear. Use Resizeobserver.
	if (!xtWinEl ) { // called by updateGUIsync
		if (!currXtrTxtWinSymbEl) return;
		else xtWinEl = currXtrTxtWinSymbEl.firstElementChild;
	}
	
	xtWinEl.style.top = Math.round(LHP)+"px";
	
	setTimeout(()=>{xtWinEl.style.opacity = 1;}, 1); // #25035ec2 opacity transition and display change problem
	xtWinEl.style.display = "inline-block";

	const xtrTxtWinFullWidth = extraWeakMap.get(xtWinEl); //might have changed meanwhile

	// output area left margin in document coordinates:
	const lll = currXtrTxtWinSymbEl.parentNode.getBoundingClientRect().left
	            + window.scrollX; // same as visualViewport.pageLeft
	var symbRight = currXtrTxtWinSymbEl.getBoundingClientRect().right + window.scrollX;
	// WARNING: ...El.style.right is something completely different
	// For the complete confusion see CSS file!!
	const maxWidth = visualViewport.width - lll - 2*MFWP;

	// If possible, #A align right sides of click-symbol and full-size ExtraTxtWin.
	// Else, #B align left of ExtraTxtWin and left of output area (with a margin of 1MFWP),
	// and if necessary, #C reduce ExtraTxtWin width such that right side aligns with viewport margin.
	
	if ( symbRight > xtrTxtWinFullWidth +lll +2*MFWP) {
		// #A
		xtWinEl.style.width = Math.floor(xtrTxtWinFullWidth+1)+"px";
		xtWinEl.style.right = "0px";
		
	} else { // #B
		if ( xtrTxtWinFullWidth > maxWidth + window.scrollX - MFWP-lll) {
			// #C
			xtWinEl.style.width = Math.floor(maxWidth+ window.scrollX -MFWP-lll+1)+"px";
			xtWinEl.style.right = Math.floor(symbRight-window.scrollX- maxWidth-lll)+"px";
			
		} else { // #D
			xtWinEl.style.width = Math.floor(xtrTxtWinFullWidth+1)+"px";
			xtWinEl.style.right = Math.floor(symbRight-xtrTxtWinFullWidth-lll-2*MFWP+1)+"px";
		}
	}
}
//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< Extratext Window


	
	

//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Context menu on colored search results

function contextMenu1(ev) {
	const clickedEl = ev.target;
	
	cntxtMenuData.txt = clickedEl.textContent;
	cntxtMenuData.targetEl = clickedEl;
		
	placePopupMenu(cntxtMenu1El, ev.clientX, ev.clientY, clickedEl);
}
//---
function _cntxtMn_Copy() {
	navigator.clipboard.writeText(cntxtMenuData.txt);
	hideContextMenus();
}
//---
function _cntxtMn_RemvClr() {
	if (!cntxtMenuData.targetEl)                            {console.error("_cntxtMn_RemvClr() BUG #59b350aa1 no targetEl");return;}
	if (cntxtMenuData.targetEl.className.indexOf("hl")==-1) {console.error("_cntxtMn_RemvClr() BUG #59b350aa2 wrong classList="+cntxtMenuData.targetEl.classList);return;} // #41f603b5 
	removeColor(cntxtMenuData.targetEl);
	hideContextMenus();
}
//---
/// TODO


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Context menu on logger IDs

// function contextMenu3(ev) --> client_filter.js







//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Client message/alert window
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


function alertRed(html)   {clientMsg(html,"clntMsgRed",3000); console.error("alertRed:", html);}
function alertBlue(html)  {clientMsg(html,"clntMsgBlue",2000); console.log("alertBlue:", html);}
function alertGreen(html) {clientMsg(html,"",1000);}
//TODO AsyncQueue for messages with pause depending on severity
function clientMsg(html, extraClass, ms) { setTimeout(()=>{clientMsg_(html, extraClass);}, 20); }
//---
const upprCentrDIV = document.getElementById("clientMsgsID");
var clientMsgFadeTimeoutID = null;
//---
function clientMsg_(html, extraClass) {
	clearTimeout(clientMsgFadeTimeoutID);
	upprCentrDIV.style.display="inline-block";
	upprCentrDIV.style.opacity = 1;
	upprCentrDIV.classList.remove("fadeout", "clntMsgBlue", "clntMsgRed");
	if (extraClass) upprCentrDIV.classList.add(extraClass);
	document.getElementById("clientMsgTxtID").innerHTML = html;

	clientMsg_fadeout();	
}
//--- CSS :hover not working  #88dff0a
function clientMsg_fadeout() {
	upprCentrDIV.style.opacity = 1;
	if ( !upprCentrDIV.classList.contains("nofadeout") ) //Not a real CSS class, just a marker #4c64a311
		clientMsgFadeTimeoutID = setTimeout( ()=>{upprCentrDIV.classList.add("fadeout");upprCentrDIV.style.opacity = 0.2;}, 5555 );
}


	
	








