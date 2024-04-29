console.log("client_filter.js: Hello World! 10");



//OLD--------------------


//NEW--------------------


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
var Filter = Filter || ( () => {
// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// "Private namespace": Closure of an anonymous immediately invoked function.
// "Public methods" are in the returned object.

// Namespacing isn't really needed, but I want to check out if this works:

class Rule extends Array {
	constructor(realm, labelsL) {
		if ( labelsL.some( (s)=> s.charAt(0)!="#") ) console.warn("#7370349f Labels should start with #. Added!");
		super( realm, labelsL.map( (s)=> (s.charAt(0)!="#")?"#"+s:s ).sort() ); 
	}
	
	toString() { return`${this[0]}>${this[1].join("&")}`;}
	
	static fromLongId(longId) {
		const [realm, rest] = longId.split("/",2);
		const LgScktNr = rest.split("#",1)[0];
		const [lbl, dpl] = rest.substring(LgScktNr.length+1).split(";",2);
		const labelsL = lbl.split("#").sort().map((s)=>"#"+s);
		return new Rule(realm, labelsL);
	}
}


class RuleMap extends Map { // A "deep Map"
	constructor() { super(); }
	
	set(key, value) {
		if (key.constructor.name!="Rule") console.error("#2677dadf Not a filter Rule");;
		super.set(JSON.stringify(key), [value, key]);
		return this;
	}
	
	get(key) {
		if (key.constructor.name!="Rule") console.error("#6cef974a Not a filter Rule");;
		return super.get(JSON.stringify(key))[0]; }

	has(key) {
		if (key.constructor.name!="Rule") console.error("#456c080d Not a filter Rule");;
		return super.has(JSON.stringify(key)); }
		
	keys() {
		return super.keys().map( (k)=> super.get(k)[1] );
	}
}

return {
	RuleMap: RuleMap,
	Rule: Rule
};

// END of var Filter = ( () => { ...	<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
})();
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

//aaa = Filter.Rule.fromLongId("rlm/77#lll1#l2;3");
//bbb = new Filter.Rule("rlm", ["#lll1", "#l2"]);
//console.log("ll--------------", JSON.stringify(aaa));
//fm = new Filter.RuleMap();
//fm.set(aaa,1);
//fm.set(bbb,2);
//console.log("ll------------", fm.get(aaa));


var filter1 = new Filter.RuleMap();



function contextMenu3(ev) {
	const clickedEl = ev.target;
	cntxtMenuData.targetEl = clickedEl;

	const longId = clickedEl.textContent.trim();
	//TODO try/catch
	cntxtMenuData.rule = Filter.Rule.fromLongId(longId); //console.log("DIAGN--------------", JSON.stringify(cntxtMenuData.rule));
	cntxtMenuData.reslt = "M"; //#2e6bc1a8
	document.getElementById("ctxMn3fltrID").innerHTML = cntxtMenuData.rule.toString();
	document.getElementById("ctxMn3fltrResID").innerHTML = `=${cntxtMenuData.reslt}`;


	placePopupMenu(cntxtMenu3El, ev.clientX, ev.clientY, clickedEl);
}
//---
function _ctxMn3radio(value) { cntxtMenuData.reslt=value; document.getElementById("ctxMn3fltrResID").innerHTML = `=${cntxtMenuData.reslt}`; }
//---
function _addFltrRl() { //DEV #23e526a3
	doSend(`/FLTR_ADD ${cntxtMenuData.rule.toString()}=${cntxtMenuData.reslt}`);
	hideContextMenus();
}

// %STOPPED" //DEV silenced!!!
// Loggers that were active, but got stopped  TODO #36e7e6a9 GUI Queue
function stppdLggrs(s) {  //DEV #23e526a3
console.log("QQQQQQQQQQQQQQQQQQ stppdLggrs: "+s); return;
	const shortIdList = s.split(" ");
	for ( const shortId of s.split(" ")) {
		let el = document.getElementById(`l${shortId}`); 
		if (el) el.classList.add("sil"); //DOCU #2540b06f  Logger Status
		else console.error(`stppdLggrs(${s}) ERROR1`, el);
	}
}
//---

// %FILTER1_ADD
function filter1_add(s) {console.log("QQQQQQQQQQQQQQQQQQQQ filter1_add", s); return;
	GUIworkQueue.push( ()=> {
		for ( const rule of s.split(" ")) {
			if (filter1.has(rule)) continue; //#7ad7d40e
			filter1.set(rule, hurz=filter1ListAdd(rule));  console.log("Rule \""+rule+"\" Element", hurz);
		}
	} );
	GUIworkQueue.push( filter1ListSetMaxHeight );
}

// %FILTER1_REMOVE 
function filter1_remove(rule) {
	let el = filter1.get(rule);
	if ( !el ) {console.error(`Rule ${rule} not in filter1`); return;}
	filter1.remove(rule);
	
}

const fltrListEl = document.getElementById("filter1LstID"); // TBODY
const fltrListLinePT = getPrototypeEl("fltrListLine");
//---
function filter1ListAdd(rule) { //used only by function filter1_add(s)
		const realm = rule.split("#", 1)[0]; // #note0
		const label = rule.substring(realm.length);
		const newLine = fltrListLinePT.cloneNode(true);
		const slct_r = newLine.querySelector("select.r"); // r ealm (not r ight)
		const slct_l = newLine.querySelector("select.l"); // l abel

		slct_r.querySelector("option[selected]").textContent = realm;
		slct_l.querySelector("option[selected]").textContent = label;



		slct_r.addEventListener("change", filter1_changeEvt); slct_l.addEventListener("change", filter1_changeEvt);
		makeSmallSELECT(slct_r); makeSmallSELECT(slct_l);

		return fltrListEl.appendChild(newLine);
}
//---
const fltrSctnDIV = document.getElementById("fltrSctnDIV");
//---
function filter1ListSetMaxHeight() { // to be called after (last) filter1ListAdd
	fltrSctnDIV.style.maxHeight = null; // no joke
	fltrSctnDIV.style.maxHeight = Math.round(fltrSctnDIV.getBoundingClientRect().height)+"px";
}
