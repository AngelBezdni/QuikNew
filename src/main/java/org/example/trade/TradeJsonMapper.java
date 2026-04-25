package org.example.trade;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Разбор объекта сделки из JSON (гибкие имена полей под разные версии Lua).
 */
public final class TradeJsonMapper {

    private TradeJsonMapper() {
    }

    public static Optional<TradeRecord> fromNode(String source, JsonNode node) {
        if (node == null || node.isNull() || !node.isObject()) {
            return Optional.empty();
        }
        String classCode = text(node, "class_code", "classcode", "CLASSCODE");
        String secCode = text(node, "sec_code", "seccode", "SECCODE");
        String tradeNum = text(node, "trade_num", "tradenum", "TRADENUM");
        String orderNum = text(node, "order_num", "ordernum", "ORDERNUM");
        String clientCode = text(node, "client_code", "CLIENT_CODE");
        String firmId = text(node, "firmid", "cpfirmid", "firm_id", "FIRMID");
        String trdAccId = text(node, "trdacc_id", "TRDACC_ID", "account", "ACCOUNT");
        Long uid = longOrNull(node, "uid", "UID", "on_behalf_of_uid", "client_uid", "userid");
        String operation = normalizeOperation(text(node, "operation", "OPERATION", "side", "SIDE"));
        Long flags = longOrNull(node, "flags", "FLAGS");
        BigDecimal qty = decimal(node, "qty", "QTY");
        BigDecimal price = decimal(node, "price", "PRICE");
        BigDecimal value = decimal(node, "value", "VALUE");

        String raw = node.toString();
        if (tradeNum.isEmpty()) {
            tradeNum = "H" + Integer.toHexString(raw.hashCode());
        }
        return Optional.of(new TradeRecord(
                source,
                classCode,
                secCode,
                tradeNum,
                orderNum,
                clientCode,
                firmId,
                trdAccId,
                uid,
                operation,
                flags,
                qty,
                price,
                value,
                raw));
    }

    private static String text(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull()) {
                if (v.isTextual()) {
                    return v.asText("");
                }
                if (v.isNumber()) {
                    return v.asText();
                }
            }
        }
        return "";
    }

    private static Long longOrNull(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull() && v.isNumber()) {
                return v.longValue();
            }
        }
        return null;
    }

    private static BigDecimal decimal(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull() && v.isNumber()) {
                return v.decimalValue();
            }
        }
        return null;
    }

    private static String normalizeOperation(String op) {
        if (op == null || op.isBlank()) {
            return "";
        }
        String v = op.trim().toUpperCase();
        if (v.startsWith("B")) {
            return "B";
        }
        if (v.startsWith("S")) {
            return "S";
        }
        return v;
    }
}
