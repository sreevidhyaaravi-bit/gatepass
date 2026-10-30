package com.gatepass.model;

/** A student's registered parent/guardian. Parents never log in; they only receive emails. */
public class Parent {

    private final int parentId;
    private String name;
    private String email;
    private String phone;

    public Parent(int parentId, String name, String email, String phone) {
        this.parentId = parentId;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public int getParentId() {
        return parentId;
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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
