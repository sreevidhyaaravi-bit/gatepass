package com.gatepass.web;

import com.gatepass.dao.DataAccessException;
import com.gatepass.model.GatePass;
import com.gatepass.service.BusinessRuleException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Receives the gate pass application form.
 *
 * <p>Only three values are read from the form: reason, exit date-time and return date-time.
 * The student's name, college ID, class, semester and hostel roll come from the database inside
 * PassService, so adding a "collegeId" field to the form (tampering) has no effect.</p>
 */
@WebServlet(name = "ApplyPassServlet", urlPatterns = "/student/apply")
public class ApplyPassServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String reason = req.getParameter("reason");
        String exit = req.getParameter("exitDateTime");
        String expectedReturn = req.getParameter("returnDateTime");
        try {
            GatePass pass = passService().applyForPass(currentUser(req), reason, exit, expectedReturn);
            flash(req, "success", "Gate pass " + pass.getPassNo()
                    + " has been submitted. It is now waiting for the warden's approval.");
        } catch (BusinessRuleException | DataAccessException e) {
            // Show the clear validation message and keep what the student typed.
            flash(req, "error", e.getMessage());
            flash(req, "formReason", reason);
            flash(req, "formExit", exit);
            flash(req, "formReturn", expectedReturn);
        }
        redirect("/student/dashboard", req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        redirect("/student/dashboard", req, resp);
    }
}
