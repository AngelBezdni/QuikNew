package org.example.quik;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Одна строка JSON для RPC «сделки»: команда и поле {@code data}, как ожидает {@code qsfunctions.dispatch_and_process}.
 * <p>
 * Свойства JVM (опционально):
 * <ul>
 *   <li>{@code quik.get_trades.cmd} — по умолчанию {@code get_trades}; для узкой выборки:
 *       {@code get_trades_by_uid}, {@code get_trades_by_client_code}, либо {@code get_trades} с {@code class|sec}.</li>
 *   <li>{@code quik.get_trades.data} — строка в {@code data} (UID числом, client_code, {@code firm|client} и т.д.).</li>
 * </ul>
 */
public final class GetTradesRpcJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GetTradesRpcJson() {
    }

    public static String requestLine() throws JsonProcessingException {
        String cmd = System.getProperty("quik.get_trades.cmd", "get_trades");
        String data = System.getProperty("quik.get_trades.data", "");
        Map<String, String> map = new LinkedHashMap<>();
        map.put("cmd", cmd);
        map.put("data", data == null ? "" : data);
        return MAPPER.writeValueAsString(map);
    }
}
