package florifulgurator.logsocket.javalggr;

import static florifulgurator.logsocket.utils.MGutils.*;
import florifulgurator.logsocket.YSeq.Tuple;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;




public class Filter {

public static record Rule(String realm, String[] labels, String strength) {
	public String toString() {
		return (realm==null ? "*" : realm) + ">"
		+ (labels==null ? "*" : Arrays.stream(labels).collect(Collectors.joining("&"))) + "="
		+ strength;
	}
}

//DEV #6cb5e491
static Set<Rule>                         rules = new LinkedHashSet<>(); // insertion order to keep things tidy
static Map<String, LinkedHashSet<Rule>>  label2ruleSet = new ConcurrentHashMap<>(); // to accelerate filter

// DOCU Rule string syntax:
// realm>#label1#label12&label3=M  // #label1#label12 treated as one label. "M" ignore *M*essages, but track existence and make ID
//  *>#label1&#label2&label3=E     // 3 labels. "E" ignore *E*xistence. No finalize(), no ID for new loggers
// rlm>*=M


static Rule parseAdd(String ruleStr) {
	int a = ruleStr.indexOf('>');
	int b = ruleStr.indexOf('=');
	// Bug safety:
	if (a<1) throw new FilterRuleError("Error1 "+ruleStr);
	if (b==-1) throw new FilterRuleError("Error2 "+ruleStr);
	if (a+1>=b) throw new FilterRuleError("Error3 "+ruleStr);
	if (b==ruleStr.length()-1) throw new FilterRuleError("Error5 "+ruleStr);

	String[] lblL = null;
	if (ruleStr.charAt(a+1)!='*') {
		if (ruleStr.charAt(a+1)!='#') throw new FilterRuleError("Error4 "+ruleStr);
		lblL = ruleStr.substring(a+1, b).split("&");
		Arrays.sort(lblL);
		//Duplicates allowed. Generator of rule is responsible for uniqueness. 
	}	
	
	Rule rule = new Rule(
		(a==1&&ruleStr.charAt(1)=='*') ? null : ruleStr.substring(0, a-1),
		lblL,
		ruleStr.substring(b+1)
	);
	if (!rules.add(rule)) throw new FilterError("Filter rule "+ruleStr+" already exists: "+rule);
	
	if (lblL!=null) {
		// Add labels to accelerator 1
		for(String lbl: lblL) {
			LinkedHashSet<Rule> ruleSet = label2ruleSet.get(lbl);
			if (ruleSet==null) {
				ruleSet = new LinkedHashSet<Rule>();
				label2ruleSet.put(lbl, ruleSet);
			}
			ruleSet.add(rule);
		}
	}
	
	return rule;
}


// Apply rule to existing lggrs
static void applyRule(Rule rule) { //DEV #23e526a3  //DEV #6cb5e491

	if (rule.realm==null || rule.labels==null || rule.labels.length!=1 ) {//All realms || all labels || more than 1 label
		LogSocket.complain("TODO #6776bf39 filter1 rule="+rule);
		LogSocket.sendMsg("/ALERT_R TODO #6776bf39 Filter:<br/>"+escapeHTML(rule.toString()));
		return;
	}
	
	String cmdArgs="";
	int goneNum=0, fltrdNum=0, alreadyfltrdNum=0;
	boolean ignore = "E".equals(rule.strength);

	synchronized(LogSocket.realmLabel2LggrList) {
		String rlmLbl = rule.realm+rule.labels[0];
		LogSocket.filteredRealmLabel.put(rlmLbl, rule.strength);
		
		ArrayList<Tuple<Integer,WeakReference<Lggr>>> lggrList = LogSocket.realmLabel2LggrList.get(rlmLbl);
		if (lggrList==null) {
			LogSocket.sendMsg("/ALERT_B Filter:<br/>No active loggers found.");
			return;
		}
		
		for (Tuple<Integer,WeakReference<Lggr>> tpl : lggrList) {
			Lggr l = tpl.toObject().t2.get();
			if ( l!=null ) {
				if ( l.on ) {
					l.on=false;
					l.ignore = ignore;
					fltrdNum++;
				} else {
					alreadyfltrdNum++;
				}
				cmdArgs += " "+l.shortId;

			} else goneNum++;
		}
	}
		
	if (!cmdArgs.isEmpty()) {
		String cmd = ignore?"!IGNORED":"!SILENCED";
		try {
			LogSocket.websocket.sendText(cmd+cmdArgs );
			LogSocket.websocket.sendText("/ "+"LogSocket /"+LogSocket.Nr+": Filter rule \""+rule+"\" stopped "+fltrdNum+" logger instance"+(fltrdNum==1?"":"s")
				                   +" - "+alreadyfltrdNum+" already stopped, "+goneNum+" gone." );
			LogSocket.sendMsg("/ALERT_B Filter: Stopped "+fltrdNum+"<br/>"+alreadyfltrdNum+" already stopped, "+goneNum+" gone.");
			//TODO #7594d994 client consistency test
		} catch (IOException e) {
			System.err.println("!!!- LogSocket_ERROR_X Filter.applyRule (TODO) "+e.getMessage());
		}
	}
}







}
