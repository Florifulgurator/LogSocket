/*****************************************************************************
 *
 * **********************
 * ** LogSocket Client **
 * **********************
 * 
 * client_init.js (this)
 * client_cmds.js
 * client_gui.js
 * index.html (might switch to index.jsp if complexity overgrows)
 * client.css
 * 
 * Evolving, started late September 2023 as a quick code exercise.
 * Lots of TODOs, but few FIXMEs.
 * 
 * Code #hashtags are my invention, ca. 1999:
 *  They are for documentation, to see what code is related, to quickly
 *  make sense of distributed constructs. E.g. #655e8593
 *  Once I had a tiny Perl app to generate #hexadecimal hashtags and put them
 *  in the selection. Today I sometimes copy random hex strings from logs.
 *****************************************************************************/

// TODO check var, const, let

var test = false; // console.log messages on/off
// For extra HTML on/off edit CSS file. <span class="test2">bla</span>

//Monospaced font width in pixel:
var	MFWP = document.getElementById("fontTestID").getBoundingClientRect().width/60;
	       document.getElementById("fontTestID").style.display = "none";
//console.log("Monospaced font width in pixel = "+MFWP);//6.596747334 independent of browser zoom

//>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Configuration
// All still hardwired.
var configID; // TODO
var colorBySubRealm = false;
// System Clock
var	nanoUnit;
var nanoUnitStr;
	//COPYPASTE DOCU client_cmds.js
	//switch (I_nanoUnit) {
	//	case 10: nanoUnitStr = "&#8530;ms"; break; // 100000ns == 1/10ms
	//	case 100:  nanoUnitStr = "10&mu;s"; break;   // 10000ns == 10μs
	//}
// nanoUnit set to 1/100 in server clock. // #1298bf37
var doSpecialEffect = false; // FIXME: No effect :-)  //The special effect is just for fun
//<<<<<<<<<<<<<<<<<<<<<<<<<<<

//>>>>>>>>>>>>>>>>>>>>>>>>>>>
// UI State
var caseSensitive = false;
var wordwrap = false;
var burstGrpThr = 999; // Bracket log msgs <999CT (clock ticks) apart each  // //TODO Clock formalism
var showGC = true;
//<<<<<<<<<<<<<<<<<<<<<<<<<<<

var websocket;
const wsStateNames = ["CONNECTING", "OPEN", "CLOSING", "CLOSED"];

var srvrMsg;
var sessionID; //null initialization used in code
//const topslctedMax = 22 //number of chars in selection/filter box -> ScrapYard.html

var highlightCounter=0, hlColrsNum=0, hlColrsEl;
var userHighlColr="", hlxColrsNum=0, hlxColrsEl; // If integer string, mark selection with user chosen color
var popupData={};
var scrollThrottle = false;

var outputEl; //= document.getElementById("outputID");
var searchEl; //= document.getElementById("searchID");
var popupEl;  // ...
// more xxxEl in init() 

// Elements to be repeatedly cloned, prepared by init()
var outputDIV;
var	popup2DIV;
var lggrListTR;
var xboxSPAN;

//>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Output DIV contents
var	prefixSPAN;
var msSPAN;
var	lastMsNmbr = 11111;
var lastOUtpDivEl;
//<<<<<<<<<<<<<<<<<<<<<<<<<<<

var cssRule_cmd;
var cssRule_ms;

function init() {

	//Prepare TEST menu entries
	let cmds = document.getElementById("TESTfrm");
	var c;
	for (c of TESTcommands) {
		var inp = document.createElement("input");
		inp.setAttribute("type", "button");
		inp.setAttribute("value", c);
		inp.setAttribute("onclick", "doSend('"+c+"')");
		inp.setAttribute("title", "Send command to server (test).");
		cmds.appendChild(inp);
	}
	if (c) cmds.appendChild(document.createElement("br"));
	for (const u of TESTurls) {
		var inp = document.createElement("input");
		inp.setAttribute("type", "button");
		inp.setAttribute("value", u);
		inp.setAttribute("onclick", "window.open('"+u+"')");
		inp.setAttribute("title", `Open ${u} in new window`);
		cmds.appendChild(inp);
	}
		
	//  Prepare output area
	outputEl = document.getElementById("outputID");
	document.getElementById("wordwrapID").firstChild.checked = wordwrap; //outputEl.style.overflowWrap="normal";
	_wordwrap();
	
	//Other frequently used (or performance relevant) HTML Elements:
	popupEl = document.getElementById("popupID");
	popup2El = document.getElementById("popup2ID");
	poppWinEl = document.getElementById("poppWinID"); poppWinEl.style.display="none" //else empty despite CSS
	hlColrsEl = document.getElementById("hlColrsID");
	hlxColrsEl = document.getElementById("hlxColrsID");
	clrsDivEl = document.getElementById("clrsDivID");
	clrsDivEl.symEl = document.getElementById("clrsDrpdwnID").querySelector(".sym");
	actnsDivEl = document.getElementById("actnsDivID");
	actnsDivEl.symEl = document.getElementById("actnsDrpdwnID").querySelector(".sym");
	searchEl = document.getElementById("searchID");
	tttttEl = document.getElementById("tttttID");
	lgScktsEl = document.getElementById("lgScktsID");
	timrsEl = document.getElementById("timrsID");
	lggrListEl =  document.getElementById("lggrTblID"); //.firstChild; //<tbody>, would be inserted automatically 
	
	//Non-document elements that will get cloned into the document:
	prefixSPAN = document.createElement("span");
		prefixSPAN.className="pf";
		prefixSPAN.appendChild(document.createTextNode(" "));
	msSPAN = document.createElement("span");//#nanoUnitStr
		msSPAN.className="ms";
		msSPAN.appendChild(document.createTextNode(" "));
	extraSPAN = document.createElement("span");
		extraSPAN.className="ex";
		extraSPAN.appendChild(document.createTextNode("…▼ ")); // #7073166e "…👁▼"  eye spoils line spacing on Chrome
	outputDIV = document.createElement("div");
 	lggrListTR = document.createElement("tr");
 		lggrListTR.className="mnspcd"; 
		lggrListTR.innerHTML="&nbsp;<td style='text-align: right;'></td>&nbsp;<td class='lg'></td>&nbsp;<td></td>"; 
	popup2DIV = document.createElement("div"); popup2DIV.className="active";
	//xboxSPAN = document.createElement("span");
	//	xboxSPAN.appendChild(document.createTextNode("ÖÖÖ"));
	//	xboxSPAN.className="x";

	//Now we could write output, e.g. writeToScreen("blah", ...);

	init_colors();
	adjustDashedLine();
	document.getElementById("clDlLgrsID").style.display = "none";

	if (navigator.cookieEnabled && document.cookie ) {
		writeToScreen(null, null, "Cookie: "+document.cookie, "Disco")
		//document.cookie = document.cookie+"; max-age=0";
	} else {
		writeToScreen(null, null, "Empty cookie or no cookies enabled.", "Disco");
	}
					
	//GUI Events using Event object. (Simple no-args event _handler() in HTML.)
	outputEl.addEventListener('mouseup', e => { mouseUp(e); });
	tttttEl.addEventListener('mouseup', e => { mouseUp(e); });
	tttttEl.addEventListener('click', hidePopupMenus );
	outputEl.addEventListener('click', e => { outputClick(e); });
	outputEl.addEventListener('contextmenu', e => { e.preventDefault(); outputCtxtMenu(e); });
	lggrListEl.addEventListener('contextmenu', e => { e.preventDefault(); outputCtxtMenu(e); });
												// ^ prevents browser context menu
	searchEl.addEventListener("paste", e => { e.preventDefault(); pasteSearch(e); });
	hlColrsEl.addEventListener('click', e => { hlColrsClick(e); });
	hlxColrsEl.addEventListener('click', e => { hlColrsClick(e); });
	popup2El.addEventListener('click', e => { popup2_Click(e); });
	popup2El.addEventListener('contextmenu', e => { e.preventDefault(); popup2_RightClick(e); });

	window.addEventListener("scroll", scrll, {passive: true} );
	window.addEventListener("resize", rsize, {passive: true} );
	
	document.getElementById("caseID").checked = caseSensitive;
	document.getElementById("showGCID").checked = showGC;
	document.getElementById("showCmdsID").checked = false; //NOTE !! See html/css file !!
	document.getElementById("millisID").checked = false; //NOTE !! See html/css file !!
	updateNonLogNum(); //number behind showCmdsID

	// Open websocket >>>>>>>>>>>>>>>>>
	websocket = new WebSocket(wsUri);
	websocket.onopen = onOpen;
	websocket.onmessage = onMessage;
	websocket.onerror = onError;
	websocket.onclose = onClose;
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
}
//---
//Websocket callbacks:
function onError(evt) {
	writeToScreen(null, null, "WebSocket ERROR: evt.data=" + evt.data + " wsUri="+wsUri, "Error");
}
function onClose(evt) {
	writeToScreen(null, null, 'WebSocket CLOSE: code=' + evt.code + ', reason=' + evt.reason + ', data=' + evt.data, "Disco");
}
function onOpen(evt) {
	writeToScreen(null, null, "WebSocket OPEN: " + (evt.data?evt.data:"Hello World!") + " wsUri="+wsUri, "Disco");
	websocket.send("!HELLO "+configID);
}
//---
function init_colors() {
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
	// Count CSS selectors for highlight background colors
	var c;
	var cssStr = "";  // #655e8593
	for (let i=0, n=cssr.length; i<n; i++ ) {
		let t=cssr[i].selectorText;
		if (t.match(/^\.hl\d$/)) { hlColrsNum++; }
		else if (t.match(/^\.hlx\d$/)) { hlxColrsNum++;	}
		else if ( c = t.match(/^\.bg([A-Z])0$/)) { lggrClrs.push(c[1]); lggrClrsNum++; } // #2419b81e
		else if ( c = t.match(/^\.bg([A-Z])1$/)) {
			// Helper for manual fine-tuning of light stripes via transparency in css definition:
			// #655e8593 Generate light stripe color without transparency to manually paste into css file
			var bgC = cssr[i].style.backgroundColor
			var [r, g, b, tr] = bgC.substring(bgC.indexOf("(")+1, bgC.indexOf(")")).split(", ");
			if (tr) cssStr += `.bg${c[1]}1 { background-color: rgb(${Math.round(r*tr+(1-tr)*255)}, ${Math.round(g*tr+(1-tr)*255)}, ${Math.round(b*tr+(1-tr)*255)});}\n`;
		}		
		if (t==".ms")  cssRule_ms = cssr[i];
		if (t==".cmd") cssRule_cmd = cssr[i];
	}
	if ( hlColrsNum==0 || !cssRule_ms || !cssRule_cmd) alert("Essential CSS rules missing. Wrong CSS file? There will be ERRORS...");
	
	if (cssStr) console.log(cssStr); // #655e8593

	const spn = document.createElement("span");
	var parent = document.getElementById("hlColrsID");
	for (let i=0; i<hlColrsNum; i++) {
		const clone = spn.cloneNode();
		clone.className = "hl"+i;
		clone.id = "hl"+i+"ID";
		clone.innerHTML="&nbsp;&nbsp;&nbsp;&nbsp;";  //innerText
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

	parent = document.getElementById("bgClrsID");
	for (let i=0; i<lggrClrsNum; i++) {
		clr2listStr[lggrClrs[i]] = ""; // #5329f239
		const clone = spn.cloneNode();
		clone.className = `bg${lggrClrs[i]}0 mnspcd`;
		clone.id = `lggrClr${lggrClrs[i]}ID`;
		clone.innerText = " --- "; // #7afe07e6
		parent.append("·", clone); //(i!=0?"·":""),
	}
	parent.append("·");
}

