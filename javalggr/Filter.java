package florifulgurator.logsocket.javalggr;

import static florifulgurator.logsocket.utils.MGutils.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;




public class Filter {

public static record RuleLR(String realm, String[] labels, long labelsCode, String result) {
	public String toRuleRStr() {
		return (realm==null?"*":realm) + ">" + (labels==null?"*":Arrays.stream(labels).collect(Collectors.joining("&")))
	           + "=" + result;
	}
	public String toRuleStr() {
		return (realm==null?"":realm) + (labels==null?"":Arrays.stream(labels).collect(Collectors.joining("")) );
	}
	public String toString() { //DIAGN
		return this.toRuleRStr()+" labelsCode="+Long.toBinaryString(labelsCode);
	}

}

static Map<String, String>            rules = new ConcurrentHashMap<>(); // insertion order to keep things tidy

static Map<String, Map<Long, String>> realm2labelsCode2RuleResult = new ConcurrentHashMap<>();




//https://stackoverflow.com/a/39506822/3123382
//Benchmark                    Mode  Cnt  Score    Error  Units
//MyBenchmark.hasMap_get       avgt    5  0.015 ?  0.001   s/op
//MyBenchmark.hashMap_put      avgt    5  0.029 ?  0.004   s/op
//MyBenchmark.skipListMap_get  avgt    5  0.312 ?  0.014   s/op
//MyBenchmark.skipList_put     avgt    5  0.351 ?  0.007   s/op

// DOCU #1c887880 Rule string syntax:
// realm>#label1#label12&#label3=M  // #label1#label12 treated as one label. "M" ignore *M*essages, but track existence and make ID
//  *>#label1&#label2&#label3=E     // 3 labels. "E" ignore *E*xistence. No finalize(), no ID for new loggers
// rlm>*=M

@SuppressWarnings("serial")
static class FilterError extends RuntimeException {
    public FilterError(String s) { super(s); }
	@Override
    public synchronized Throwable fillInStackTrace() { return this; }
}



static RuleLR parseRuleString(String ruleRStr) throws FilterError {
	long labelsCode = 0L;
	int a = ruleRStr.indexOf('>');
	int b = ruleRStr.indexOf('=');
	// Bug safety:
	if (a<1) throw new FilterError("parseRuleString("+ruleRStr+") Error1");
	if (b==-1) throw new FilterError("parseRuleString("+ruleRStr+") Error2");
	if (b==ruleRStr.length()-1) throw new FilterError("parseRuleString("+ruleRStr+") Error3");

	// Split labels and register:
	String[] lblL = null;
	if (ruleRStr.charAt(a+1)=='*') {
		labelsCode = 0L;
	} else {
		if (ruleRStr.charAt(a+1)!='#') throw new FilterError("parseRuleString("+ruleRStr+") Error4");
		lblL = ruleRStr.substring(a+1, b).split("&");
		Arrays.sort(lblL);
		labelsCode = registerLabels( Arrays.stream(lblL) );
	}

	// New rule:
	String result = ruleRStr.substring(b+1);
	RuleLR ruleLR = new RuleLR(
		(a==1&&ruleRStr.charAt(1)=='*') ? null : ruleRStr.substring(0, a-1),
		lblL,
		labelsCode,
		result
	);
	// really new?
	String ruleStr = ruleLR.toRuleStr();
	if ( rules.containsKey(ruleStr) )  throw new FilterError("Filter rule "+ruleStr+" already exists: "+ruleLR.toRuleRStr());
	rules.put(ruleStr, ruleLR.result);
	
	//Register rule:
	Map<Long, String> labelsCode2RuleResult = realm2labelsCode2RuleResult.get(ruleLR.realm);
	if (null==labelsCode2RuleResult) {
		labelsCode2RuleResult = new ConcurrentHashMap<>();
		realm2labelsCode2RuleResult.put(ruleLR.realm, labelsCode2RuleResult);
	}
	labelsCode2RuleResult.put(labelsCode, ruleLR.result);
		
	return ruleLR;
}


// DOCU #681a8b4e long integer labelsCode
// There can be max. 63 sub-labels per LogSocket. Each new sub-label gets assigned a bit.
// The order of the sub-labels in the labels string assigned to a logger will not be
// changed, but for filtering the labels string is represented by the bit pattern
// given by OR-ing the label bits in a long integer.
// If zero it means "*" in the rule String.

static Map<String, Long> label2Nr = new ConcurrentHashMap<>();
static long nextLabelNr = 1L;

public static long registerLabels(Stream<String> labelsStream) {
	long[] labelsCode = {0L}; // effectively final
	Long[] labelNr = {0L};

	labelsStream.forEach( lbl -> {
		if( null==( labelNr[0]=label2Nr.get(lbl) ) ) {
			label2Nr.put(lbl, nextLabelNr); System.out.println("label2Nr.put("+lbl+", "+Long.toBinaryString(nextLabelNr)+"))");
			labelsCode[0] |= nextLabelNr;
			if( (nextLabelNr<<=1) == -9223372036854775808L ) {
				//TODO Exlude from filter
				throw new FilterError("Max. number of labels reached");
			}
		} else {
			labelsCode[0] |= labelNr[0];
		}
	});
	
	return labelsCode[0];
}






// Apply rule to existing lggrs
static void applyRule(RuleLR ruleLR) { //DEV #23e526a3  //DEV #6cb5e491
//DEV
	if ( true || ruleLR.realm==null || ruleLR.labels==null || ruleLR.labels.length!=1 ) {//All realms || all labels || more than 1 label
		LogSocket.complain("TODO #6776bf39 filter1 rule="+ruleLR);
		LogSocket.sendMsg("/ALERT_R TODO #6776bf39 Filter:<br/>"+escapeHTML(ruleLR.toString()));
		return;
	}
	
	String cmdArgs="";
	int goneNum=0, fltrdNum=0, alreadyfltrdNum=0;
	boolean ignore = "E".equals(ruleLR.result);
	
/*	
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
*/
}




// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
public static class TEST {

	public static void main(String[] args) {

		System.out.println(">>>>>>>>>> Testing Filter >>>>>>>>>>\n");
		for(int i=1;i<70;i++) {
			System.out.println(i+" "+Long.toBinaryString((nextLabelNr<<=1))+" "+nextLabelNr );
		}

	}
}


}
