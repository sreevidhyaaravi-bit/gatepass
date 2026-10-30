package com.gatepass.web;

import com.gatepass.model.Student;
import com.gatepass.model.User;
import com.gatepass.service.PassService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Warden dashboard with five tabs: pending requests, movement updates, late returns,
 * overdue students and student history (search by college ID).
 *
 * <p>All lists are loaded on every visit, so each tab shows an up-to-date count and automatic
 * expiry runs every time the warden opens the page.</p>
 */
@WebServlet(name = "WardenDashboardServlet", urlPatterns = "/warden/dashboard")
public class WardenDashboardServlet extends BaseServlet {

    private static final Set<String> TABS = Set.of("pending", "movements", "late", "overdue", "history");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User warden = currentUser(req);
        PassService service = passService();

        String tab = req.getParameter("tab");
        if (tab == null || !TABS.contains(tab)) {
            tab = "pending";
        }

        req.setAttribute("movements", service.getMovementUpdates(warden)); // runs automatic expiry first
        req.setAttribute("pending", service.getPendingRequests(warden));
        req.setAttribute("lateReturns", service.getLateReturns(warden));
        req.setAttribute("overdue", service.getOverdueList(warden));

        String collegeId = req.getParameter("collegeId");
        if (collegeId != null && !collegeId.isBlank()) {
            tab = "history";
            req.setAttribute("searchedId", collegeId.trim());
            Optional<Student> student = service.findStudentProfile(warden, collegeId);
            if (student.isPresent()) {
                req.setAttribute("historyStudent", student.get());
                req.setAttribute("history", service.getStudentHistory(warden, collegeId));
            } else {
                req.setAttribute("historyError", "Student not found");
            }
        }
        req.setAttribute("tab", tab);
        // POLYMORPHISM: Warden.showDashboard() returns "warden-dashboard" -> warden-dashboard.jsp
        showView(warden.showDashboard(), req, resp);
    }
}
