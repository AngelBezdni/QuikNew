package org.example.scripts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.example.quik.dto.QuikMessage;
import org.example.quik.rpc.QuikRpcClient;

import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Фильтр сделок по UID на стороне JVM после штатного {@code get_trades}.
 * В JSON из Lua имена полей и регистр могут отличаться; часть полей приходит как double или строка.
 */
public final class GetTradesByUidScript {

    public static final long DEFAULT_UID = 115L;

    private static final String[] UID_JSON_FIELDS = {
            "on_behalf_of_uid",
            "userid",
            "user_id",
            "uid",
            "client_uid",
            "user",
            "userid_ext",
            "investment_decision_maker_short_code",
            "executing_trader_short_code",
            "client_short_code"
    };

    /** Поля с числами, которые почти никогда не совпадают с UID пользователя — не используем в «широком» поиске. */
    private static final Set<String> NUMERIC_NO_UID = Set.of(
            "trade_num", "ordernum", "order_num", "trans_id", "flags", "qty", "value", "price",
            "accruedint", "yield", "reporate", "repay", "open_interest", "balance", "commission",
            "datetime", "canceled_uid", "order_revision_number", "order_qty", "order_price"
    );

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
            if (uidMatches(trade, uid)) {
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
        if (trade == null || trade.isNull()) {
            return false;
        }
        if (trade.isObject()) {
            if (matchKnownUidFields(trade, uid)) {
                return true;
            }
            if (matchByFieldNameHeuristic(trade, uid)) {
                return true;
            }
            if (matchClientCodeAsUid(trade, uid)) {
                return true;
            }
            if (matchLooseNumericValue(trade, uid)) {
                return true;
            }
            return false;
        }
        // редкий случай: элемент массива не объект — если это число, сравниваем напрямую
        Long v = nodeAsLong(trade);
        return v != null && v == uid;
    }

    private static boolean matchKnownUidFields(JsonNode trade, long uid) {
        for (String field : UID_JSON_FIELDS) {
            Long v = nodeAsLong(getPropIgnoreCase(trade, field));
            if (v != null && v == uid) {
                return true;
            }
        }
        return false;
    }

    /** Любое поле, в имени которого есть uid / user / client (кроме исключений), со значением = uid. */
    private static boolean matchByFieldNameHeuristic(JsonNode trade, long uid) {
        Iterator<String> names = trade.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            String lower = name.toLowerCase(Locale.ROOT);
            if (!(lower.contains("uid") || lower.contains("user") || lower.contains("client"))) {
                continue;
            }
            if (NUMERIC_NO_UID.contains(lower)) {
                continue;
            }
            Long v = nodeAsLong(trade.get(name));
            if (v != null && v == uid) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchClientCodeAsUid(JsonNode trade, long uid) {
        JsonNode cc = getPropIgnoreCase(trade, "client_code");
        if (cc == null || cc.isNull() || !cc.isTextual()) {
            return false;
        }
        String t = cc.asText().trim();
        if (t.isEmpty()) {
            return false;
        }
        try {
            return Long.parseLong(t) == uid;
        } catch (NumberFormatException e) {
            return t.equals(Long.toString(uid));
        }
    }

    /**
     * Последняя попытка: любое поле со значением, равным uid, кроме явно «торговых» числовых полей.
     * Нужна, если брокер кладёт идентификатор в нестандартное имя.
     */
    private static boolean matchLooseNumericValue(JsonNode trade, long uid) {
        Iterator<String> names = trade.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            String lower = name.toLowerCase(Locale.ROOT);
            if (NUMERIC_NO_UID.contains(lower)) {
                continue;
            }
            if (lower.contains("time") || lower.contains("date")) {
                continue;
            }
            Long v = nodeAsLong(trade.get(name));
            if (v != null && v == uid) {
                return true;
            }
        }
        return false;
    }

    public static JsonNode getPropIgnoreCase(JsonNode obj, String field) {
        if (obj == null || !obj.isObject()) {
            return null;
        }
        if (obj.has(field)) {
            return obj.get(field);
        }
        Iterator<String> it = obj.fieldNames();
        while (it.hasNext()) {
            String n = it.next();
            if (n.equalsIgnoreCase(field)) {
                return obj.get(n);
            }
        }
        return null;
    }

    public static Long nodeAsLong(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isIntegralNumber()) {
            return n.longValue();
        }
        if (n.isFloatingPointNumber()) {
            double d = n.asDouble();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return null;
            }
            long r = Math.round(d);
            if (Math.abs(d - r) > 1e-6) {
                return null;
            }
            return r;
        }
        if (n.isTextual()) {
            String t = n.asText().trim();
            if (t.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Для тестов / отладки: извлечь первое похожее на UID значение из известного списка полей. */
    static Long extractUid(JsonNode trade) {
        if (trade == null || !trade.isObject()) {
            return null;
        }
        for (String field : UID_JSON_FIELDS) {
            Long v = nodeAsLong(getPropIgnoreCase(trade, field));
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
