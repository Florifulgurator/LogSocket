package florifulgurator.logsocket.javalggr;

import static florifulgurator.logsocket.utils.MGutils.*;
import florifulgurator.logsocket.utils.Tuple;

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
	public String toString() { return (realm==null?"*":realm) + ">"	+ (labels==null?"*":Arrays.stream(labels).collect(Collectors.joining("&"))) + "="	+ strength;	}
	public String toString2() {	return (realm==null?"":realm) + (labels==null?"":Arrays.stream(labels).collect(Collectors.joining("")) ); }

}

static Set<String>                       rules = new LinkedHashSet<>(); // insertion order to keep things tidy

public static class LookupNode<T> {
	public Set<T> results;
	public Map<String, LookupNode<T>> more;
}
static Map<String, LookupNode<WeakReference<Lggr>>> lggrForest = new ConcurrentHashMap<>();
static Map<String, LookupNode<Rule>> ruleForest = new ConcurrentHashMap<>();


// DOCU Rule string syntax:
// realm>#label1#label12&label3=M  // #label1#label12 treated as one label. "M" ignore *M*essages, but track existence and make ID
//  *>#label1&#label2&label3=E     // 3 labels. "E" ignore *E*xistence. No finalize(), no ID for new loggers
// rlm>*=M


static Rule parseRuleString(String ruleStr) {
	int a = ruleStr.indexOf('>');
	int b = ruleStr.indexOf('=');
	// Bug safety:
	if (a<1) throw new FilterRuleError("Error1 "+ruleStr);
	if (b==-1) throw new FilterRuleError("Error2 "+ruleStr);
	if (a+1>=b) throw new FilterRuleError("Error3 "+ruleStr);
	if (b==ruleStr.length()-1) throw new FilterRuleError("Error5 "+ruleStr);

	// Split labels and sort:
	String[] lblL = null;
	if (ruleStr.charAt(a+1)!='*') {
		if (ruleStr.charAt(a+1)!='#') throw new FilterRuleError("Error4 "+ruleStr);
		lblL = ruleStr.substring(a+1, b).split("&");
		Arrays.sort(lblL);	//Duplicates allowed. Generator of rule is responsible for uniqueness. 
	}

	// New rule:
	Rule rule = new Rule(
		(a==1&&ruleStr.charAt(1)=='*') ? null : ruleStr.substring(0, a-1),
		lblL,
		ruleStr.substring(b+1)
	);
	// really new?
	String ruleString2 = rule.toString2();
	if (!rules.add(ruleString2)) throw new FilterError("Filter rule "+ruleStr+" already exists: "+rule);
	
	// Add to lookup accelerator:
	// (Could be extra method, but lblL already there.)
	String rlm = (rule.realm==null ? "" : rule.realm);
	String firstRlmLbl = rlm+(lblL!=null?lblL[0]:"");
	
	LookupNode<Rule> node = ruleForest.get(firstRlmLbl);
	if (node==null) {
		node = new LookupNode<Rule>();
		ruleForest.put(firstRlmLbl, node);
	}
	if (lblL!=null) {
		for(int i=1; i<lblL.length; i++) {
			LookupNode<Rule> nextNode;
			if (node.more==null) {
				node.more = new ConcurrentHashMap<>();
				node.more.put( lblL[i], node = new LookupNode<Rule>() );
			} else if ( null== (nextNode=node.more.get(lblL[i])) ) {
				node.more.put( lblL[i], node = new LookupNode<Rule>() );
			} else {
				node = nextNode;
			}
		}
	}
	if (node.results==null) { node.results = new LinkedHashSet<>(); }
	node.results.add(rule);
	
	return rule;
}

static void registerLggr(Lggr lgr) {


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
			Lggr l = tpl.t2.get();
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
