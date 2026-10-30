<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gp" uri="http://gatepass.local/functions" %>
<%--
  Warden dashboard. Data prepared by WardenDashboardServlet:
    tab          = pending | movements | late | overdue | history
    pending      = PENDING passes          movements   = passes with an actual exit (latest first)
    lateReturns  = passes with late flag   overdue     = OverdueEntry records (pass + overdueBy)
    searchedId / historyStudent / history / historyError = student history search
  OverdueEntry is a Java record, so its values are read with method calls: o.pass(), o.overdueBy().
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="en">
<head>
    <%@ include file="includes/head.jspf" %>
    <title>Warden Dashboard | Digital Gate Pass</title>
</head>
<body>
<%@ include file="includes/header.jspf" %>
<main class="container">
    <div class="page-head">
        <div>
            <h1>Warden dashboard</h1>
            <p class="muted">Approve requests and follow every student who leaves or returns.</p>
        </div>
        <a class="btn btn-light" href="${ctx}/warden/dashboard?tab=${tab}">Refresh</a>
    </div>

    <%@ include file="includes/messages.jspf" %>

    <div class="stats">
        <a class="stat ${fn:length(pending) > 0 ? 'stat-warn' : ''}" href="${ctx}/warden/dashboard?tab=pending">
            <span class="stat-value">${fn:length(pending)}</span><span class="stat-label">Pending requests</span>
        </a>
        <a class="stat ${fn:length(overdue) > 0 ? 'stat-danger' : ''}" href="${ctx}/warden/dashboard?tab=overdue">
            <span class="stat-value">${fn:length(overdue)}</span><span class="stat-label">Overdue now</span>
        </a>
        <a class="stat" href="${ctx}/warden/dashboard?tab=late">
            <span class="stat-value">${fn:length(lateReturns)}</span><span class="stat-label">Late returns</span>
        </a>
        <a class="stat" href="${ctx}/warden/dashboard?tab=movements">
            <span class="stat-value">${fn:length(movements)}</span><span class="stat-label">Gate movements</span>
        </a>
    </div>

    <nav class="tabs" aria-label="Dashboard sections">
        <a class="tab ${tab == 'pending' ? 'active' : ''}" href="${ctx}/warden/dashboard?tab=pending">Pending requests <span class="count">${fn:length(pending)}</span></a>
        <a class="tab ${tab == 'movements' ? 'active' : ''}" href="${ctx}/warden/dashboard?tab=movements">Movement updates</a>
        <a class="tab ${tab == 'late' ? 'active' : ''}" href="${ctx}/warden/dashboard?tab=late">Late returns <span class="count">${fn:length(lateReturns)}</span></a>
        <a class="tab ${tab == 'overdue' ? 'active' : ''}" href="${ctx}/warden/dashboard?tab=overdue">Overdue <span class="count ${fn:length(overdue) > 0 ? 'count-danger' : ''}">${fn:length(overdue)}</span></a>
        <a class="tab ${tab == 'history' ? 'active' : ''}" href="${ctx}/warden/dashboard?tab=history">Student history</a>
    </nav>

    <section class="card card-tab">
    <c:choose>

        <%-- ===================== A. Pending requests ===================== --%>
        <c:when test="${tab == 'pending'}">
            <h2 class="card-title">Pending requests</h2>
            <c:choose>
                <c:when test="${empty pending}"><p class="empty">No requests are waiting for a decision.</p></c:when>
                <c:otherwise>
                    <div class="table-wrap">
                        <table class="table">
                            <thead><tr>
                                <th>Pass no.</th><th>Student</th><th>Reason</th><th>Requested exit</th>
                                <th>Expected return</th><th>Decision</th>
                            </tr></thead>
                            <tbody>
                            <c:forEach var="p" items="${pending}">
                                <tr>
                                    <td class="mono"><c:out value="${p.passNo}"/></td>
                                    <td class="nowrap">
                                        <strong><c:out value="${p.student.name}"/></strong>
                                        <span class="cell-sub mono"><c:out value="${p.collegeId}"/> &middot; Hostel <c:out value="${p.student.hostelRoll}"/></span>
                                        <span class="cell-sub"><c:out value="${p.student.className}"/>, semester <c:out value="${p.student.semester}"/></span>
                                    </td>
                                    <td class="wrap"><c:out value="${p.reason}"/></td>
                                    <td>${gp:dt(p.exitDateTime)}</td>
                                    <td>${gp:dt(p.expectedEntryDateTime)}</td>
                                    <td class="decision">
                                        <form method="post" action="${ctx}/warden/decision">
                                            <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                                            <input type="hidden" name="passNo" value="${fn:escapeXml(p.passNo)}">
                                            <input type="hidden" name="action" value="approve">
                                            <button type="submit" class="btn btn-success btn-sm">Approve</button>
                                        </form>
                                        <form method="post" action="${ctx}/warden/decision" class="reject-form"
                                              data-confirm="Reject pass ${fn:escapeXml(p.passNo)} for ${fn:escapeXml(p.student.name)}?">
                                            <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                                            <input type="hidden" name="passNo" value="${fn:escapeXml(p.passNo)}">
                                            <input type="hidden" name="action" value="reject">
                                            <input type="text" name="remark" maxlength="200" required
                                                   placeholder="Rejection remark" aria-label="Rejection remark for ${fn:escapeXml(p.passNo)}">
                                            <button type="submit" class="btn btn-danger btn-sm">Reject</button>
                                        </form>
                                    </td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:otherwise>
            </c:choose>
        </c:when>

        <%-- ===================== B. Movement updates ===================== --%>
        <c:when test="${tab == 'movements'}">
            <h2 class="card-title">Movement updates</h2>
            <p class="hint">Every recorded exit, latest first. Students still outside have status EXITED.</p>
            <c:choose>
                <c:when test="${empty movements}"><p class="empty">No exits have been recorded yet.</p></c:when>
                <c:otherwise>
                    <div class="table-wrap">
                        <table class="table">
                            <thead><tr>
                                <th>Pass no.</th><th>Student</th><th>College ID</th><th>Status</th><th>Requested exit</th>
                                <th>Actual exit</th><th>Expected return</th><th>Actual entry</th><th>Late</th>
                            </tr></thead>
                            <tbody>
                            <c:forEach var="p" items="${movements}">
                                <tr>
                                    <td class="mono"><c:out value="${p.passNo}"/></td>
                                    <td><c:out value="${p.student.name}"/></td>
                                    <td class="mono"><c:out value="${p.collegeId}"/></td>
                                    <td><span class="badge badge-${fn:toLowerCase(p.status)}">${p.status}</span></td>
                                    <td class="nowrap">${gp:dt(p.exitDateTime)}</td>
                                    <td class="nowrap">${gp:dt(p.actualExitDateTime)}</td>
                                    <td class="nowrap">${gp:dt(p.expectedEntryDateTime)}</td>
                                    <td class="nowrap">${gp:dt(p.actualEntryDateTime)}</td>
                                    <td>${p.lateFlag ? '<span class="badge badge-late">LATE</span>' : '<span class="muted">No</span>'}</td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:otherwise>
            </c:choose>
        </c:when>

        <%-- ===================== C. Late returns ===================== --%>
        <c:when test="${tab == 'late'}">
            <h2 class="card-title">Late returns</h2>
            <p class="hint">Students who came back after their expected return time.</p>
            <c:choose>
                <c:when test="${empty lateReturns}"><p class="empty">No late returns recorded.</p></c:when>
                <c:otherwise>
                    <div class="table-wrap">
                        <table class="table">
                            <thead><tr>
                                <th>Pass no.</th><th>Student</th><th>College ID</th><th>Actual exit</th>
                                <th>Expected return</th><th>Actual return</th><th>Late by</th>
                            </tr></thead>
                            <tbody>
                            <c:forEach var="p" items="${lateReturns}">
                                <tr>
                                    <td class="mono"><c:out value="${p.passNo}"/></td>
                                    <td><c:out value="${p.student.name}"/></td>
                                    <td class="mono"><c:out value="${p.collegeId}"/></td>
                                    <td class="nowrap">${gp:dt(p.actualExitDateTime)}</td>
                                    <td class="nowrap">${gp:dt(p.expectedEntryDateTime)}</td>
                                    <td class="nowrap">${gp:dt(p.actualEntryDateTime)}</td>
                                    <td class="nowrap text-danger">${gp:lateBy(p)}</td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:otherwise>
            </c:choose>
        </c:when>

        <%-- ===================== D. Overdue students ===================== --%>
        <c:when test="${tab == 'overdue'}">
            <h2 class="card-title">Overdue students</h2>
            <p class="hint">Still outside (status EXITED) and past the expected return time. A student leaves this list as soon as the guard records the return.</p>
            <c:choose>
                <c:when test="${empty overdue}"><p class="empty">Nobody is overdue right now.</p></c:when>
                <c:otherwise>
                    <div class="table-wrap">
                        <table class="table">
                            <thead><tr>
                                <th>Student</th><th>College ID</th><th>Pass no.</th><th>Actual exit</th>
                                <th>Expected return</th><th>Overdue by</th>
                            </tr></thead>
                            <tbody>
                            <c:forEach var="o" items="${overdue}">
                                <c:set var="p" value="${o.pass()}"/>
                                <tr class="row-danger">
                                    <td><c:out value="${p.student.name}"/></td>
                                    <td class="mono"><c:out value="${p.collegeId}"/></td>
                                    <td class="mono"><c:out value="${p.passNo}"/></td>
                                    <td class="nowrap">${gp:dt(p.actualExitDateTime)}</td>
                                    <td class="nowrap">${gp:dt(p.expectedEntryDateTime)}</td>
                                    <td class="nowrap text-danger"><strong>${gp:duration(o.overdueBy())}</strong></td>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:otherwise>
            </c:choose>
        </c:when>

        <%-- ===================== E. Student history ===================== --%>
        <c:otherwise>
            <h2 class="card-title">Student history</h2>
            <form method="get" action="${ctx}/warden/dashboard" class="search-form">
                <input type="hidden" name="tab" value="history">
                <label class="field">
                    <span>College ID</span>
                    <input type="text" name="collegeId" value="<c:out value='${searchedId}'/>" placeholder="e.g. CS24001"
                           autocapitalize="characters" required>
                </label>
                <button type="submit" class="btn btn-primary">Search</button>
            </form>

            <c:if test="${not empty historyError}">
                <div class="alert alert-error" role="alert"><c:out value="${historyError}"/></div>
            </c:if>

            <c:if test="${not empty historyStudent}">
                <div class="profile-strip">
                    <div><span class="muted small">Student</span><strong><c:out value="${historyStudent.name}"/></strong></div>
                    <div><span class="muted small">College ID</span><strong class="mono"><c:out value="${historyStudent.collegeId}"/></strong></div>
                    <div><span class="muted small">Class / Sem</span><strong><c:out value="${historyStudent.className}"/> / <c:out value="${historyStudent.semester}"/></strong></div>
                    <div><span class="muted small">Hostel roll</span><strong class="mono"><c:out value="${historyStudent.hostelRoll}"/></strong></div>
                    <div><span class="muted small">Parent</span><strong><c:out value="${historyStudent.parent.name}"/></strong>
                        <span class="small"><c:out value="${historyStudent.parent.phone}"/> &middot; <c:out value="${historyStudent.parent.email}"/></span></div>
                </div>
                <c:choose>
                    <c:when test="${empty history}"><p class="empty">This student has no gate passes yet.</p></c:when>
                    <c:otherwise>
                        <div class="table-wrap">
                            <table class="table">
                                <thead><tr>
                                    <th>Pass no.</th><th>Reason</th><th>Requested exit</th><th>Expected return</th>
                                    <th>Actual exit</th><th>Actual entry</th><th>Status</th><th>Late</th><th>Remark</th>
                                </tr></thead>
                                <tbody>
                                <c:forEach var="p" items="${history}">
                                    <tr>
                                        <td class="mono"><c:out value="${p.passNo}"/></td>
                                        <td class="wrap"><c:out value="${p.reason}"/></td>
                                        <td class="nowrap">${gp:dt(p.exitDateTime)}</td>
                                        <td class="nowrap">${gp:dt(p.expectedEntryDateTime)}</td>
                                        <td class="nowrap">${gp:dt(p.actualExitDateTime)}</td>
                                        <td class="nowrap">${gp:dt(p.actualEntryDateTime)}</td>
                                        <td><span class="badge badge-${fn:toLowerCase(p.status)}">${p.status}</span></td>
                                        <td>${p.lateFlag ? '<span class="badge badge-late">LATE</span>' : '<span class="muted">No</span>'}</td>
                                        <td class="wrap"><c:out value="${empty p.rejectionRemark ? '-' : p.rejectionRemark}"/></td>
                                    </tr>
                                </c:forEach>
                                </tbody>
                            </table>
                        </div>
                    </c:otherwise>
                </c:choose>
            </c:if>
        </c:otherwise>
    </c:choose>
    </section>
</main>
<%@ include file="includes/footer.jspf" %>
</body>
</html>
