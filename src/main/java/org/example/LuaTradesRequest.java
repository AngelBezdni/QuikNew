package org.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.ConnectException;
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
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 60_000;
    private static final int DEFAULT_ATTEMPTS = 2;
    private static final int MAX_RESPONSE_CHARS = 20_000_000;

    /**
     * Получить все сделки из таблицы "Сделки" (команда Lua: get_trades, data = "").
     */
    public String requestAllTrades(String[] args) throws IOException {
        Settings s = Settings.fromArgs(args);
        System.out.println("Запрос get_trades к Lua: " + s.host + ":" + s.responsePort + " / " + s.callbackPort
                + " (read timeout " + s.readTimeoutMs + " ms)");

        IOException last = null;
        for (int attempt = 1; attempt <= s.attempts; attempt++) {
            try (Socket response = connect(s.host, s.responsePort, s.connectTimeoutMs, s.readTimeoutMs);
                 Socket callback = connect(s.host, s.callbackPort, s.connectTimeoutMs, 0)) {

                String requestJson = "{\"cmd\":\"get_trades\",\"data\":\"\"}";
                String responseJson = rpc(response, requestJson);
                System.out.println("Lua ответ получен.");
                return responseJson;
            } catch (SocketTimeoutException e) {
                last = new IOException(buildTimeoutHint(s, attempt), e);
            } catch (ConnectException e) {
                last = new IOException(buildConnectHint(s), e);
                break;
            }
            sleepQuiet(500L * attempt);
        }
        throw last != null ? last : new IOException("Неизвестная ошибка запроса get_trades");
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
        return readJsonLineStreaming(in);
    }

    /**
     * Потоковое чтение одной JSON-строки до символа '\n'.
     * Нужен для больших ответов (тысячи сделок), где readLine может вести себя нестабильно при таймаутах.
     */
    private static String readJsonLineStreaming(Reader in) throws IOException {
        StringBuilder sb = new StringBuilder(64 * 1024);
        char[] buf = new char[4096];
        while (true) {
            int n = in.read(buf);
            if (n == -1) {
                if (sb.isEmpty()) {
                    throw new IOException("Lua закрыл response-соединение без ответа.");
                }
                break;
            }
            for (int i = 0; i < n; i++) {
                char c = buf[i];
                if (c == '\n') {
                    return sb.toString();
                }
                if (c != '\r') {
                    sb.append(c);
                }
                if (sb.length() > MAX_RESPONSE_CHARS) {
                    throw new IOException("Ответ Lua слишком большой (>" + MAX_RESPONSE_CHARS + " символов).");
                }
            }
        }
        return sb.toString();
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String buildConnectHint(Settings s) {
        return "Connection refused: на " + s.host + ":" + s.responsePort + " или " + s.callbackPort + " нет слушателя. "
                + "Проверьте запуск Lua-скрипта в QUIK и порты.";
    }

    private static String buildTimeoutHint(Settings s, int attempt) {
        return "Timeout при get_trades (попытка " + attempt + "/" + s.attempts + "). "
                + "Увеличьте readTimeoutMs (4-й аргумент запуска) или уменьшите объём данных.";
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
        private final int connectTimeoutMs;
        private final int readTimeoutMs;
        private final int attempts;

        private Settings(String host, int responsePort, int callbackPort, int connectTimeoutMs, int readTimeoutMs, int attempts) {
            this.host = host;
            this.responsePort = responsePort;
            this.callbackPort = callbackPort;
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
            this.attempts = attempts;
        }

        private static Settings fromArgs(String[] args) {
            if (args == null || args.length == 0) {
                return new Settings(
                        DEFAULT_HOST,
                        DEFAULT_RESPONSE_PORT,
                        DEFAULT_RESPONSE_PORT + 1,
                        DEFAULT_CONNECT_TIMEOUT_MS,
                        DEFAULT_READ_TIMEOUT_MS,
                        DEFAULT_ATTEMPTS);
            }
            String host = args[0];
            int response = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_RESPONSE_PORT;
            int callback = args.length >= 3 ? Integer.parseInt(args[2]) : response + 1;
            int readTimeout = args.length >= 4 ? Integer.parseInt(args[3]) : DEFAULT_READ_TIMEOUT_MS;
            int connectTimeout = args.length >= 5 ? Integer.parseInt(args[4]) : DEFAULT_CONNECT_TIMEOUT_MS;
            int attempts = args.length >= 6 ? Integer.parseInt(args[5]) : DEFAULT_ATTEMPTS;
            return new Settings(host, response, callback, connectTimeout, readTimeout, Math.max(1, attempts));
        }
    }
}
