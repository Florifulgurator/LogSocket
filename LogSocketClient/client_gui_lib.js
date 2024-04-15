/**
 * 
 */

// HTML utils >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
function getPrototypeEl(name) {
	let el = document.getElementById(name+"PRTTP").firstElementChild.cloneNode(true);
	document.getElementById(name+"PRTTP").remove();
	return el;
}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Context popup menu infrastructure
//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

// There can be only one open context menu (i.e. clicked element dependent menu).
// Data for/about open context menus: 
var cntxtMenuData={};
const contextMenuElList = [cntxtMenu1El, cntxtMenu3El];
//---
function hideContextMenus() {
	// Called before contextMenuX(ev)
	if (!cntxtMenuData.targetEl) return; // performance
	
	for (cntxtMnE of contextMenuElList) {
		if ( cntxtMnE.style.display!="" && cntxtMnE.style.display!="none" ) { // First time cntxtMnE.style.display=="" despite CSS !
			cntxtMnE.style.display = "none";
			pausePointerEvents.launch();
		}
	}
	cntxtMenuData.targetEl.classList.remove("ctxHvr");
	cntxtMenuData.targetEl = null;
}
//---
const pausePointerEvents = new OnceThenPauseThenFinishTask(
	()=> { outputParntEl.classList.add("pn");    lggrListEl.classList.add("pn");},
	()=> { outputParntEl.classList.remove("pn"); lggrListEl.classList.remove("pn");},
500);
//---
function placePopupMenu(poppEl, clientX, clientY, clickedEl) {
	if (!cntxtMenuData.targetEl) console.error("BUG #764ecd48 placePopupMenu ",poppEl);

	clickedEl.classList.add("ctxHvr");
	poppEl.style.display = "inline-block";
	
	const x = Math.round(Math.max(0,
			    Math.min( clientX + window.scrollX, document.documentElement.clientWidth-poppEl.clientWidth-3 + window.scrollX )
			  ));
	const y = Math.round(Math.max(0,
	            Math.min( clientY + window.scrollY, document.documentElement.clientHeight-poppEl.clientHeight-3 + window.scrollY )
	          ));
	poppEl.style.left = x+"px";
	poppEl.style.top = y+"px";
	
	pausePointerEvents.finish();
}



// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Translate server clock tick integer into string.
// COPYPASTE #758d3edc Performance-optimized version used in function logOutput
// CAVEAT #70249bfa This version requires nanoUnit == 100  
function ClockTick2Str(T_nmbr) {
	if (T_nmbr>99) { // false for NaN
		const ms  = T_nmbr*I_nanoUnit;
		const sec = Math.floor(ms*0.001);
		if (sec!=0) {
			if (sec<10) { // 1 decimal place:
				return `${sec}.${Math.round((ms-sec*1000)*0.01)}s`;
			} else {
				const min = Math.floor(sec/60);
				if (min!=0) {
					if (min<10) {
						return `${min}m${sec-min*60}s`;
					} else {
						const hrs = Math.floor(min/60);
						if (hrs!=0)  {
							//TODO days - but enough for the time being :-)
							return `${hrs}h${min-hrs*60}m`;
						} else {
							return `${min}m`;
						}
					}
				} //sec>=10 && min==0 =>	
				else {
					return `${sec}s`; 
				}	
			}
		} //T_nmbr>99 && sec==0 =>
		else {
			if (ms>=10) return ""+round(ms);
			if (ms>=1)  { let ret=""+round1(ms); return ret.indexOf(".")!=-1 ? ret : ret+".0"; }
			return round2(ms); //should not happen
		}
	} //T_nmbr<=99 
	else {
		let ret=""+round2(T_nmbr*I_nanoUnit); if (ret.length<4) ret+="0";
		return ret;
	}
}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


// >>>> CSS class .smallslct >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Small <select> using pure CSS :hover would give bad flicker effects. Workaround requires some machinery. #490e293d
function makeSmallSELECT(selectEl) {
	selectEl.addEventListener("mouseenter", smallslct_mouseenter);
	selectEl.addEventListener("mouseout", smallslct_mouseout);
	selectEl.addEventListener("contextmenu", smallslct_contextmenu);
}
//---
var smallslct_lastHover;
function smallslct_mouseenter(ev) {
	if (smallslct_lastHover) {
		smallslct_lastHover.classList.remove("hover");
		smallslct_lastHover = null;
	}
	smallslct_lastHover = ev.target;
	smallslct_lastHover.classList.add("hover");
	smallslct_lastHover.parentElement.style.height = Math.round(smallslct_lastHover.getBoundingClientRect().height)+"px";	
	smallslct_lastHover.style.width  = Math.round(smallslct_lastHover.getBoundingClientRect().width)+"px";
}
function smallslct_mouseout(ev) {
	setTimeout( ()=>{ev.target.blur();}, 5); // Chrome would not need setTimeout, but Firefox.
	setTimeout( ()=>{
		ev.target.classList.remove("hover");
		ev.target.parentElement.style.height=null;
		ev.target.style.width=null;
	}, 222);
}
function smallslct_contextmenu(ev) {
	ev.preventDefault();
	//ev.cancelable = false; // not working
	//ev.target.click(); // not working
	//var ClickEvent = document.createEvent('MouseEvents')
	//ClickEvent.initMouseEvent('mousedown', true, false, window, 0, 0, 0, 0, 0, false, false, false, false, 0, null); //https://stackoverflow.com/a/50772808/3123382
	//ev.target.dispatchEvent(ClickEvent); // not working
	ev.target.showPicker(); // This is how you do it!
}
// <<<< CSS class .smallslct <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

