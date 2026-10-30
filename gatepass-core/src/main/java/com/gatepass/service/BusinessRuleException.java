package com.gatepass.service;

/**
 * A rule of the gate pass system was broken (e.g. "Pass not found", "Exit already recorded").
 * The message is safe and clear enough to show directly to the user.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
