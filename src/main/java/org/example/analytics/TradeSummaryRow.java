package org.example.analytics;

/**
 * Агрегат по бумаге из сохранённых в H2 сделок.
 */
public record TradeSummaryRow(String classCode, String secCode, long boughtLots, long soldLots, long netLots) {
}
