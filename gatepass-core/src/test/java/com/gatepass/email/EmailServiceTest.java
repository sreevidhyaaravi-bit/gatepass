package com.gatepass.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gatepass.model.GatePass;
import com.gatepass.model.Parent;
import com.gatepass.model.PassStatus;
import com.gatepass.model.Student;
import com.gatepass.service.EmailResult;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sends REAL emails to GreenMail, a small SMTP server that runs inside the test (port 3025), and
 * checks what arrived. No internet or Gmail account is needed.
 */
class EmailServiceTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("gate@college.test", "secret"))
            .withPerMethodLifecycle(true);

    @TempDir
    Path tempDir;

    private static final Parent PARENT = new Parent(1, "Mr. Rajan Nair", "parent.cs24001@example.test", "9000000001");
    private static final Student STUDENT = new Student("CS24001", "Ananya Nair", "CSE", 3, "H24001");
    private static final LocalDateTime EXIT = LocalDateTime.of(2026, 10, 30, 16, 5);
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 10, 30, 21, 0);

    static MailSettings settings(String password) {
        Properties p = new Properties();
        p.setProperty("smtp.host", "127.0.0.1");
        p.setProperty("smtp.port", String.valueOf(ServerSetupTest.SMTP.getPort()));
        p.setProperty("smtp.security", "NONE");
        p.setProperty("smtp.username", "gate@college.test");
        p.setProperty("smtp.password", password);
        p.setProperty("mail.from", "gate@college.test");
        p.setProperty("smtp.timeout.ms", "5000");
        return MailSettings.fromProperties(p);
    }

    static GatePass exited() {
        return new GatePass("GP1002", STUDENT, "Dental appointment in town", EXIT.withHour(16).withMinute(0), DUE,
                EXIT, null, PassStatus.EXITED, false, null, EXIT.minusDays(1));
    }

    static GatePass returned(LocalDateTime actualEntry, boolean late) {
        return new GatePass("GP1002", STUDENT, "Dental appointment in town", EXIT.withMinute(0), DUE,
                EXIT, actualEntry, PassStatus.EXPIRED, late, null, EXIT.minusDays(1));
    }

    private static String body(MimeMessage message) throws Exception {
        return message.getContent().toString().replace("\r\n", "\n");
    }

    @Test
    void exitEmailArrivesWithEveryRequiredDetail() throws Exception {
        EmailService service = new EmailService(settings("secret"));
        EmailResult result = service.notifyExit(exited(), PARENT).get(10, TimeUnit.SECONDS);

        assertTrue(result.submitted(), result.message());
        assertTrue(greenMail.waitForIncomingEmail(5000, 1));
        MimeMessage message = greenMail.getReceivedMessages()[0];
        assertEquals("Campus Exit Notification - CS24001", message.getSubject());
        assertEquals("parent.cs24001@example.test", message.getAllRecipients()[0].toString());
        String body = body(message);
        assertTrue(body.contains("Dear Mr. Rajan Nair"));
        assertTrue(body.contains("Ananya Nair"));
        assertTrue(body.contains("CS24001"));
        assertTrue(body.contains("GP1002"));
        assertTrue(body.contains("30 Oct 2026, 04:05 PM"), "actual exit time");
        assertTrue(body.contains("30 Oct 2026, 09:00 PM"), "expected return time");
        assertTrue(body.contains("Dental appointment in town"), "reason");
        service.shutdown(5);
    }

    @Test
    void returnEmailSaysOnTimeOrLate() throws Exception {
        EmailService service = new EmailService(settings("secret"));
        assertTrue(service.notifyEntry(returned(DUE.minusMinutes(30), false), PARENT).get(10, TimeUnit.SECONDS).submitted());
        assertTrue(service.notifyEntry(returned(DUE.plusMinutes(150), true), PARENT).get(10, TimeUnit.SECONDS).submitted());

        assertTrue(greenMail.waitForIncomingEmail(5000, 2));
        MimeMessage onTime = greenMail.getReceivedMessages()[0];
        MimeMessage late = greenMail.getReceivedMessages()[1];
        assertEquals("Campus Return Notification - CS24001", onTime.getSubject());
        assertTrue(body(onTime).contains("Return status      : On time"));
        assertTrue(body(onTime).contains("30 Oct 2026, 08:30 PM"), "actual return time");
        assertTrue(body(late).contains("Return status      : LATE (by 2 h 30 min)"));
        assertTrue(body(late).contains("30 Oct 2026, 11:30 PM"));
        service.shutdown(5);
    }

    @Test
    void wrongSmtpPasswordIsReportedNotThrown() throws Exception {
        EmailService service = new EmailService(settings("not-the-password"));
        EmailResult result = service.notifyExit(exited(), PARENT).get(10, TimeUnit.SECONDS);
        assertFalse(result.submitted());
        assertTrue(result.message().contains("refused the login"), result.message());
        assertEquals(0, greenMail.getReceivedMessages().length);
        service.shutdown(5);
    }

    @Test
    void missingSettingsFileMeansNotSentAndNoCrash() throws Exception {
        MailSettings missing = MailSettings.load(tempDir.resolve("gatepass-mail.properties"));
        assertFalse(missing.isConfigured());
        EmailService service = new EmailService(missing);
        EmailResult result = service.notifyExit(exited(), PARENT).get(1, TimeUnit.SECONDS);
        assertFalse(result.submitted());
        assertTrue(result.message().startsWith("Parent email NOT sent: email is not set up"), result.message());
        assertEquals(0, greenMail.getReceivedMessages().length);
        service.shutdown(1);
    }

    @Test
    void unreachableMailServerIsReportedNotThrown() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort(); // free port; closed again when this block ends
        }
        Properties p = new Properties();
        p.setProperty("smtp.host", "127.0.0.1");
        p.setProperty("smtp.port", String.valueOf(closedPort));
        p.setProperty("smtp.security", "NONE");
        p.setProperty("mail.from", "gate@college.test");
        p.setProperty("smtp.timeout.ms", "3000");
        EmailService service = new EmailService(MailSettings.fromProperties(p));
        EmailResult result = service.notifyEntry(returned(DUE, false), PARENT).get(15, TimeUnit.SECONDS);
        assertFalse(result.submitted());
        assertTrue(result.message().contains("could not be reached"), result.message());
        service.shutdown(1);
    }

    @Test
    void parentWithoutEmailIsReported() throws Exception {
        EmailService service = new EmailService(settings("secret"));
        Parent noEmail = new Parent(9, "Guardian", " ", null);
        EmailResult result = service.notifyExit(exited(), noEmail).get(1, TimeUnit.SECONDS);
        assertFalse(result.submitted());
        assertTrue(result.message().contains("no parent email address"));
        service.shutdown(1);
    }

    @Test
    void resultMessagesNeverShowAnEmailAddress() throws Exception {
        // The guard sees these messages, and the guard must not see parent email addresses.
        EmailService ok = new EmailService(settings("secret"));
        EmailService bad = new EmailService(settings("wrong"));
        for (EmailResult r : new EmailResult[] {
                ok.notifyExit(exited(), PARENT).get(10, TimeUnit.SECONDS),
                bad.notifyExit(exited(), PARENT).get(10, TimeUnit.SECONDS)}) {
            assertFalse(r.message().contains("@"), r.message());
        }
        ok.shutdown(1);
        bad.shutdown(1);
    }

    @Test
    void settingsValidationAndNoPasswordInToString() {
        Properties p = new Properties();
        assertEquals("smtp.host is empty", MailSettings.fromProperties(p).getProblem());
        p.setProperty("smtp.host", "smtp.gmail.com");
        assertEquals("mail.from is empty", MailSettings.fromProperties(p).getProblem());
        p.setProperty("mail.from", "gate@college.test");
        p.setProperty("smtp.username", "gate@college.test");
        assertTrue(MailSettings.fromProperties(p).getProblem().contains("smtp.password is empty"));
        p.setProperty("smtp.password", "abcd efgh ijkl mnop");
        MailSettings ok = MailSettings.fromProperties(p);
        assertTrue(ok.isConfigured());
        assertEquals("Parent emails ON via smtp.gmail.com:587 (STARTTLS)", ok.toString());
        assertFalse(ok.toString().contains("abcd"));
        p.setProperty("smtp.security", "carrier-pigeon");
        assertFalse(MailSettings.fromProperties(p).isConfigured());
        p.setProperty("smtp.security", "SSL");
        assertEquals("Parent emails ON via smtp.gmail.com:465 (SSL)", MailSettings.fromProperties(p).toString());
        p.setProperty("mail.enabled", "false");
        assertFalse(MailSettings.fromProperties(p).isConfigured());
    }
}
