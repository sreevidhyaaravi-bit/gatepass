# Live demo script (about 10 minutes)

## Before the demo (same day)

1. Run `3-REFRESH-DEMO-DATA.bat` so GP1002 is "approved for exit TODAY" and GP1003 is overdue.
2. Optional, to show a real email: create `gatepass-mail.properties` (see README) and run
   `4-SET-PARENT-EMAIL.bat` for CS24002 with an inbox you can open on the projector.
3. Start `1-START-WEBSITE.bat` (wait for "website is running") and `2-START-GUARD-APP.bat`.
4. Open http://localhost:8080 in the browser. Keep the inbox tab ready if you set up email.

## 1. Student applies (website)

* Log in as `CS24001` / `Student@123`.
* Point at: profile, hostel roll, parent details, last 10 passes with status colours (GP1004 shows LATE,
  GP1007 on time).
* Try a wrong form: expected return BEFORE exit. The browser stops it. Say: "the server checks this too."
* Submit a correct request for tomorrow. Show "Gate pass GP1008 has been submitted" and PENDING.
* Log out.

## 2. Warden decides (website)

* Log in as `WARDEN01` / `Warden@123`. Show the four counters and the tabs.
* Pending: try Reject without a remark (refused), then reject GP1008 with a remark. Approve GP1001.
* Overdue tab: GP1003 Meera Joseph, still outside, "overdue by" time.
* Late returns tab: GP1004. Student history: search `CS24001`, then `CS99999` ("Student not found").
* Mention: the warden never sees passwords.

## 3. Guard at the gate (Swing app)

* Log in as `GUARD01` / `Guard@123`. Say: "student and warden accounts cannot log in here."
* Search `GP1005` (approved for 3 days ago, never used): it shows EXPIRED and both buttons are
  disabled. Say: "no background job; automatic expiry runs whenever a pass is searched or a list loads"
  (the warden's page load in step 2 already expired it).
* Search `GP1001` (approved for tomorrow): press Mark Exit. Refused: "valid for exit only on ...".
* Search `GP1002`: APPROVED, Mark Exit enabled. Press it. Show "Exit recorded" and, separately,
  "Parent email accepted by the mail server" (or "NOT sent: email is not set up").
* Press Mark Exit again on GP1002: refused, and no second email.
* If email is set up: show the "Campus Exit Notification - CS24002" email in the inbox.
* Search `GP1003` (overdue): press Mark Entry. "LATE return". Show the return email if set up.

## 4. Both programs share one database

* Back on the warden website, press Refresh: GP1002 now EXITED in Movement updates, GP1003 has left the
  Overdue list and appears in Late returns.
* Say: "the website and the guard app are two separate programs; they meet in one SQLite file."

## 5. Close

* Show the 44 passing tests (`mvn clean package`) or the test list in `STAGE-7-14-GUIDE.md`.
* One-line summary: rules in one place (PassService), two interfaces, one database, parent emails
  that never block or undo the gate.

## If something goes wrong

| Problem | Fix |
|---|---|
| Browser cannot open localhost:8080 | the website window must stay open; or port 8080 is used by another Tomcat: close it |
| GP1002 cannot exit ("valid for exit only on") | run `3-REFRESH-DEMO-DATA.bat` (the data is from another day) |
| "Parent email NOT sent: email is not set up" | expected without `gatepass-mail.properties`; the movement is still saved |
| "The mail server refused the login" | use a Gmail App Password, not the normal password |
| Guard app and website show different data | both must use the same `gatepass.db`; the guard login window shows its path |
