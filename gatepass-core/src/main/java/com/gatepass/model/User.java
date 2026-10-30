package com.gatepass.model;

/**
 * Abstract parent of every person who logs in (ABSTRACTION + INHERITANCE).
 *
 * <p>It holds what all users share. Each subclass says which role it is and where its
 * dashboard lives, so code can treat any user the same way and still get role-specific
 * behaviour (POLYMORPHISM).</p>
 *
 * <p>Note: there is deliberately NO password field here. Passwords are only handled inside
 * {@code UserDAO.authenticate()}, so no screen can ever display one.</p>
 */
public abstract class User {

    private String userId;
    private String name;
    private String email;

    protected User(String userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    /** Which role this object represents (each subclass answers differently). */
    public abstract Role getRole();

    /**
     * Where this user goes after login. Students and wardens return the NAME of a web view that
     * the servlet forwards to. The guard returns a description only, because the guard
     * dashboard is a Swing window started by its own main() and can never be opened from inside
     * Tomcat.
     */
    public abstract String showDashboard();
}
