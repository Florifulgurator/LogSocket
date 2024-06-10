package florifulgurator.logsocket.javalggr;

import static florifulgurator.logsocket.utils.MGutils.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;




public class Filter {

static boolean TEST1 = true;

static Map<String, String> rulesUniq = new ConcurrentHashMap<>(); // insertion order to keep things tidy
static Map<String, ConcurrentSkipListMap<Long, String>> realm2labelsCode2RuleResult = new ConcurrentHashMap<>();

public static record RuleLR(String realm, String[] labels, long labelsCode, String result) {
	public String toRuleRStr() {
		return realm + ">" + (labels==null?"*":Arrays.stream(labels).collect(Collectors.joining("^")))
	           + "=" + result;
	}
	public String toRuleuniqStr() {
		return realm + (labels==null?"":Arrays.stream(labels).collect(Collectors.joining("")) );
	}
	public String toString() { //DIAGN
		return this.toRuleRStr()+" labelsCode="+Long.toBinaryString(labelsCode);
	}

}


@SuppressWarnings("serial")
static class FilterError extends RuntimeException {
    public FilterError(String s) { super(s); }
	@Override
    public synchronized Throwable fillInStackTrace() { return this; }
}


// DOCU #681a8b4e long integer labelsCode
// There can be max. 63 sub-labels per LogSocket. Each new sub-label gets assigned a bit.
// The order of the sub-labels in the labels string in a logger longId will not be
// changed, but for filtering the labels string is represented by the bit pattern
// given by OR-ing the label bits in a long integer.
// If zero it means "*" in the rule String.

static Map<String, Long> label2Nr = new ConcurrentHashMap<>();
static Map<Long, Integer> labelsCode2NumBits = new ConcurrentHashMap<>(); 
static long nextLabelNr = 1L;
/**
 * Selfdocumenting name
 */
public static long registerLabels(Stream<String> labelsStream) {
	long[] labelsCode = {0L}; // effectively final
	Long[] sublabelNr = {0L};
	int[] numBits = {0};
	
	labelsStream.distinct().forEach( lbl -> {
		if( null==( sublabelNr[0]=label2Nr.get(lbl) ) ) {
			label2Nr.put(lbl, nextLabelNr); System.out.println("DIAGN label2Nr.put("+lbl+", "+Long.toBinaryString(nextLabelNr)+"))");
			labelsCode[0] |= nextLabelNr;
			if( (nextLabelNr<<=1) == -9223372036854775808L ) {
				//TODO Exlude from filter
				throw new FilterError("Max. number of labels reached");
			}
		} else {
			labelsCode[0] |= sublabelNr[0];
		}
		numBits[0]++;
	});
	labelsCode2NumBits.put(labelsCode[0], numBits[0]);
	
	return labelsCode[0];
}


// DOCU #1c887880 Rule string syntax:
// realm>#label1#label12^#label3=M  // #label1#label12 treated as one label. "M" ignore *M*essages, but track existence and make ID
//  *>#label1^#label2^#label3=E     // 3 labels. "E" ignore *E*xistence. No finalize(), no ID for new loggers
// rlm>*=M
public static Comparator<Long> labelsCodeComparator = (l1,l2) -> { // Sort by specificity #1ab76b52
	Integer b1=labelsCode2NumBits.get(l1), b2=labelsCode2NumBits.get(l2);
	if ( b1==null || b2==null ) { b1=0; b2=0; throw new FilterError("BUG: ConcurrentSkipListMap comparator: labelsCode2NumBits gives null."); }
	return b1!=b2 ? (int)Math.signum(b1-b2) : (int)Math.signum(l1-l2);
};
/**
 * Selfdocumenting name
 */
static RuleLR parseRuleStringAndStore(String ruleRStr) throws FilterError {
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
		lblL = hatPttrn.split(ruleRStr.substring(a+1, b));
		Arrays.sort(lblL);
		labelsCode = registerLabels( Arrays.stream(lblL).map( s->s.substring(1) ) );
	}

	// New rule:
	String result = ruleRStr.substring(b+1);
	String realm = ruleRStr.substring(0, a);
	RuleLR ruleLR = new RuleLR(realm, lblL, labelsCode, result );
	// really new?
	String ruleuniqStr = ruleLR.toRuleuniqStr();
	if ( rulesUniq.containsKey(ruleuniqStr) )  throw new FilterError("Filter: "+ruleuniqStr+" already exists: "+ruleLR.toRuleRStr());
	rulesUniq.put(ruleuniqStr, ruleLR.result);
	
	//Register rule:
	ConcurrentSkipListMap<Long, String> labelsCode2RuleResult = realm2labelsCode2RuleResult.get(ruleLR.realm);
	if (null==labelsCode2RuleResult) {
		labelsCode2RuleResult = new ConcurrentSkipListMap<Long,String>( labelsCodeComparator ); // Sort keys by specificity #1ab76b52
		realm2labelsCode2RuleResult.put(ruleLR.realm, labelsCode2RuleResult);
	}
	labelsCode2RuleResult.put(labelsCode, ruleLR.result);
		
	return ruleLR;
}


/**
 * Apply all filter rules to realm and labelsCode.
 * 
 * The rule with the largest number of bits in its labelsCode wins.
 * A rule for a specific realm wins over rules for any realm.
 */
static String filter(String realm, long labelsCode) {
	String[] result = {null};
	
	Stream.of("*", realm).forEach( rlm -> {
		Map<Long, String> labelsCode2RuleResult = realm2labelsCode2RuleResult.get(rlm);
		if ( labelsCode2RuleResult!=null ) {
			labelsCode2RuleResult.keySet().forEach( ruleLblsCd -> {
				if ( (ruleLblsCd & labelsCode) == ruleLblsCd ) {
					result[0] = labelsCode2RuleResult.get(ruleLblsCd);
System.out.println("=== DIAGN filter result=="+result[0]+" ruleLblsCd=="+Long.toBinaryString(ruleLblsCd)+" labelsCode=="+Long.toBinaryString(labelsCode));
					// More specific rules (with more bits in the labelsCode) override the more general rules. #1ab76b52
				}
			});
		}
	});

	return result[0];
}







static int[] DIAGNfltrdNum = {0};
static int[] DIAGNalreadyfltrdNum = {0};
static int[] DIAGNgoneNum = {0};
static final Map<String, String> ruleResult2cmd = Map.of("M", "!SILENCED ", "E", "%IGNORED ");

/** 
 * Apply a filter rule to all existing lggrs
 */
static synchronized void applyRule2lggrs(RuleLR ruleLR) { //DEV #23e526a3 #6cb5e491
System.out.println("== DIAGN applyRule2lggrs "+ruleLR);
	if ( ruleLR.labels==null ) {
		LogSocket.complain("TODO #6776bf39 filter1 rule="+ruleLR);
		LogSocket.sendMsg("%/ALERT_R TODO #6776bf39 Filter:<br/>"+escapeHTML(ruleLR.toRuleRStr()));
		return;
	}
	
	Set<String> realms = null;
	if ( ruleLR.realm=="*" ) {
		 realms = LogSocket.realm2labelsCode2LggrRefRcrdList.keySet();
		 if (realms.isEmpty()) return;
	} else {
		if ( !LogSocket.realm2labelsCode2LggrRefRcrdList.containsKey(ruleLR.realm) ) return;
		realms = Set.of(ruleLR.realm);
	}

	boolean resultIsE = "E".equals(ruleLR.result);
	StringJoiner joiner = new StringJoiner(" ", ruleResult2cmd.get(ruleLR.result), "");
	joiner.setEmptyValue("");
	DIAGNfltrdNum[0]=0;
	DIAGNalreadyfltrdNum[0]=0;
	DIAGNgoneNum[0]=0;

	realms.forEach( realm -> {
		Map<Long, ArrayList<LogSocket.LggrRefRcrd>> labelsCode2lggrRefRcrdList  =  LogSocket.realm2labelsCode2LggrRefRcrdList.get(realm);
		labelsCode2lggrRefRcrdList.keySet().forEach( labelsCode -> {
			if ( (labelsCode & ruleLR.labelsCode) == ruleLR.labelsCode ) {
				ArrayList<LogSocket.LggrRefRcrd> lggrRefRcrdList = labelsCode2lggrRefRcrdList.get(labelsCode);
				if (!lggrRefRcrdList.isEmpty()) {
					synchronized (lggrRefRcrdList) { //>>>>>>>>>>>>>>>>>>>>>>>>
						lggrRefRcrdList.forEach(
							rcrd -> {

								Lggr l = rcrd.wref().get();
								if ( l!=null ) {
System.out.println("=== DIAGN applyRule2lggrs realmlabel=="+l.realm+l.label+" longIdX=="+rcrd.longIdX());

									if ( l.on ) {
										l.on=false;
										DIAGNfltrdNum[0]++;
										LogSocket.filteredRealmLabel.put(l.realm+l.label, ruleLR.result);
									} else {
										//----------------------------------
										if (TEST1) {
											if ( !LogSocket.filteredRealmLabel.containsKey(l.realm+l.label) ) {
												System.err.println("**** LogSocket.Filter TEST1.B: BUG! LogSocket.filteredRealmLabel.containsKey(\""+l.realm+l.label+"\") == false");
											}
										}
										//----------------------------------
										DIAGNalreadyfltrdNum[0]++;
									}
									if (resultIsE) l.dontMakeKnown = true;
									if (rcrd.madeKnown()[0]) joiner.add(l.shortId);
																
								} else {
									DIAGNgoneNum[0]++;
System.out.println("=== DIAGN applyRule2lggrs longIdX=="+rcrd.longIdX());
								}
							}
						);
					} // synchronized <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
				}
			}
		});
	});
	
	try {
		if (joiner.length()!=0) LogSocket.websocket.sendText(joiner.toString());
		
		LogSocket.websocket.sendText("%/DIAGN "+"LogSocket /"+LogSocket.Nr+": Filter rule \""+ruleLR.toRuleRStr()
			+"\" stopped "+DIAGNfltrdNum[0]+" logger instance"+(DIAGNfltrdNum[0]==1?"":"s")
			+" - "+DIAGNalreadyfltrdNum[0]+" already stopped, "+DIAGNgoneNum[0]+" gone."
		);
		LogSocket.sendMsg("%/ALERT_B Filter: Stopped "+DIAGNfltrdNum[0]
			+((DIAGNalreadyfltrdNum[0]!=0) ? "<br/>"+DIAGNalreadyfltrdNum[0]+" already stopped." : "")
		);
			//TODO #7594d994 client consistency test

	} catch (IOException e) {
			System.err.println("!!!- LogSocket_ERROR_X Filter.applyRule (TODO) "+e.getMessage());
	}
}
//---








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
