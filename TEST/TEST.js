//servletURL set by JSP

var Empty = {}; //Emptiness is difficult in Javascript...
var mouseLogcounter = 0;
var text ="Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
var colors = [];
var colorTestLggrs = Empty;
var testLogEl;
var mouseLogOn = false;
var loop = 1000;

var realm = "JS";
if (LogSocketCreatorNr) realm = realm+LogSocketCreatorNr; // LogSocketCreatorNr set by JSP

const lg0 = LogSocket.newLggr(realm, "#PAGE", "Test page log");

// Tests >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

const lg1 = LogSocket.newLggr(realm, "#TEST#MOUSEENTER", "Lorem ipsum");
const lg2 = LogSocket.newLggr(realm, "#TEST#EVENTSOURCE", "JS EventSource, receives ServerSentEvents from Servlet");
const lg3 = LogSocket.newLggr(realm, "#TEST#XMLHttpRequest", "POST to Servlet");
//const lg4 = LogSocket.newLggr(realm, "#TEST#JSLOOP", `JavaScript TEST: ${loop} quick Lorem Ipsums x2`);

function mouseEnter(ev) {
	if (!mouseLogOn) return;
	if (colorTestLggrs==Empty) {
		lg1.log(`${ev.target.innerText} ${++mouseLogcounter} ${text}`);
	} else {
		for (var i=0, n=Math.round(Math.random()*4); i<=n; i++)
			colorTestLggrs[ev.target.classList[1]].log(
				`${ev.target.innerText} ${++mouseLogcounter} ${i!=0?"REPETITION"+i:""} ${text}`
			);
	}	
} 

function JSLoop() {
	const lg4 = LogSocket.newLggr(realm, "#TEST#JSLOOP", `JavaScript TEST: ${loop} quick Lorem Ipsums x2`);
	var i = loop, startT=Date.now();
	
	lg4.timerStart("JSLoop");
	while (--i >= 0) {
		lg4.log(`${i} ${text} -- ${text}`);
	}
	lg4.timerStop("JSLoop");
}

function JavaLoop() {
	const xhr = new XMLHttpRequest();
	xhr.onload = xhrOnLoad;
	xhr.open("POST", servletURL);
	xhr.setRequestHeader("Content-Type", "text/plain;charset=UTF-8");
	const cmd = `CMD: javaloop ${loop}`;
	xhr.send(cmd);
	lg3.log(`#POST "${cmd}" to servlet`);
}

function JSJavaLoop() { JavaLoop(); JSLoop(); }
	
function servletThread() {
	const xhr = new XMLHttpRequest();
	xhr.onload = xhrOnLoad;
	xhr.open("POST", servletURL);
	xhr.setRequestHeader("Content-Type", "text/plain;charset=UTF-8");
	const cmd = `CMD: thread`;
	xhr.send(cmd);
	lg3.log(`#POST "${cmd}" to servlet`);
}

function pingServlet() {
	const xhr = new XMLHttpRequest();
	xhr.onload = xhrOnLoad;
	xhr.onerror = xhrOnError;
	xhr.open("POST", servletURL);
	//You must call setRequestHeader() after open(), but before send().
	xhr.setRequestHeader("Content-Type", "text/plain;charset=UTF-8");
	xhr.send("Ping!");
	lg3.log("#POST \"Ping!\" to servlet");
}
//---
function xhrOnLoad() {
	lg3.log("XMLHttpRequest#onload() Servlet replied to POST. responseText=\""+this.responseText+"\"");
}
//---
function xhrOnError() {//TODO logErr
	lg3.log("XMLHttpRequest#onerror() Servlet Error. responseText=\""+this.responseText+"\"");
}
//---


function initEventSource() {
	testLog("Creating EventSource. servletURL="+servletURL);
	
	evntSrc = new EventSource(servletURL);
	
	evntSrc.onmessage = function(evt) {
		lg2.log("EventSource onmessage: "+evt.data+" evt.id="+evt.id);
	}
	evntSrc.onopen = function(evt) {
		lg2.log("EventSource onopen: "+evt.data+" evt.id="+evt.id);
	}
	evntSrc.onerror = function(evt) {
		lg2.logErr("EventSource onerror: "+evt.data+" evt.id="+evt.id);
	}
	evntSrc.onclose = function(evt) {
		lg2.log("EventSource onclose: "+evt.data+" evt.id="+evt.id);
	}
	
	document.getElementById("initEventSourceID").disabled = true;
	document.getElementById("closeEventSourceID").disabled = false;
}
//---
function closeEventSource() {
	evntSrc.close();
	testLog("EventSource.close() called");

	document.getElementById("closeEventSourceID").disabled = true;
	document.getElementById("initEventSourceID").disabled = false;

}


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// UI, Init, Helpers
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

function mouseEnterLogClick() { mouseLogOn = document.getElementById("mouseEnterLogID").firstChild.checked; }

function colorTestCreate() {
	if (colorTestLggrs!=Empty) {
		testLog("colorTestCreate() BUG 1");
		return;
	}
	testLog("colorTestCreate()");

	colorTestLggrs = {}; // Is empty, but not equal to Empty
	var ms=Date.now();
	var n=0;
	colors.forEach( (css) => {
		colorTestLggrs[css] = LogSocket.newLggr("xxx"+n, "#MOUSEENTER#COLORTEST");
		//Client might assign different color
		n++;
	});
	if (n!=0) {
		ms = Date.now()-ms;
		testLog("Object A containing "+n+" loggers with realms \"xxx0\" - \"xxx"+(n-1)+"\" created in "+ms+"ms");
	} else {
		testLog("colorTestCreate() BUG 2");
	}
		
	document.getElementById("clrTstCrtID").disabled = true;
	document.getElementById("clrTstDstrID").disabled = false;
}
//---
function colorTestDestroy() {
	testLog( "Object A emptied. "+ (window.gc?"Calling garbage collection.":"") );
	colorTestLggrs = Empty;
	if (!window.gc) {
		console.error("colorTestDestroy() No garbage collection function. Start Chrome with --js-flags=\"--expose-gc\" ?");
	} else {
		window.gc();
	}
	document.getElementById("clrTstCrtID").disabled = false;
	document.getElementById("clrTstDstrID").disabled = true;
}


function init() {
	
	document.getElementById("loopNrID1").innerText = loop;
	document.getElementById("loopNrID2").innerText = loop;
	document.getElementById("loopNrID3").innerText = loop;
	document.getElementById("mouseEnterLogID").firstChild.checked = mouseLogOn;
	
	testLogEl = document.getElementById("testlogID");
	testLog("Garbage collection function window.gc() "+ (window.gc?"exists.":"not available."))

	// CSS selectors for logger output background colors
	const cssRules = document.styleSheets[0].cssRules;
	for (var i=0, n=cssRules.length; i<n; i++ ) {
		var c, t=cssRules[i].selectorText;
		if ( c = t.match(/^\.bg([A-Z])0$/) ) {
			colors.push(t.substring(1));
		}
	}
	testLog(colors.length+" colors found.");
	if (colors.length!=0) {	document.getElementById("clrTstCrtID").disabled = false; }
	
	//For each word addEventListener and color
	var n=0;
	Array.from(document.getElementsByClassName("a")).forEach( (el) => {
		el.addEventListener('mouseenter', (ev) => {mouseEnter(ev);});
		el.classList.add(colors[n]);
		n = (n+1)%colors.length;
	});
	

	document.getElementById("pingServletID").disabled = false;
	document.getElementById("servletThreadID").disabled = false;
	document.getElementById("initEventSourceID").disabled = false;


	let diff, start;
	for(let i=0; i<5; i++) {
		start = performance.now();
		while ((diff = performance.now() - start) === 0);
	}
	diff = Math.round(diff*1000.0)/1000.0;
	testLog("performance.now() resolution=="+diff+"ms");


	lg0.log("init() done");
}

function testLog(txt){
	testLogEl.insertAdjacentHTML("beforeend",txt);
	testLogEl.appendChild(document.createElement("br"));
	lg0.log("#TESTLOG "+txt);
}


window.addEventListener("load", init, false);
LogSocket.newLggr(realm, "#END", "Throwaway logger created at end of file TEST.js").log("Final message of last logger in the text.");