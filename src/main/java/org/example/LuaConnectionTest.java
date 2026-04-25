package org.example;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Отдельный тестовый класс для проверки подключения к Lua (QUIK#).
 * <p>
 * Ожидает, что Lua-скрипт уже запущен и слушает два порта:
 * response и callback.
 */
public final class LuaConnectionTest {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_RESPONSE_PORT = 34130;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 8_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 8_000;

    public void run(String[] args) throws IOException {
        Settings s = Settings.fromArgs(args);
        System.out.println("Проверка Lua-соединения: " + s.host + ":" + s.responsePort + " / " + s.callbackPort);

        try {
            try (Socket response = connect(s.host, s.responsePort, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
                 Socket callback = connect(s.host, s.callbackPort, DEFAULT_CONNECT_TIMEOUT_MS, 0)) {

                // В QUIK# важен порядок: сначала response, потом callback.
                System.out.println("TCP OK: response и callback подключены.");

                String pingReq = "{\"cmd\":\"ping\",\"data\":\"Ping\"}";
                String pingResp = rpc(response, pingReq);
                System.out.println("Ping response: " + pingResp);
                System.out.println("Проверка завершена успешно.");
            }
        } catch (ConnectException e) {
            throw new IOException(buildConnectRefusedHint(s), e);
        } catch (SocketTimeoutException e) {
            throw new IOException(buildTimeoutHint(s), e);
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

    private static String buildConnectRefusedHint(Settings s) {
        return "Connection refused: на " + s.host + ":" + s.responsePort + " или " + s.callbackPort
                + " нет слушателя.\n"
                + "Проверьте:\n"
                + "1) В QUIK запущен Lua-скрипт сервера (QuikSharp/Quik_2).\n"
                + "2) Порты совпадают с config.json.\n"
                + "3) Для Quik_2 обычно: response=34132, callback=34133.\n"
                + "Пример запуска: 127.0.0.1 34132 34133";
    }

    private static String buildTimeoutHint(Settings s) {
        return "Timeout при подключении/чтении " + s.host + ":" + s.responsePort + " / " + s.callbackPort + ".\n"
                + "Проверьте доступность портов и локальные firewall/антивирус.";
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
