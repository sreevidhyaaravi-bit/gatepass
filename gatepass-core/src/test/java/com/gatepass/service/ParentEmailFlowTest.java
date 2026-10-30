package com.gatepass.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gatepass.dao.GatePassDAO;
import com.gatepass.dao.UserDAO;
import com.gatepass.db.DBConnection;
import com.gatepass.db.DatabaseInitializer;
import com.gatepass.email.EmailService;
import com.gatepass.email.MailSettings;
import com.gatepass.model.PassStatus;
import com.gatepass.model.Role;
import com.gatepass.model.User;
import com.gatepass.util.AppConfig;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/**
 * The whole chain with a REAL database and a REAL (test) SMTP server:
 * guard -> PassService -> GatePassDAO -> SQLite, then EmailService -> SMTP -> parent's inbox.
 */
class ParentEmailFlowTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 30);

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP).withPerMethodLifecycle(true);

    @TempDir
    static Path tempDir;

    static TestClock clock;
    static EmailService emailService;
    static PassService service;
    static User guard;

    @BeforeAll
    static void setUp() {
        DBConnection.setDatabasePath(tempDir.resolve("email-flow.db"));
        clock = new TestClock(AppConfig.CAMPUS_ZONE, TODAY.atTime(10, 0));
        Properties p = new Properties();
        p.setProperty("smtp.host", "127.0.0.1");
        p.setProperty("smtp.port", String.valueOf(ServerSetupTest.SMTP.getPort()));
        p.setProperty("smtp.security", "NONE");
        p.setProperty("mail.from", "gate@college.test");
        emailService = new EmailService(MailSettings.fromProperties(p));
        service = new PassService(new UserDAO(), new GatePassDAO(), emailService, clock);
        DatabaseInitializer.resetEverything(clock);
        guard = new AuthService().login("GUARD01", "Guard@123", Role.GUARD).orElseThrow();
    }

    @AfterAll
    static void tearDown() {
        emailService.shutdown(5);
    }

    @BeforeEach
    void freshPasses() {
        clock.set(TODAY.atTime(10, 0));
        DatabaseInitializer.refreshDemoPasses(clock);
    }

    @Test
    void exitThenLateReturnSendsExactlyTwoEmailsToTheRegisteredParent() throws Exception {
        MovementResult exit = service.markExit(guard, "GP1002"); // CS24002, due back 21:00
        assertEquals(PassStatus.EXITED, exit.pass().status(), "saved before any email");
        assertTrue(exit.email().get(10, TimeUnit.SECONDS).submitted());

        clock.set(TODAY.atTime(22, 15));
        MovementResult entry = service.markEntry(guard, "GP1002");
        assertTrue(entry.pass().lateFlag());
        assertTrue(entry.email().get(10, TimeUnit.SECONDS).submitted());

        assertTrue(greenMail.waitForIncomingEmail(5000, 2));
        MimeMessage[] inbox = greenMail.getReceivedMessages();
        assertEquals(2, inbox.length);
        assertEquals("Campus Exit Notification - CS24002", inbox[0].getSubject());
        assertEquals("Campus Return Notification - CS24002", inbox[1].getSubject());
        assertEquals("parent.cs24002@example.test", inbox[0].getAllRecipients()[0].toString());
        assertTrue(inbox[1].getContent().toString().contains("LATE (by 1 h 15 min)"));
    }

    @Test
    void refusedOperationsSendNoEmail() throws Exception {
        assertThrows(BusinessRuleException.class, () -> service.markExit(guard, "GP1001"));  // pending
        assertThrows(BusinessRuleException.class, () -> service.markEntry(guard, "GP1002")); // not exited
        service.markExit(guard, "GP1002").email().get(10, TimeUnit.SECONDS);
        assertThrows(BusinessRuleException.class, () -> service.markExit(guard, "GP1002"));  // repeated
        Thread.sleep(500);
        assertEquals(1, greenMail.getReceivedMessages().length, "only the one valid exit was emailed");
    }

    @Test
    void mailServerDownStillRecordsTheMovement() throws Exception {
        greenMail.stop();
        MovementResult exit = service.markExit(guard, "GP1002");
        assertFalse(exit.email().get(30, TimeUnit.SECONDS).submitted());
        assertEquals(PassStatus.EXITED, new GatePassDAO().findByPassNo("GP1002").orElseThrow().getStatus());
    }
}
