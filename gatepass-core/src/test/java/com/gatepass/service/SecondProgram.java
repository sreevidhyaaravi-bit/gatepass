package com.gatepass.service;

import com.gatepass.model.Role;
import com.gatepass.model.User;
import java.util.Optional;

/**
 * Started as a SEPARATE Java process by {@link TwoProgramsShareOneDatabaseTest}, exactly like the
 * guard app runs next to Tomcat. It opens the database chosen with -Dgatepass.db.path.
 *
 * <pre>SecondProgram exit GP1002   -> prints OK or REFUSED: message
 * SecondProgram status GP1002 -> prints the status</pre>
 */
public final class SecondProgram {

    public static void main(String[] args) {
        PassService service = new PassService();
        User guard = new AuthService().login("GUARD01", "Guard@123", Role.GUARD).orElseThrow();
        try {
            switch (args[0]) {
                case "exit" -> {
                    service.markExit(guard, args[1]);
                    System.out.println("OK");
                }
                case "status" -> {
                    Optional<GuardPassView> pass = service.searchPass(guard, args[1]);
                    System.out.println(pass.map(p -> p.status().name()).orElse("NOT_FOUND"));
                }
                default -> System.out.println("UNKNOWN");
            }
        } catch (BusinessRuleException e) {
            System.out.println("REFUSED: " + e.getMessage());
        }
    }
}
