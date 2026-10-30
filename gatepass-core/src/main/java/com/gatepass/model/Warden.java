package com.gatepass.model;

/** The hostel warden (INHERITANCE: Warden IS-A User). Uses the website. */
public class Warden extends User {

    public Warden(String userId, String name, String email) {
        super(userId, name, email);
    }

    @Override
    public Role getRole() {
        return Role.WARDEN;
    }

    @Override
    public String showDashboard() {
        return "warden-dashboard";
    }
}
