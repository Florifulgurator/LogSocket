<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="javalggr.LogSocket" %>
<%@ page import="javalggr.Lggr" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.stream.Collectors" %>

<%-- Java brimborium to simply print the request headers: --%>
<%@ page import="java.util.Spliterator" %>
<%@ page import="java.util.Spliterators" %>
<%@ page import="java.util.stream.Stream" %>
<%@ page import="java.util.stream.StreamSupport" %>


<%!	boolean testLggrOK = LogSocket.newLggr("Jsp", "#HELLOWORLD", "Throwaway logger in declaration")
	                      .log("!!!!!!!!!!!!!!!! Hello World !!!!!!!!!!!!!!!!");
	//Lggr lggr1 = LogSocket.newLggr("Jsp", "#MAINLGGR", "Created at compileTime="+compileTime);
	// 4 Errors: lggr1.log("Hello World");
	
	Integer crtrNr = 0; // TODO  = LogSocket.newCreatorNr("Jsp")
	String servletURL = "./TESTservlet";
	String text = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.";
	String text2 = "https://www.bobrosslipsum.com/ Only eight lorem ipsum colors that you need. If you dont like it - change it. It is your world. Life is too short to be alone, too precious. Share it with a friend. Volunteering your time; it pays you and your whole community fantastic dividends. Automatically, all of these beautiful, beautiful things will happen. We spend so much of our life looking - but never seeing. We need dark in order to show light. In your imagination you can go anywhere you want. Every time you practice, you learn more. This present moment is perfect simply due to the fact you are experiencing it. There are no mistakes. You can fix anything that happens.";

	String requestHeadersToString(HttpServletRequest r) { // getHeaderNames() => Enumeration<String>
		Stream<String> str = StreamSupport.stream(Spliterators.spliteratorUnknownSize(r.getHeaderNames().asIterator(), Spliterator.ORDERED),false);
		return str.map(e -> e+"="+r.getHeader(e)).collect(Collectors.joining("\n"));
	}
	String responseHeadersToString(HttpServletResponse r) { // getHeaderNames() => Collection<String>
		return r.getHeaderNames().stream().map(e -> e+"="+r.getHeader(e)).collect(Collectors.joining("\n"));
	}
%>

<!-- ===================================================================== -->

<% 	crtrNr++;

	Lggr lggr1 = LogSocket.newLggr(this, "Jsp", "#INSTANCE"+crtrNr+"#MAINLGGR");
	lggr1.logM("Request headers="+requestHeadersToString(request));
	lggr1.log("... Compile time logger has logged == "+testLggrOK);
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

/* Copied from socklog.css */
/* Loggers. Single Letters A-Z only. TODO: get rid of opacity */

.bgC0 { background-color: rgb(218, 240, 217);}
/* .bgC1 { background-color: rgb(218, 240, 217, 0.69);} */
.bgA0 { background-color: rgb(230, 231, 242);}
/* .bgA1 { background-color: rgb(230, 231, 242, 0.70);} */
.bgB0 { background-color: rgb(202, 226, 249);}
/* .bgB1 { background-color: rgb(202, 226, 249, 0.78);} */
.bgR0 { background-color: rgb(200, 200, 255);} 
/* .bgR1 { background-color: rgb(200, 200, 255, 0.83);} */
.bgJ0 { background-color: rgb(245, 201, 238);}
/* .bgJ1 { background-color: rgb(245, 201, 238, 0.80);} */
.bgN0 { background-color: rgb(231, 219, 232);} 
/* .bgN1 { background-color: rgb(231, 219, 232, 0.77);} */
.bgQ0 { background-color: rgb(238, 225, 176);} 
/* .bgQ1 { background-color: rgb(238, 225, 176, 0.80);}  */
.bgO0 { background-color: rgb(242, 209, 183);} 
/* .bgO1 { background-color: rgb(242, 209, 183, 0.80);}  */		

	</style>	
</head>
<body>

<!-- ===================================================================== -->

<h1>LogSocket Test Page</h1>


<h2>  ~&nbsp;Pure JavaScript loggers&nbsp;~</h2>
<hr><!-- ----------------------------------------------------------------- -->

<input onclick="colorTestCreate()" id="clrTstCrtID" value="Create" type="button" disabled>
<input onclick="colorTestDestroy()" id="clrTstDstrID" value="Delete" type="button" disabled>
loggers of differently colored realms to test output colors.
<br>
<label id="mouseEnterLogID"><input type="checkbox" onclick="mouseEnterLogClick()"/>
	Log <tt>mouseenter</tt> event on each word:
</label>

<p><%= spanText %> -- <%= spanText2 %></p>

<hr><!-- ----------------------------------------------------------------- -->

<input onclick="JSLoop()" id="JSLoopID" value="JSLoop()" type="button">
Loop <span id="loopNrID1">???</span> x 2 Lorem Ipsum logs from JavaScript.


<hr><!-- ----------------------------------------------------------------- -->
<h2>~&nbsp;"Pure" Java loggers&nbsp;~</h2>
<hr><!-- ----------------------------------------------------------------- -->

<input onclick="pingServlet()" id="pingServletID" value="pingServlet()" type="button" disabled>

<input onclick="servletThread()" id="servletThreadID" value="servletThread()" type="button" disabled>

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
%>

Adding Cookies: <br>

<%	response.addCookie(new Cookie("aaaaa", "bbbbb"));
	response.addCookie(new Cookie("axxaa", "byybb"));
	response.flushBuffer(); //!!!!
%>		
 
<hr>


UTF-8 Symbols:
💬 ▼▲/ 📄 / 🕮 / 🖵 / 🖹 / 🗈 / 🗩 / 👁 / ☝ / ❏ / … / &#9746; / &#9776;
⧌
⅒AAAAA
½BBBBBB
⅒CCCCCC&#8530;	


</body><% lggr1.log(" HTML tag close"); %></html>