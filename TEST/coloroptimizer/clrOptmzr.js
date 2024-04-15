
var avgStripeRgbDist_wanted = 24;  //was 16 //#AS set null to get avg of input



var colors0=[];
var colors1=[];
var colors2=[];
var clrsLength = 0;

var black = new Color("black",0,0,0);
var white = new Color("white",255,255,255);

var avgDist2Black = 0, avgDist2White = 0;
var avgStripeRgbDist = 0;
var avgMinDist2All = 0;
var MAXrgbTrWhtMinDistToAll = 0, MINrgbTrWhtMinDistToAll = 10000;

function Color (name, r,g,b, tr) {
	this.name=name;
	this.r=Number.parseFloat(r);this.g=Number.parseFloat(g);this.b=Number.parseFloat(b);
	this.tr=Number.parseFloat(tr);
}

function trOnWhite2noTrColor(c) {  // #655e8593 
	return new Color( c.name, c.r*c.tr+(1-c.tr)*255, c.g*c.tr+(1-c.tr)*255, c.b*c.tr+(1-c.tr)*255 );
}

function forgetTr(c) {
	return new Color( c.name, c.r, c.g, c.b );
}

function color2CSSrgbTr(c) {
	var str1 = `rgb(${Math.min(255,round(c.r))}, ${Math.min(255,round(c.g))}, ${Math.min(255,round(c.b))}`;
	var str2 = isNaN(c.tr) ? ")" : `, ${round3(c.tr)})`;
	return str1+str2;
}

function rgbDist(c1,c2) { // wikipedia: color difference
	const rm=0.5*(c1.r-c2.r);
	return Math.sqrt(
		(2+rm/256)*(c1.r-c2.r)*(c1.r-c2.r)
		+4*(c1.g-c2.g)*(c1.g-c2.g)
		+(2+(255-rm)/256)*(c1.b-c2.b)*(c1.b-c2.b)
	);
}

function rgbTrWhtMinDist(c1, c2) {
	return Math.min(
		rgbDist( c1, c2 ),
		rgbDist( trOnWhite2noTrColor(c1), c2 ),
		rgbDist( c1, trOnWhite2noTrColor(c2) )
	);
}

function rgbTrWhtMinDistToAll(c, j) {
	var min = Number.POSITIVE_INFINITY;
	var d;
	for (let i=0; i<clrsLength; i++) {
		if ( i!=j && (d = rgbTrWhtMinDist(c, colors2[i])) < min )  min = d;
	}
	return min;
}

function dist2Black(c) { return rgbDist(c,black); }
function dist2White(c) { return rgbDist(c,white); }

function round3(x) {return Math.round(1000*x)/1000;}
function round2(x) {return Math.round(100*x)/100;}
function round1(x) {return Math.round(10*x)/10;}
function round(x) {return Math.round(x);}

var name2CSSr = new Map();
var changed=0;

// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// ...
function _optimize(numExprmnts) {	console.log(`_optimize(${numExprmnts}) ------------------`);

	const guessBetter=[];
 	const resltStr=[];
 	for (let i=0; i<clrsLength; i++) {guessBetter[i] = __guessBetter(i); resltStr[i] = "#"+i; }
 	 	
 	for ( let n=0; n<numExprmnts; n++) {
		let i= Math.floor(Math.random()*clrsLength);
		resltStr[i] += guessBetter[i]();
		
		MAXrgbTrWhtMinDistToAll = 0, MINrgbTrWhtMinDistToAll = 10000;
		for (let j=0; j<clrsLength; j++ ) {
			const res = round1(rgbTrWhtMinDistToAll( colors2[j], j ));
			MAXrgbTrWhtMinDistToAll = Math.max(MAXrgbTrWhtMinDistToAll, res);
			MINrgbTrWhtMinDistToAll = Math.min(MINrgbTrWhtMinDistToAll, res);
		}

	}
	
	// Write in HTML table:
	for (let i=0; i<clrsLength; i++) {
		name2CSSr.get(colors2[i].name).style.backgroundColor = color2CSSrgbTr(colors2[i]);
		name2CSSr.get(colors0[i].name).style.backgroundColor = color2CSSrgbTr(forgetTr(colors2[i]));
		name2CSSr.get(colors1[i].name).style.backgroundColor = color2CSSrgbTr(trOnWhite2noTrColor(colors2[i]));
		
		document.getElementById("distRgb2Tr"+i).textContent = ""+round1(rgbDist( colors2[i], trOnWhite2noTrColor(colors2[i]) ));
		document.getElementById("tr"+i).textContent = round3(colors2[i].tr);
		document.getElementById("dist2All"+i).textContent = round1(rgbTrWhtMinDistToAll( colors2[i], i ));
		document.getElementById("dist2Black"+i).textContent = round(dist2Black(colors2[i]));
		document.getElementById("dist2White"+i).textContent = round(dist2White(colors2[i]));

		console.log(resltStr[i]);
	}
}
//---
var goodDeltaTR = [];
var goodDeltaR = [];
var goodDeltaG = [];
var goodDeltaB = [];
//---
function __guessBetter(i) {
	goodDeltaTR[i] = 0.0;
	goodDeltaR[i] = 0.0;
	goodDeltaG[i] = 0.0;
	goodDeltaB[i] = 0.0;
	return  () => { //...
		var newTrClr = {...colors2[i]};

		var oldrgbDist = rgbDist( colors2[i], trOnWhite2noTrColor(newTrClr) );
		var deltaTr = goodDeltaTR[i] + (Math.random()-0.5)* Math.random()*0.2; // 0.5*0.2 == 0.1  // [-0.1,0.1]
			goodDeltaTR[i] = goodDeltaTR[i] * 0.75; // 0.75**5 == 0.24

		newTrClr.tr = newTrClr.tr + deltaTr;

		// 1) Transparency improvement?
		if ( Math.abs( rgbDist( colors2[i], trOnWhite2noTrColor(newTrClr) ) - avgStripeRgbDist ) < Math.abs( oldrgbDist - avgStripeRgbDist ) ) {
			goodDeltaTR[i] = deltaTr;
			colors2[i] = {...newTrClr};
			changed++;
			return "T"; //  <<<<<<<<<<=
		}
		
		newTrClr.tr = newTrClr.tr - deltaTr; // Back to fresh from colors2[i]

		var old_rgbTrWhtMinDistToAll = rgbTrWhtMinDistToAll( newTrClr, i );

		// 2) Close to maximum min-distance to all? (Better maximise  min-distance of others...)
		if (old_rgbTrWhtMinDistToAll > MAXrgbTrWhtMinDistToAll - 2.5 ) return "+"; //  <<<<<<<<<<=
		
		var oldDist2Black = dist2Black(newTrClr);
		var oldDist2White = dist2White(newTrClr);
		var deltaR = goodDeltaR[i] + (Math.random()-0.5)* Math.random()*3.5; goodDeltaR[i] = goodDeltaR[i] * 0.8; 
		var deltaG = goodDeltaG[i] + (Math.random()-0.5)* Math.random()*3.5; goodDeltaG[i] = goodDeltaG[i] * 0.8; 
		var deltaB = goodDeltaB[i] + (Math.random()-0.5)* Math.random()*3.5; goodDeltaB[i] = goodDeltaB[i] * 0.8; 
		newTrClr.r = newTrClr.r + deltaR;
		newTrClr.g = newTrClr.g + deltaG;
		newTrClr.b = newTrClr.b + deltaB;
		


		// 3b) Distance to white near average?
		if ( Math.abs( dist2White(newTrClr) - avgDist2White ) + (Math.random()-0.5)*3 >= Math.abs( oldDist2White - avgDist2White ) )  return "w"; //  <<<<<<<<<<=
		// 3a) Distance to black near average?
		if ( Math.abs( dist2Black(newTrClr) - avgDist2Black ) + (Math.random()-0.5)*3 >= Math.abs( oldDist2Black - avgDist2Black ) )  return "b"; //  <<<<<<<<<<=
		
		// 4) Improvement of min-distance to all?
		const dist = rgbTrWhtMinDistToAll( newTrClr, i );
		if ( false ||
			 dist > MINrgbTrWhtMinDistToAll 
			 && ( dist > old_rgbTrWhtMinDistToAll - 3.5 || dist < MINrgbTrWhtMinDistToAll + 5.5 || dist > MAXrgbTrWhtMinDistToAll - 10.5 )
		   )  {
			goodDeltaR[i] = deltaR;
			goodDeltaG[i] = deltaG;
			goodDeltaB[i] = deltaB;
			colors2[i] = {...newTrClr};
			changed++;
			//avgMinDist2All+=0.5;
			
			return "C"; //  <<<<<<<<<<=
		}		
		
		return "-"; //  <<<<<<<<<<=
	}; //...//
}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<



// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// ...
function print2Console() {
	var outputStr = "/* ***************************** */\n"
	+ "/* OUTPUT of clrOptmzr.js:       */\n"
	+ "/* Logger background colors      */\n"
	+ "/* ***************************** */\n"
	+ "/* #"+ Math.round(Math.random()*1679616).toString(36).padStart(4,"0") +" */\n"
	+ "/* "+(new Date()).toString()+" */\n"
	+ "/*  */\n";
	+ "/* bgX2: Source of bgX0 and bgX1: (used only by clrOptmzr.js) */\n";
	for ( let i=0; i<clrsLength; i++) outputStr += "."+colors2[i].name+" { background-color: "+color2CSSrgbTr( colors2[i] )+";}\n"		
	outputStr += "/* bgX0 == bgX2 without transparency: */\n";
	for ( let i=0; i<clrsLength; i++) outputStr += "."+colors0[i].name+" { background-color: "+color2CSSrgbTr(forgetTr( colors2[i] ) )+";}\n"		
	outputStr += "/* bgX1 == no-transparency RGB from bgX2 being transparent on white: */\n";
	for ( let i=0; i<clrsLength; i++) outputStr += "."+colors1[i].name+" { background-color: "+color2CSSrgbTr(trOnWhite2noTrColor( colors2[i] ) )+";}\n"		
	outputStr += "/* END OUTPUT of clrOptmzr.js <<<<<<<<<<<<<<<<<<<<<<<<<<<<<< */\n";

	console.log(outputStr);
}


window.addEventListener("load", 
() => { 
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
	var c, cssStr = "";
	
	for (let i=0, n=cssr.length; i<n; i++ ) {
		let t=cssr[i].selectorText;
		if ( c = t.match(/^\.bg([A-Z])0$/)) {
			let bgC = cssr[i].style.backgroundColor
			let [r, g, b, tr] = bgC.substring(bgC.indexOf("(")+1, bgC.indexOf(")")).split(", ");
			colors0.push(new Color(c[0].substring(1), r,g,b));
			name2CSSr.set( c[0].substring(1), cssr[i]); console.log(c[0].substring(1), cssr[i]);
			
		} else if ( c = t.match(/^\.bg([A-Z])1$/)) {
			let bgC = cssr[i].style.backgroundColor
			let [r, g, b, tr] = bgC.substring(bgC.indexOf("(")+1, bgC.indexOf(")")).split(", ");
			colors1.push(new Color(c[0].substring(1), r,g,b));
			name2CSSr.set( c[0].substring(1), cssr[i]); console.log(c[0].substring(1), cssr[i]);
			
		} else if ( c = t.match(/^\.bg([A-Z])2$/)) {
			let bgC = cssr[i].style.backgroundColor
			let [r, g, b, tr] = bgC.substring(bgC.indexOf("(")+1, bgC.indexOf(")")).split(", ");
			colors2.push(new Color(c[0].substring(1), r,g,b, tr));
			name2CSSr.set( c[0].substring(1), cssr[i]); console.log(c[0].substring(1), cssr[i]);

		}		
	}
	clrsLength = colors0.length;
	if ( clrsLength==0 ) alert("Wrong CSS file? There will be ERRORS...");
	
	const td = document.createElement("td");
	var i=0;
	for (i=0; i<clrsLength; i++) {
//		console.log("."+colors1[i].name+" { background-color: "+color2CSSrgbTr(trOnWhite2noTrColor( colors2[i] ) )+";}" );
		
		var clone = td.cloneNode();
		clone.textContent = colors2[i].tr;
		clone.id="tr"+i;
		document.getElementById("trID").append(clone);

		clone = td.cloneNode();
		clone.textContent = colors2[i].name;
		document.getElementById("nameID").append(clone);

		clone = td.cloneNode();
		clone.className = `${colors2[i].name} mnspcd pre`;
		clone.textContent = "         ";
		document.getElementById("transpID").append(clone);
		
		clone = td.cloneNode();
		clone.className = `${colors1[i].name} mnspcd pre`;
		clone.textContent = "         ";
		document.getElementById("bg1ClrsID").append(clone);

		clone = td.cloneNode();
		clone.className = `${colors0[i].name} mnspcd pre`;
		clone.textContent = "         ";
		document.getElementById("bg0ClrsID").append(clone);
	
		clone = td.cloneNode();
		clone.textContent = round1(rgbDist(colors0[i],colors1[i]));
		avgStripeRgbDist += rgbDist(colors0[i],colors1[i]); //#AS 
		document.getElementById("distID").append(clone);

		clone = td.cloneNode();
		clone.textContent = round(dist2Black(colors0[i]));
		avgDist2Black += dist2Black(colors0[i]);
		clone.id="dist2Black"+i;
		document.getElementById("dist2BlackID").append(clone);

		clone = td.cloneNode();
		clone.textContent = round(dist2White(colors0[i]));
		avgDist2White += dist2White(colors0[i]);
		clone.id="dist2White"+i;
		document.getElementById("dist2WhiteID").append(clone);
		
		clone = td.cloneNode();
		clone.textContent = round1(rgbDist( colors2[i], trOnWhite2noTrColor(colors2[i]) )) ;
		clone.id="distRgb2Tr"+i;
		document.getElementById("distRgb2TrID").append(clone);
		
		clone = td.cloneNode();
		const res = round1(rgbTrWhtMinDistToAll( colors2[i], i ));
		MAXrgbTrWhtMinDistToAll = Math.max(MAXrgbTrWhtMinDistToAll, res);
		MINrgbTrWhtMinDistToAll = Math.min(MINrgbTrWhtMinDistToAll, res);
		clone.textContent = res ;
		avgMinDist2All += res ;
		clone.id="dist2All"+i;
		document.getElementById("dist2AllID").append(clone);		
	}
	
	avgDist2Black=avgDist2Black/i; avgDist2White=avgDist2White/i; 
	avgStripeRgbDist = avgStripeRgbDist/i;
	avgMinDist2All=avgMinDist2All/i;
	
	clone = td.cloneNode();
	clone.textContent = round1(avgDist2Black);
	document.getElementById("dist2BlackID").append(clone);
	
	clone = td.cloneNode();
	clone.textContent = round1(avgDist2White);
	document.getElementById("dist2WhiteID").append(clone);

	clone = td.cloneNode();
	clone.textContent = "avg=" + round1(avgStripeRgbDist) + (avgStripeRgbDist_wanted? " wanted="+avgStripeRgbDist_wanted : "");  //#AS
	document.getElementById("distID").append(clone);
	
	if (avgStripeRgbDist_wanted)	avgStripeRgbDist = avgStripeRgbDist_wanted; //#AS

		
}, true); 
