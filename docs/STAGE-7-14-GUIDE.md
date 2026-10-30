# Stages 7-14 guide: email, website, guard app, tests, packaging

Read `STAGE-1-6-GUIDE.md` first: everything here sits on top of the shared core (`PassService`,
the DAOs and the models). Nothing in this guide adds a business rule; the new code only shows
screens, sends emails, and calls `PassService`.

## 1. What was added

| Module | New classes / files | Purpose |
|---|---|---|
| `gatepass-core` | `email/EmailService`, `email/MailSettings`, `email/EmailTemplates` | parent emails over SMTP |
| `gatepass-core` | `AppConfig.mailSettingsPath()`, `DateTimeUtil.display()`, `UserDAO.updateParentEmail()` | one settings file, friendly dates, demo helper |
| `gatepass-web` | `AppStartupListener`, `BaseServlet`, 6 servlets, `SecurityFilter`, `RoleFilter`, `ViewFunctions` | the website |
| `gatepass-web` | `web.xml`, `gatepass.tld`, 4 JSP pages + 4 fragments, `style.css`, `app.js` | pages and settings |
| `gatepass-guard` | `GuardApp`, `GuardLoginWindow`, `GuardWindow`, `MovementTableModel`, `MovementCellRenderer`, `GuardStyle` | Swing guard app |
| `gatepass-launcher` | `WebLauncher` | starts Tomcat 10.1 with the website |
| `gatepass-dist` | start scripts, `README.txt` | ready-to-run demo folder + zip |

## 2. Stage 7: parent emails

```
Guard clicks Mark Exit
  -> PassService.markExit()         rules checked, conditional UPDATE, COMMITTED
  -> PassService.notifySafely()     loads the parent, never throws
  -> EmailService.notifyExit()      returns a CompletableFuture at once
       (background thread "parent-email-sender")
       -> EmailTemplates.exitSubject/exitBody
       -> Transport.send()          SMTP server accepts or refuses
  <- EmailResult(submitted, message) arrives later; GuardWindow shows it in "Last operation"
```

| Class | What it does | OOP idea |
|---|---|---|
| `MailSettings` | reads `gatepass-mail.properties`; `isConfigured()` + `getProblem()`; `toString()` never shows the password | encapsulation, nested enum `Security` |
| `EmailTemplates` | subject and body text of both emails | utility class (private constructor) |
| `EmailService` | `implements MovementNotifier`; sends on one daemon thread; turns every failure into an `EmailResult` | interface implementation, polymorphism |

How each email requirement is met:

| Requirement | Where |
|---|---|
| Save movement first, then email | `PassService.markExit/markEntry` call the notifier only after `recordExit/recordEntry` returned true |
| Asynchronous | `CompletableFuture.supplyAsync(..., sender)` in `EmailService.sendInBackground` |
| No email when validation fails | rules throw before the notifier is reached (tested in `ParentEmailFlowTest.refusedOperationsSendNoEmail`) |
| No duplicates from repeated clicks | the conditional UPDATE succeeds once; later clicks are refused before any email |
| Email failure never undoes the movement | `notifySafely` catches everything; tested with the SMTP server switched off |
| Recorded movement vs submitted email | `MovementResult.message()` vs `MovementResult.email()`; two separate labels in the guard window |
| No false "delivered" claim | the strongest message is "accepted by the mail server" |
| No secrets in logs | logs name the pass number and the error TYPE only; no password, no parent address |
| Missing settings | `MailSettings` not configured: the result says "email is not set up (reason)" and the gate keeps working |

Settings keys: `smtp.host`, `smtp.port`, `smtp.security` (STARTTLS / SSL / NONE), `smtp.username`,
`smtp.password`, `mail.from`, `mail.from.name`, `smtp.timeout.ms`, `mail.enabled`.

## 3. Stages 8-10: the website

### Request flow and the filter chain

```
Browser -> SecurityFilter -> RoleFilter (only /student/*, /warden/*) -> Servlet -> PassService -> DAO -> SQLite
                                                                          |
                                                          forward to /WEB-INF/jsp/*.jsp (HTML)
```

Filters are listed in `web.xml` because their ORDER matters. Servlets use `@WebServlet` annotations.

| URL | Class | Who | What |
|---|---|---|---|
| `/` and `/login` | `LoginServlet` | anyone | GET shows the form; POST logs in (STUDENT or WARDEN only) |
| `/logout` | `LogoutServlet` | logged in | POST ends the session |
| `/student/dashboard` | `StudentDashboardServlet` | STUDENT | profile, parent, form, last 10 passes |
| `/student/apply` | `ApplyPassServlet` | STUDENT | POST: `applyForPass`, then redirect |
| `/warden/dashboard` | `WardenDashboardServlet` | WARDEN | 5 tabs: pending, movements, late, overdue, history |
| `/warden/decision` | `PassDecisionServlet` | WARDEN | POST: approve / reject (+ remark) |

| Class | Purpose | OOP idea |
|---|---|---|
| `AppStartupListener` | on start: `DatabaseInitializer.initialize()`, creates ONE `PassService` and `AuthService` for all servlets | implements `ServletContextListener` |
| `BaseServlet` (abstract) | helpers: services, current user, `showView`, `redirect`, flash messages, friendly error page | inheritance: every servlet extends it |
| `SecurityFilter` | UTF-8, safety headers, CSRF check on every POST except login | implements `Filter` |
| `RoleFilter` | one class used twice in `web.xml` (role STUDENT / WARDEN); no-cache headers | reuse through configuration |
| `ViewFunctions` + `gatepass.tld` | `${gp:dt(...)}`, `${gp:duration(...)}`, `${gp:lateBy(...)}` in JSPs | static methods as EL functions |

**Polymorphism in use:** the dashboard servlets show `user.showDashboard() + ".jsp"`; a `Student`
answers `student-dashboard`, a `Warden` answers `warden-dashboard`.

### Security measures (good viva material)

| Threat | Protection |
|---|---|
| Student opens warden pages | `RoleFilter` (403) AND `PassService.requireRole` (second check) |
| Student creates a pass for someone else | servlet reads only reason + 2 dates; `PassService` loads the student from the SESSION user |
| Student edits status / times / late flag | there is no form field or method for it; `GatePass` has no setters for them |
| Another website submits our forms (CSRF) | random token in the session, hidden field in every form, checked by `SecurityFilter` |
| Script typed into "reason" (XSS) | every value printed with `<c:out>` or `fn:escapeXml` (escaped); CSP header allows scripts only from our site |
| Stolen session id | new session at every login, cookie `HttpOnly` + `SameSite=Lax`, never in the URL, 30 min timeout |
| Back button after logout | `Cache-Control: no-store` on all dashboard pages |
| Password exposure | no password field in any model; the hash is read only inside `UserDAO.authenticate` |
| Error details leaking | friendly `error.jsp`; DAO messages contain no SQL or paths |
| JSP opened directly | JSPs live under `WEB-INF` (browser gets 404) |
| Double-submit / F5 resubmits | Post/Redirect/Get + button disabled after the first click |

Browser checks in `app.js` (return after exit, confirm before reject) are only for convenience;
the server checks everything again.

## 4. Stage 11: the guard app

```
GuardApp.main()  -> DatabaseInitializer.initialize(), EmailService.fromSettingsFile(), new PassService(email)
  GuardLoginWindow -> AuthService.login(id, pw, Role.GUARD)    (SwingWorker)
  GuardWindow
     Search     -> PassService.searchPass()   (runs automatic expiry first)  -> GuardPassView or "Pass not found"
     Mark Exit  -> PassService.markExit()     -> MovementResult (saved) + email future
     Mark Entry -> PassService.markEntry()    -> MovementResult (saved, late flag) + email future
     table      -> PassService.getGateMovements()
```

| Class | Purpose | OOP idea |
|---|---|---|
| `GuardApp` | its own `main()`; builds the shared objects; opens windows; closes cleanly (waits for running emails) | composition |
| `GuardLoginWindow` | login form; guard role only | extends `JFrame` |
| `GuardWindow` | search, details, Mark Exit / Mark Entry, last operation, movement table, refresh, logout | extends `JFrame`; generic method `runInBackground<T>` |
| `MovementTableModel` | table data from `GuardPassView` rows | extends `AbstractTableModel` |
| `MovementCellRenderer` | colours status and LATE | overrides `getTableCellRendererComponent` |
| `GuardStyle` | colours, header bar, error dialogs | utility class |

**Threads (important viva question):** Swing has one event-dispatch thread (EDT) that paints the
window. Database calls run in `SwingWorker.doInBackground()` (another thread); results are shown in
`done()`, which Swing runs on the EDT. The email result arrives later through
`CompletableFuture.thenAccept(...)` and is shown with `SwingUtilities.invokeLater(...)`, again on the EDT.
So the window never freezes, even when the mail server is slow.

**Buttons vs rules:** Mark Exit is enabled only for APPROVED, Mark Entry only for EXITED
(`GuardPassView.canMarkExit/canMarkEntry`). That is a convenience: `PassService` re-checks every rule
when the button is pressed, including "exit date must be today".

**Privacy:** the guard receives only `GuardPassView` (no parent, email, reason or password fields).
Email results shown to the guard never contain an address.

## 5. Stage 12: tests (44, all passing with `mvn clean package`)

| Test class | Count | Proves |
|---|---|---|
| `PassServiceDbTest` | 24 | every business rule on a real SQLite file (her stages 1-6) |
| `EmailServiceTest` | 8 | real SMTP sending to GreenMail; content of both emails; wrong password, server down, missing settings, no address in messages |
| `ParentEmailFlowTest` | 3 | exit + late return = exactly 2 emails to the right parent; refused actions send none; mail server down still records the exit |
| `TwoProgramsShareOneDatabaseTest` | 3 | separate Java PROCESSES see each other's changes; 3 programs press Mark Exit together, exactly 1 wins |
| `MovementTableModelTest` | 1 | guard table columns and values |
| `WebsiteTest` | 5 | the real website on Tomcat over HTTP: logins, wrong roles, 403s, CSRF, validation, tampered form, XSS escaping, approve/reject, overdue/late/history, logout |

## 6. Stage 13: running and packaging

* `WebLauncher` starts Tomcat 10.1 (embedded) on port 8080 with the unpacked `ROOT.war`. It contains no
  gate pass logic. Options: `-Dgatepass.port=8081`, `-Dgatepass.openBrowser=false`.
* `gatepass-dist` copies the website, the launcher, the guard app and the scripts into
  `GatePass-Demo/` and zips it. `.bat` files get Windows line endings automatically.
* `DatabaseInitializer` command line: `init`, `refresh` (demo passes for today), `reset` (everything),
  `parent-email <collegeId> <email>`.

## 7. Decisions and assumptions added in stages 7-14

1. **Tomcat 10.1 + `jakarta.*`** everywhere (Servlet 6.0, JSP 3.1, JSTL 3.0, Jakarta Mail 2.1).
2. **Compiled for Java 17**, so the same files run on Java 17, 21 and 25.
3. **The website never sends emails**: only the guard records movements, so only the guard app
   creates an `EmailService`. The website's `PassService` uses `MovementNotifier.NONE`.
4. **One settings file** for SMTP, next to `gatepass.db`, outside the project folder and in `.gitignore`.
5. **Login CSRF is not checked** (the login form has no session yet); every other POST is checked.
6. **Confirm dialogs** before Mark Exit / Mark Entry / Reject, to avoid accidental clicks.
7. **Seed parents use `@example.test`** (cannot receive mail by design); `parent-email` points one
   at a real test inbox for the demo.
8. **Overdue is shown only to the warden** (PassService.getOverdueList); the guard sees the late flag.

## 8. Java features added (for the "OOP concepts used" slide)

Inheritance (`BaseServlet`, `AbstractTableModel`, `DefaultTableCellRenderer`, `JFrame`), interfaces
(`MovementNotifier`, `Filter`, `ServletContextListener`), polymorphism (`showDashboard()`, overridden
renderer, `EmailService` used as a `MovementNotifier`), abstraction (`BaseServlet` is abstract),
encapsulation (`MailSettings` hides the password), generics (`runInBackground<T>`,
`SwingWorker<T, Void>`), lambdas and functional interfaces (`Supplier`, `Consumer`), records and
pattern matching (`value instanceof PassStatus status`), annotations (`@WebServlet`, `@WebListener`,
`@Override`), enums in `switch` expressions, custom exception hierarchy, try-with-resources,
threads (`ExecutorService`, daemon thread, `CompletableFuture`, `SwingWorker`).
