package com.gatepass.model;

/**
 * The gate security guard (INHERITANCE: SecurityGuard IS-A User). Uses the Swing desktop app.
 */
public class SecurityGuard extends User {

    public SecurityGuard(String userId, String name, String email) {
        super(userId, name, email);
    }

    @Override
    public Role getRole() {
        return Role.GUARD;
    }

    /**
     * Returns only a description. The guard's dashboard is the Swing GuardWindow, opened by the
     * separate guard program's own main(). Tomcat (a server with no screen) must never try to
     * open a window, so this method does not try to either.
     */
    @Override
    public String showDashboard() {
        return "GuardWindow (Swing desktop app, started by the guard program's main())";
    }
}
