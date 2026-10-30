DIGITAL GATE PASS & CAMPUS EXIT MANAGEMENT SYSTEM  (Group 13)
Ready-to-run demo folder
=============================================================

You need: Java 17 or newer (Java 25 is fine). Nothing else to install:
Apache Tomcat 10.1 is already inside this folder.

ON WINDOWS (double-click):
  1-START-WEBSITE.bat      Starts the website. Then open http://localhost:8080
                           Keep that black window open while you use the website.
  2-START-GUARD-APP.bat    Starts the security guard desktop app.
  3-REFRESH-DEMO-DATA.bat  On demo day: puts the 7 demo passes back with TODAY's dates.
  4-SET-PARENT-EMAIL.bat   Optional: send a demo parent's emails to an inbox you can open.

ON macOS: ./start-website.sh, ./start-guard-app.sh, ./refresh-demo-data.sh,
          ./set-parent-email.sh CS24002 someone@example.com

DEMO LOGINS (fictional demo data)
  Students : CS24001, CS24002, CS24003   password Student@123   (website)
  Warden   : WARDEN01                    password Warden@123    (website)
  Guard    : GUARD01                     password Guard@123     (guard app only)

DEMO PASSES (dates are relative to the day the data was created or refreshed)
  GP1001 pending           -> warden approves or rejects it
  GP1002 approved, today   -> guard: Mark Exit, later Mark Entry
  GP1003 outside, overdue  -> warden: Overdue tab; guard: Mark Entry = late return
  GP1004 returned late     -> warden: Late returns tab
  GP1005 approved, unused, exit date passed -> becomes EXPIRED when it is looked up
  GP1006 rejected with a remark
  GP1007 returned on time

WHERE THE DATA LIVES
  Both programs use ONE file:  C:\Users\<you>\gatepass-data\gatepass.db
  (macOS: /Users/<you>/gatepass-data/gatepass.db). It is created on the first start.

PARENT EMAILS (optional)
  Copy gatepass-mail.properties.example to C:\Users\<you>\gatepass-data\gatepass-mail.properties,
  fill in the SMTP settings (instructions inside), and restart the guard app.
  Without that file everything still works and the guard app says "Parent email NOT sent".

ALREADY HAVE TOMCAT 10.1 INSTALLED?
  Instead of 1-START-WEBSITE.bat you can copy ROOT.war into Tomcat's "webapps" folder
  (remove the existing webapps\ROOT folder first) and start Tomcat with bin\startup.bat.
  Run Tomcat as yourself (not as a Windows service) so it uses the same database file.
