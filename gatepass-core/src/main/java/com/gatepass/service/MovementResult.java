package com.gatepass.service;

import java.util.concurrent.CompletableFuture;

/**
 * Result of Mark Exit / Mark Entry.
 *
 * <ul>
 *   <li>{@code pass} and {@code message}: the DATABASE result. Already saved when you get this.</li>
 *   <li>{@code email}: the EMAIL result, finished later. A screen should show the movement
 *       message at once and add the email outcome when the future completes.</li>
 * </ul>
 */
public record MovementResult(GuardPassView pass, String message, CompletableFuture<EmailResult> email) {
}
