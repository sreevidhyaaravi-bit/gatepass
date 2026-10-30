# Viva guide: who presents what, and likely questions

Every member should be able to explain the whole flow in section 1. Then each member owns one
part (section 2) and should know the questions for that part (section 3) well.

## 1. The whole system in one minute (everyone)

"Students apply for a gate pass on the website. The warden approves or rejects it on the website.
At the gate, the security guard searches the pass in a separate Swing desktop program and presses
Mark Exit or Mark Entry. Both programs use the same shared Java classes and the same SQLite file,
`gatepass.db`. Every rule lives in one class, `PassService`; the servlets and the Swing buttons only
call it. When an exit or entry is saved, `EmailService` emails the parent in the background. The
warden sees movements, late returns, overdue students and each student's history."

Request flows:
* Website: Browser -> Servlet -> PassService -> DAO -> SQLite
* Guard app: Swing button -> PassService -> DAO -> SQLite (+ EmailService -> SMTP)

## 2. Suggested split for the presentation

| Member | Part | Files to know |
|---|---|---|
| Liya (3) | Problem, solution, architecture, model classes and OOP concepts | `model/*`, `STAGE-1-6-GUIDE.md` section 3 |
| Sayanora (54) | Database: 4 tables, `DBConnection`, DAOs, JDBC, transactions, shared file | `db/*`, `dao/*` |
| Sreevidhya A (65) | Business rules: status model, approval, exit/entry, expiry, late, overdue; tests | `PassService`, `PassStatus`, `PassServiceDbTest` |
| Thomas Jacob (70) | Website: servlets, filters, JSP, sessions, security | `gatepass-web/*` |
| Thwisha S (71) | Guard Swing app and parent emails, threads | `gatepass-guard/*`, `email/*` |

## 3. Likely questions with short answers

### Architecture and OOP (Liya)

1. **Why two applications?** The guard works at the gate on a desktop; students and the warden use a
   browser. Both reuse `gatepass-core`, so no rule is written twice.
2. **Where is abstraction?** `User` is abstract with abstract `getRole()` and `showDashboard()`.
   `BaseServlet` is abstract. `MovementNotifier` is an interface.
3. **Inheritance?** `Student`, `Warden`, `SecurityGuard` extend `User`; every servlet extends
   `BaseServlet`; `MovementTableModel` extends `AbstractTableModel`; windows extend `JFrame`.
4. **Polymorphism?** `user.showDashboard()` returns a different page per role and the servlet shows
   it; `PassService` calls `notifier.notifyExit()` without knowing whether it is the real
   `EmailService` or the test fake.
5. **Encapsulation?** Private fields everywhere. `GatePass` has NO setters for status, movement times
   or late flag, so only `PassService` can change a pass (through the database).
6. **Constructor overloading?** `Student` has a full constructor and a short one that calls `this(...)`.
7. **Why is `Parent` not a `User`?** Parents never log in; they only receive email.
8. **What is a record?** A short read-only data class (`GuardPassView`, `EmailResult`, `MovementResult`,
   `OverdueEntry`). Java writes the constructor, getters, `equals` and `toString`.

### Database (Sayanora)

9. **Tables?** `users` (login + PBKDF2 hash + role), `students` (college ID, class, semester, hostel roll,
   parent), `parents` (name, email, phone), `gate_passes` (all pass data, status, late flag, remark).
10. **How do both programs use the same file?** `AppConfig.databasePath()` resolves one absolute path
    (`-Dgatepass.db.path`, else `GATEPASS_DB_PATH`, else `<home>/gatepass-data/gatepass.db`).
11. **What does `DBConnection` configure?** Foreign keys ON, 5 s busy timeout, WAL journal (readers and
    a writer together), IMMEDIATE transactions.
12. **What if the guard and the warden write at the same moment?** SQLite lets one writer at a time; the
    other waits up to 5 s (busy timeout). If still busy, the user sees "The database is busy right now".
13. **How do you stop two guards recording the same exit?** Conditional update:
    `UPDATE ... SET status='EXITED' WHERE pass_no=? AND status='APPROVED'`. Only one succeeds (1 row);
    the other gets 0 rows and is refused. Tested with 6 threads and with 3 separate programs.
14. **Why `PreparedStatement`?** Values are sent separately from the SQL, so typed text can never change
    the query (prevents SQL injection).
15. **How are pass numbers unique?** `GPnnnn` = highest number + 1, chosen inside the same write
    transaction as the insert; `pass_no` is also the primary key.
16. **Why are resources closed?** try-with-resources closes `Connection`, `PreparedStatement` and
    `ResultSet` automatically, even when an exception happens.

### Business rules (Sreevidhya A)

17. **Status model?** PENDING -> APPROVED / REJECTED; APPROVED -> EXITED (or EXPIRED if never used);
    EXITED -> EXPIRED on return. REJECTED and EXPIRED are final (`PassStatus.canMoveTo`).
18. **Exit rule?** Pass must be APPROVED and today must be the exit DATE (time of day does not matter).
19. **Entry rule?** Pass must be EXITED. Status becomes EXPIRED; late flag = actual entry is after expected.
20. **Automatic expiry without a background job?** `expireUnusedPasses()` runs whenever a pass is
    searched or a list is loaded (website and guard app). It changes only APPROVED passes whose exit date
    is before today and that never exited. An EXITED student is never expired.
21. **Late vs overdue?** Late = already returned, after the expected time (late flag). Overdue = still
    outside (EXITED) and the expected time has passed. Returning removes a student from overdue.
22. **Why does `PassService` receive the logged-in user?** Every action checks the role
    (`requireRole`), so even a missed URL check cannot let a student approve a pass.
23. **How are rules tested without waiting for real time?** A `Clock` is passed into `PassService`; tests
    use `TestClock` to set "now" (for example 21:01 to test a late return).

### Website (Thomas Jacob)

24. **Servlet life cycle?** Tomcat creates one servlet object, calls `init()` once, then `service()` for
    each request (which calls `doGet`/`doPost`), and `destroy()` at shutdown.
25. **Why `javax` vs `jakarta`?** Tomcat 10+ uses `jakarta.servlet`; Tomcat 9 used `javax.servlet`. We use
    Tomcat 10.1, so all imports are `jakarta.*`.
26. **How does login work?** `LoginServlet` -> `AuthService.login(id, pw, STUDENT, WARDEN)` ->
    `UserDAO.authenticate` -> `PasswordUtil.verify`. On success: new session, user + CSRF token stored.
27. **How are pages protected?** `RoleFilter` for `/student/*` and `/warden/*`, plus the role check inside
    `PassService`.
28. **What is CSRF and how is it stopped?** Another website tricking your browser into submitting our
    form. Each form carries a secret token from the session; `SecurityFilter` refuses POSTs without it.
29. **What is XSS and how is it stopped?** Script typed into a field and shown to others. `<c:out>` escapes
    it, and the Content-Security-Policy header blocks inline scripts.
30. **What is Post/Redirect/Get?** After a form POST we redirect to a GET page, so refreshing never
    submits twice. Messages travel in a one-time "flash" value in the session.
31. **Why are JSPs inside WEB-INF?** The browser cannot open them directly; only servlets can forward to them.

### Guard app and email (Thwisha S)

32. **Why `SwingWorker`?** Database work must not run on the event-dispatch thread, or the window
    freezes. `doInBackground()` runs elsewhere; `done()` updates the screen on the EDT.
33. **How is the email sent without freezing the window?** `EmailService` returns a `CompletableFuture`
    at once and sends on its own background thread; the result is shown later with `invokeLater`.
34. **What if the mail server is down?** The exit/entry is already saved; the guard sees "Parent email NOT
    sent: ..." and the movement stays recorded. Tested with the SMTP server switched off.
35. **Where are SMTP settings and why not in the code?** `gatepass-mail.properties`, next to the database,
    excluded from Git, because it contains a password.
36. **Can the guard see parent emails or the reason?** No. The guard only receives `GuardPassView`, which
    has no such fields; email result messages contain no address.
37. **Why can Mark Exit still be refused when the button is enabled?** The button is only a hint;
    `PassService` re-checks (for example a pass approved for tomorrow).
38. **What does "accepted by the mail server" mean?** The SMTP server took the message for delivery. We do
    not claim the parent read it.

## 4. Honest limitations (say them before you are asked)

* Dummy login instead of the college's real authentication system (as the brief asks).
* SQLite suits one laptop; a real campus deployment would use a database server.
* Email proves submission to the mail server, not that the parent read it.
* No password reset or account management screens (accounts are created by the setup code).
