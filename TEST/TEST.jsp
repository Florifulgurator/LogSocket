<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="florifulgurator.logsocket.javalggr.LogSocket" %>
<%@ page import="florifulgurator.logsocket.javalggr.Lggr" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.stream.Collectors" %>

<%-- Java brimborium to simply print the request headers: --%>
<%@ page import="java.util.Spliterator" %>
<%@ page import="java.util.Spliterators" %>
<%@ page import="java.util.stream.Stream" %>
<%@ page import="java.util.stream.StreamSupport" %>


<%!	boolean testLggrOK = LogSocket.newLggr("Jsp", "#HELLOWORLD", "(UTF-8 test: 👁↑) Throwaway logger in declaration")
	                      .log("!!!!!!!!!!!!!!!! Hello World !!!!!!!!!!!!!!!! (UTF-8 test: 👁↑ -- Oversized? Check line height.)");
	//Lggr lggr1 = LogSocket.newLggr("Jsp", "#MAINLGGR", "Created at compileTime="+compileTime);
	// 4 Errors: lggr1.log("Hello World");
	
	Integer crtrNr = 0; // TODO  = LogSocket.newCreatorNr("Jsp")
	String servletURL = "./TESTservlet";
	String text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
	String text2 = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
//	String text2 = "https://www.bobrosslipsum.com/ Only eight lorem ipsum colors that you need. If you dont like it - change it. It is your world. Life is too short to be alone, too precious. Share it with a friend. Volunteering your time; it pays you and your whole community fantastic dividends. Automatically, all of these beautiful, beautiful things will happen. We spend so much of our life looking - but never seeing. We need dark in order to show light. In your imagination you can go anywhere you want. Every time you practice, you learn more. This present moment is perfect simply due to the fact you are experiencing it. There are no mistakes. You can fix anything that happens.";

	String requestHeadersToString(HttpServletRequest r) { // getHeaderNames() => Enumeration<String>
		Stream<String> str = StreamSupport.stream(Spliterators.spliteratorUnknownSize(r.getHeaderNames().asIterator(), Spliterator.ORDERED),false);
		return str.map(e -> e+"="+r.getHeader(e)).collect(Collectors.joining("\n"));
	}
	String responseHeadersToString(HttpServletResponse r) { // getHeaderNames() => Collection<String>
		return r.getHeaderNames().stream().map(e -> e+"="+r.getHeader(e)).collect(Collectors.joining("\n"));
	}
%>

<!-- ===================================================================== -->

<% 	System.out.println("TEST.jsp Run !!!!!!!!!!!!!!!! Hello World !!!!!!!!!!!!!!!!");
	crtrNr++;

	Lggr lggr1 = LogSocket.newLggr(this, "Jsp", "#INSTANCE"+crtrNr+"#MAINLGGR");
	lggr1.logM("Request headers: "+requestHeadersToString(request));
	if(!testLggrOK) lggr1.logErr("Compile time logger has not logged");
	lggr1.log("... Setting COOP and COEP HTTP headers to allow JavaScript precision timer. See JS log for result.");

	response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
	response.setHeader("Cross-Origin-Opener-Policy", "same-origin");

//  https://web.dev/articles/coop-coep
// Use a combination of COOP and COEP HTTP headers to opt a web page into a special cross-origin
// isolated state. You will be able to examine self.crossOriginIsolated to determine whether a web
// page is in a cross-origin isolated state.
//
// https://web.dev/articles/monitor-total-page-memory-usage
// https://incolumitas.com/2021/12/18/on-high-precision-javascript-timers/


%>

<%	String spanText  =  Arrays.stream(text.split(" ")).map( t -> "<span class='a'>"+t+" </span>").collect(Collectors.joining(""));
	String spanText2 = Arrays.stream(text2.split(" ")).map( t -> "<span class='a'>"+t+" </span>").collect(Collectors.joining(""));
%>

<!-- ===================================================================== -->

<!DOCTYPE html>
<html><% lggr1.log("HTML tag open "); %>
<head>
	<meta charset="UTF-8">
	<title>LogSocket Test Page</title>

	<script>
		const servletURL = "<%= servletURL %>";
		const LogSocketCreatorNr = <%= crtrNr %>;
	</script>
	<script type="text/javascript" src="LogSocket.js"></script> 
	<script type="text/javascript" src="TEST.js"></script>

	<style>
		.a:hover {outline: solid;}
		#testlogID {outline: solid;}
		.mnspcd  {font-family: monospace, monospace; } /* sic! */
		.smallr  {font-size: smaller;}
		.bold    {font-weight: 700;}
		.nobr    {white-space: nowrap;}
		.pre     {white-space: pre;}

/* To see the logger colors close together: */
/* COPYPASTE #7eb9e155 from client.css Only bg[A-Z]0 is used*/
/* ***************************** */
/* OUTPUT of clrOptmzr.js:       */
/* Logger background colors      */
/* ***************************** */
/* #g968 */
/* Fri Mar 01 2024 15:46:32 GMT+0100 (Central European Standard Time) */
/* M odifications */
.bgC2 { background-color: rgb(201, 242, 207, 0.81);} /*M*/
.bgA2 { background-color: rgb(205, 223, 218, 0.863);}
.bgM2 { background-color: rgb(183, 236, 227, 0.867);}
.bgR2 { background-color: rgb(186, 222, 244, 0.867);}
.bgJ2 { background-color: rgb(204, 208, 253, 0.867);}
.bgN2 { background-color: rgb(224, 208, 226, 0.863);}
.bgQ2 { background-color: rgb(244, 212, 207, 0.867);}
.bgO2 { background-color: rgb(228, 228, 199, 0.863);}
/* bgX0 == bgX2 without transparency: */
.bgC0 { background-color: rgb(201, 242, 207);}
.bgA0 { background-color: rgb(205, 223, 218);}
.bgM0 { background-color: rgb(183, 236, 227);}
.bgR0 { background-color: rgb(186, 222, 244);}
.bgJ0 { background-color: rgb(204, 208, 253);}
.bgN0 { background-color: rgb(224, 208, 226);}
.bgQ0 { background-color: rgb(244, 212, 207);}
.bgO0 { background-color: rgb(228, 228, 199);}
/* bgX1 == no-transparency RGB from bgX2 being transparent on white: */
.bgC1 { background-color: rgb(210, 244, 215);}
.bgA1 { background-color: rgb(212, 227, 223);}
.bgM1 { background-color: rgb(193, 239, 231);}
.bgR1 { background-color: rgb(195, 226, 245);}
.bgJ1 { background-color: rgb(211, 214, 253);}
.bgN1 { background-color: rgb(228, 214, 230);}
.bgQ1 { background-color: rgb(245, 218, 213);}
.bgO1 { background-color: rgb(232, 232, 207);}
/* END OUTPUT of clrOptmzr.js <<<<<<<<<<<<<<<<<<<<<<<<<<<<<< */

	</style>	
</head>
<body>

<!-- ===================================================================== -->

<h1>LogSocket Test Page</h1>

Testing Tomcat UTF-8 issue: "👁↑" (=&uarr;&uarr;&uarr;?...) 
Oversized UTF-8 "👁↑" to be sent by #HELLOWORLD and #UTFTEST loggers.

<h2>  ~&nbsp;Pure JavaScript loggers&nbsp;~</h2>
<hr><!-- ----------------------------------------------------------------- -->

<input onclick="colorTestCreate()" id="clrTstCrtID" value="Create" type="button" disabled>
<input onclick="colorTestDestroy()" id="clrTstDstrID" value="Delete" type="button" disabled>
loggers of differently colored realms to test output colors.
<br>
<label id="mouseEnterLogID"><input type="checkbox" onclick="mouseEnterLogClick()"/>
	Log <span class="mnspcd">mouseenter</span> event on each word:
</label>

<p><%= spanText %> <br> <%= spanText2 %></p>

<hr><!-- ----------------------------------------------------------------- -->

<input onclick="JSLoop()" id="JSLoopID" value="JSLoop()" type="button">
Loop <span id="loopNrID1">???</span> x 2 Lorem Ipsum logs from JavaScript.

<hr><!-- ----------------------------------------------------------------- -->

Testing garbage collection and weak reference things:
<br>
<input onclick="duplicates_1()" value="duplicates_1()" type="button"> Create local array[10] of WeakRefs to "same" loggers, return WeakRef to array (lands nowhere).
<!-- WeakRef delays garbage collection -->
<br>
<input onclick="duplicates_2()" value="duplicates_2()" type="button"> Create global array[10] of "same" loggers
<input onclick="duplicates=[]" value="duplicates=[]" type="button">
<br>
<input onclick="duplicates_3()" value="async duplicates_3()" type="button"> Call duplicates_1() and duplicates_2(), check array WeakRef in loop with ExtraButton logger and window.gc();



<hr><!-- ----------------------------------------------------------------- -->
<h2>~&nbsp;"Pure" Java loggers&nbsp;~</h2>
<hr><!-- ----------------------------------------------------------------- -->

<input onclick="pingServlet()" id="pingServletID" value="pingServlet()" type="button" disabled>
<br>

<input onclick="servletThread()" id="servletThreadID" value="servletThread()" type="button" disabled>
Test LogSocket WeakRef cleanup daemon and ExtraButton logger with CompletableFuture

<br>
<input onclick="initEventSource()" id="initEventSourceID" value="Start" type="button" disabled>
<input onclick="closeEventSource()" id="closeEventSourceID" value="Stop" type="button" disabled>
Javascript EventSource subscribed to Servlet

<hr><!-- ----------------------------------------------------------------- -->

<input onclick="JavaLoop()" id="JavaLoopID" value="JavaLoop()" type="button">
Loop <span id="loopNrID2">???</span> x 2 Lorem Ipsum logs at Java Servlet
<br>


<hr><!-- ----------------------------------------------------------------- -->
<h2>~&nbsp;Mixed JS/Java loggers&nbsp;~</h2>
<hr><!-- ----------------------------------------------------------------- -->

<input onclick="JSJavaLoop()" id="JSJavaLoopID" value="JSJavaLoop()" type="button">
Javascript and Java loop, 2x <span id="loopNrID3">???</span> x 2 Lorem Ipsum logs
<br>
N.B.: https://develotters.com/posts/how-not-to-measure-elapsed-time/

<hr><!-- ----------------------------------------------------------------- -->

<br>

<h2>Page log:</h2>
<div id="testlogID"></div>


<hr><!-- ----------------------------------------------------------------- -->


<h2>Miscellania</h2>

<%	Lggr l2 = LogSocket.newLggr(this, "Jsp", "#COOKIETEST");

	if (request.getCookies() != null)
		l2.log("Cookies: "+ Arrays.stream(request.getCookies()).map(c -> c.getName()+"="+c.getValue()).collect(Collectors.joining(", ")));
	else
		l2.log("No Cookies");
		
	Lggr l3 = LogSocket.newLggr("Jsp", "#UTFTEST", "UTF-8 test: 👁↑💬 -- Oversized? Check line height.");
	l3.log("UTF-8 test 1: 👁↑ -- Oversized? Check line height.");
	l3.log("UTF-8 test 2: …💬 ");

%>

Adding Cookies: <br>

<%	response.addCookie(new Cookie("aaaaa", "bbbbb"));
	response.addCookie(new Cookie("axxaa", "byybb"));
	response.flushBuffer(); //!!!!
%>		
 
<hr>


UTF-8 Symbols:
↑💬 ▼▲ / 📄 / 🕮 / 🖵 / 🖹 / 🗈 / 🗩 / 👁 / ☝ / ❏ / … / &#9746; / &#9776;
⧌ / 🡅 / ↑ / ☒ /
⅒AAAAA
½BBBBBB
⅒CCCCCC&#8530;	
↑ Not quite monospaced!

</body><% lggr1.log(" HTML tag close"); %></html>