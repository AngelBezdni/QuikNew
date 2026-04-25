package org.example.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.scripts.GetTradesByUidScript;

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
        Long q = signedQty(trade);
        if (q != null) {
            return Math.abs(q);
        }
        return 0L;
    }

    /** Сырой signed qty, если поле в источнике может быть со знаком. */
    public static Long signedQty(JsonNode trade) {
        Long q = GetTradesByUidScript.nodeAsLong(GetTradesByUidScript.getPropIgnoreCase(trade, "qty"));
        if (q != null) {
            return q;
        }
        q = GetTradesByUidScript.nodeAsLong(GetTradesByUidScript.getPropIgnoreCase(trade, "quantity"));
        if (q != null) {
            return q;
        }
        return null;
    }

    public static long flags(JsonNode trade) {
        JsonNode f = GetTradesByUidScript.getPropIgnoreCase(trade, "flags");
        Long v = GetTradesByUidScript.nodeAsLong(f);
        return v != null ? v : 0L;
    }

    /** true = SELL, false = BUY. */
    public static boolean isSell(JsonNode trade) {
        Boolean explicit = sideFromTextFields(trade);
        if (explicit != null) {
            return explicit;
        }
        Long signedQty = signedQty(trade);
        if (signedQty != null && signedQty < 0) {
            return true;
        }
        return (flags(trade) & SELL_BIT) != 0;
    }

    private static Boolean sideFromTextFields(JsonNode trade) {
        JsonNode raw = getAny(trade, "operation", "side", "buy_sell", "buysell", "type");
        if (raw == null || raw.isNull()) {
            return null;
        }
        String s = raw.asText("").trim().toUpperCase();
        if (s.isEmpty()) {
            return null;
        }
        if ("S".equals(s) || "SELL".equals(s) || "ПРОДАЖА".equals(s)) {
            return true;
        }
        if ("B".equals(s) || "BUY".equals(s) || "ПОКУПКА".equals(s)) {
            return false;
        }
        return null;
    }

    /** Цена сделки за единицу (поле price в QUIK). */
    public static double unitPrice(JsonNode trade) {
        Double p = nodeAsDouble(GetTradesByUidScript.getPropIgnoreCase(trade, "price"));
        return p != null ? p : 0.0;
    }

    /**
     * Денежный объём сделки в рублях: при наличии {@code value} берём его, иначе {@code qty * price}.
     */
    public static double amountRub(JsonNode trade) {
        JsonNode val = getAny(trade, "value", "VALUE");
        Double v = nodeAsDouble(val);
        if (v != null && Math.abs(v) > 1e-9) {
            return Math.abs(v);
        }
        return Math.abs(unitPrice(trade) * qtyLots(trade));
    }

    public static Double nodeAsDouble(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isNumber()) {
            return n.doubleValue();
        }
        if (n.isTextual()) {
            String t = n.asText().trim().replace(',', '.');
            if (t.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
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
