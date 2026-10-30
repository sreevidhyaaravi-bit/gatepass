package com.gatepass.service;

/** The logged-in user's role is not allowed to do this action. */
public class AccessDeniedException extends BusinessRuleException {

    public AccessDeniedException() {
        super("You do not have permission to perform this action.");
    }
}
