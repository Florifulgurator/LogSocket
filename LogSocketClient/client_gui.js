
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// GUI Events
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

// Functions directly attached to elements in HTML code are prepended by _

function _garbageColl() { doSend("!GC"); }
function _search()      { if (!highlight(searchEl.value)) alert("Nothing found"); }
function _comment()     { send_comment(textID.value); }
function _pingLogSockets() { doSend("/PING"); }
function _caseSensi() { caseSensitive = document.getElementById("caseID").checked; }
function _showGC()  { showGC = document.getElementById("showGCID").checked; }

function _wordwrap() {
	wordwrap = document.getElementById("wordwrapID").firstChild.checked
	if (wordwrap) {
		outputEl.style.whiteSpace = "pre-wrap";
		outputEl.style.wordBreak = "break-all";
	} else {
		outputEl.style.whiteSpace = "pre";
	}
}
function _millis()  {
	cssRule_ms.style.display =
		document.getElementById("millisID").firstChild.checked ?
		"inline-block" : "none";
}
function _showCmds()  {
	cssRule_cmd.style.display =
		document.getElementById("showCmdsID").firstChild.checked ?
		"block" : "none";
}
/* Painful variant:
	if (showCmds) {
		Array.from(outputEl.getElementsByClassName("cmd")).forEach( (el) => { //.forEach not directly working
			el.classList.remove("hide")
		});
	} else {
		Array.from(outputEl.getElementsByClassName("cmd")).forEach( (el) => { 
			el.classList.add("hide")
		});
	}
*/	


function _clrsDrpdwnClick() {__tgglDialgDspl(clrsDivEl, true);}
function _actnsDrpdwnClick() {__tgglDialgDspl(actnsDivEl, true);}

function __tgglDialgDspl(el,repainttt) {
	if ( el.style.display == "none" ) {
		el.style.display="inline-block";
		el.style.opacity=1;
		el.symEl.innerHTML="&#9746;";
	} else {
		el.style.display="none";
		el.symEl.innerHTML="&#9776;";	
	}
	
	// The weird but secessary <br id="hurzID">	
	document.getElementById("hurzID").style.display
	 = ( clrsDivEl.style.display == "none" && actnsDivEl.style.display != "none" ) ? "none" : "inline-block";

	if (repainttt) adjustDashedLine();
	scrollThrottle = true;	setTimeout(function(){scrollThrottle=false;}, 100);
}
	
function scrll() {
	// Copy&Paste 1 >>>>>>>>>>>>>>
	if (scrollThrottle) return;
	scrollThrottle = true; setTimeout(function(){scrollThrottle=false;}, 50);
	
	hidePopupMenus(); // The only serious action here
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<

	if (window.scrollY<3) {
		doSpecialEffect = true;
		adjustDashedLine();
	}
	if (!doSpecialEffect) return;
	
	var bndgRct, y;
	[actnsDivEl, clrsDivEl].forEach((el) => {
		if (el.style.display != "none") {
			bndgRct = el.getBoundingClientRect();
			y = document.getElementById("dshdLnID").getBoundingClientRect().y;
			opcty = 1-Math.round( 10*(bndgRct.bottom-y)/bndgRct.height )/10; //Change opacity in 0.1 steps

			if ( opcty>0 && opcty<1.01 && el.style.opacity!=opcty) { el.style.opacity = opcty; }
			if ( opcty<0.01) {
				__tgglDialgDspl(el);
				//doSpecialEffect = false;
			}	
		}	
	});

}

function rsize() {
	// Copy&Paste 1 >>>>>>>>>>>>>>
	if (scrollThrottle) return;
	scrollThrottle = true;
	setTimeout(function(){scrollThrottle=false;}, 50);
	
	hidePopupMenus();
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<
	adjustDashedLine();
}

function pasteSearch(ev) {
	let txt = ev.clipboardData.getData("text");
	searchEl.value = txt?txt:"";
	_search();
}

function mouseUp(ev) {
	if (test) console.log("mouseUp target="+ev.target);
	let selectedStr = window.getSelection().toString().trim();
	if (selectedStr.length) {
		highlight(selectedStr);
	}
}

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


function outputClick(ev) {
	hidePopupMenus();
	var el = ev.target;
	if ( el.classList.contains('hl') ) { removeColor(el); return; }
	if ( el.classList.contains('ex') ) { popupWin(el); return; }
}
function outputCtxtMenu(ev) { // Right click
	hidePopupMenus();
	var el = ev.target;
	if ( el.classList.contains('hl') ) { popupMenu1(ev); return; }
	if ( el.classList.contains('pf') || el.classList.contains('lg') ) { popupMenu2(ev); return; }
	if ( el.classList.contains('ex') ) { popupWin(el); return; }
}


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// GUI functions 
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

function adjustDashedLine() {
	tttttEl.style.minHeight = Math.round(document.getElementById("upprRghtCrnrID").getBoundingClientRect().height)+"px";
}

function removeColor(el) {//Also used by context menu
	//TODO as background thread
	// Remove all SPAN tags of same color
	// N.B.: The NodeList object returned by output.getElementsByClassName(...) is "live"
	//       and changes as the document changes. So to reliably change it we need a non-live copy.
	Array.from(outputEl.getElementsByClassName(el.className)).forEach( (x) => { x.outerHTML = x.innerHTML; } )
}

function highlight(txt) {
	let stuffGotHghltd = false;
	if (!txt.length) { return false; }
	
	//TODO: optimize - if possible
	if (userHighlColr=="") {
		spncss = '<span class="hl hl'+highlightCounter+'">';
		//spncss must be global scope for ttt.replaceAll
	} else {
		spncss = '<span class="hl hlx'+userHighlColr+'">';
	}

	var rgxp = new RegExp(txt.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, "\\$&"), caseSensitive?"g":"gi");

	outputEl.childNodes.forEach( (childNode) => {
		const ttt = childNode.innerHTML;
		if (ttt && rgxp.test(ttt) ) {  // ttt.indexOf(txt)!=-1
			childNode.innerHTML = ttt.replaceAll(rgxp, spncss + '$&</span>');
			stuffGotHghltd = true;
		}
	});
	
	if ( stuffGotHghltd && userHighlColr=="" ) {
		//Mark next color to come
		for (var i=0; i<hlColrsNum; i++) { document.getElementById("hl"+i+"ID").classList.remove("boxed"); }
		for (var i=0; i<hlxColrsNum; i++) { document.getElementById("hlx"+i+"ID").classList.remove("boxed"); }
		highlightCounter = (highlightCounter+1)%hlColrsNum;

		document.getElementById("hl"+highlightCounter+"ID").classList.add("boxed");
	}

	return stuffGotHghltd;
}


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Popup Window
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

function popupWin(el) { // caller: outputClick
	if (poppWinEl.style.display != "none") {
		poppWinEl.style.display = "none";
		return;
	}
	var extraClass = el.className.split(" ").find( (c) => c.match(/^ex\d+$/)); //TODO efficient code
	var bgClass = el.parentNode.className.split(" ").find( (c) => c.match(/^bg[A-Z]\d$/) );
	if (!extraClass) { console.error("popupWin: el="+el+" classList="+el.classList); return;} 

	poppWinEl.className = `poppWin ${bgClass}`;
	poppWinEl.lastChild.textContent = extrTxt[extraClass.substring(2)];
	poppWinEl.style.width = "fit-content"; // perhaps got/gets overwritten #15e600d9
	poppWinEl.style.display = "inline-block";

	placePppWin(el);
}
//---
function placePppWin(el) { //TODO call upon window resize
	if (poppWinEl.style.display == "none") return;
	
	const lll = el.parentNode.getBoundingClientRect().left+MFWP;
	const maxWidth = Math.floor( document.getElementsByTagName("html")[0].clientWidth -lll-4*MFWP );
	const poppWinWidth = Math.floor( poppWinEl.lastChild.getBoundingClientRect().width ); // it seriously has decimal places

	if(maxWidth<poppWinWidth) {
		poppWinEl.style.left =  Math.round(lll)+"px";
		poppWinEl.style.width = maxWidth+"px"; // #15e600d9
	} else if( el.getBoundingClientRect().right > poppWinWidth+lll) {
		poppWinEl.style.left = Math.round(el.getBoundingClientRect().right - poppWinWidth)+"px";
	} else {
		poppWinEl.style.left =  Math.round(lll)+"px";
	}
	poppWinEl.style.top = Math.floor( el.getBoundingClientRect().bottom + window.scrollY )+"px";
}


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Popup Menus
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
function hidePopupMenus() {
	if ( popupEl.style.display!="none" ) { popupEl.style.display = "none"; }
	if ( popup2El.style.display!="none" ) { popup2El.style.display = "none"; }
}

function placePopupMenu(ppElm, ev) {
	ppElm.style.display = "inline-block";
	
	const popupBCR = ppElm.getBoundingClientRect();
	const x = Math.round(Math.max(0, Math.min(
				ev.clientX+window.scrollX,
				document.body.offsetWidth+window.scrollX-popupBCR.width)));
	const y = Math.round(Math.max(0, Math.min(
				ev.clientY+window.scrollY,
				window.innerHeight+window.scrollY-popupBCR.height)));
	
	ppElm.style.left = x+"px";
	ppElm.style.top = y+"px";
}

//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

function popupMenu1(ev) {
	if (test) console.log(`popupMenu1 ev.target=${ev.target} txt=${ev.target.innerText}`);

	const clickedEl = ev.target;
	
	popupData.txt = clickedEl.innerHTML;
	popupData.targetEl = clickedEl;
		
	placePopupMenu(popupEl,ev);
}
//---
function _ctxtMenuCopy() {
	navigator.clipboard.writeText(popupData.txt);
	popupEl.style.display = "none";
}
//---
function _ctxtMenuRemvClr() {
	var clr = (""+popupData.targetEl.classList).match(/hl\d/)[0];
	if (!clr) clr = (""+popupData.targetEl.classList).match(/hlx\d/)[0];
	if (!clr) {
		console.error("_ctxtMenuRemvClr() not color class found in "+popupData.targetEl.classList);
		return;
	}
	removeColor(popupData.targetEl);
	popupEl.style.display = "none";
}

//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

var popup2_vals = ["L", "D", "E", "W", "P", "Q"]; popup2_vals.sort();
//---
function popupMenu2(ev) {
	const clickedEl = ev.target;
	popupData.txt = clickedEl.innerText;
	document.getElementById("pp2jL").innerText = popupData.txt;
	
	const [part1, dpl] = popupData.txt.split(".");
	if (dpl) {
		document.getElementById("IDpp2D").style.display = "block"; // break ID convention for easier coding 
		document.getElementById("IDpp2E").style.display = "block";
		document.getElementById("pp2jD").innerText = `.${dpl}`;
		document.getElementById("pp2jE").innerText = `.${dpl}`;
	} else {
		document.getElementById("IDpp2D").style.display = "none";
		document.getElementById("IDpp2E").style.display = "none";
	}

	const [realm, rest] = part1.split("/");
	document.getElementById("pp2jW").innerText = realm;
	const [logSckt, ...labels] = rest.split("#")
	document.getElementById("pp2jP").innerText = realm+"/"+logSckt;
	document.getElementById("pp2jQ").innerText = realm+"/"+logSckt;
	
	var i=0, pp2XEl, pp2YEl;
	var popup2HintEl = document.getElementById("pop2HintID");// else ID breaks popup2_getClickedVal 
	for (var n=labels.length; i<n; i++) {
		pp2XEl = document.getElementById(`IDpp2X${i}`);
		if ( pp2XEl==null ) {
			pp2XEl = popup2DIV.cloneNode();
			pp2XEl.id = `IDpp2X${i}`;
			pp2XEl.innerHTML = ` <span class="test2">X${i}</span><input type="checkbox" id="pp2X${i}" value="X${i}"/> Label <span class="mnspcd" id="pp2jX${i}"></span>`
			popup2HintEl.before(pp2XEl);
			pp2YEl = popup2DIV.cloneNode(); //var popup2DIV see init()
			pp2YEl.id = `IDpp2Y${i}`;
			pp2YEl.innerHTML = ` <span class="test2">Y${i}</span><input type="checkbox" id="pp2Y${i}" value="Y${i}"/> <i>Except</i> <span class="mnspcd" id="pp2jY${i}"></span>`	
			popup2HintEl.before(pp2YEl);
		} else {
			pp2YEl = document.getElementById(`IDpp2Y${i}`);
		}
		document.getElementById(`pp2jX${i}`).innerText = `${realm}#${labels[i]}`;
		document.getElementById(`pp2jY${i}`).innerText = `${realm}#${labels[i]}`;
		document.getElementById(`pp2X${i}`).checked = false;
		document.getElementById(`pp2Y${i}`).checked = false;

		pp2XEl.style.display = "block";
		pp2YEl.style.display = "none";
	}
	
	while (	null != (pp2XEl=document.getElementById(`IDpp2X${i}`)) )  {
		pp2XEl.style.display = "none";
		document.getElementById(`IDpp2Y${i}`).style.display = "none";
		document.getElementById(`pp2X${i}`).checked = false;
		document.getElementById(`pp2Y${i}`).checked = false;
		i++;
	}
	
	popup2_vals.forEach( (val) => { document.getElementById(`pp2${val}`).checked = false; } );

	placePopupMenu(popup2El,ev);
}
//---
function popup2_getClickedVal(ev) {//console.log("popup2_getClickedVal "+ev.target.id+" "+ev.target);
	var str = ev.target.id;
	var res;
	
	for (str=ev.target.id; true; res = "") {
		     if ( str.substring(0,5) == "IDpp2" ) { res = str.substring(5); }
		else if ( str.substring(0,4) == "pp2j" )  { res = str.substring(4); }
		else if ( str.substring(0,3) == "pp2" )   { res = str.substring(3); }
		else { str = ev.target.parentNode.id; }

		if ( res!=undefined ) return res;
	}
}
//---
function popup2_RightClick(ev) {
	var val=popup2_getClickedVal(ev);
	
	switch ( val.at(0) ) {
		case "X":
			document.getElementById(`IDpp2${val}`).style.display = "none"
			document.getElementById(`pp2${val}`).checked = false;
			document.getElementById(`IDpp2Y${val.substring(1)}`).style.display = "block"
			break;
		case "Y":
			document.getElementById(`IDpp2${val}`).style.display = "none"
			document.getElementById(`pp2${val}`).checked = false;
			document.getElementById(`IDpp2X${val.substring(1)}`).style.display = "block"
			break;
	}
}
//---
var popup2_xor  = ["PQ", "QP", "LE", "EL"];
var popup2_fltr = ["LD", "LW", "LP", "EP", "DP", "DW", "EW", "PW", "QW"]; // "L is contained in D" ...
//---
function popup2_Click(ev) { //TODO X Y extra treatment
	var clickedVal = popup2_getClickedVal(ev);
	if ( clickedVal=="" ) return;

	var clickedInpEl = document.getElementById(`pp2${clickedVal}`);
	if ( clickedInpEl == null ) { console.error("popup2_Click clicked="+clickedVal+" but clickedInp NULL"); return; }
	if ( clickedInpEl != ev.target ) clickedInpEl.checked = !clickedInpEl.checked; // else it got already checked
	
	if (!clickedInpEl.checked ) { return; }
	
	// unchecking the contradicting values:
	var val;
	state = [];
	popup2_vals.forEach( (val) => {
		 if ( document.getElementById(`pp2${val}`).checked ) {
			 if ( popup2_xor.includes(clickedVal+val) ) {
				 document.getElementById(`pp2${val}`).checked = false; 
			 } else {
				 state.push(val);
			 }
		} 
	});
	
	// unchecking the unnecessary values:
	var uncheck = [];
	state.forEach( (checked) => { if ( popup2_fltr.includes(clickedVal+checked) ) uncheck.push(checked); } )
	state.forEach( (checked) => { if ( popup2_fltr.includes(checked+clickedVal) ) uncheck.push(checked); } )
	uncheck.forEach( (val) => { document.getElementById(`pp2${val}`).checked = false; } );
	
	console.log("popup2_Click uncheck="+uncheck);
}

//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


//appendExtrTxtPopup(divEl, extraText)


window.addEventListener("load", init, false);
//??? "unload" websocket.close() ??
