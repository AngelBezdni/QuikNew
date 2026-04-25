package org.example.quik;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Одна строка JSON для RPC «сделки»: команда и поле {@code data}, как ожидает {@code qsfunctions.dispatch_and_process}.
 * <p>
 * Режим фильтра на стороне Lua: {@code -Dquik.get_trades.filter_json={"class_code":"TQBR","sec_code":"SBER"}}
 * или {@code -Dquik.get_trades.filter_file=C:/path/filter.json} (UTF-8) — тогда {@code cmd=get_trades_filtered}, {@code data} — объект.
 * <p>
 * Иначе: {@code quik.get_trades.cmd} / {@code quik.get_trades.data} как строка.
 * <p>
 * Поля фильтра (AND): {@code class_code}, {@code sec_code}, {@code client_code}, {@code firm_id}, {@code uid},
 * {@code order_num}, {@code flags}, {@code trdacc_id} — см. {@code get_trades_filtered} в {@code lua/qsfunctions.lua}.
 */
public final class GetTradesRpcJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GetTradesRpcJson() {
    }

    public static String requestLine() throws IOException {
        try {
            String filterJson = System.getProperty("quik.get_trades.filter_json");
            if (filterJson == null || filterJson.isBlank()) {
                String filterPath = System.getProperty("quik.get_trades.filter_file");
                if (filterPath != null && !filterPath.isBlank()) {
                    filterJson = Files.readString(Path.of(filterPath.trim()), StandardCharsets.UTF_8);
                }
            }
            if (filterJson != null && !filterJson.isBlank()) {
                JsonNode spec = MAPPER.readTree(filterJson.trim());
                if (!spec.isObject()) {
                    throw new IOException("quik.get_trades.filter_json: ожидается JSON-объект {...}");
                }
                ObjectNode root = MAPPER.createObjectNode();
                root.put("cmd", "get_trades_filtered");
                root.set("data", spec);
                return MAPPER.writeValueAsString(root);
            }

            String cmd = System.getProperty("quik.get_trades.cmd", "get_trades");
            String data = System.getProperty("quik.get_trades.data", "");
            Map<String, String> map = new LinkedHashMap<>();
            map.put("cmd", cmd);
            map.put("data", data == null ? "" : data);
            return MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IOException("Ошибка JSON в параметрах запроса сделок", e);
        }
    }
}
