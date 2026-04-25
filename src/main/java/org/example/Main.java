package org.example;

/**
 * По умолчанию: синхронизация сделок (bootstrap + OnTrade в H2).
 * Режим проверки соединения: первый аргумент {@code ping}.
 */
public class Main {

    public static void main(String[] args) {
        String[] rest = args;
        if (args != null && args.length > 0 && "ping".equalsIgnoreCase(args[0])) {
            rest = new String[args.length - 1];
            System.arraycopy(args, 1, rest, 0, rest.length);
            try {
                new LuaConnectionTest().run(rest);
            } catch (Exception e) {
                System.err.println("Ошибка проверки Lua-соединения:");
                System.err.println(e.getMessage());
            }
            return;
        }
        try {
            TradeSyncApp.run(args != null ? args : new String[0]);
        } catch (Exception e) {
            System.err.println("Ошибка синхронизации сделок:");
            System.err.println(e.getMessage());
        }
    }
}
