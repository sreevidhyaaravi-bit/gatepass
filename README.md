# Digital Gate Pass & Campus Exit Management System
Java OOP project, Group 13 (B.Tech CSE)

| Member | Roll No. |
|---|---|
| Liya | 3 |
| Sayanora | 54 |
| Sreevidhya A | 65 |
| Thomas Jacob | 70 |
| Thwisha S | 71 |

Students and the warden use a website (Servlets + JSP on Apache Tomcat 10.1). The security guard uses a
separate Swing desktop program. Both read and write ONE SQLite file, `gatepass.db`. Parents get emails
when their child leaves and returns.

```
Browser ------> Servlet --\                                 /--> users, students,
                           >--> PassService --> DAO --> SQLite      parents, gate_passes
Swing window -------------/     (all rules)    (all SQL)   ONE file: gatepass.db
                                     \--> EmailService --> SMTP --> parent's inbox
```

## Build status (honest)

| Stage | What | State |
|---|---|---|
| 1-2 | Architecture, versions, Maven setup | done |
| 3 | Schema + demo data (`DatabaseInitializer`) | done |
| 4 | Models | done |
| 5 | `DBConnection`, `UserDAO`, `GatePassDAO` | done |
| 6 | `PassService`, `AuthService` | done |
| 7 | `EmailService` + one SMTP settings file | done, tested with a real (test) SMTP server |
| 8 | Login, role filters, CSRF protection | done |
| 9-10 | Student + warden website | done, tested in a browser and by HTTP tests |
| 11 | Swing guard app | done, screens checked; see "Not verified yet" |
| 12 | Integration tests | done (44 automated tests, all passing) |
| 13 | One-folder demo package with start scripts | done |
| 14 | Docs and viva guide | done (`docs/`) |

**Verified on a Mac (JDK 21, Maven 3.9):** `mvn clean package` builds everything and runs 44 tests:
rules on a real SQLite file, real SMTP sending (GreenMail test server), the real website on Tomcat over
HTTP (logins, roles, CSRF, validation, escaping, approve/reject, history), and three separate Java
programs pressing Mark Exit on one database file at the same time (exactly one wins).

**Not verified yet (please check on your Windows laptop):** the `.bat` start files (written for
Windows, but built and tried only on a Mac with the matching `.sh` files), clicking through the guard
app on Windows (its screens were drawn and checked on macOS), and sending to a real Gmail inbox
(needs your own App Password; see "Parent emails").

## Run it (no Tomcat install needed)

After `mvn clean package`, the folder `gatepass-dist/target/GatePass-Demo/` (also zipped as
`GatePass-Demo.zip`) contains everything. On Windows, double-click:

1. `1-START-WEBSITE.bat` and open http://localhost:8080 (keep the black window open)
2. `2-START-GUARD-APP.bat`
3. `3-REFRESH-DEMO-DATA.bat` on demo day, so the demo passes have today's dates
4. `4-SET-PARENT-EMAIL.bat` (optional) to send one demo parent's emails to an inbox you can open

Only Java 17 or newer is needed (your Java 25 is fine). `1-START-WEBSITE.bat` starts Apache Tomcat
10.1 itself (embedded, same server code as the Tomcat download).

**Using your own Tomcat 10.1 instead:** copy `gatepass-web/target/ROOT.war` into Tomcat's `webapps`
folder (delete the existing `webapps/ROOT` folder first) and run `bin/startup.bat`. Start Tomcat as
yourself, not as a Windows service, so it uses the same database file as the guard app.

## Build and test

```
mvn clean package
```
This compiles all modules, runs all tests and creates the demo folder. Running the website from
IntelliJ / Eclipse: run `com.gatepass.launcher.WebLauncher` with program argument
`gatepass-web/target/ROOT`, and `com.gatepass.guard.GuardApp` for the guard app.

## Versions (one set for the whole project)

| Part | Version | Package names used in the code |
|---|---|---|
| Java | compiled for 17 (runs on 17, 21, 25) | `java.sql`, `java.time`, `javax.swing`, `javax.crypto` |
| Build | Maven 3.9.x (4.x also works) | |
| Web server | Apache Tomcat 10.1.61 | `org.apache.catalina` (launcher only) |
| Servlet API | 6.0.0 | `jakarta.servlet.*` (Tomcat 10.1 uses `jakarta`, NOT `javax`) |
| JSP | 3.1.1 | `jakarta.servlet.jsp` |
| JSTL | API 3.0.0 + Glassfish 3.0.1 | taglibs `jakarta.tags.core`, `jakarta.tags.functions` |
| SQLite JDBC | `org.xerial:sqlite-jdbc` 3.50.3.0 | `org.sqlite.JDBC`, `org.sqlite.SQLiteConfig` |
| Mail | Jakarta Mail API 2.1.3 + Eclipse Angus Mail 2.0.3 | `jakarta.mail.*`, `jakarta.mail.internet.*` |
| Tests | JUnit 5.11.4, GreenMail 2.1.8 (test SMTP server) | `org.junit.jupiter.api` |

## Project layout

```
gatepass/
  pom.xml                 parent: versions for all modules
  gatepass-core/          SHARED code (used by website AND guard app)
    model/    Role PassStatus User Student Warden SecurityGuard Parent GatePass
    util/     AppConfig DateTimeUtil PasswordUtil
    db/       DBConnection DatabaseInitializer
    dao/      UserDAO GatePassDAO DataAccessException
    service/  PassService AuthService MovementNotifier EmailResult GuardPassView
              MovementResult OverdueEntry BusinessRuleException AccessDeniedException
    email/    EmailService MailSettings EmailTemplates
  gatepass-web/           website: servlets, filters, JSP pages, CSS, JS  -> ROOT.war
  gatepass-guard/         Swing guard app: GuardApp GuardLoginWindow GuardWindow
                          MovementTableModel MovementCellRenderer GuardStyle
  gatepass-launcher/      WebLauncher: starts Tomcat 10.1 for the website (+ website tests)
  gatepass-dist/          builds the GatePass-Demo folder and zip (start scripts)
  config/                 gatepass-mail.properties.example
  docs/                   class guides, viva guide, demo script
```

## Where the database lives

Both programs open the same absolute path, chosen in `AppConfig.databasePath()`:
1. `-Dgatepass.db.path=...`, else 2. environment variable `GATEPASS_DB_PATH`, else
3. `<home folder>/gatepass-data/gatepass.db` (Windows: `C:\Users\<you>\gatepass-data\gatepass.db`).

The guard app's login window shows the path it uses, so you can check both programs agree.

## Demo logins (created automatically; fictional data)

| Role | Login ID | Password | Where |
|---|---|---|---|
| Student | CS24001, CS24002, CS24003 | Student@123 | website |
| Warden | WARDEN01 | Warden@123 | website |
| Guard | GUARD01 | Guard@123 | guard app only |

Passwords are stored only as salted PBKDF2 hashes; nobody can read them back from the database.

Demo passes GP1001-GP1007 are created relative to the day the data is created or refreshed:
GP1001 pending, GP1002 approved for exit today, GP1003 outside and overdue, GP1004 late return,
GP1005 unused approved pass from 3 days ago (expires when looked up), GP1006 rejected with remark,
GP1007 on-time return.

## Parent emails

All SMTP settings live in ONE file, `gatepass-mail.properties`, next to `gatepass.db`
(or wherever `-Dgatepass.mail.config` / `GATEPASS_MAIL_CONFIG` points). It is never committed
(`.gitignore`). Start from `config/gatepass-mail.properties.example`, which explains two options:

* **Gmail:** turn on 2-Step Verification, create an App Password, use `smtp.gmail.com`, port 587, STARTTLS.
* **Local test inbox, no internet:** run [Mailpit](https://mailpit.axllent.org/), use `localhost`, port 1025,
  security `NONE`, and see every email at http://localhost:8025.

The demo parents have fictional `@example.test` addresses that cannot receive mail. To show a real
email arriving, point one parent at an inbox you can open:
`4-SET-PARENT-EMAIL.bat` (or `DatabaseInitializer parent-email CS24002 you@example.com`).

Without the settings file everything still works; the guard app reports "Parent email NOT sent".
The guard app shows the movement result and the email result separately, and "accepted by the mail
server" is the strongest claim it makes (a program cannot know that the parent read it).

## Documents

* `docs/STAGE-1-6-GUIDE.md`: shared core (models, DAOs, PassService), call sequences, OOP map.
* `docs/STAGE-7-14-GUIDE.md`: email, website, guard app, tests, packaging, security measures.
* `docs/VIVA-GUIDE.md`: who presents what, and likely viva questions with answers.
* `docs/DEMO-SCRIPT.md`: the live demo, click by click.
