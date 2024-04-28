package florifulgurator.logsocket.server;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.OptionalDouble;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.Random;//for TEST
//import static org.junit.jupiter.api.Assertions.assertEquals;
// etc. etc... Too much fuss! Just run RandomVar::TEST::main


//TODO setVals(...) using System.arraycopy


public class RandomVar {

	public final int SIZE = 9999;
	public double[] values = new double[SIZE];
	public int numValues = 0;
	public String name = "unnamed";

	public RandomVar() {}
	public RandomVar(String nam) {this.name=nam;}

	
	// Results >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	private OptionalDouble max = OptionalDouble.empty();
	private OptionalDouble min = OptionalDouble.empty();
	private OptionalDouble mean = OptionalDouble.empty(); // Optional not only for fun: The average() method of DoubleStream class returns OptionalDouble
	private OptionalDouble median = OptionalDouble.empty();
	private String art = "";
	private String artHeader = "";
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	
	private boolean isSorted = false;
	private int lastMeanNr, lastMedianNr, lastMaxNr, lastMinNr;//FIXME what if lastMeanNr==numValues==0 ?
	private int lastArtNr=-1;
	
	private static final Pattern blankPttrn = Pattern.compile("\\s+");
	
	//ASCII art diagram stuff >>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	public static int numBuckets = 25;
	public static int maxBarHeight = 25;
	private static int numBars = 0; // numBars== 3*maxBarHeight+1
	private static String[] bars = new String[3*maxBarHeight+1];
	static {
		char[] c = {'-', '='};
		bars[numBars++] = "";
		
		String barBase = "";
		for(int j=1; j<=maxBarHeight; j++) {
			for(int i=0; i<2; i++) bars[numBars++] = barBase + c[i];
			barBase += "#";
			bars[numBars++] = barBase;
		}
	}
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


synchronized public boolean setVal(double v) {
	if ( numValues >= SIZE ) {
		values[(int)Math.round(Math.random()*(SIZE-1))] = v;
		isSorted = false;
		lastMeanNr = -1; lastMedianNr = -1; lastMaxNr = -1; lastMinNr = -1;
		return false;
	}
	values[numValues++] = v;
	isSorted = false;
	return true;
}

synchronized public String setValsIntString(String valStr, double unit) {
	String errr[] = {""}; //effectively final
	Arrays.stream( blankPttrn.split(valStr) ).forEach( x -> {
		try {
			setVal(Double.parseDouble(x)*unit);
		} catch(Exception e) {
			if(errr[0].isEmpty()) errr[0] = e.toString();
		}
	});
	return errr[0];
}

synchronized public double getMean() {
	if (mean.isPresent() && lastMeanNr==numValues) return mean.getAsDouble();
	lastMeanNr = numValues;
	mean = Arrays.stream(values, 0, numValues).average();
	return mean.getAsDouble();
}

synchronized public double getMedian() {
	if(median.isPresent() && lastMedianNr==numValues) return median.getAsDouble();
	if(!isSorted) { Arrays.sort(values, 0, numValues); isSorted = true; }
	lastMedianNr = numValues;
	
	if(numValues%2 != 0) median = OptionalDouble.of(values[(numValues-1)/2]);
	else median = OptionalDouble.of(0.5*(values[numValues/2]+values[numValues/2-1]));
	return median.getAsDouble();
}

synchronized public double getMax() {
	if(max.isPresent() && lastMaxNr==numValues) return max.getAsDouble();
	if(!isSorted) { Arrays.sort(values, 0, numValues); isSorted = true; }
	lastMaxNr = numValues;
	max = OptionalDouble.of(values[numValues-1]);
	return max.getAsDouble();
}

synchronized public double getMin() {
	if(min.isPresent() && lastMinNr==numValues) return min.getAsDouble();
	if(!isSorted) { Arrays.sort(values, 0, numValues); isSorted = true; }
	lastMinNr = numValues;
	min = OptionalDouble.of(values[0]);
	return min.getAsDouble();
}

synchronized public String getASCIIart(int decPlaces, String unit) {
	if( lastArtNr==numValues ) return art;
	makeASCIIart(decPlaces, unit);
	lastArtNr=numValues;
	return art;
}

synchronized public String getASCIIartHeader() {
	if( lastArtNr==numValues ) return artHeader;
	return "RandomVar::getASCIIartHeader() ERROR: ASCIIart not yet done";
}


// Show distribution of values as ASCII art bar diagram with numBuckets bars.
// !! May contain UTF-8 characters like μ ⋮ 
//    The point is that monospaced typewriter font is required for display.
//
// To avoid concentration in less than numBuckets-2 bars we need to discard outliers.
// !! Right now only outliers at the +infinity tail are treated
//TODO Outliers at the -infinity tail.

synchronized private void makeASCIIart(int decPlaces, String unit) {

	// Extreme cases >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	if(numValues<=0) { art = "[EMPTY]"; return; }
	
	DecimalFormat dF = new DecimalFormat();
	dF.setMaximumFractionDigits(decPlaces);dF.setGroupingUsed(false);
	DecimalFormat dF1 = new DecimalFormat();
	dF1.setMaximumFractionDigits(decPlaces);dF1.setGroupingUsed(false); // was decPlaces+1

	if(numValues==1) { art = "N=1 value="+dF1.format(values[0]); return; }
	
	Arrays.sort(values, 0, numValues); isSorted = true;

	double meanD=getMean(), medianD=getMedian(), minD=getMin(), maxD=getMax();

	if(maxD-minD < 10*Double.MIN_VALUE ) { art = "N="+numValues+" Min≃Max="+dF1.format(minD); return; } //TODO TEST
	// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	//boolean TEST = true;
	
	int[] buckets;
	int firstOutlierBckt = numBuckets;
	int meanBcktIndx, medianBcktIndx;
	int[] firstIndexInBckt;
	int[] lastIndexInBckt;
	int oldNrValues = numValues;
	float hurz;
	String redos="";

	do {
		int maxBcktSize = 0;
		double step = (values[numValues-1]-minD)/numBuckets; //numValues (hence max) changes in 2nd pass, restored at the end.
		int numBucketed = 0; // #X Last bucket might miss max value(s) due to rounding error
		double bcktMaxVal = minD + step; 
		
		buckets = new int[numBuckets];
		firstIndexInBckt = new int[numBuckets];
		lastIndexInBckt = new int[numBuckets];
		meanBcktIndx = -1; medianBcktIndx = -1;
		
		int bckt = 0, i = 0;
		while(i < numValues && bckt < numBuckets) {
			if (meanBcktIndx == -1 && meanD <= bcktMaxVal)     meanBcktIndx = bckt;
			if (medianBcktIndx == -1 && medianD <= bcktMaxVal) medianBcktIndx = bckt;
			firstIndexInBckt[bckt] = i;
			while(i < numValues && values[i] <= bcktMaxVal) {
				buckets[bckt]++;
				i++;
			}
			lastIndexInBckt[bckt] = i-1;
			numBucketed += buckets[bckt]; // #X
			if(buckets[bckt] > maxBcktSize) maxBcktSize = buckets[bckt];
			bcktMaxVal += step;       // #X rounding errors!
			bckt++;
		}
		if(numBucketed != numValues) { buckets[numBuckets-1] += numValues-numBucketed; } // #X
	
		// Outliers are in "tail" buckets with zero bar height
		hurz = (float)(numBars-1)/(float)maxBcktSize;
		for( bckt=numBuckets-1; bckt>=0 && 0==(int)Math.round( hurz*(float)buckets[bckt] ) ; bckt--) {}
		// If last bucket before outlier has bar height >=2, step back one bucket
		firstOutlierBckt = ( 1==(int)Math.round( hurz*(float)buckets[bckt] ) ) ? bckt+1 : bckt+2;
		// Should be at least 3 outlier buckets
		if(firstOutlierBckt < numBuckets-2) {
			//Discard outliers for 2nd pass
			numValues = firstIndexInBckt[firstOutlierBckt];
			redos += (redos==""?"":",") +firstOutlierBckt;
		}
		
	} while(firstOutlierBckt < numBuckets-2);

	//Now for the ASCII art	
	art = "";

	int maxLineLength = 0;
	String line = "";
	for(int bckt=0; bckt<numBuckets; bckt++) {
		if(bckt==meanBcktIndx)        line = "\n+"; // has priority over median mark
		else if(bckt==medianBcktIndx) line = "\n%"; // #B
		else                          line = "\n|";

		line += bars[(int)Math.round( hurz*(float)buckets[bckt] )]
		     + "   (" + buckets[bckt]; //COPYPASTE #A 3 blanks
		if(buckets[bckt]==0) {
			line += ")";
		} else if(buckets[bckt]==1) {
			line += ") " + dF.format(values[firstIndexInBckt[bckt]])
			     + unit;
		} else {
			line += ") " + dF.format(values[firstIndexInBckt[bckt]])
		         + " ~ " + dF.format(values[lastIndexInBckt[bckt]])
		         + unit;
		}
		if(line.length() > maxLineLength) maxLineLength = line.length();
		art += line;
	}
	//Outliers in one bar:
	if(oldNrValues != numValues) {
		if(meanBcktIndx == -1)        line = "\n+"; // has priority over median mark
		else if(medianBcktIndx == -1) line = "\n%"; // #B
		else                          line = "\n|";
		
		line += "   (" + (oldNrValues-numValues)   ; //COPYPASTE #A 3 blanks
		if(oldNrValues-numValues <= 4) {
			line += ") "
			     + Arrays.stream(values, numValues, oldNrValues).mapToObj(x->dF.format(x)).collect( Collectors.joining(", ") )
			     + unit;
		} else {
			line += ") "
			     + dF.format(values[numValues]) + " ~ "
			     + dF.format(values[oldNrValues-3]) + ", "
			     + dF.format(values[oldNrValues-2]) + ", "
			     + dF.format(values[oldNrValues-1])
			     + unit;
		}
		if(line.length() > maxLineLength) maxLineLength = line.length();
		art += "\n⋮" + line;
	}

	artHeader = "N="+oldNrValues
	      + " Min="+dF.format(minD) + " Max="+dF.format(maxD)
	      + " Median="+dF1.format(medianD)
	      + " Mean="+dF1.format(meanD);

	art = artHeader
		  + "\n" + "-".repeat(maxLineLength)
	      + art
	      + "\n" + "-".repeat(maxLineLength)
	      + (redos!="" ? "\nSkew:"+redos : "")
	      + (meanBcktIndx==medianBcktIndx ? " \"+\"=Mean,Median" : " \"%\"=Median \"+\"=Mean "); // #B
	// ASCII art done
	
	numValues = oldNrValues;
	return;
}

// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<



/*
// Test #WeakReference
// (finalize() is fundamentally flawed and deprecated!)
public boolean noFinalize = true;
public void finalize() { if (noFinalize) return;
	System.out.println("finalize() "+this+" name="+this.name);
}

private static final Cleaner cleaner = Cleaner.create(); //
private final Cleaner.Cleanable cleanable;
final Runnable F  = () -> System.out.println("Hurz!");

	public RandomVar() {this.cleanable = null;}
	public RandomVar(String nam) {this.name=nam;this.cleanable = cleaner.register(this, () -> System.out.println("Hurz! "+nam) );}
*/

// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
public static class TEST { // >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
// >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


public static int checkNr(String out) {
	Pattern blankPttrn = Pattern.compile("\\s+");
	int summ = 0;
	for( String s : blankPttrn.split(out) ) {
		if (s.charAt(0)=='(') {
			summ += Integer.parseInt(s.substring(1,s.length()-1));
		}
	}
	return summ;
}

public static String shortClObjID(Object o) {return Stream.of(o.toString().split("[.]")).reduce((first,last)->last).get();}

public static String testValStr = "3786 5622 3890 4224 3901 3700 4911 3831 4914 5346 4127 5939 10936 7085 4320 7525 6488 27655 5696 7028 5401 5433 4406 4940 5553 5656 5519 4427 4573 3654 2980 3556 2407 2865 3278 3065 3594 3845 345529 31792 3736 3406 3433 9154 6131 9516 11363 3493 3359 2948 3195 3213 3533 20329 9431 3356 2536 4063 3255 10416 20032 57498 6547 64720 13795 8612 3115 4118 5618 10489 6959 12398 6841 3922 2891 18043 58991 14130 18968 7616 3916 12589 4539 3496 3160 2130 2875 10315 4414 3740 2610 8484 3890 2950 1999 3067 1804 2458 2219 2657 2888 2115 2201 3293 2060 7825 7418 3991 2929 12313 4381 12280 5636 7235 5265 2518 2594 5541 11344 2563 2718 2342 2008 4171 69378 5087 8781 3761 5901 3226 1991 2099 5228 3732 2988 2193 2011 2089 2625 2439 2062 1973 3218 8363 13818 2752 2144 3291 4151 3342 3168 3001 3050 2915 2069 2093 2421 2167 5992 5596 2528 2183 4684 6216 5544 3118 2519 2096 2552 2310 2804 2491 2914 2566 2038 2378 2349 2223 3917 2209 1963 2392 2790 2773 9194 5747 2881 6880 4965 9139 3732 2153 2115 2309 2810 2097 2046 2302 2715 1987 1817 1832 2006 2279 1877 1782 1718 1768 2119 5205 2132 2114 3529 2804 1966 2026 1889 6520 7446 4754 2509 2085 2300 3149 2018 2927 1854 2046 2271 2024 2025 2106 2209 2509 2170 1861 1823 1915 2335 2007 1847 1868 1902 2358 1847 1885 1900 1930 2203 3652 2070 1959 1836 2392 1799 1854 1855 1803 2243 1890 1878 1848 1827 2525 1821 1817 1812 1797 2138 2207 1824 1834 1781 2251 1836 2562 1767 1733 2177 1931 1728 1664 1719 2196 1647 2950 1884 1816 2350 2269 9301 2628 1822 1979 1719 1661 1550 1472 3734 2015 2212 1745 1730 1959 1717 1748 1599 3053 2073 1745 1670 1738 1662 2135 1548 1632 2356 2370 2663 2955 1917 2364 2287 2146 2204 2763 2309 2430 2353 1866 2426 2077 1883 2325 2602 2088 1970 2138 3003 2127 1776 1852 1783 2336 1922 1821 2530 1868 2524 2070 2578 1795 1742 2228 2072 1894 1919 2330 2410 2035 1911 2554 1920 4924 1808 2169 2058 3679 2348 2067 3666 2538 2030 2135 1919 1941 1993 2229 2200 2375 1815 1937 1805 2340 1858 1707 1765 1791 2280 2226 1812 1789 1887 2221 1843 2679 2786 1684 1857 1689 1481 1561 1507 2645 1440 1502 1363 1495 1830 1475 1422 1376 1553 1900 2084 1576 1474 1474 1925 1985 1385 1397 1449 1758 1530 1605 2037 3103 2515 1607 1524 1491 1481 1849 1691 1466 1560 1547 1770 2661 1578 1481 1471 1770 1432 2088 1502 1486 2261 1634 1525 1473 2115 1746 2516 2648 2425 3648 3693 2033 2438 1711 5162 2532 1716 2121 2031 2684 1982 2541 1626 1646 1542 2151 2795 2371 3964 1678 1895 1940 1850 1777 2914 2084 1672 1497 1587 1667 1889 1661 1608 1835 1680 2029 1599 2382 1582 1582 2044 1558 1650 1584 1618 1710 1671 1590 1625 1637 1866 1934 1513 1558 1508 4030 1663 1603 1599 1594 1850 1558 1586 1556 1603 1809 1542 1568 1485 2184 2195 3203 1597 4226 3459 2298 1664 14265 2345 1764 4951 1667 1668 1754 1576 2303 1857 1655 1465 1452 2185 1433 5598 1678 1480 1759 2541 54234 12090 11178 8571 2102 1647 1359 1285 2581 1673 1453 1274 1226 2314 1322 1224 3335 1547 14493 3105 7699 1999 1633 2558 1640 2442 1605 1803 2296 1641 1697 1542 1397 1771 1441 1248 1408 1216 1411 1197 1121 1165 1187 1517 1293 1318 1477 1383 1732 1884 1503 1426 1771 1645 1501 1367 1355 1353 1803 1325 2006 1290 1420 1572 1397 1384 1376 1291 1597 2126 1517 1633 1332 1684 1302 2248 1410 1496 1534 1221 1260 1276 1340 2257 2090 14297 2125 1441 1633 1316 1332 3232 1323 1749 1379 1271 1211 1131 2291 1351 1584 1534 1628 1921 1305 1291 1551 1421 1571 1594 1487 1552 1640 1952 1311 1244 1268 1237 1779 1235 1251 1132 1227 1377 1228 1119 1241 1144 1636 1184 1141 1200 1313 1970 1486 1727 1460 1630 2133 1688 1707 1291 1442 1474 1949 1119 1146 1071 1690 1336 3500 1189 1082 2484 1465 1390 2595 1408 1517 1592 1348 1306 1340 1805 1318 1136 1473 1156 1404 1623 1390 1286 1144 1806 1081 1238 1470 1405 1453 1331 1309 1641 1176 1478 1404 1287 1298 3038 3671 1439 1334 1389 1261 1793 1271 1206 1300"
+" 2612 2345 1659 2359 2165 1553 2348 2349 2705 2719 2504 2823 1743 1777 2778 2502 8534 2703 2569 1888 2312 1786 1935 1496 3706 1660 1845 2453 1559 2005 2892 11894 5866 2004 2334 2000 1926 1629 1707 2013 1830 2865 1845 3161 2939 1859 2005 2399 2079 2082 1797 1689 1747 3531 2442 3013 2185 2828 5072 3983 5469 1979 3963 2259 4146 2132 1519 3367 2298 2907 2005 1701 1832 2125 2040 1589 1769 1646 1867 2061 1893 1800 1779 1871 2148 2425 1781 1468 2283 2792 2452 2412 2193 1820 2697 2311 5394 1826 2754 2386 1481 1685 1493 1692 1981 1479 1653 1731 1515 3050 5123 1867 2304 1422 1804 3301 2252 2887 2304 2265 2558 2643 1650 1460 1870 1726 2946 1469 1872 2254 2510 7935 4536 1722 1908 1774 2078 1538 1546 2080 1627 1640 1840 1544 6354 1595 1653 1566 1738 1922 1608 1889 1597 1791 2262 1628 1707 1643 1753 1819 1980 1469 1806 1568 1931 1677 1608 1480 1492 1795 1591 1565 2060 1589 1778 1541 1519 1564 1518 1782 1515 1559 1582 1523 1823 1468 1571 1661 2247 1958 1523 1361 1921 1735 1930 3649 1506 1634 2554 2823 2254 1464 1506 1695 1697 1590 2380 7566 1599 2090 1483 1563 1323 1482 1810 1605 1437 1860 1608 3958 1884 1804 1552 1645 2292 1485 1603 1628 2319 3460 1970 1610 1484 1510 1883 1620 1534 1552 1492 1905 1575 1542 1564 1530 2098 1574 1580 2661 1819 1661 2015 1549 1805 1745 1925 1695 1619 1694 1709 2120 1644 1907 2222 1591 2311 1620 1478 1471 1513 1645 1575 1482 1457 1617 1767 1683 1630 1434 1525 1652 1473 1810 1465 1417 1638 1484 1542 1607 2614 2253 1514 1493 1623 1538 3440 1492 1487 1459 1548 2081 1417 1376 1504 1763 1716 1604 1559 1481 3417 1925 1575 1666 1601 2108 1569 2658 2498 1507 1523 2810 3614 1925 1507 2612 2421 1555 1511 1549 1512 1919 1572 1947 1604 1546 1754 1652 1531 2462 1657 2182 1479 1408 1349 1431 1914 1353 1494 1382 1800 1544 1804 1381 2226 1398 1437 1412 1501 2359 1703 2188 1384 1337 2918 1674 1786 1581 1604 1565 1596 3984 1795 1634 2170 1529 1659 1648 1350 3302 1469 1816 3162 1684 1504 1407 3024 1671 1841 1472 1448 1461 1871 1523 1994 2177 1682 1575 1510 2038 2015 1679 1448 1493 1537 1443 1596 1461 1471 1588 1442 1622 1427 1545 1573 3785 1780 1390 1382 2424 1595 1627 1606 1338 1306 1398 1458 1339 1468 1475 1517 1628 1474 1596 1555 1660 1603 1525 1426 1452 2423 1721 1502 1491 1541 3013 1683 1525 1843 1411 2774 1498 1636 1869 1418 1376 1664 1487 1499 1476 1479 1551 1475 1474 3476 1836 1662 1534 1517 3100 1651 1980 1641 2972 1530 1744 1753 1638 1547 1646 1559 1573 1390 1526 2975 1523 1461 2487 1988 1468 1381 2490 1275 1560 1425 1443 1493 1441 1529 1349 1243 1415 1278 1479 1461 2139 1492 1343 1471 2562 1428 2434 1595 1434 1550 1784 1734 2705 1324 1312 1324 1412 1247 2927 1321 1277 1750 2478 1313 1302 2488 1363 1351 5305 1520 1334 2277 3137 1244 1204 2940 1313 1291 1247 1182 1192 1772 2586 1219 1151 2182 1305 1181 2522 1170 1158 12089 1590 1213 1470 1150 1772 2348 1210 1447 2378 1351 1270 1329 1321 2803 1364 1230 1931 2160 1277 1322 2253 1221 1368 1744 1538 1445 1279 2996 1269 1441 2655 2557 1229 2158 2254 1744 1622 1526 3171 1483 1311 1323 2196 3407 2044 1343 1348 1326 2362 1437 1347 2761 1396 1419 1719 2764 1460 1312 1315 1637 1342 1363 2419 2122 1437 1308 1280 2712 1403 1633 1332 1268 1299 2208 1559 1280 1286 1352 2968 1559 1312 1388 1291 2391 8667 1480 1348 1558 3070 2746 46203 7159 3585 3197 1619 1758 1259 1291 2908 2679 1305 1300 2253 1533 1379 2132 1359 2779 1418 1332 1178 1913 1808 1153 1378 2912 1298 1228 1262 1571 1547 1420 1290 2436 3281 2431 1680 2113 3113 1608 1545 1446 1942 1862 1766 1595 1689 1652 1418 1646 1718 1865 3071 2326 9169 2085 1813 2650 1538 1421 1248 1297 1268 1328 1329 1238 1938 1271 1290 1345 1289 1906 1519 1437 14162 1803 1610 2410 2392 3413 3408 1525 1430 1470 1577 2081 1703 1659 2986 1476 1423 1322 1439 2706 1502 2146 1909 1365 1414 3767 1695 1851 1344 2634 1581 1443 1906"
+" 1416 1388 1271 1622 1187 1514 1411 1299 1352 1402 1279 1259 1438 1588 1652 1593 1472 1341 1632 2483 2340 1670 2014 1504 1307 2547 1672 1460 1659 2746 6478 1426 4265 1630 1302 1340 1263 1512 2032 1557 2274 1517 1403 1671 1313 1463 1565 1331 1387 1490 3120 1385 1579 2275 1421 1404 2159 1514 2294 1386 1343 1508 1269 7504 1559 1418 1384 1359 1508 1327 1306 2555 1532 1394 1250 1362 1449 1452 1674 1342 1491 1327 1297 1632 1442 1427 1378 1465 1514 2239 1486 1225 2258 1560 1078 2072 1436 1144 2969 1307 1279 2919 4797 4906 2288 2677 1139 1053 3382 3184 2150 2154 1197 1442 1509 1405 1316 1280 1348 1278 1313 1270 1350 1381 1210 1248 1217 1215 1385 1214 1220 1222 1220 1326 1220 1238 1248 1198 1242 1136 1050 1049 1067 1239 1329 1225 1212 1175 1355 1230 1222 1179 1251 1337 1173 1161 1178 1190 1327 1211 1184 1302 1278 1349 1159 1207 1215 1223 1271 1397 1216 1117 1104 1185 1105 1028 1040 2653 1732 1209 1250 1292 1213 1331 1284 1303 1294 1183 1243 994 1045 1205 1214 1407 1209 1228 1238 1304 1325 1208 1264 1227 1217 1305 1263 1234 1193 1216 1357 1214 1218 1231 1219 1339 1201 1220 1292 2748 1432 1161 1186 1211 1100 1306 1043 1147 1216 1196 1335 1226 1193 1214 1279 1363 1181 1240 1199 1186 1295 1211 1576 1163 1198 1399 1184 1217 1206 1224 1377 1184 1168 1208 1204 1269 1249 1287 1159 1096 1197 1012 1100 1196 1221 1321 1255 1206 1184 1194 1407 1230 1207 1240 1279 1361 1251 1267 1264 1236 1338 1321 1292 1220 1207 1270 1160 1170 1091 1073 1177 1094 1325 1369 1261 1479 1226 1236 1251 1214 1341 1227 1217 1671 1173 1169 1271 1256 1271 1257 1406 1278 1254 1198 1226 1254 1149 1223 1209 1230 1317 1219 1218 1165 1255 1344 1258 1265 1208 1228 1379 1254 1247 1230 1214 1302 1204 1216 1250 1225 1327 1213 1242 1233 1204 1363 1271 1211 1224 1224 1332 1215 1423 1208 1188 1356 1252 1061 1131 1189 1281 1227 1199 1061 992 1109 1031 1025 1023 1063 1294 1308 1369 1352 1250 1429 1299 1268 1360 1414 1435 1328 1299 1310 1309 1485 1374 1215 1152 1337 1391 1316 1275 1312 1299 1287 1288 1183 1380 1277 1491 1187 1265 1262 1176 1591 1185 1353 1245 1263 1454 1380 1272 1307 1290 1509 1435 1375 1245 1351 1333 1375 1225 1344 1358 1486 1266 1380 1256 1394 1361 1281 1317 1198 1201 1372 1232 1398 1200 1316 1277 1310 1235 1357 1249 1494 1341 1372 1264 1382 1355 1330 1273 1310 1474 1456 1336 1239 1191 1291 1374 1318 1338 1385 1310 1371 1259 1249 1123 1121 1051 965 1145 1119 1219 1475 1316 1361 1383 1378 1354 1322 1372 1274 1305 1396 1191 1286 1260 1261 1579 1372 1769 1452 1565 1787 1225 1349 1341 1379 1402 1254 1385 1278 1281 1345 1250 1353 1317 1237 1384 1317 1259 1338 1379 1212 976 944 933 909 1246 1159 1265 1357 1313 1435 1388 1316 1317 1371 1746 1174 1353 1324 1365 1361 1331 1382 1300 1319 1363 1444 1363 1326 1336 1437 1298 1281 1310 1300 1425 1252 1302 1344 1327 1470 1413 1274 1322 1271 1354 1226 1250 1237 1220 1279 1409 1342 1314 1287 1380 1317 1262 1319 1341 1540 1330 1496 1518 1316 1342 1044 972 1028 1104 1391 1491 1416 1514 1119 1216 1165 1147 1168 1099 1166 1098 1105 1100 1112 1202 1113 1113 1101 1098 1201 1099 1212 1154 1097 1581 1100 1081 1052 981 1208 994 4222 1174 1038 1117 1112 1144 1174 1075 1268 1102 1147 1197 1206 1361 1176 1140 1195 1232 1197 1106 1137 1194 1190 1315 1231 1211 1444 1284 1369 1266 1262 1391 1221 1274 1326 1237 1252 1232 1324 1178 1114 1153 1166 1277 1174 1147 1085 1182 1199 1103 1134 1153 1117 1229 1172 1376 1337 1293 1245 1033 996 961 1090 1290 1125 1095 1191 1269 1373 1226 1335 1149 1385 1269 1307 1237 1374 1268 1506 1262 1458 1277 1359 1381 1285 1337 1155 1040 1053 969 951 1128 1219 1245 1182 1338 1352 1306 1217 1269 1348 1362 1213 1498 1252 1294 1206 1298 1408 1320 1379 1357 1337 1418 1279 1313 1354 1302 1440 1384 1354 1297 1194 1378 1361 1305 1594 1220 1319 1223 1331 1307 1349 1503 1322 1317 1172";

/*
// #WeakReferenceTEST
public static ReferenceQueue<RandomVar> rq = new ReferenceQueue<>(); // #WeakReferenceTEST

public static Tuple<String, WeakReference<RandomVar>> someFunctn(String xx) {
	RandomVar r = new RandomVar("WeakReferenceTEST "+xx); r.noFinalize = false;
	Random rnd = new Random();
	for(int i=1; i<=r.SIZE; i++) r.setVal(rnd.nextGaussian());
	r.makeASCIIart(3,"μs");
	return new Tuple<String, WeakReference<RandomVar>>(r.art, new WeakReference<RandomVar>(r, rq));
}
*/	

//----------------------------------------------------------------------------
public static void main(String[] args) {

	RandomVar r = new RandomVar("test1");

	System.out.println("Testing RandomVar");
	
	System.out.println("\nnumBars=="+numBars+" maxBarHeight=="+maxBarHeight);
	if(numBars != 3*maxBarHeight+1) System.err.println("numBars != 3*maxBarHeight+1");
	//for(var i=0; i<numBars; i++) System.out.println(bars[i]+"   "+i);

	//Check if average works:
	int halfsize = r.SIZE/2;
	double sum=0;
	for(int i=1; i<=halfsize; i++) {r.setVal(i); sum+=i;}
	if(r.getMean()*halfsize != sum) System.err.println( (r.getMean()*halfsize)+"!="+sum);
	// assertEquals(r.getMean()*halfsize, sum); // shall I put this in a try/catch :-)

	String out = "";
	int outNr = 0;
	double startSetVal = System.nanoTime();

	// Values from real-world string (incl. perhaps error, cf. testValStr) approx. Erlang distribution
	r = new RandomVar("test2");
	System.out.println("\n*** New "+shortClObjID(r)+" - setValsFromIntString(...) real-world string, approx. Erlang distribution ***\n");
	startSetVal = System.nanoTime();
	String errr = r.setValsIntString(testValStr, 0.1);
	System.out.println("Error: " + ( errr.isEmpty() ? "[No Error]" : errr ));
	System.out.println("μSeconds to set values: "+(System.nanoTime()-startSetVal)/1000.0+"\n");
	out = r.getASCIIart(3,"μs"); // Unit μF=microfarad :-)
	System.out.println(out);
	outNr = checkNr(out);
	if( outNr!=r.numValues ) System.err.println("(Numbers) sum to "+outNr+", not "+r.SIZE);
	
	
	//Generate values with Gaussian distribution, filling only half of the array:
	r = new RandomVar("test3");
	System.out.println("\n*** New "+shortClObjID(r)+" - Gaussian distribution N="+halfsize+" ***\n");
	Random rnd = new Random();
	startSetVal = System.nanoTime();
	for(int i=1; i<=halfsize; i++) r.setVal(rnd.nextGaussian());
	System.out.println("μSeconds to set values: "+(System.nanoTime()-startSetVal)/1000.0+"\n");
	out = r.getASCIIart(3,"μs"); // Unit μF=microfarad :-)
	System.out.println(out);
	outNr = checkNr(out);
	if( outNr!=r.numValues ) System.err.println("(Numbers) sum to "+outNr+", not "+r.SIZE);

	// One more, now full size:
	r = new RandomVar("test4");
	System.out.println("\n*** New "+shortClObjID(r)+" - Gaussian distribution N="+r.SIZE+" ***\n");
	startSetVal = System.nanoTime();
	for(int i=1; i<=r.SIZE; i++) r.setVal(rnd.nextGaussian());
	System.out.println("μSeconds to set values: "+(System.nanoTime()-startSetVal)/1000.0+"\n");
	out = r.getASCIIart(3,"μs");
	System.out.println(out);
	outNr = checkNr(out);
	if( outNr!=r.SIZE ) System.err.println("(Numbers) add up to "+outNr+", not "+r.SIZE);

	// Exponential distribution:
	r = new RandomVar("test5");
	System.out.println("\n*** New "+shortClObjID(r)+" - Exponential distribution N="+r.SIZE+" ***\n");
	startSetVal = System.nanoTime();
	for(int i=1; i<=r.SIZE; i++) r.setVal(-Math.log(1-rnd.nextDouble()));
	System.out.println("μSeconds to set values: "+(System.nanoTime()-startSetVal)/1000.0+"\n");
	out = r.getASCIIart(3,"μs");
	System.out.println(out);
	outNr = checkNr(out);
	if( outNr!=r.SIZE ) System.err.println("(Numbers) add up to "+outNr+", not "+r.SIZE);
	
	// "overload" RandomVar with uniform distribution
	int sz = r.SIZE/10;
	System.out.println("\n*** Overload "+shortClObjID(r)+" with uniform distribution ]0,100[ N="+sz+" ***\n");
	for(int i=1; i<=sz; i++) r.setVal(100*rnd.nextDouble());
	out = r.getASCIIart(3,"μs");
	System.out.println(out);
	outNr = checkNr(out);
	if( outNr!=r.SIZE ) System.err.println("(Numbers) add up to "+outNr+", not "+r.SIZE);
	
/* setVal timing (ns):
 * 
 * snychronized:
 * 4197700.0 3583600.0 4203100.0
 * 3660300.0 4295200.0 4776000.0
 * 5395900.0 3205500.0 3391100.0
 * 
 * not:
 * 5240500.0 3478900.0 3584100.0
 * 3685800.0 4543400.0 3061200.0
 * 3244100.0 7213800.0 2682300.0
 * 
 * setValsFromIntString timing (μs):
 * 
 * snychronized: 27047.5 21895.0 56227.0 24562.0
 * not:          23906.0 26057.7 50449.7 22412.1
*/
	
/*	// Unrelated, but opportunity for #WeakReferenceTEST
	// https://web.archive.org/web/20100819115659/http://weblogs.java.net/blog/2006/05/04/understanding-weak-references
	// "WeakReferences are enqueued as soon as the object to which they point becomes weakly reachable."
	// NOT HERE! (Likely depends on version of garbage collector)
	System.out.println("\n\n\n----- And now for something completely different:");

	Tuple<String, WeakReference<RandomVar>> tplwref = someFunctn("aa");
	System.out.println(" ASCII art length:"+tplwref.t1.length());
	System.out.println(" rq.poll():"+rq.poll());
	System.out.println(" Weak ref="+tplwref.t2.get());
	System.out.println(" Sleeping 5s:");
	try { Thread.sleep(5000); } catch (InterruptedException ignore) {}
	System.out.println(" rq.poll():"+rq.poll());
	System.out.println(" Weak ref="+tplwref.t2.get());

	Tuple<String, WeakReference<RandomVar>> tplwref2 = someFunctn("bb");
	System.out.println(" 2nd Weak ref="+tplwref2.t2.get());

	System.out.println(" Sleeping 5s:");
	try { Thread.sleep(5000); } catch (InterruptedException ignore) {}
	System.out.println(" rq.poll():"+rq.poll());
	System.out.println(" Weak ref="+tplwref.t2.get());
	
	System.out.println(" Making 1000 RandomVars:");
	RandomVar[] r1000 = new RandomVar[1000];
	for(int j=0; j<1000; j++) { r1000[j]=new RandomVar(); for(int i=1; i<=r.SIZE; i++) r1000[j].setVal(rnd.nextGaussian());}
	System.out.println(" rq.poll():"+rq.poll());
	System.out.println(" Weak ref="+tplwref.t2.get());

	System.out.println(" Sleeping 5s:");
	try { Thread.sleep(5000); } catch (InterruptedException ignore) {}
	System.out.println(" rq.poll():"+rq.poll());
	System.out.println(" Weak ref="+tplwref.t2.get());

	tplwref = someFunctn("aa2");
	tplwref2 = someFunctn("bb2");
	System.out.println(" Sleeping 3s:");
	try { Thread.sleep(3000); } catch (InterruptedException ignore) {}
	System.out.println(" rq.poll():"+rq.poll());
	System.out.println(" New Weak ref="+tplwref.t2.get());
	System.out.println(" Calling garbage collection:");
	System.gc();
	System.out.println(" rq.poll():"+rq.poll());
	System.out.println(" Weak ref="+tplwref.t2.get());

	System.out.println(" ASCII art length:"+tplwref.t1.length());

	System.out.println(" Sleeping 5s:");
	try { Thread.sleep(5000); } catch (InterruptedException ignore) {}
	System.out.println(" Calling garbage collection:");
	System.gc();
	System.out.println(" Sleeping 5s:");
	try { Thread.sleep(5000); } catch (InterruptedException ignore) {}
	System.out.println(" END");
*/
	
} // END main <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


} // END class TEST <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
}
