package com.gatepass.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.gatepass.dao.GatePassDAO;
import com.gatepass.db.DBConnection;
import com.gatepass.model.GatePass;
import com.gatepass.model.PassStatus;
import java.net.CookieManager;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the REAL website: starts Apache Tomcat with the built ROOT folder (gatepass-web/target/ROOT)
 * on a free port and talks to it over HTTP like a browser (cookies, forms, redirects).
 * Runs during "mvn package" (the website must be built first; with plain "mvn test" it is skipped).
 */
class WebsiteTest {

    @TempDir
    static Path tempDir;

    static Tomcat tomcat;
    static String base;

    @BeforeAll
    static void startWebsite() throws Exception {
        Path webapp = Paths.get("..", "gatepass-web", "target", "ROOT").toAbsolutePath().normalize();
        assumeTrue(Files.isRegularFile(webapp.resolve("WEB-INF/web.xml")), "website not built yet");
        Path db = tempDir.resolve("web-test.db");
        System.setProperty("gatepass.db.path", db.toString()); // read by the website's DBConnection
        DBConnection.setDatabasePath(db);                       // and by this test's own DAO checks
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        tomcat = WebLauncher.start(webapp, port);
        base = "http://localhost:" + port;
    }

    @AfterAll
    static void stopWebsite() throws Exception {
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    // ------------------------------------------------------------ a tiny browser

    static final class Browser {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER).build();

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        HttpResponse<String> post(String path, Map<String, String> form) throws Exception {
            String body = form.entrySet().stream()
                    .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                    .collect(Collectors.joining("&"));
            return client.send(HttpRequest.newBuilder(URI.create(base + path))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        /** Follows one redirect (Post/Redirect/Get) and returns the page. */
        HttpResponse<String> follow(HttpResponse<String> redirect) throws Exception {
            String location = redirect.headers().firstValue("Location").orElseThrow();
            return get(URI.create(location).getRawPath() + (URI.create(location).getRawQuery() == null
                    ? "" : "?" + URI.create(location).getRawQuery()));
        }

        String login(String id, String password) throws Exception {
            HttpResponse<String> r = post("/login", form("loginId", id, "password", password));
            return r.statusCode() == 302 ? r.headers().firstValue("Location").orElse("") : "refused";
        }

        String csrf(String page) {
            Matcher m = Pattern.compile("name=\"csrfToken\" value=\"([^\"]+)\"").matcher(page);
            assertTrue(m.find(), "page has a CSRF token");
            return m.group(1);
        }

        private static String enc(String s) {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        }
    }

    static Map<String, String> form(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    // ------------------------------------------------------------ tests

    @Test
    void loginPageAndWrongRoles() throws Exception {
        Browser b = new Browser();
        HttpResponse<String> home = b.get("/");
        assertEquals(200, home.statusCode());
        assertTrue(home.body().contains("Digital Gate Pass"));
        assertEquals("refused", b.login("CS24001", "wrong-password"));
        assertEquals("refused", b.login("GUARD01", "Guard@123"), "guards must use the desktop app");
        assertTrue(b.post("/login", form("loginId", "GUARD01", "password", "Guard@123")).body()
                .contains("Invalid login ID or password."));
    }

    @Test
    void pagesNeedTheRightLogin() throws Exception {
        Browser anonymous = new Browser();
        HttpResponse<String> r = anonymous.get("/student/dashboard");
        assertEquals(302, r.statusCode());
        assertTrue(r.headers().firstValue("Location").orElseThrow().endsWith("/login?msg=login"));
        assertEquals(404, anonymous.get("/WEB-INF/jsp/student-dashboard.jsp").statusCode(), "JSPs are hidden");

        Browser student = new Browser();
        assertTrue(student.login("CS24001", "Student@123").endsWith("/student/dashboard"));
        assertEquals(403, student.get("/warden/dashboard").statusCode());
        assertEquals(403, student.post("/warden/decision", form("action", "approve", "passNo", "GP1001")).statusCode());

        Browser warden = new Browser();
        assertTrue(warden.login("WARDEN01", "Warden@123").endsWith("/warden/dashboard"));
        assertEquals(403, warden.get("/student/dashboard").statusCode());
    }

    @Test
    void studentSeesOnlyOwnDataAndCanApply() throws Exception {
        Browser b = new Browser();
        b.login("CS24002", "Student@123");
        HttpResponse<String> page = b.get("/student/dashboard");
        assertEquals(200, page.statusCode());
        assertTrue(page.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        String html = page.body();
        assertTrue(html.contains("Rahul Menon") && html.contains("H24002") && html.contains("parent.cs24002@example.test"));
        assertTrue(html.contains("GP1002"));
        assertFalse(html.contains("GP1003") || html.contains("Meera Joseph"), "no other student's passes");
        assertFalse(html.contains("pbkdf2"), "no password hash anywhere");

        // no CSRF token -> refused
        String tomorrow = LocalDate.now().plusDays(1).toString();
        assertEquals(403, b.post("/student/apply", form("reason", "x",
                "exitDateTime", tomorrow + "T09:00", "returnDateTime", tomorrow + "T18:00")).statusCode());

        // server-side validation (the browser checks are bypassed here on purpose)
        String token = b.csrf(html);
        HttpResponse<String> bad = b.follow(b.post("/student/apply", form("csrfToken", token, "reason", "x",
                "exitDateTime", tomorrow + "T18:00", "returnDateTime", tomorrow + "T09:00")));
        assertTrue(bad.body().contains("The expected return must be later than the exit time."));

        // a tampered form (another student's ID, HTML in the reason) still creates a pass for ME, shown as text
        HttpResponse<String> ok = b.follow(b.post("/student/apply", form("csrfToken", token,
                "reason", "<script>alert(1)</script>", "collegeId", "CS24001", "status", "APPROVED",
                "exitDateTime", tomorrow + "T09:00", "returnDateTime", tomorrow + "T18:00")));
        Matcher m = Pattern.compile("Gate pass (GP\\d+) has been submitted").matcher(ok.body());
        assertTrue(m.find(), ok.body());
        GatePass created = new GatePassDAO().findByPassNo(m.group(1)).orElseThrow();
        assertEquals("CS24002", created.getCollegeId());
        assertEquals(PassStatus.PENDING, created.getStatus());
        assertFalse(ok.body().contains("<script>alert(1)</script>"));
        assertTrue(ok.body().contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
    }

    @Test
    void wardenDecidesAndSearches() throws Exception {
        Browser b = new Browser();
        b.login("WARDEN01", "Warden@123");
        String html = b.get("/warden/dashboard").body();
        String token = b.csrf(html);
        assertTrue(html.contains("GP1001"), "pending request listed");
        assertFalse(html.contains("pbkdf2"));

        String noRemark = b.follow(b.post("/warden/decision", form("csrfToken", token,
                "action", "reject", "passNo", "GP1001", "remark", "  "))).body();
        assertTrue(noRemark.contains("A rejection remark is required."));

        String approved = b.follow(b.post("/warden/decision", form("csrfToken", token,
                "action", "approve", "passNo", "GP1001"))).body();
        assertTrue(approved.contains("was approved"));
        assertEquals(PassStatus.APPROVED, new GatePassDAO().findByPassNo("GP1001").orElseThrow().getStatus());

        String again = b.follow(b.post("/warden/decision", form("csrfToken", token,
                "action", "reject", "passNo", "GP1001", "remark", "late"))).body();
        assertTrue(again.contains("Only pending passes can be approved or rejected"));

        assertTrue(b.get("/warden/dashboard?tab=overdue").body().contains("GP1003"));
        assertTrue(b.get("/warden/dashboard?tab=late").body().contains("GP1004"));
        String history = b.get("/warden/dashboard?collegeId=cs24001").body();
        assertTrue(history.contains("Ananya Nair") && history.contains("GP1007"));
        assertTrue(b.get("/warden/dashboard?collegeId=CS99999").body().contains("Student not found"));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        Browser b = new Browser();
        b.login("CS24001", "Student@123");
        String token = b.csrf(b.get("/student/dashboard").body());
        HttpResponse<String> out = b.post("/logout", form("csrfToken", token));
        assertTrue(out.headers().firstValue("Location").orElseThrow().endsWith("/login?msg=loggedout"));
        assertEquals(302, b.get("/student/dashboard").statusCode());
    }
}
