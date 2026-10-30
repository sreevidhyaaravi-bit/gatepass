package com.gatepass.email;

import com.gatepass.model.GatePass;
import com.gatepass.model.Parent;
import com.gatepass.service.EmailResult;
import com.gatepass.service.MovementNotifier;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends the parent emails over SMTP using Jakarta Mail (POLYMORPHISM: it IMPLEMENTS the
 * {@link MovementNotifier} interface, so PassService can use it without knowing about SMTP).
 *
 * <p>How it behaves:</p>
 * <ul>
 *   <li>PassService calls it only AFTER the exit/entry is saved in the database.</li>
 *   <li>Sending happens on one background thread. The method returns at once with a
 *       {@link CompletableFuture} that completes later, so the guard window never freezes.</li>
 *   <li>{@code submitted = true} only after the mail server ACCEPTED the message. That is all a
 *       program can know; it does not prove the parent has read it.</li>
 *   <li>Any failure becomes an {@link EmailResult} with {@code submitted = false}. It never throws
 *       and never undoes the saved movement.</li>
 *   <li>Logs name the pass number only. The SMTP password and the parent's address are never
 *       logged, and the result messages (shown to the guard) contain no email address.</li>
 * </ul>
 */
public class EmailService implements MovementNotifier {

    private static final Logger LOG = Logger.getLogger(EmailService.class.getName());

    private final MailSettings settings;
    private final ExecutorService sender;

    public EmailService(MailSettings settings) {
        this.settings = settings;
        // One "daemon" thread: emails go out one after another, and the thread never stops the
        // program from closing.
        this.sender = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "parent-email-sender");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** The usual setup: settings from gatepass-mail.properties. */
    public static EmailService fromSettingsFile() {
        return new EmailService(MailSettings.load());
    }

    public boolean isConfigured() {
        return settings.isConfigured();
    }

    /** e.g. "Parent emails ON via smtp.gmail.com:587 (STARTTLS)". No secrets. */
    public String describeStatus() {
        return settings.toString();
    }

    // ---------------------------------------------------------------- MovementNotifier

    @Override
    public CompletableFuture<EmailResult> notifyExit(GatePass pass, Parent parent) {
        return sendInBackground(pass, parent, EmailTemplates.exitSubject(pass), EmailTemplates.exitBody(pass, parent));
    }

    @Override
    public CompletableFuture<EmailResult> notifyEntry(GatePass pass, Parent parent) {
        return sendInBackground(pass, parent, EmailTemplates.returnSubject(pass), EmailTemplates.returnBody(pass, parent));
    }

    // ---------------------------------------------------------------- sending

    private CompletableFuture<EmailResult> sendInBackground(GatePass pass, Parent parent,
                                                            String subject, String body) {
        if (!settings.isConfigured()) {
            return CompletableFuture.completedFuture(
                    EmailResult.notSent("Parent email NOT sent: email is not set up (" + settings.getProblem() + ")."));
        }
        if (parent == null || parent.getEmail() == null || parent.getEmail().isBlank()) {
            return CompletableFuture.completedFuture(
                    EmailResult.notSent("Parent email NOT sent: no parent email address is registered."));
        }
        try {
            return CompletableFuture.supplyAsync(
                    () -> send(pass.getPassNo(), parent.getEmail(), subject, body), sender);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.completedFuture(
                    EmailResult.notSent("Parent email NOT sent: the email service has been stopped."));
        }
    }

    /** Runs on the background thread. Talks to the SMTP server and reports what happened. */
    private EmailResult send(String passNo, String to, String subject, String body) {
        try {
            Session session = Session.getInstance(settings.toJakartaMailProperties());
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(settings.getFromAddress(), settings.getFromName(), "UTF-8"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true));
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");
            message.setSentDate(new Date());

            if (settings.needsLogin()) {
                Transport.send(message, settings.getUsername(), settings.getPassword());
            } else {
                Transport.send(message);
            }
            LOG.info("Parent email for " + passNo + " was accepted by the mail server.");
            return EmailResult.submitted("Parent email accepted by the mail server.");
        } catch (AuthenticationFailedException e) {
            LOG.warning("Parent email for " + passNo + " failed: the mail server refused the login.");
            return EmailResult.notSent("Parent email NOT sent: the mail server refused the login "
                    + "(check smtp.username and smtp.password).");
        } catch (AddressException e) {
            LOG.warning("Parent email for " + passNo + " failed: the registered parent address is not valid.");
            return EmailResult.notSent("Parent email NOT sent: the registered parent email address is not valid.");
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            // Only the error type is logged: server replies can echo addresses, and we never log those.
            LOG.log(Level.WARNING, "Parent email for {0} failed: {1}",
                    new Object[] {passNo, e.getClass().getSimpleName()});
            return EmailResult.notSent("Parent email NOT sent: the mail server could not be reached "
                    + "or refused the message.");
        }
    }

    /**
     * Stops the background thread after the emails already started have finished (waits at most
     * {@code seconds}). Called when the website or the guard app shuts down.
     */
    public void shutdown(int seconds) {
        sender.shutdown();
        try {
            if (!sender.awaitTermination(seconds, TimeUnit.SECONDS)) {
                LOG.warning("Some parent emails were still being sent when the program closed.");
                sender.shutdownNow();
            }
        } catch (InterruptedException e) {
            sender.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
