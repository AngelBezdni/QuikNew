package org.example.analytics;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Разбор полей сделки из JSON QUIK# (после {@code get_trades}).
 */
public final class TradeRowParser {

    /** В QLua часто: младший бит flags = 1 → продажа (см. документацию QUIK по таблице «Сделки»). */
    private static final long SELL_BIT = 1L;

    private TradeRowParser() {
    }

    public static String secCode(JsonNode trade) {
        return textPropIgnoreCase(trade, "sec_code");
    }

    public static String classCode(JsonNode trade) {
        return textPropIgnoreCase(trade, "class_code");
    }

    public static long qtyLots(JsonNode trade) {
        Long q = GetTradesByUidScript.nodeAsLong(GetTradesByUidScript.getPropIgnoreCase(trade, "qty"));
        if (q != null) {
            return Math.abs(q);
        }
        q = GetTradesByUidScript.nodeAsLong(GetTradesByUidScript.getPropIgnoreCase(trade, "quantity"));
        if (q != null) {
            return Math.abs(q);
        }
        return 0L;
    }

    public static long flags(JsonNode trade) {
        JsonNode f = GetTradesByUidScript.getPropIgnoreCase(trade, "flags");
        Long v = GetTradesByUidScript.nodeAsLong(f);
        return v != null ? v : 0L;
    }

    /**
     * true = продажа, false = покупка (если flags нет — считаем покупку).
     */
    public static boolean isSell(JsonNode trade) {
        return (flags(trade) & SELL_BIT) != 0;
    }

    private static String textPropIgnoreCase(JsonNode obj, String field) {
        JsonNode n = GetTradesByUidScript.getPropIgnoreCase(obj, field);
        if (n == null || n.isNull()) {
            return "";
        }
        if (n.isTextual()) {
            return n.asText().trim();
        }
        return n.toString();
    }

    static JsonNode getAny(JsonNode trade, String... names) {
        for (String n : names) {
            JsonNode v = GetTradesByUidScript.getPropIgnoreCase(trade, n);
            if (v != null && !v.isNull()) {
                return v;
            }
        }
        return null;
    }

    /** Номер сделки для отладки (строка, чтобы не терять точность). */
    public static String tradeNumText(JsonNode trade) {
        JsonNode n = getAny(trade, "trade_num", "tradenum");
        if (n == null || n.isNull()) {
            return "";
        }
        return n.isTextual() ? n.asText() : n.toString();
    }

    public static boolean hasSecCode(JsonNode trade) {
        return !secCode(trade).isEmpty();
    }
}
