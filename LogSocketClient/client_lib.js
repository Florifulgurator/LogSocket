console.log("client_lib.js: Hello World! 9");


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Queue for async functions for sequential execution.
// Otionally with pause in between. 
// Functions can be added "while" the queue is working.
function MakeAsyncQueue (name) {
	var queue = [];
	var notifyWorker;
	var queueNotEmptyPromise = new Promise( (resolve)=>{notifyWorker=resolve;} );
	var working;
	
	async function queueWorker() {
		console.log("Hello World! queueWorker() for "+name+" awaiting work.");
		working = true;
		while (working) { await queueNotEmptyPromise;
			if (!queue.length) break; // Promise broken, stop work
			do { const [func, arg, millis] = queue.shift();
				if (millis) await pause(millis);
				try { await func(arg); // On await (even if nothing to wait) main thread is free for next task in event loop (e.g. add more functions to queue)
				} catch (error) { console.error(error, func, arg); }
			} while (queue.length);
			queueNotEmptyPromise = new Promise( (resolve)=>{notifyWorker=resolve;} );
		}
		console.log("queueWorker() for "+name+" done.");
	}
	queueWorker();
	// TODO #48f9a725 Test garbage collection: queueWorker keeps running/awaiting when out of scope. 
	
	return { // "public methods"
		push: (func, arg, pauseMillis) => {
			queue.push( [func, arg, pauseMillis] );
			if (queue.length==1) notifyWorker(); // Was ==0 and worker awaiting queueNotEmptyPromise
		},
		len: () => queue.length,
		start: () => { !working && queueWorker(); }, 
		stop: () => { queue=[]; working=false; notifyWorker(); }
	};	
}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Always use *** await pause(111) ***   Else perhaps no pause. 
async function pause(millis) { return new Promise( reslv => setTimeout(reslv, millis) ); }
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<




// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// setTimeout(...) variants setting the timeout only once when called repeatedly.
// No function args for good reason.
// >>> Don't forget the new operator! <<<
// * First variant: No reset of timer upon relaunch
function OnceAfterTimeoutTask(func, ms) {
	this.cllbck = func;
	this.ms = ms;
	this.notPending = true;
	this.launch = function () {
		if (this.notPending === void null) { throw new Error("No object. Consult your JavaScript linguist."); return;}
		if (this.notPending) {
			this.notPending = false;
			setTimeout(
				() => {
					this.cllbck();
					this.notPending = true;
				},
				this.ms 
			);
		}
	}
}
//--- --- ---
// * Second variant: If ms2 is given, set timer again if relaunched shortly (ms2) before timeout. 
// * May resolve a promise for a waiting async function (see function below)
function OnceAfterPauseTask(func, ms, ms2) {
	this.cllbck = func;
	this.ms = ms;
	this.ms2 = ms2?ms2:0;
	this.notPending = true;
	this.launchTime;
	this.lastRelaunch;
	this.resolvePromise; // in case someone else is waiting for it; //TODO  #7e7d8a17: List of Promise resolve()rs
	this.launch = function () {
		if (this.notPending === void null) { throw new Error("No object. Consult your JavaScript linguist."); return;}
		if (this.notPending) {
			this.notPending = false;
			this.launchTime = performance.now();
			this.lastRelaunch = this.launchTime;
			this.timeoutID = setTimeout(
				() => { 
					try {
						if ( this.ms2!=0 && this.lastRelaunch-this.launchTime > this.ms-this.ms2 ) {
							this.notPending = true;
							this.launch();
						} else {
							this.cllbck();
							this.notPending = true;
							this.resolvePromise && this.resolvePromise();
						}
					} catch (error) { console.error(error); }
					finally { this.notPending = true; }
				},
				this.ms 
			);
		} else {
			this.lastRelaunch = performance.now();
		}
	}
	this.cancel = function () { clearTimeout(this.timeoutID); }
}
//---
function awaitPendingTaskAsynFn(obj) {
	return async function () {
		await new Promise( (resolve) => {
			if (obj.notPending) resolve();
			else {
				if (obj.resolvePromise) console.error("BUG/TODO #7e7d8a17 Something is already waiting for this:", obj)
				obj.resolvePromise = resolve;
			}
		});
	}
}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
		 

// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Call a function (microtask? #note5), then pause before next call.
// E.g. for immediate GUI updates
// or being called by a OnceAfterTimeoutTask.
function OnceThenPauseTask(func, ms) {
	this.cllbck = func;
	this.ms = ms;
	this.notPausing = true;
	this.notAgain = true;
	this.timeoutID;
	this.launch = function () {
		if (this.notPausing) {
			this.cllbck();
			this.notAgain = true;
			this.notPausing = false;
			this.timeoutID = setTimeout(
				() => { 
					if ( !this.notAgain ) {
						this.notAgain = true;
						this.notPausing = true;
						this.launch();
						return;
					}
					this.notPausing = true;
				},
				this.ms
			);
		} else if (this.notAgain) this.notAgain = false;
	}
}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// Launch func1 immediately, then pause ms before calling func2
// If launch() is called again while pausing, prolong pause.
function OnceThenPauseThenFinishTask(func1, func2, ms) {
	this.cllbck1 = func1;
	this.cllbck2 = func2;
	this.ms = ms;
	this.pausing = false;
	this.pauseAgain = false;
	this.launchTime;
	this.lastRelaunch = -1;
	this.timeoutID;
	this.launch = function () {
		if (this.pausing) {
			this.pauseAgain = true;
			this.lastRelaunch = performance.now();
		} else {
			let mms = this.ms;
			if (this.pauseAgain) {
				mms = Math.max(3, Math.floor(this.lastRelaunch - this.launchTime) );
			} else {
				this.launchTime = performance.now();
				this.cllbck1();
			}
			this.pauseAgain = false;
			this.pausing = true;
			this.lastRelaunch = -1;

			this.timeoutID = setTimeout(
				() => {
					this.pausing = false;
					if ( this.pauseAgain ) {
						this.launch();
					} else {
						this.cllbck2();
						this.lastRelaunch = -1;
					}
				},
				mms
			);
		} 
	}
	this.finish = function () {
		if (this.pausing) {
			clearTimeout(this.timeoutID);
			this.cllbck2();
			this.pausing = false;
			this.pauseAgain = false;
			this.lastRelaunch = -1;
		}
	}
}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<



// Math utils >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

function avg(arr) { return arr.reduce((acc,x)=> acc+x)/arr.length;}

function round4(x) {return Math.round(10000*x)/10000;}
function round3(x) {return Math.round(1000*x)/1000;}
function round2(x) {return Math.round(100*x)/100;}
function round1(x) {return Math.round(10*x)/10;}
function round(x)  {return Math.round(x);}
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<




// https://stackoverflow.com/questions/11547672/how-to-stringify-event-object
function stringify(object, depth=0, max_depth=5) {

	if (depth > max_depth) return 'Object';

	const obj = {};
	for (let key in object) {
		let value = object[key];
		if (value instanceof Node)
			// specify which properties you want to see from the node
			value = { id: value.id };
		else if (value instanceof Window) value = 'Window';
		else if (value instanceof Object) value = stringify(value, depth + 1, max_depth);
		obj[key] = value;
	}

	return depth ? obj : JSON.stringify(obj);
}

