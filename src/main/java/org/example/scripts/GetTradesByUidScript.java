package org.example.scripts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.example.quik.dto.QuikMessage;
import org.example.quik.rpc.QuikRpcClient;

import java.io.IOException;

/**
 * Все сделки по UID без изменений Lua: запрос {@code get_trades} с пустым фильтром, затем отбор строк в JVM
 * по полям UID, которые присылает QUIK в JSON (имена могут отличаться по версии).
 */
public final class GetTradesByUidScript {

    public static final long DEFAULT_UID = 115L;

    private static final String[] UID_JSON_FIELDS = {
            "on_behalf_of_uid",
            "userid",
            "user_id",
            "uid",
            "client_uid",
            "user"
    };

    private final QuikRpcClient rpc;

    public GetTradesByUidScript(QuikRpcClient rpc) {
        this.rpc = rpc;
    }

    public QuikMessage run(long uid) throws IOException {
        QuikMessage raw = new GetTradesScript(rpc).runAll();
        if (raw.getLuaError() != null || "lua_error".equals(raw.getCmd())) {
            return raw;
        }
        JsonNode data = raw.getData();
        if (data == null || !data.isArray()) {
            QuikMessage err = new QuikMessage();
            err.setCmd("lua_error");
            err.setLuaError("get_trades: ожидался массив в data, получено: " + (data == null ? "null" : data.getNodeType().toString()));
            return err;
        }
        ArrayNode filtered = JsonNodeFactory.instance.arrayNode();
        for (JsonNode trade : data) {
            if (trade.isObject() && uidMatches(trade, uid)) {
                filtered.add(trade);
            }
        }
        QuikMessage out = new QuikMessage();
        out.setCmd("get_trades");
        out.setData(filtered);
        if (raw.getT() != null) {
            out.setT(raw.getT());
        }
        return out;
    }

    public QuikMessage runDefault() throws IOException {
        return run(DEFAULT_UID);
    }

    static boolean uidMatches(JsonNode trade, long uid) {
        Long v = extractUid(trade);
        return v != null && v == uid;
    }

    static Long extractUid(JsonNode trade) {
        for (String field : UID_JSON_FIELDS) {
            if (!trade.has(field)) {
                continue;
            }
            JsonNode n = trade.get(field);
            if (n == null || n.isNull()) {
                continue;
            }
            if (n.isNumber()) {
                return n.longValue();
            }
            if (n.isTextual()) {
                try {
                    return Long.parseLong(n.asText().trim());
                } catch (NumberFormatException ignored) {
                    // next field
                }
            }
        }
        return null;
    }
}
