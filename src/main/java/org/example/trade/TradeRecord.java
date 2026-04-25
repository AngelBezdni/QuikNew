package org.example.trade;

import java.math.BigDecimal;

/**
 * Сделка для записи в хранилище (поля из JSON QUIK# / таблицы «Сделки»).
 */
public record TradeRecord(
        String source,
        String classCode,
        String secCode,
        String tradeNum,
        String orderNum,
        String operation,
        Long flags,
        BigDecimal qty,
        BigDecimal price,
        BigDecimal valueRub,
        String rawJson
) {
}
