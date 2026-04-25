package org.example;

import org.example.quik.ConnSettings;
import org.example.quik.QuikLineProtocol;
import org.example.quik.QuikSocketPair;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * Отдельный тестовый класс для проверки подключения к Lua (QUIK#).
 * <p>
 * Ожидает, что Lua-скрипт уже запущен и слушает два порта:
 * response и callback.
 */
public final class LuaConnectionTest {

    public void run(String[] args) throws IOException {
        ConnSettings s = ConnSettings.fromArgs(args, 20_000, 2);
        System.out.println("Проверка Lua-соединения: " + s.host() + ":" + s.responsePort() + " / " + s.callbackPort());

        try {
            try (QuikSocketPair pair = QuikSocketPair.open(s)) {
                System.out.println("TCP OK: response и callback подключены.");

                String pingReq = "{\"cmd\":\"ping\",\"data\":\"Ping\"}";
                QuikLineProtocol.writeJsonLine(pair.response(), pingReq);
                String pingResp = QuikLineProtocol.readJsonLineStreaming(pair.response());
                System.out.println("Ping response: " + pingResp);
                System.out.println("Проверка завершена успешно.");
            }
        } catch (ConnectException e) {
            throw new IOException(buildConnectRefusedHint(s), e);
        } catch (SocketTimeoutException e) {
            throw new IOException(buildTimeoutHint(s), e);
        }
    }

    private static String buildConnectRefusedHint(ConnSettings s) {
        return "Connection refused: на " + s.host() + ":" + s.responsePort() + " или " + s.callbackPort()
                + " нет слушателя.\n"
                + "Проверьте:\n"
                + "1) В QUIK запущен Lua-скрипт сервера (QuikSharp/Quik_2).\n"
                + "2) Порты совпадают с config.json.\n"
                + "3) Для Quik_2 обычно: response=34132, callback=34133.\n"
                + "Пример запуска: 127.0.0.1 34132 34133 20000 10000";
    }

    private static String buildTimeoutHint(ConnSettings s) {
        return "Timeout при подключении/чтении " + s.host() + ":" + s.responsePort() + " / " + s.callbackPort()
                + " (read timeout " + s.readTimeoutMs() + " ms).\n"
                + "Проверьте доступность портов и локальные firewall/антивирус.";
    }

    public static void main(String[] args) {
        try {
            new LuaConnectionTest().run(args);
        } catch (Exception e) {
            System.err.println("Ошибка проверки Lua-соединения:");
            System.err.println(e.getMessage());
        }
    }
}
