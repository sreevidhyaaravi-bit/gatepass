<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gp" uri="http://gatepass.local/functions" %>
<%--
  Student dashboard. Data prepared by StudentDashboardServlet:
    profile  = Student (with Parent)       passes = last 10 GatePass objects (newest first)
    minExit  = today at 00:00 (for the date pickers)
    success / error / formReason / formExit / formReturn = flash values after submitting the form
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="en">
<head>
    <%@ include file="includes/head.jspf" %>
    <title>Student Dashboard | Digital Gate Pass</title>
</head>
<body>
<%@ include file="includes/header.jspf" %>
<main class="container">
    <div class="page-head">
        <div>
            <h1>Welcome, <c:out value="${profile.name}"/></h1>
            <p class="muted">Apply for a gate pass and follow the status of your requests.</p>
        </div>
    </div>

    <%@ include file="includes/messages.jspf" %>

    <div class="layout-sidebar">
        <div class="stack">
            <section class="card">
                <h2 class="card-title">My profile</h2>
                <dl class="facts">
                    <dt>Name</dt><dd><c:out value="${profile.name}"/></dd>
                    <dt>College ID</dt><dd><c:out value="${profile.collegeId}"/></dd>
                    <dt>Class</dt><dd><c:out value="${profile.className}"/></dd>
                    <dt>Semester</dt><dd><c:out value="${profile.semester}"/></dd>
                    <dt>Hostel roll no.</dt><dd><c:out value="${profile.hostelRoll}"/></dd>
                </dl>
            </section>
            <section class="card">
                <h2 class="card-title">Parent / guardian</h2>
                <dl class="facts">
                    <dt>Name</dt><dd><c:out value="${profile.parent.name}"/></dd>
                    <dt>Email</dt><dd class="break"><c:out value="${profile.parent.email}"/></dd>
                    <dt>Phone</dt><dd><c:out value="${profile.parent.phone}"/></dd>
                </dl>
                <p class="hint">Your parent is emailed automatically when you leave and when you return.</p>
            </section>
        </div>

        <section class="card">
            <h2 class="card-title">Apply for a gate pass</h2>
            <p class="hint">Your name, college ID, class, semester and hostel roll number are taken from college records automatically.</p>
            <form method="post" action="${ctx}/student/apply" class="form" id="applyForm">
                <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                <label class="field">
                    <span>Reason for leaving</span>
                    <textarea name="reason" rows="3" maxlength="300" required
                              placeholder="e.g. Medical appointment in town"><c:out value="${formReason}"/></textarea>
                </label>
                <div class="field-row">
                    <label class="field">
                        <span>Exit date and time</span>
                        <input type="datetime-local" name="exitDateTime" min="${minExit}"
                               value="${fn:escapeXml(formExit)}" required>
                    </label>
                    <label class="field">
                        <span>Expected return date and time</span>
                        <input type="datetime-local" name="returnDateTime" min="${minExit}"
                               value="${fn:escapeXml(formReturn)}" required>
                    </label>
                </div>
                <p class="form-error" id="applyClientError" role="alert" hidden></p>
                <button type="submit" class="btn btn-primary">Submit request</button>
            </form>
        </section>
    </div>

    <section class="card">
        <div class="card-head">
            <h2 class="card-title">My recent passes</h2>
            <span class="muted small">Last 10, newest first</span>
        </div>
        <c:choose>
            <c:when test="${empty passes}">
                <p class="empty">You have not applied for a gate pass yet.</p>
            </c:when>
            <c:otherwise>
                <div class="table-wrap">
                    <table class="table">
                        <thead>
                        <tr>
                            <th>Pass no.</th><th>Reason</th><th>Exit (requested)</th><th>Expected return</th>
                            <th>Status</th><th>Actual exit</th><th>Actual entry</th><th>Remark</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="p" items="${passes}">
                            <tr>
                                <td class="mono"><c:out value="${p.passNo}"/></td>
                                <td class="wrap"><c:out value="${p.reason}"/></td>
                                <td class="nowrap">${gp:dt(p.exitDateTime)}</td>
                                <td class="nowrap">${gp:dt(p.expectedEntryDateTime)}</td>
                                <td class="nowrap">
                                    <span class="badge badge-${fn:toLowerCase(p.status)}">${p.status}</span>
                                    <c:if test="${p.lateFlag}"><span class="badge badge-late">LATE</span></c:if>
                                </td>
                                <td class="nowrap">${gp:dt(p.actualExitDateTime)}</td>
                                <td class="nowrap">${gp:dt(p.actualEntryDateTime)}</td>
                                <td class="wrap"><c:out value="${empty p.rejectionRemark ? '-' : p.rejectionRemark}"/></td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:otherwise>
        </c:choose>
    </section>
</main>
<%@ include file="includes/footer.jspf" %>
</body>
</html>
