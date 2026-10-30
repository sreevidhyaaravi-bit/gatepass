package com.gatepass.launcher;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;

/**
 * Starts Apache Tomcat 10.1 inside this program and serves the website at http://localhost:8080/.
 *
 * <pre>
 *   java -jar gatepass-launcher.jar [website folder]      (default folder: "website")
 * </pre>
 *
 * The website folder is the unpacked ROOT.war (WEB-INF/web.xml, JSPs, CSS ...). This class
 * contains no gate pass logic at all: it is only a convenient way to run Tomcat for the demo.
 * Options: {@code -Dgatepass.port=8081} for another port, {@code -Dgatepass.openBrowser=false}.
 */
public final class WebLauncher {

    private WebLauncher() {
    }

    public static void main(String[] args) throws Exception {
        Path webapp = Paths.get(args.length > 0 ? args[0] : "website").toAbsolutePath().normalize();
        int port = Integer.getInteger("gatepass.port", 8080);

        if (!Files.isRegularFile(webapp.resolve("WEB-INF").resolve("web.xml"))) {
            System.err.println("Website folder not found: " + webapp);
            System.err.println("Build the project first (mvn package) or pass the folder as an argument.");
            System.exit(1);
        }

        Tomcat tomcat = start(webapp, port);
        String url = "http://localhost:" + port + "/";
        System.out.println();
        System.out.println("=====================================================");
        System.out.println(" Digital Gate Pass website is running:  " + url);
        System.out.println(" Keep this window open. Press Ctrl+C to stop.");
        System.out.println("=====================================================");

        if (Boolean.parseBoolean(System.getProperty("gatepass.openBrowser", "true"))) {
            openBrowser(url);
        }
        tomcat.getServer().await();
    }

    /** Creates and starts Tomcat. Also used by the website tests. */
    public static Tomcat start(Path webapp, int port) throws Exception {
        Path workDir = Files.createTempDirectory("gatepass-tomcat");
        Tomcat tomcat = new Tomcat();
        tomcat.setBaseDir(workDir.toString());   // Tomcat's own scratch folder (compiled JSPs)
        tomcat.getHost().setAppBase(Files.createDirectories(workDir.resolve("webapps")).toString());
        tomcat.setPort(port);
        Connector connector = tomcat.getConnector(); // the HTTP connector on the chosen port
        tomcat.addWebapp("", webapp.toString());  // "" = site root, like ROOT.war

        try {
            tomcat.start();
        } catch (LifecycleException e) {
            throw new IllegalStateException("Tomcat could not start: " + e.getMessage(), e);
        }
        if (connector.getState() != LifecycleState.STARTED) {
            tomcat.stop();
            tomcat.destroy();
            throw new IllegalStateException("Port " + port + " is already in use. Close the other program "
                    + "(maybe another Tomcat) or start with -Dgatepass.port=8081");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (LifecycleException ignored) {
                // the program is ending anyway
            }
            deleteQuietly(workDir.toFile());
        }));
        return tomcat;
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception | Error e) {
            // No browser (or no screen): the URL printed above is enough.
        }
    }

    private static void deleteQuietly(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteQuietly(child);
            }
        }
        file.delete();
    }
}
