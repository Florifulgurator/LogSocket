package florifulgurator.logsocket.utils;

import java.util.regex.Pattern;
import java.util.stream.Stream;

public class MGutils {

// General >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

static final Pattern dotPttrn = Pattern.compile("[.]");

public static String shortClObjID(Object o) { return o==null?"null":Stream.of( dotPttrn.split(o.toString()) ).reduce((first,last)->last).get(); }

public static String getCallr(int skp)	{ // getCallr(0)=="getCallr"  getCallr(1)==called
	return StackWalker.getInstance().walk(stream -> stream.skip(skp).findFirst().get()).getMethodName();
}


// Maths >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

public static double square(double x) { return x*x; }

public static double round6(double x) {return Math.round(1000000.0*x)/1000000.0;}
public static double round5(double x) {return Math.round(100000.0*x)/100000.0;}
public static double round4(double x) {return Math.round(10000.0*x)/10000.0;}
public static double round3(double x) {return Math.round(1000.0*x)/1000.0;}
public static double round2(double x) {return Math.round(100.0*x)/100.0;}
public static double round1(double x) {return Math.round(10.0*x)/10.0;}
public static double round(double x)  {return Math.round(x);}


// Time >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

public static final long   sysT_T0 = System.nanoTime();
public static double       sysT_Q = 1.0; //Clock/quartz drift
public static final double sysT_ms() { return round3(sysT_Q*(System.nanoTime()-sysT_T0) /1000000.0); }


// WWW >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

public static String escapeHTML(String s) { // non-HTML --> safe HTML
	if( s == null || s.length() == 0 ) return s;

	StringBuilder sb = new StringBuilder( s );
	for( int i=0 ; i < sb.length() ; i++ ) {
		if ( -1 != "&\"\'<>".indexOf(sb.charAt(i)) ) {
			switch (sb.charAt( i )) {
				case '&':  sb.replace( i, i+1, "&amp;"  ); i+=4; break;
				case '"':  sb.replace( i, i+1, "&quot;" ); i+=5; break;
				case '\'': sb.replace( i, i+1, "&apos;" ); i+=5; break;
				case '>':  sb.replace( i, i+1, "&gt;"   ); i+=3; break;
				case '<':  sb.replace( i, i+1, "&lt;"   ); i+=3; break;
			}
		}
	}
	return sb.toString();
}


}


