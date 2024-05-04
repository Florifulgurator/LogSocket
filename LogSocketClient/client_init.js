/*****************************************************************************
 * client_init.js
 *
 *                 ************************
 * First file of   ** LogSocket ~Client~ **
 *                 ************************
 * 
 * LogSocketServer URI *** ws://localhost:8080/logsocket/ws *** HARDWIRED #57977fd8
 *
 * * Tested only for Chrome browser *
 * 
 * Project started late September 2023 as a quick paradigmatic code exercise.
 * Pure JavaScript, no external lib, not even jQuery. For good reasons :-)
 * Still lots of TODOs, but few FIXMEs.
 * 
 *
 *** Files {} ***
 *   client_init.js {}
 *   client_view.js
 *   client_cmds.js {}
 *   client_gui_lib.js
 *   client_gui.js  {}
 *   client_filter.js
 *   client_selection.js
 *   client_clock.js {} (DEV/experiment: System clocks synchronization. No use case yet.)
 *   client_lib.js   {} (World's smallest lib :-) )
 *   client_TEST.js     (optional)
 *   index.html      {} (standard http headers)
 *   client.css      {}
 *   index.jsp             (adds cross-origin isolation headers for improved performance.now() resolution. Else static html.)
 *                         TODO #37076d2e Chrome command flag?
 * 
 *** Task/code tags ***
 *   TODO, FIXME, HARDWIRED, DOCU, COPYPASTE, TEST, NOTE, CAVEAT,
 *   CLEANUP, WHY, DEL, DEV, UNFINISHED, DIAGN,
 *   #<string> e.g. #UTF to mark special UTF-8 characters (test rendering required).
 *   Single-letter #L hashtags refer to things in the visible vicinity, e.g. in complicated algorithms.
 * 
 * BUG is for inside error message strings, just in case one really shows up.
 * Use FIXME for actually known bugs.
 * DEL means stuff to be deleted ASAP.
 * DEV is current development/work, while TODO is postponed finishing-up.
 * Test stuff has an all-caps TEST somewhere.
 * 
 * //--- Separator between things that belong together
 * 
 *** 8-digit hex #hashtags ***
 *   are references to cross-file things. E.g. #655e8593
 *   They are for documentation, to see what code is related and to quickly
 *   make sense of distributed constructs. Strings copied from Java object IDs.
 * 
 * Hex #hashtags are my invention, ca. 1999, not patented :-)
 * TODO Write little app to auto generate random hashtags.
 * (Why hex: Only 2.3% look decimal.)
 *
 *****************************************************************************/


/*** Recommended work for first code study ***
 * TODO #7e23cb75 JavaScript microTimer for performance comparison with Java
 * TODO #6b2b50f8 Navigate to next infoLine
 * TODO #6103a6ee Unify _millis() and _linewrap()
 * TODO #20eaba80 If performance benefit: Put output chunk element in queue, append to DOM when scrolled down or needed by search.
 * incl. LogSocketServer.java: TODO #1538e9e5 Let server send only new timer results, append named list in client.
 * Log bursts: TODO #7d3b1629, FIXME #69c6a3f0 HTML input box size
 * Functional programming: TODO #48f9a725 Test garbage collection of infinite-loop async function running in a function closure.
 * TODO #2376c42f get rid of chunkArr
 * 
 */

 
/*** Tech notes ***
 * #note0 Java/JS DIFFERENCE in String.split: 
 *        JS ... stops when limit entries have been placed in the array. >>> Any leftover text is not included in the array at all. <<<
 *        Java ... the pattern will be applied at most limit - 1 times, the array's length will be no greater than limit,
 *             and >>> the array's last entry will contain all input beyond the last matched delimiter <<< .
 * #note1 https://yonatankra.com/layout-reflow/
 *       ( ... since innerText takes CSS styles into account, reading the value of innerText triggers a reflow to ensure up-to-date computed styles. (Reflows can be computationally expensive, and thus should be avoided when possible.) https://devdocs.io/dom/node/textcontent#differences_from_innertext
 * #note2 COOP and COEP HTTP headers to allow JavaScript precision timer: https://web.dev/articles/coop-coep
 *     https://incolumitas.com/2021/12/18/on-high-precision-javascript-timers/
 * #note3 "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe" --js-flags="--expose-gc"
 *        #37076d2e ...
 * #note4 Maps are faster than Objects https://rehmat-sayany.medium.com/using-map-over-objects-in-javascript-a-performance-benchmark-ff2f351851ed
 * #note5 https://devdocs.io/dom/html_dom_api/microtask_guide
 * #note6 https://develotters.com/posts/how-not-to-measure-elapsed-time/
 * #note7 https://keithjgrant.com/posts/2023/04/transitioning-to-height-auto/
 * #note8 New experimental CSS feature content-visibility/contain-intrinsic-height doesn't help for 3 reasons:
 *        A) absolute positioning no longer works
 *        B) x-overflow no longer visible
 *        C) contentvisibilityautostatechange event is buggy #4bcc717c.
 *        Using IntersectionObserver speeds up rendering performance just as well, if not better
 * 
 */


/* END of Preample ***********************************************************/


var TEST1 = true; // 1) Show test menu, 2) add thin dashed red line to bottom of output chunks #5cabad3d
var TEST2 = true; // Filter tests



window.addEventListener("load", init, false); 


//>>>>>>>>>>>>>>>>>>>>>>>>>>>
//___ Global globals
var sessionID = null;    // From corresponding Java WsSession object ID.
                         // sessionID!=null indicates a clean connection. // Else not yet critical: used as client ID and for testing. 
var firstT = 0;          // Server clock Tick at first LogSocket connection. 
var srvrMsg;             // Current message from LogSocketServer

//>>>>>>>>>>>>>>>>>>>>>>>>>>>
//___ Parameters
const CHUNK_SIZE = 80;
const MAX_CHUNKS = 125;
const MAX_CHUNKS_TOL = 3;  // Output limit: 10000 lines +/- 240
const MAX_SRCHTXTLEN = 32; // Max search text length
// TODO hardwired parameters hardwired

//>>>>>>>>>>>>>>>>>>>>>>>>>>>
//___ Session configuration
var colorBySubRealm = true; // still HARDWIRED

//>>>>>>>>>>>>>>>>>>>>>>>>>>>
//___ UI State
var caseSensitive = false;
var burstGrpThr = 999;   // Bracket log msgs <999CT (clock ticks) apart each.
var cmdGC = true;        // Treat GC messages as internal via CSS cmd class
var synchDaemon = false; // TODO #66eb4a7b
// No vars needed yet:
// document.getElementById("showCmdsID").checked = false; // #65f24547
// document.getElementById("millisID").checked = false;   // #4ca41226 
// document.getElementById("linewrapID").checked = false; // #4ca41226  necessary at init (WHY?)

// TODO #60ba89a6 save state in cookie


//>>>>>>>>>>>>>>>>>>>>>>>>>>>
//___ Font constants in pixel
// Monospaced font width:, line height, em in pixel:  // MFWP==6.5958 (browser zoom 75%) 6.5969 (175%)
const MFWP = document.getElementById("fontTestID").getBoundingClientRect().width/60.0;
// Line height:
const LHP = document.getElementById("fontTestID").getBoundingClientRect().height; // ==14.703 (75%) 14.321 (175%)  TODO
document.getElementById("fontTestID").style.display = "none";
// em
const testDiv = document.getElementById("testDivID"); testDiv.style.height = '10em';
const EMP = testDiv.offsetHeight/10; // ==12 (any zoom)
testDiv.style.display = "none";


//>>>>>>>>>>>>>>>>>>>>>>>>>>>
//___ Some important or frequently used HTML elements
var outputEl  //current chunk DIV where output goes. No ID.
const outputParntEl = document.getElementById("outputID"); //parent of output chunk
const searchEl     = document.getElementById("searchID");
const cntxtMenu1El  = document.getElementById("contxtMenu1ID"); // Context menu on colored text
const cntxtMenu3El = document.getElementById("contxtMenu3ID");  // Context menu on Logger ID in output and logger list
const menuDivEl    = document.getElementById("menuDivID");
	  menuDivEl.symEl = document.getElementById("menuButtonID").querySelector(".sym");
const clrsDivEl    = document.getElementById("clrsDivID");
	  clrsDivEl.symEl = document.getElementById("clrsButtonID").querySelector(".sym");
const hlColrsEl    = document.getElementById("hlColrsID");
const hlxColrsEl   = document.getElementById("hlxColrsID");

const loggersEl    = document.getElementById("loggersID");
	  loggersEl.symEl = document.getElementById("lggrsButtonID").querySelector(".sym");

const lggrListEl   = document.getElementById("lggrTblBdID");

const timrsEl      = document.getElementById("timrsID");
const timrsBtnEl   = document.getElementById("cleanSysTimrsBtn");

//>>>>>>>>>>>>>>>>>>>>>>>>>>>
//___ JS manipulated CSS classes
var cssRule_cmd;  // .cmd
var cssRule_posLT;  // .posLT
var cssRule_posRT;  // .posRT


//...
//...
//...
// ...more global const and var where they are first used.



//>>>>>>>>>>>>>>>>>>>>>>>>>>>
//___ Technical Details
function init() {
 	console.log("init(): Hello World! 8");
	
	newOutputChunk();
	//---
	init_colors_css();
	//---
	cssRule_posRT.style.top = Math.floor(document.getElementById("menuButtonID").getBoundingClientRect().bottom +5)+"px"; // #3aedf5bc COPYPASTE #72736288
	//---
	msgOutput("Garbage collection function window.gc() "+ (window.gc?"exists.":"not available."), "D"); //#note3
	//---
	let diff, start;
	for(let i=0; i<5; i++) {
		start = performance.now();
		while ((diff = performance.now() - start) === 0);
	}
	msgOutput("window.crossOriginIsolated=="+window.crossOriginIsolated+", performance.now() resolution=="+round3(diff)+"ms", "D"); //#note2
	

	//---
	//GUI Events using Event object. (Simple no-args event _handler() in HTML.)

	window.addEventListener("contextmenu", e => { e.preventDefault();} );
	window.addEventListener("scroll", onScroll, {passive: true} );
	window.addEventListener("resize", onResize, {passive: true} );
	window.addEventListener("error", () => { alertRed("ERROR<br>See JS console."); } );
	
	outputParntEl.addEventListener('mouseup', onSelect); // TODO selection event
	loggersEl.addEventListener('mouseup', onSelect);
	loggersEl.addEventListener('click', hideContextMenus);
	loggersEl.addEventListener('scroll', hideContextMenus);
	outputParntEl.addEventListener('click', outputClick);
	outputParntEl.addEventListener('contextmenu', outputCtxtMenu);
	lggrListEl.addEventListener('contextmenu', outputCtxtMenu);
	searchEl.addEventListener("paste", pasteSearch);
	searchEl.addEventListener("keydown", e => { if(e.key=="Enter") _search(); });
	hlColrsEl.addEventListener('click', hlColrsClick);
	hlxColrsEl.addEventListener('click', hlColrsClick);
	menuDivEl.addEventListener('contextmenu', e => { toggleDrpdwnDspl(menuDivEl); });
	clrsDivEl.addEventListener('contextmenu', e => { toggleDrpdwnDspl(clrsDivEl); });
	loggersEl.addEventListener('contextmenu', e => { if (!e.target.classList.contains('lg') && loggersEl.classList.contains("posLT")) toggleDrpdwnDspl(loggersEl); });
	upprCentrDIV.addEventListener('mouseenter', clientMsg_fadeout);
	upprCentrDIV.addEventListener('contextmenu', ()=>{upprCentrDIV.style.display="none";});

	document.getElementById("caseID").checked = caseSensitive;
	document.getElementById("cmdGCID").checked = cmdGC;

	document.getElementById("cleanLggrListBtn").style.display = "none";
	updateNumMsgs();
	menuDivEl.style.display="none"; // #499996d7
	clrsDivEl.style.display="none"; // Needed even if "none" already set in CSS. Else clrsDivEl.style.display == ""

	//---

	// Open websocket >>>>>>>>>>>>>>>>>
	connect();
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	
	clientMsg("... init done.");
	console.log("init() done.");
}
//---
function init_colors_css() {
	var stylSheet;
	// We assume exactly one CSS file loaded
	for (let i=0, n=document.styleSheets.length; i<n; i++ ) {
		if (document.styleSheets[i].href) {
			stylSheet = document.styleSheets[i];
			break;
		}
	}
	if (!stylSheet) {
		alert("Style sheet not found! There will be ERRORS...");
		return;
	}

	const cssr = stylSheet.cssRules;
	var bgColrsNum, c, cssStr = "";  // #H
	
	for (let i=0, n=cssr.length; i<n; i++ ) {
		let t=cssr[i].selectorText;
		if (t.match(/^\.hl\d$/)) { hlColrsNum++; }
		else if (t.match(/^\.hlx\d$/)) { hlxColrsNum++;	}
		else if ( c = t.match(/^\.bg([A-Z])0$/)) { bgColrsNum++; }
		else if ( c = t.match(/^\.bg([A-Z])2$/)) {
			// /////////////////////////////////////////
			// Meanwhile job for the color optimizer
			// #H Helper for manual fine-tuning of light stripes via transparency in css definition:
			// Generate light stripe color without transparency to manually paste into css file
			if ( false && "SLBEDF".indexOf(c[1]) == -1 ) {
				var bgC = cssr[i].style.backgroundColor
				var [r, g, b, tr] = bgC.substring(bgC.indexOf("(")+1, bgC.indexOf(")")).split(", ");
				if (tr) { //add WHITE BACKGROUND
					cssStr += `.bg${c[1]}1 { background-color: rgb(${Math.round(r*tr+(1-tr)*255)}, ${Math.round(g*tr+(1-tr)*255)}, ${Math.round(b*tr+(1-tr)*255)});}\n`;
				}
			}
			// \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
		}		
		if (t==".cmd") cssRule_cmd = cssr[i];
		if (t==".posLT") cssRule_posLT = cssr[i];
		if (t==".posRT") cssRule_posRT = cssr[i];
	}
	if ( hlColrsNum==0 || bgColrsNum==0 || !cssRule_cmd) alert("Essential CSS rules missing. Wrong CSS file? There will be ERRORS...");
	
	if (cssStr) console.log(cssStr); // #H

	const spn = document.createElement("span");
	var parent = document.getElementById("hlColrsID");
	for (let i=0; i<hlColrsNum; i++) {
		const clone = spn.cloneNode();
		clone.className = "hl"+i;
		clone.id = "hl"+i+"ID";
		clone.innerHTML="&nbsp;&nbsp;&nbsp;&nbsp;";  // textContent would be literal
		parent.appendChild(clone); 
	}
	document.getElementById("hl"+highlightCounter+"ID").classList.add("boxed");

	parent = document.getElementById("hlxColrsID");
	for (let i=0; i<hlxColrsNum; i++) {
		const clone = spn.cloneNode();
		clone.className = "hlx"+i;
		clone.id = "hlx"+i+"ID";
		clone.innerHTML="&nbsp;&nbsp;&nbsp;&nbsp;";
		parent.appendChild(clone); 
	}

	// Logger colors
	// ( To optimize performance, the /^\.bg([A-Z])0$/ background color notation is also used for system message colors )
	lggrClrs = ["C","A","M","R","J","N","Q","O"]; // COPYPASTE #2419b81e  Don't mess this up!
	lggrClrsLength = lggrClrs.length;
	parent = document.getElementById("bgClrsID");
	for (let i=0; i<lggrClrsLength; i++) {
		const clone = spn.cloneNode();
		clone.className = `bg${lggrClrs[i]}0 mnspcd pre`;
		clone.id = `lggrClr${lggrClrs[i]}ID`;
		clone.textContent = " --- "; // #7afe07e6
		parent.append((i!=0?" · ":""), clone); // Chrome linebreaking works bad without "·"
	}
}





