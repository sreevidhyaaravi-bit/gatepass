package com.gatepass.guard;

import com.gatepass.db.DBConnection;
import com.gatepass.db.DatabaseInitializer;
import com.gatepass.email.EmailService;
import com.gatepass.email.MailSettings;
import com.gatepass.model.Role;
import com.gatepass.model.User;
import com.gatepass.service.AuthService;
import com.gatepass.service.PassService;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Developer tool (not part of the app): draws the guard windows into PNG files WITHOUT showing
 * them, so the layout can be checked and used in the project report.
 *
 * <pre>java -cp ... com.gatepass.guard.GuardScreenshotTool &lt;database file&gt; &lt;output folder&gt;</pre>
 */
public final class GuardScreenshotTool {

    public static void main(String[] args) throws Exception {
        DBConnection.setDatabasePath(Paths.get(args[0]));
        Path out = Paths.get(args[1]);
        DatabaseInitializer.initialize();
        DatabaseInitializer.refreshDemoPasses();

        EmailService email = new EmailService(MailSettings.fromProperties(new Properties())); // emails OFF
        GuardApp app = new GuardApp(new PassService(email), new AuthService(), email);
        User guard = new AuthService().login("GUARD01", "Guard@123", Role.GUARD).orElseThrow();

        JFrame[] login = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> login[0] = new GuardLoginWindow(app));
        save(login[0], out.resolve("guard-login.png").toFile());

        GuardWindow[] window = new GuardWindow[1];
        SwingUtilities.invokeAndWait(() -> window[0] = new GuardWindow(app, guard));
        searchFor(window[0], "GP1002");
        save(window[0], out.resolve("guard-approved.png").toFile());

        app.passService().markExit(guard, "GP1002");
        app.passService().markEntry(guard, "GP1003"); // the overdue student comes back late
        searchFor(window[0], "GP1003");
        save(window[0], out.resolve("guard-late-return.png").toFile());
        System.exit(0);
    }

    private static void searchFor(GuardWindow window, String passNo) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                var field = GuardWindow.class.getDeclaredField("passNoField");
                field.setAccessible(true);
                ((JTextField) field.get(window)).setText(passNo);
                Method refresh = GuardWindow.class.getDeclaredMethod("refresh");
                refresh.setAccessible(true);
                var current = GuardWindow.class.getDeclaredField("current");
                current.setAccessible(true);
                current.set(window, null);
                Method search = GuardWindow.class.getDeclaredMethod("search");
                search.setAccessible(true);
                search.invoke(window);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        Thread.sleep(1500); // let the SwingWorkers finish
    }

    private static void save(JFrame frame, File file) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame.addNotify();
            frame.validate();
            BufferedImage image = new BufferedImage(frame.getRootPane().getWidth(), frame.getRootPane().getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            frame.getRootPane().printAll(g);
            g.dispose();
            try {
                ImageIO.write(image, "png", file);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
        System.out.println("saved " + file);
    }
}
