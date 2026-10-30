<%@ page contentType="text/html;charset=UTF-8" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  One friendly error page for 403 / 404 / 405 / unexpected errors. It never shows a stack trace.
  errorMessage is set only by BaseServlet, for messages that are written for users.
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="code" value="${requestScope['jakarta.servlet.error.status_code']}"/>
<!DOCTYPE html>
<html lang="en">
<head>
    <%@ include file="includes/head.jspf" %>
    <title>Problem | Digital Gate Pass</title>
</head>
<body>
<%@ include file="includes/header.jspf" %>
<main class="container narrow">
    <section class="card error-card">
        <c:choose>
            <c:when test="${not empty errorMessage}">
                <h1>Something needs attention</h1>
                <p><c:out value="${errorMessage}"/></p>
            </c:when>
            <c:when test="${code == 403}">
                <h1>Access denied</h1>
                <p>You do not have permission to open this page.</p>
            </c:when>
            <c:when test="${code == 404}">
                <h1>Page not found</h1>
                <p>The page you asked for does not exist.</p>
            </c:when>
            <c:otherwise>
                <h1>Something went wrong</h1>
                <p>The request could not be completed. Please go back and try again.</p>
            </c:otherwise>
        </c:choose>
        <a class="btn btn-primary" href="${ctx}/">Go to my dashboard</a>
    </section>
</main>
<%@ include file="includes/footer.jspf" %>
</body>
</html>
