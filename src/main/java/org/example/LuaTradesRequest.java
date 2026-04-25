package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.example.quik.ConnSettings;
import org.example.quik.GetTradesRpcJson;
import org.example.quik.QuikLineProtocol;
import org.example.quik.QuikSocketPair;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * Отправляет в Lua (QUIK#) команду get_trades и возвращает ответ JSON.
 * <p>
 * Важно: Lua-сервер QuikSharp ожидает два подключения:
 * 1) response порт (запрос/ответ),
 * 2) callback порт (push-события).
 * Поэтому здесь поднимаются оба сокета, даже если callback не читаем.
 */
public final class LuaTradesRequest {

    /**
     * Получить все сделки из таблицы "Сделки" (команда Lua: get_trades, data = "").
     */
    public String requestAllTrades(String[] args) throws IOException {
        ConnSettings s = ConnSettings.fromArgs(args, ConnSettings.DEFAULT_READ_TIMEOUT_MS, 2);
        System.out.println("Запрос сделок к Lua: " + s.host() + ":" + s.responsePort() + " / " + s.callbackPort()
                + " (read timeout " + s.readTimeoutMs() + " ms)");

        final String requestJson;
        try {
            requestJson = GetTradesRpcJson.requestLine();
        } catch (JsonProcessingException e) {
            throw new IOException("Не удалось собрать JSON запроса сделок", e);
        }
        System.out.println("RPC: " + requestJson);
        IOException last = null;
        for (int attempt = 1; attempt <= s.attempts(); attempt++) {
            try (QuikSocketPair pair = QuikSocketPair.open(s)) {
                QuikLineProtocol.writeJsonLine(pair.response(), requestJson);
                String responseJson = QuikLineProtocol.readJsonLineStreaming(pair.response());
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

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String buildConnectHint(ConnSettings s) {
        return "Connection refused: на " + s.host() + ":" + s.responsePort() + " или " + s.callbackPort() + " нет слушателя. "
                + "Проверьте запуск Lua-скрипта в QUIK и порты.";
    }

    private static String buildTimeoutHint(ConnSettings s, int attempt) {
        return "Timeout при get_trades (попытка " + attempt + "/" + s.attempts() + "). "
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
}
