# Stages 1-6 guide: shared core (`gatepass-core`)

## 1. Architecture

```
Browser --> Servlet --\                                      /--> users, students,
                       >--> PassService --> DAO --> SQLite -<      parents, gate_passes
Swing guard window ---/       (all rules)   (all SQL)        \--> ONE file: gatepass.db
```

`gatepass-core` is a plain jar used by the web module (stage 9) and the guard module (stage 11), so the
rules are written once.

```
gatepass/
  pom.xml                      parent: versions for all modules
  gatepass-core/
    src/main/java/com/gatepass/
      model/    Role PassStatus User Student Warden SecurityGuard Parent GatePass
      util/     AppConfig DateTimeUtil PasswordUtil
      db/       DBConnection DatabaseInitializer
      dao/      UserDAO GatePassDAO DataAccessException
      service/  PassService AuthService MovementNotifier EmailResult
                GuardPassView MovementResult OverdueEntry
                BusinessRuleException AccessDeniedException
    src/test/java/com/gatepass/service/   PassServiceDbTest TestClock RecordingNotifier
  gatepass-web/    (stage 9)    gatepass-guard/   (stage 11)
```

## 2. Decisions and assumptions (please read)

1. **Multi-module Maven** (core / web / guard) so both apps share code without copying.
2. **Passwords:** PBKDF2-HMAC-SHA256 with a random salt, from the JDK itself (no extra library to explain).
3. **`EmailService` arrives in stage 7.** `PassService` talks to an interface, `MovementNotifier`, so the
   rules can be tested without a mail server. Until stage 7 the app uses `MovementNotifier.NONE`,
   which honestly reports "not sent".
4. **Exit date rule:** "exit date cannot be in the past" is checked on the DATE. Asking for an exit
   at an earlier time today is allowed (the guard rule is also date-only).
5. **Late means strictly after** the expected time, compared at whole-minute precision (the stored
   format has no seconds). Exactly on time is not late.
6. **Guard privacy by construction:** the guard only ever receives `GuardPassView`, a class that has no
   parent, email, reason or password field. Hiding labels in Swing would not be enough.
7. **Pass numbers:** `GP1001`, `GP1002`, ... chosen as "highest + 1" inside one write transaction, so two
   programs cannot get the same number.
8. **Expiry** runs inside `PassService` whenever a pass is searched or a list is loaded. It only
   touches `APPROVED` passes, so a student who is still outside (`EXITED`) is never expired.

## 3. Classes

### model
| Class | Purpose | OOP idea |
|---|---|---|
| `User` (abstract) | userId, name, email; abstract `getRole()` and `showDashboard()` | abstraction |
| `Student`, `Warden`, `SecurityGuard` | the three roles | inheritance, `@Override`, `super(...)` |
| `Student` constructors | full (with parent) and short (for pass lists) | constructor overloading, `this(...)` |
| `Parent` | name, email, phone; never logs in | encapsulation |
| `GatePass` | one pass; **no setters** for status, movement times, late flag | encapsulation, two constructors |
| `PassStatus` | PENDING, APPROVED, REJECTED, EXITED, EXPIRED + `canMoveTo()` | enum with behaviour |
| `Role` | STUDENT, WARDEN, GUARD | enum |

`SecurityGuard.showDashboard()` returns text only: Tomcat has no screen, so the Swing window can only be opened by the guard program's own `main()`.

### util / db
* `AppConfig`: campus timezone (`Asia/Kolkata`) and the database file path.
* `DateTimeUtil`: the single format `yyyy-MM-dd HH:mm`; text to `LocalDateTime` before any comparison.
* `PasswordUtil`: `hash(password)` and `verify(password, stored)`.
* `DBConnection`: `getConnection()` opens the shared file with foreign keys on, 5 s busy timeout, WAL mode, immediate transactions.
* `DatabaseInitializer`: `initialize()` (safe on every start), `refreshDemoPasses()`, `resetEverything()`.

### dao (SQL only, no rules)
* `UserDAO`: `authenticate(id, password)` (the ONLY query that reads a hash), `findStudent(id)`, `findParentByCollegeId(id)`, plus insert helpers used for seeding.
* `GatePassDAO`: `create`, `findByPassNo`, `findByStudent`, `findByStatus`, `findMovements`, `findLateReturns`, and the conditional updates `approve`, `reject`, `recordExit`, `recordEntry`, `expireUnused`. Each returns `true` only if exactly one row changed.

### service (all rules)
`PassService` methods, grouped by who may call them (a wrong role throws `AccessDeniedException`):

* Student: `applyForPass`, `getMyProfile`, `getMyRecentPasses`
* Warden: `getPendingRequests`, `approvePass`, `rejectPass`, `getMovementUpdates`, `getLateReturns`, `getOverdueList`, `findStudentProfile`, `getStudentHistory`
* Guard: `searchPass`, `markExit`, `markEntry`, `getGateMovements`
* Anyone internal: `expireUnusedPasses`, `isLateReturn`

`AuthService.login(id, password, allowedRoles...)`: website passes `STUDENT, WARDEN`; guard app passes `GUARD`.

## 4. Key methods, step by step

**`PassService.markExit(guard, passNo)`**
1. `requireRole(guard, GUARD)`.
2. `loadPass` (throws "Pass not found"), then `expireIfNeeded` (an old unused APPROVED pass becomes EXPIRED here).
3. Status must be APPROVED; each other status gets its own message (pending, rejected, already exited, expired).
4. Pass exit date must equal today's date.
5. `gatePassDAO.recordExit(...)`: `UPDATE ... SET status='EXITED' ... WHERE status='APPROVED'`. If 0 rows changed, someone else got there first: refuse.
6. Reload the saved pass, then (only now) start the parent email; the result comes back as a separate `CompletableFuture<EmailResult>`.

**`PassService.markEntry`** is the mirror image: requires EXITED, computes `late = actualEntry.isAfter(expectedEntry)`, saves status EXPIRED + time + late flag in one conditional UPDATE.

**`PassService.getOverdueList`**: loads EXITED passes, keeps those whose expected entry is before now. A returned student has status EXPIRED, so cannot appear.

**`GatePassDAO.create`**: opens a transaction, picks the next `GPnnnn` number, inserts, reads the row back, commits (rolls back on any error).

## 5. Call sequences (implemented parts)

| Workflow | Chain |
|---|---|
| Login | (UI) -> `AuthService.login` -> `UserDAO.authenticate` -> `SELECT ... FROM users` -> `PasswordUtil.verify` |
| Apply | (servlet, stage 9) -> `PassService.applyForPass` -> validation -> `UserDAO.findStudent` -> `GatePassDAO.create` -> INSERT |
| Approve / reject | (servlet) -> `approvePass` / `rejectPass` -> `GatePassDAO.approve` / `reject` -> conditional UPDATE |
| Search pass | (Swing) -> `searchPass` -> `GatePassDAO.findByPassNo` -> `expireIfNeeded` -> `GatePassDAO.expireUnused` |
| Exit / entry | (Swing) -> `markExit` / `markEntry` -> `recordExit` / `recordEntry` -> `MovementNotifier` (stage 7: `EmailService`) |
| Expiry | any list/search -> `expireUnusedPasses` -> `findByStatus(APPROVED)` -> `expireUnused` |
| Overdue / history | (servlet) -> `getOverdueList` / `getStudentHistory` -> `findByStatus(EXITED)` / `findByStudent` |

## 6. Java features used so far
constructors and overloading, `this`/`super`, `abstract`, `@Override`, enums with a `switch` expression,
interfaces (`MovementNotifier`), records (`GuardPassView`, `MovementResult`, `OverdueEntry`, `EmailResult`: small
read-only data holders), custom exceptions, try-with-resources, `PreparedStatement`, `ResultSet`,
transactions with `commit()`/`rollback()`, `LocalDate`, `LocalDateTime`, `DateTimeFormatter`, `Clock`
(lets tests move time), `CompletableFuture` (email result later).

## 7. Tests

`PassServiceDbTest` (about 25 tests on a real temporary SQLite file) covers: login per role, hashed
passwords, seed data without duplicates, apply validation, approve/reject, exit and entry rules, early /
on-time / late return, overdue list, expiry, student isolation, guard view contents, email failure,
shared database, and 6 simultaneous exit attempts (exactly one wins). Not yet covered (later stages):
servlet access checks, Swing, real SMTP, two separate programs on one file.
