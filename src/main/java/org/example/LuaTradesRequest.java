package org.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Отправляет в Lua (QUIK#) команду get_trades и возвращает ответ JSON.
 * <p>
 * Важно: Lua-сервер QuikSharp ожидает два подключения:
 * 1) response порт (запрос/ответ),
 * 2) callback порт (push-события).
 * Поэтому здесь поднимаются оба сокета, даже если callback не читаем.
 */
public final class LuaTradesRequest {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_RESPONSE_PORT = 34130;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 8_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 15_000;

    /**
     * Получить все сделки из таблицы "Сделки" (команда Lua: get_trades, data = "").
     */
    public String requestAllTrades(String[] args) throws IOException {
        Settings s = Settings.fromArgs(args);
        System.out.println("Запрос get_trades к Lua: " + s.host + ":" + s.responsePort + " / " + s.callbackPort);

        try (Socket response = connect(s.host, s.responsePort, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
             Socket callback = connect(s.host, s.callbackPort, DEFAULT_CONNECT_TIMEOUT_MS, 0)) {

            String requestJson = "{\"cmd\":\"get_trades\",\"data\":\"\"}";
            String responseJson = rpc(response, requestJson);
            System.out.println("Lua ответ получен.");
            return responseJson;
        } catch (SocketTimeoutException e) {
            throw new IOException("Таймаут при get_trades: " + e.getMessage(), e);
        }
    }

    private static Socket connect(String host, int port, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        socket.setSoTimeout(readTimeoutMs);
        return socket;
    }

    private static String rpc(Socket responseSocket, String requestJson) throws IOException {
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(responseSocket.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader in = new BufferedReader(new InputStreamReader(responseSocket.getInputStream(), StandardCharsets.UTF_8));
        out.write(requestJson);
        out.write('\n');
        out.flush();

        String line = in.readLine();
        if (line == null) {
            throw new IOException("Lua закрыл response-соединение без ответа.");
        }
        return line;
    }

    public static void main(String[] args) {
        try {
            String response = new LuaTradesRequest().requestAllTrades(args);
            System.out.println(response);
        } catch (Exception e) {
            System.err.println("Ошибка запроса get_trades:");
            System.err.println(e.getMessage());
        }
    }

    private static final class Settings {
        private final String host;
        private final int responsePort;
        private final int callbackPort;

        private Settings(String host, int responsePort, int callbackPort) {
            this.host = host;
            this.responsePort = responsePort;
            this.callbackPort = callbackPort;
        }

        private static Settings fromArgs(String[] args) {
            if (args == null || args.length == 0) {
                return new Settings(DEFAULT_HOST, DEFAULT_RESPONSE_PORT, DEFAULT_RESPONSE_PORT + 1);
            }
            String host = args[0];
            int response = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_RESPONSE_PORT;
            int callback = args.length >= 3 ? Integer.parseInt(args[2]) : response + 1;
            return new Settings(host, response, callback);
        }
    }
}
