package com.gatepass.service;

import com.gatepass.model.GatePass;
import com.gatepass.model.Parent;
import java.util.concurrent.CompletableFuture;

/**
 * "Something that can tell a parent about a movement" (an INTERFACE).
 *
 * <p>PassService only knows this interface. In stage 7 {@code EmailService} implements it and
 * sends real emails; in tests a fake implementation just records the calls. This is called
 * loose coupling, and it is why the business rules can be tested without a mail server.</p>
 *
 * <p>Implementations must return quickly: the actual sending happens in the background, and the
 * returned future completes later with the outcome.</p>
 */
public interface MovementNotifier {

    CompletableFuture<EmailResult> notifyExit(GatePass pass, Parent parent);

    CompletableFuture<EmailResult> notifyEntry(GatePass pass, Parent parent);

    /** Used when no email service is configured: reports honestly that nothing was sent. */
    MovementNotifier NONE = new MovementNotifier() {
        @Override
        public CompletableFuture<EmailResult> notifyExit(GatePass pass, Parent parent) {
            return CompletableFuture.completedFuture(EmailResult.notSent("Email notifications are not configured."));
        }

        @Override
        public CompletableFuture<EmailResult> notifyEntry(GatePass pass, Parent parent) {
            return CompletableFuture.completedFuture(EmailResult.notSent("Email notifications are not configured."));
        }
    };
}
