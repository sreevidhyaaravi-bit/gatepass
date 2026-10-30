package com.gatepass.web;

import com.gatepass.model.User;
import com.gatepass.util.AppConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;

/**
 * Student dashboard: own profile, parent details, the application form and the last 10 passes.
 * Everything is loaded for the LOGGED-IN student only (the id comes from the session, never from
 * the URL), so one student can never see another student's data.
 */
@WebServlet(name = "StudentDashboardServlet", urlPatterns = "/student/dashboard")
public class StudentDashboardServlet extends BaseServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User student = currentUser(req);
        req.setAttribute("profile", passService().getMyProfile(student));
        req.setAttribute("passes", passService().getMyRecentPasses(student)); // also runs automatic expiry
        // Earliest value the date pickers offer (PassService checks the real rule again).
        req.setAttribute("minExit", LocalDate.now(AppConfig.CAMPUS_ZONE) + "T00:00");
        // POLYMORPHISM: Student.showDashboard() returns "student-dashboard" -> student-dashboard.jsp
        showView(student.showDashboard(), req, resp);
    }
}
