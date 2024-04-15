package florifulgurator.logsocket.javalggr;

import static florifulgurator.logsocket.utils.MGutils.*;
import florifulgurator.logsocket.utils.Tuple;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;




public class Filter {

public static record Rule(String realm, String[] labels, long labelsCode, String result) {
	public String toString() { return (realm==null?"*":realm) + ">"	+ (labels==null?"*":Arrays.stream(labels).collect(Collectors.joining("&"))) + "="	+ result;	}
	public String toString2() {	return (realm==null?"":realm) + (labels==null?"":Arrays.stream(labels).collect(Collectors.joining("")) ); }

}

static Set<String>                       rules = ConcurrentHashMap.newKeySet(); // insertion order to keep things tidy

static Map<String, Map<Long, String>> realm2labelsCode2RuleResult = new ConcurrentHashMap<>();




//https://stackoverflow.com/a/39506822/3123382
//Benchmark                    Mode  Cnt  Score    Error  Units
//MyBenchmark.hasMap_get       avgt    5  0.015 ?  0.001   s/op
//MyBenchmark.hashMap_put      avgt    5  0.029 ?  0.004   s/op
//MyBenchmark.skipListMap_get  avgt    5  0.312 ?  0.014   s/op
//MyBenchmark.skipList_put     avgt    5  0.351 ?  0.007   s/op

// DOCU Rule string syntax:
// realm>#label1#label12&label3=M  // #label1#label12 treated as one label. "M" ignore *M*essages, but track existence and make ID
//  *>#label1&#label2&label3=E     // 3 labels. "E" ignore *E*xistence. No finalize(), no ID for new loggers
// rlm>*=M


static Rule parseRuleString(String ruleStr) {
	long labelsCode = 0L;
	int a = ruleStr.indexOf('>');
	int b = ruleStr.indexOf('=');
	// Bug safety:
	if (a<1) throw new FilterRuleError("Error1 "+ruleStr);
	if (b==-1) throw new FilterRuleError("Error2 "+ruleStr);
	if (a+1>=b) throw new FilterRuleError("Error3 "+ruleStr);
	if (b==ruleStr.length()-1) throw new FilterRuleError("Error5 "+ruleStr);

	// Split labels and register:
	String[] lblL = null;
	if (ruleStr.charAt(a+1)=='*') {
		labelsCode = 0L;
	} else {
		if (ruleStr.charAt(a+1)!='#') throw new FilterRuleError("Error4 "+ruleStr);
		lblL = ruleStr.substring(a+1, b).split("&");
		labelsCode = registerLabels( Arrays.stream(lblL) );
	}

	// New rule:
	Rule rule = new Rule(
		(a==1&&ruleStr.charAt(1)=='*') ? null : ruleStr.substring(0, a-1),
		lblL,
		labelsCode,
		ruleStr.substring(b+1)
	);
	// really new?
	if (!rules.add(rule.toString2())) throw new FilterError("Filter rule "+ruleStr+" already exists: "+rule);
	
	//Register rule:
	Map<Long, String> labelsCode2RuleResult = realm2labelsCode2RuleResult.get(rule.realm);
	if (null==labelsCode2RuleResult) {
		labelsCode2RuleResult = new ConcurrentHashMap<>();
		realm2labelsCode2RuleResult.put(rule.realm,labelsCode2RuleResult);
	}
	labelsCode2RuleResult.put(labelsCode, rule.result);
		
	return rule;
}


// DOCU #681a8b4e long integer labelsCode
// There can be max. 63 sub-labels per LogSocket. Each new sub-label gets assigned a bit.
// The order of the sub-labels in the labels string assigned to a logger will not be
// changed, but for filtering the labels string is represented by the bit pattern
// given by OR-ing the label bits in a long integer.
// If zero it means "*" in the rule String.

static Map<String, Long> label2Nr = new ConcurrentHashMap<>();
static long nextLblNr = 1L;

public static long registerLabels(Stream<String> labelsStream) {
	long[] labelsCode = {0L}; // effectively final
	Long[] labelNr = {0L};

	labelsStream.forEach( lbl -> {
		if( null==(labelNr[0]=label2Nr.get(lbl)) ) {
			label2Nr.put(lbl,nextLblNr);
			labelsCode[0] |= nextLblNr;
			if( (nextLblNr<<=1) == -9223372036854775808L ) {
				//TODO #681a8b4e Exlude from filter
				throw new FilterError("Max. number of labels reached");
			}
		} else {
			labelsCode[0] |= labelNr[0];
		}
	});
	
	return labelsCode[0];
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
	boolean ignore = "E".equals(rule.result);
	
	
	
	
	
	

	synchronized(LogSocket.realmLabel2LggrList) {
		String rlmLbl = rule.realm+rule.labels[0];
		LogSocket.filteredRealmLabel.put(rlmLbl, rule.result);
		
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




// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
public static class TEST {

	public static void main(String[] args) {

		System.out.println(">>>>>>>>>> Testing Filter >>>>>>>>>>\n");
		for(int i=1;i<70;i++) {
			System.out.println(i+" "+Long.toBinaryString((nextLblNr<<=1))+" "+nextLblNr );
		}

	}
}


}
