package com.gatepass.service;

/**
 * Outcome of a parent notification, kept SEPARATE from the movement result.
 *
 * <p>{@code submitted = true} only means the mail server accepted the message. It does not prove
 * the parent read it or that it reached the inbox.</p>
 */
public record EmailResult(boolean submitted, String message) {

    public static EmailResult submitted(String message) {
        return new EmailResult(true, message);
    }

    public static EmailResult notSent(String message) {
        return new EmailResult(false, message);
    }
}
