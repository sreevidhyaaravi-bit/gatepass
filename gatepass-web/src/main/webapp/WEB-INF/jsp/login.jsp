<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="en">
<head>
    <%@ include file="includes/head.jspf" %>
    <title>Login | Digital Gate Pass</title>
</head>
<body class="login-page">
<main class="login-wrap">
    <section class="login-card">
        <div class="login-brand">
            <span class="brand-mark brand-mark-lg" aria-hidden="true">GP</span>
            <h1>Digital Gate Pass</h1>
            <p class="muted">Campus Exit Management System</p>
        </div>

        <%-- Messages chosen from a fixed list: the text in the URL is never printed. --%>
        <c:choose>
            <c:when test="${param.msg == 'loggedout'}"><div class="alert alert-success" role="status">You have been logged out.</div></c:when>
            <c:when test="${param.msg == 'expired'}"><div class="alert alert-info" role="status">Your session expired. Please log in again.</div></c:when>
            <c:when test="${param.msg == 'login'}"><div class="alert alert-info" role="status">Please log in to continue.</div></c:when>
        </c:choose>
        <c:if test="${not empty error}">
            <div class="alert alert-error" role="alert"><c:out value="${error}"/></div>
        </c:if>

        <form method="post" action="${ctx}/login" class="form">
            <label class="field">
                <span>Login ID</span>
                <input type="text" name="loginId" value="<c:out value='${loginId}'/>" placeholder="e.g. CS24001 or WARDEN01"
                       autocomplete="username" autocapitalize="characters" required autofocus>
            </label>
            <label class="field">
                <span>Password</span>
                <input type="password" name="password" autocomplete="current-password" required>
            </label>
            <button type="submit" class="btn btn-primary btn-block">Log in</button>
        </form>

        <div class="login-notes">
            <p><strong>Students:</strong> use your college ID. <strong>Warden:</strong> use your warden ID.</p>
            <p>Security guards: please use the <em>Gate Guard</em> desktop application.</p>
        </div>
    </section>
</main>
<%@ include file="includes/footer.jspf" %>
</body>
</html>
