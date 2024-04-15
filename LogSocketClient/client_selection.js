console.log("client_selection.js: Hello World!");

//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Search/highlight functions 
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


var searchWorkQueue = MakeAsyncQueue("searchWorkQueue");


function removeColor(el) {//Also used by context menu
	var clrCSSmatch = el.className.match(/hl\d/);
	if (!clrCSSmatch) clrCSSmatch = el.className.match(/hlx\d/);
	if (!clrCSSmatch) {
		console.error("removeColor()",el," no color class found in "+el.classList);
		return;
	}
	const clrCSSclass = clrCSSmatch[0];
	const now = T_ms(); //DIAGN

	if (rlvntBottomChunkEl==null || rlvntTopChunkEl==null) {alertRed("FIXME #3f5a7da8:<br>Lost track of output chunks.<br>Scrolled outside output boundary?"); return;}
	
	if (searchWorkQueue.len()!=0) {console.warn("removeColor", el, "searchWorkQueue.len="+searchWorkQueue.len());}
	// CAVEAT #654a9561 Make sure GUIworkQueue does not do things that can interfere with searchWorkQueue
	// #34b1c95d not total COPYPASTE:
	searchWorkQueue.push( awaitPendingTaskAsynFn(deleteOldestChunkAfterPause) );
	searchWorkQueue.push( awaitPendingTaskAsynFn(findRelevantChunks) );
	searchWorkQueue.push( removeColorFromChunks, [rlvntBottomChunkEl, rlvntTopChunkEl, clrCSSclass], 20 ); // take a breath
	if (outputParntEl.firstElementChild != rlvntTopChunkEl)
		searchWorkQueue.push( removeColorFromChunks, [rlvntTopChunkEl.previousElementSibling, outputParntEl.firstElementChild, clrCSSclass] );
	if (outputParntEl.lastElementChild != rlvntBottomChunkEl)
		searchWorkQueue.push( removeColorFromChunks, [outputParntEl.lastElementChild, rlvntBottomChunkEl.nextElementSibling, clrCSSclass] );
	searchWorkQueue.push( (t)=>console.log("removeColor "+clrCSSmatch+" '"+el.textContent+"' in "+round(T_ms()-t)+"ms  (except browser rendering)"), now);  //DIAGN COPYPASTE #499503cc
}
		
		
function removeColorFromChunks([first, last, clrCSSclass]) { //TODO async function, await pause(0) between chunks ?
	for ( let chunkEl=first; chunkEl; chunkEl = (chunkEl==last)?null:chunkEl.previousElementSibling ) {
		for ( let lineEl=chunkEl.lastElementChild; lineEl; lineEl=lineEl.previousElementSibling ) {
			Array.from(lineEl.getElementsByClassName(clrCSSclass)).forEach(
				(x) => { x.outerHTML = x.innerHTML; }
			)
		}
	}
}



//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
const infoLineDIV = getPrototypeEl("infoLine");
var infoLineNr = 0;
//---
function highlight(str) { 
	alertBlue("Searching...");
	setTimeout(()=>{highlight_(str);}, 50); //10ms not working, 20ms unreliable
}

function highlight_(str) { // max. 77777 finds #247ff72c
	//TODO
	//TODO #49d13562 similar to removeColor:
	// Task 1) Relevant chunks: Start with infoLineEl=null and store A={numFound up to first found infoLineEl (if any)}
	//         If 2nd+ infoLineEl found, write stuff in it, and so on. Finally store C={last found infoLineEl (if any)} B={numFound since last infoLineEl, if any, else B=A} 
	// Pause for Browser rendering
	// Task 2) Continue to top: if C add B to it. If C==null A=B
	// Task 3) From bottom infoline to before rlvntBottomChunkEl. To last infoLineEl found add A.
	
	const strLen = str.length;
	if (!strLen) { return false; }
	
	const now = performance.now();
	var numFound = 0, stopped = false;
	//var findsMp = new Map(); //TODO #141a941d Attach to infoLineEl. Count different case insensitive finds?
	var infoLineEl = outputEl.lastChild; //could be empty chunk
	
	if (!infoLineEl || !infoLineEl.classList.contains("infoLine")) {
		outputEl.append(infoLineEl=infoLineDIV.cloneNode(true));
		infoLineEl.id=`iL${infoLineNr++}`;
	}
		
	const hlSPAN = document.createElement("span");
	if (userHighlColr=="") {
		hlSPAN.className = `hl${highlightCounter}`;
	} else {
		hlSPAN.className = `hlx${userHighlColr}`;
	}

	if (caseSensitive) {
		var findIndx = (s)=>s.indexOf(str);  //#A
	} else {  // Turn search str into regexp
		// str.replace(...): returns a new string, leaves original string unchanged.
		const rgxp = new RegExp( str.replace(/[-[\]{}()*+?.,\\^$|#\s]/g, "\\$&"), "gi");
		var findIndx = (s)=>s.search(rgxp); // No significant performance difference to indexOf
	}
	
	for ( let chunkEl=outputParntEl.lastChild; chunkEl; chunkEl=chunkEl.previousElementSibling ) {
		for ( let lineEl=chunkEl.lastChild; lineEl; lineEl=lineEl.previousElementSibling ) {  // >2x faster than for (const lineEl of outputEl.children) [code when there were no chunks]
		
			if ( prefix=lineEl.querySelector(".pf") ) {
				// TODO treat prefix node: do ... while
				// 
				// Treating Text nodes (nodeType==3) only,
				// so, excluding already highlighted text inside SPANs.
				let indx, part2;
				for ( chldNd of Array.from(lineEl.childNodes) ) { // Note: The NodeList being live means that its content is changed each time new children are added or removed.
					if (chldNd.nodeType==3) { // Node.TEXT_NODE
						while ( (indx=findIndx(chldNd.data)) != -1 ) { //#A
							chldNd = (part2=chldNd.splitText(indx)).splitText(strLen);
							const clone = hlSPAN.cloneNode();
							clone.prepend(lineEl.removeChild(part2));
							lineEl.insertBefore(clone, chldNd);
							if (++numFound >= 77777) { stopped = true; break; } // 77777 HARDWIRED #247ff72c
						}
					}
					if (numFound >= 77777)  { stopped = true; break; }; // #247ff72c
				}
			}
		}
	}

	// Display search str marked in search color on infoLine
	if (numFound!=0) {
		const clone = hlSPAN.cloneNode();
		clone.textContent = str;
		infoLineEl.append(` ${numFound}x`, clone);
	} else {
		if ( infoLineEl.textContent.indexOf(` 0x'${str}'`) == -1 
		     && infoLineEl.textContent.indexOf(` x${str}`) == -1 ) { //FIXME Quick bad fix (TODO #141a941d )
			infoLineEl.append(` 0x'${str}'`); 
		}
	}
	
	// Increment highlight color counter and update color dialog.
	if (numFound!=0) {
		setTimeout(()=>{window.getSelection().removeAllRanges();}, 2);
		if (userHighlColr=="") {
			//Mark next color to come
			for (var i=0; i<hlColrsNum; i++) { document.getElementById("hl"+i+"ID").classList.remove("boxed"); }
			highlightCounter = (highlightCounter+1)%hlColrsNum;
			document.getElementById("hl"+highlightCounter+"ID").classList.add("boxed");
		}
	}

	console.log(`highlight('${str}') found ${numFound} in ${Math.round(performance.now()-now)}ms `);

	if (numFound==0)
		alertRed("Nothing found.");
	else if (stopped)
		alertRed("Found "+numFound + "+ <br>Limit reached, STOPPED.");
	else
		alertBlue("Found "+numFound);

	return numFound;
}
	//OLD Performance: 4xJSLoop
	//Not case sensitive: (RegExp search)
	//highlight('Lorem') found 8000 in 154ms 
	//highlight('dolor') found 32000 in 346ms 
	//highlight('consectetur') found 8000 in 166ms 
	//  Reload, case sensitive: (String search)
	//highlight('Lorem') found 8000 in 123ms 
	//highlight('dolor') found 32000 in 320ms 
	//highlight('consectetur') found 8000 in 177ms 

//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< function highlight(str)
	
