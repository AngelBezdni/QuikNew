package org.example;

import javax.swing.SwingUtilities;

/**
 * По умолчанию запускает Swing UI.
 * Доступные режимы:
 * 1) {@code ping} — проверка соединения,
 * 2) {@code cli} — консольная синхронизация (bootstrap + OnTrade в H2).
 */
public class Main {

    public static void main(String[] args) {
        if (args != null && args.length > 0 && "ping".equalsIgnoreCase(args[0])) {
            String[] rest = new String[args.length - 1];
            System.arraycopy(args, 1, rest, 0, rest.length);
            try {
                new LuaConnectionTest().run(rest);
            } catch (Exception e) {
                System.err.println("Ошибка проверки Lua-соединения:");
                System.err.println(e.getMessage());
            }
            return;
        }

        if (args != null && args.length > 0 && "cli".equalsIgnoreCase(args[0])) {
            String[] rest = new String[args.length - 1];
            System.arraycopy(args, 1, rest, 0, rest.length);
            try {
                TradeSyncApp.run(rest);
            } catch (Exception e) {
                System.err.println("Ошибка синхронизации сделок:");
                System.err.println(e.getMessage());
            }
            return;
        }

        SwingUtilities.invokeLater(() -> QuikClientSwingApp.main(new String[0]));
    }
}
