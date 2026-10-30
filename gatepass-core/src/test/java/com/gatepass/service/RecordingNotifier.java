package com.gatepass.service;

import com.gatepass.model.GatePass;
import com.gatepass.model.Parent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Fake email service for tests: counts calls and can pretend the mail server is down. */
class RecordingNotifier implements MovementNotifier {

    final AtomicInteger exitCalls = new AtomicInteger();
    final AtomicInteger entryCalls = new AtomicInteger();
    volatile boolean failing;

    void reset() {
        exitCalls.set(0);
        entryCalls.set(0);
        failing = false;
    }

    @Override
    public CompletableFuture<EmailResult> notifyExit(GatePass pass, Parent parent) {
        exitCalls.incrementAndGet();
        return result();
    }

    @Override
    public CompletableFuture<EmailResult> notifyEntry(GatePass pass, Parent parent) {
        entryCalls.incrementAndGet();
        return result();
    }

    private CompletableFuture<EmailResult> result() {
        if (failing) {
            return CompletableFuture.completedFuture(EmailResult.notSent("SMTP server unreachable"));
        }
        return CompletableFuture.completedFuture(EmailResult.submitted("accepted by test mail server"));
    }
}
