# Digital Gate Pass & Campus Exit Management System
Java OOP project, Group 13 (B.Tech CSE)

Students and the warden use a website (Servlets + JSP on Tomcat). The security guard uses a separate
Swing desktop program. Both read and write ONE SQLite file, `gatepass.db`. Parents get emails.

## Build status (honest)

| Stage | What | State |
|---|---|---|
| 1-2 | Architecture, versions, Maven setup | done |
| 3 | Schema + demo data (`DatabaseInitializer`) | written |
| 4 | Models | written |
| 5 | `DBConnection`, `UserDAO`, `GatePassDAO` | written |
| 6 | `PassService`, `AuthService` | written, rules checked |
| 7 | `EmailService` + SMTP config | next |
| 8 | Servlet login/role filters | next |
| 9-10 | Student + warden website | next |
| 11 | Swing guard app | next |
| 12-14 | Integration test, docs, viva guide | later |

What was verified:

* On your laptop (Java 25, Maven 3.10.0) the code compiled and `mvn -pl gatepass-core test` ran
  24 tests against a real SQLite file. 23 passed on the first run; the 24th found a real bug (all three
  demo students shared one password hash and salt). That is fixed; re-run to confirm all 24 pass.
* Not covered yet: web pages, Swing, real SMTP, two separate programs sharing one file.

## Versions (one set for the whole project)

| Part | Version | Why |
|---|---|---|
| Java | 25 (`maven.compiler.release=25`) | your laptop's JDK. Code uses nothing newer than Java 21 features |
| Build | Maven (3.9.x or 4.x all work) | see note on "Maven 10.0" below |
| Servlet API | `jakarta.servlet` 6.0.0 | Tomcat 10.1 uses the `jakarta.*` namespace (not `javax.*`) |
| JSP | `jakarta.servlet.jsp` 3.1.1 | matches Tomcat 10.1 |
| Tomcat | 10.1.x | use this one, not 9 (which needs `javax.*`) |
| SQLite JDBC | `org.xerial:sqlite-jdbc` 3.50.3.0 | |
| Mail | `jakarta.mail` 2.1.3 + `angus-mail` 2.0.3 | stage 7 |
| Tests | JUnit 5.11.4 | |

**Your setup:** Java 25 (Temurin 25.0.4) and Maven 3.10.0 on Windows 11. The `pom.xml` files are
standard and also work on Maven 3.9.x and 4.x.

## Run the tests (on your laptop)

```
mvn -pl gatepass-core test
```
If your JDK is older than 25 (for example 21): `mvn -Dmaven.compiler.release=21 -pl gatepass-core test`

## Where the database lives

Both programs open the same absolute path, chosen in `AppConfig.databasePath()`:
1. `-Dgatepass.db.path=...`, else 2. environment variable `GATEPASS_DB_PATH`, else
3. `<home folder>/gatepass-data/gatepass.db`.

## Demo logins (created automatically; fictional data)

| Role | Login ID | Password |
|---|---|---|
| Student | CS24001, CS24002, CS24003 | Student@123 |
| Warden | WARDEN01 | Warden@123 |
| Guard | GUARD01 | Guard@123 |

Demo passes GP1001-GP1007 are created relative to today's date:
GP1001 pending, GP1002 approved for exit today, GP1003 overdue, GP1004 late return,
GP1005 unused approved (expires when looked up), GP1006 rejected with remark, GP1007 on-time return.

Refresh them before a demo: `DatabaseInitializer.refreshDemoPasses()` (a command-line launcher comes with stage 11).

## More

* `docs/STAGE-1-6-GUIDE.md`: class-by-class explanation, call sequences, OOP map for what exists so far.
