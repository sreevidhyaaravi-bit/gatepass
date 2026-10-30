package com.gatepass.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gatepass.db.DBConnection;
import com.gatepass.db.DatabaseInitializer;
import com.gatepass.model.PassStatus;
import com.gatepass.model.Role;
import com.gatepass.model.User;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the "one shared gatepass.db" design with REAL separate programs (separate JVM processes),
 * like Tomcat and the guard app on the demo laptop. Uses the real clock: demo dates are relative
 * to today, so GP1002 is always "approved for exit today".
 */
class TwoProgramsShareOneDatabaseTest {

    @TempDir
    Path tempDir;

    Path db;

    @BeforeEach
    void freshDatabase() {
        db = tempDir.resolve("shared.db");
        DBConnection.setDatabasePath(db);
        DatabaseInitializer.resetEverything();
    }

    @Test
    void aChangeMadeByAnotherProgramIsSeenHere() throws Exception {
        assertEquals("OK", runSecondProgram("exit", "GP1002"));
        User guard = new AuthService().login("GUARD01", "Guard@123", Role.GUARD).orElseThrow();
        assertEquals(PassStatus.EXITED, new PassService().searchPass(guard, "GP1002").orElseThrow().status());
    }

    @Test
    void aChangeMadeHereIsSeenByAnotherProgram() throws Exception {
        User warden = new AuthService().login("WARDEN01", "Warden@123", Role.WARDEN).orElseThrow();
        new PassService().rejectPass(warden, "GP1001", "Not this week");
        assertEquals("REJECTED", runSecondProgram("status", "GP1001"));
    }

    @Test
    void threeProgramsPressMarkExitTogetherAndOnlyOneWins() throws Exception {
        List<CompletableFuture<String>> runs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            runs.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return runSecondProgram("exit", "GP1002");
                } catch (Exception e) {
                    return "ERROR " + e;
                }
            }));
        }
        long ok = 0;
        for (CompletableFuture<String> run : runs) {
            String out = run.get(60, TimeUnit.SECONDS);
            assertTrue(out.equals("OK") || out.startsWith("REFUSED"), out);
            if (out.equals("OK")) {
                ok++;
            }
        }
        assertEquals(1, ok, "exactly one exit is recorded");
    }

    /** Starts a second JVM on the same database file and returns the last line it printed. */
    private String runSecondProgram(String... args) throws IOException, InterruptedException {
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(java,
                "-Dgatepass.db.path=" + db.toAbsolutePath(),
                "-cp", System.getProperty("java.class.path"),
                SecondProgram.class.getName()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "second program finished");
        String[] lines = output.strip().split("\\R");
        return lines[lines.length - 1].strip();
    }
}
