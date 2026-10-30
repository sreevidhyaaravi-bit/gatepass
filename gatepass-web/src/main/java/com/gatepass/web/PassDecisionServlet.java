package com.gatepass.web;

import com.gatepass.dao.DataAccessException;
import com.gatepass.model.GatePass;
import com.gatepass.model.User;
import com.gatepass.service.BusinessRuleException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Approve / Reject buttons of the warden's pending list. The rules (only PENDING passes, remark
 * required for rejection, only the warden role) are all checked inside PassService.
 */
@WebServlet(name = "PassDecisionServlet", urlPatterns = "/warden/decision")
public class PassDecisionServlet extends BaseServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User warden = currentUser(req);
        String action = req.getParameter("action");
        String passNo = req.getParameter("passNo");
        try {
            if ("approve".equals(action)) {
                GatePass pass = passService().approvePass(warden, passNo);
                flash(req, "success", "Pass " + pass.getPassNo() + " for " + pass.getStudent().getName() + " was approved.");
            } else if ("reject".equals(action)) {
                GatePass pass = passService().rejectPass(warden, passNo, req.getParameter("remark"));
                flash(req, "success", "Pass " + pass.getPassNo() + " for " + pass.getStudent().getName() + " was rejected.");
            } else {
                flash(req, "error", "Unknown action.");
            }
        } catch (BusinessRuleException | DataAccessException e) {
            flash(req, "error", (passNo == null ? "" : passNo.trim() + ": ") + e.getMessage());
        }
        redirect("/warden/dashboard?tab=pending", req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        redirect("/warden/dashboard", req, resp);
    }
}
