<?xml version="1.0" encoding="UTF-8"?>
<%@ page pageEncoding="UTF-8" contentType="text/html; charset=UTF-8" %>
<%-- HTTP response headers for less secure JavaScript policy, giving 5μs timer precision instead of 0.1ms --%>
<% response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
response.setHeader("Cross-Origin-Opener-Policy", "same-origin"); 
response.setCharacterEncoding("utf-8");
%>
<%@ include file="index.html" %>
<%--  #jsputf 👁↑ UTF-8 from index.html not rendering. All else, UTF-8 works! You can even send it across a websocket and embed it per JavaScript. Only UTF-8 directly embedded in index.html messes up.  --%>
<%-- The ancient principle still holds: Use HTML entities when not ASCII! --%>