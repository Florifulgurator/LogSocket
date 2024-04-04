package florifulgurator.logsocket.utils;

import java.util.regex.Pattern;
import java.util.stream.Stream;

public class MGutils {

static final Pattern dotPttrn = Pattern.compile("[.]");

public static String shortClObjID(Object o) { return o==null?"null":Stream.of( dotPttrn.split(o.toString()) ).reduce((first,last)->last).get(); }

public static double square(double x) { return x*x; }

public static double round4(double x) {return Math.round(10000.0*x)/10000.0;}
public static double round3(double x) {return Math.round(1000.0*x)/1000.0;}
public static double round2(double x) {return Math.round(100.0*x)/100.0;}
public static double round1(double x) {return Math.round(10.0*x)/10.0;}
public static double round(double x)  {return Math.round(x);}

}
