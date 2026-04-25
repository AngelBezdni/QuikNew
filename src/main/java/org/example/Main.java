package org.example;

public class Main {

    public static void main(String[] args) {
        try {
            new LuaConnectionTest().run(args);
        } catch (Exception e) {
            System.err.println("Ошибка проверки Lua-соединения:");
            System.err.println(e.getMessage());
        }
    }
}
