console.log("client_TEST.js: Hello World! 2");

// Tests switched on/off in client_init()
// var TEST1 = true; // 1) Show test menu, 2) add thin dashed red line to bottom of output chunks #5cabad3d
                     // 3) What ever is in the #Test areas here.
// var TEST2 = true; // Lggr Filter tests


// ******************************
// * Performance test scenario: *
// ******************************
//  1) Fresh start of test server and LogSocket server
//  2) Fresh start of index.jsp client on GC-enabled Chrome #note3
//  3) 4x JSLoop (4000x2 Lorem ipsums)
//  4) Reload client
//  5) 4x JSLoop
//  6) TEST


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Test environment >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Not a test! Tests come below.

var TESTT=T_ms();

async function measureUserAgentSpecificMemory() {
	result = await performance.measureUserAgentSpecificMemory();
	console.log('Memory usage:', result);
}


// Init Tests >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
if (TEST1) {
	window.addEventListener("load",
	()=> {
		console.log("Init TEST menu");
		
		let cmds = document.getElementById("TESTfrm");
		let c, u, inp;
		for (c of TESTsrvrCmds) {
			inp = document.createElement("input");
			inp.className="smallbtn";
			inp.setAttribute("type", "button");
			inp.setAttribute("value", c);
			inp.setAttribute("onclick", "doSend('"+c+"')");
			inp.setAttribute("title", "Send command '"+c+"' to server.");
			cmds.appendChild(inp);
		}
		for (u of TESTurls) {
			if (c) {c=null; cmds.appendChild(document.createElement("br"));}
			inp = document.createElement("input");
			inp.className="smallbtn";
			inp.setAttribute("type", "button");
			inp.setAttribute("value", u);
			inp.setAttribute("onclick", "window.open('"+u+"')");
			inp.setAttribute("title", `Open ${u} in new window`);
			cmds.appendChild(inp);
			cmds.appendChild(document.createElement("br"));
		}
		if (window.crossOriginIsolated) {
			if (!u) {cmds.appendChild(document.createElement("br"));}
			inp = document.createElement("input");
			inp.className="smallbtn";
			inp.setAttribute("type", "button");
			inp.setAttribute("value", "performance.measureUserAgentSpecificMemory()");
			inp.setAttribute("onclick", "measureUserAgentSpecificMemory()");
			cmds.appendChild(inp);
		}
		if (TEST1) {
			cmds.appendChild(document.createElement("br"));
			inp = document.createElement("input");
			inp.className="smallbtn";
			inp.setAttribute("type", "button");
			inp.setAttribute("value", "TEST1_launch()");
			inp.setAttribute("onclick", "TEST1_launch()");
			cmds.appendChild(inp);
			inp = document.createElement("input");
			inp.className="smallbtn";
			inp.setAttribute("type", "button");
			inp.setAttribute("value", "TEST1_stop()");
			inp.setAttribute("onclick", "TEST1_stop()");
			cmds.appendChild(inp);
		}
		{//
		}

	});
} else {
	document.getElementById("TESTcmdsID").style.display = "none";
}
//--- ---
const TESTwin1El = document.getElementById("TESTwin1ID");
var TESTwinElcolor;



//-------------------------------------------------------------
var TEST1_tickerCntr = 0;
var TEST1_lastNow = Math.floor(performance.now()*1); // micros
var TEST1_resetClrLater;
var TEST1_timerID;
var TEST1_T_launch;
var TEST1_txt = "???";
//---
function TEST1_ticker() {
	var now = Math.floor(performance.now()*1);
	if (now-TEST1_lastNow> 125*1 ) { //#X
		TESTwin1El.style.color = "red";
		TEST1_resetClrLater.launch();
	}
	
	TEST1_txt = `${TEST1_tickerCntr++} ${Math.round( (T()-TEST1_T_launch)/1000.0 - TEST1_tickerCntr*10)}`;  //#X
	setTimeout(()=>{ TESTwin1El.textContent = TEST1_txt; }, 10); // setTimeout makes "Forced reflow" less likely
	
	TEST1_lastNow = Math.floor(performance.now()*1);
	//setTimeout(TEST1_ticker,100); //#X
}
//---
function TEST1_launch() {
	TEST1_T_launch = T();
	console.log("TEST1_launch() T()="+TEST1_T_launch);
	upprCentrDIV.classList.add("nofadeout");upprCentrDIV.style.opacity = 0.8;//??document.getElementById("clientMsgTxtID")
	TESTwinElcolor = TESTwin1El.style.color;
	TEST1_resetClrLater = new OnceAfterTimeoutTask( ()=>{TESTwin1El.style.color = TESTwinElcolor;}, 2000);
	TEST1_lastNow = Math.floor(performance.now()*1);
	TEST1_tickerCntr = 0;
	clearTimeout(TEST1_timerID);
	TEST1_timerID = setInterval(TEST1_ticker,100); //#X
	//setTimeout(TEST1_ticker,100); //#X

// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<Test environment
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


// #Test launch-by-click area:	
// ////////////////////////////////////////////////////////////	
// >>> ...
	




// ... <<<	
// \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
}
//---
function TEST1_stop() {
	clearTimeout(TEST1_timerID);
	upprCentrDIV.classList.remove("nofadeout"); // #4c64a311
// #Test kill-by-click area:	
// ////////////////////////////////////////////////////////////	
// >>> ...



// ... <<<	
// \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\	
}


if (TEST1) {
// #Test preparation on load area:
// ////////////////////////////////////////////////////////////	
// >>>

	
	
	
	
// <<<	
// \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
}




/* Test junkyard: >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

//!! not working:
//var m = new Map();
//m.set("aa", new Number(0));
//console.log((m.get("aa"))++, (m.get("aa"))++, (m.get("aa"))++, (m.get("aa"))++);

// works:
m = new Map();
m.set("aa", []);
console.log((m.get("aa")).push("x"), (m.get("aa")).push("y"), m.get("aa").push("z"), m.get("aa") );

//-------------------------------------------------------------
var TESTcounter = 0;
function TESTdummy() { var x = Math.sqrt(Math.random()*Math.random()+T_ms()); TESTcounter++; }
//-------------------------------------------------------------


//COPYPASTE
	var TESTms=Date.now();
	// . . . 
	if (TESTgui) {
		console.log("onResize() " + (Date.now()-TESTms)
			+" window.scrollX="+window.scrollX // == visualViewport.pageLeft
			+" visualViewport.width="+visualViewport.width
		);
		TESTms=Date.now();
	}
//-------------------------------------------------------------



<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< */