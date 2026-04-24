package org.example.analytics;

/**
 * Агрегат по бумаге из сохранённых в H2 сделок (лоты, суммы, реализованная разница по min(куп,прод), остаток).
 */
public record TradeSummaryRow(
        String classCode,
        String secCode,
        long boughtLots,
        long soldLots,
        long netLots,
        double sumBuyRub,
        double sumSellRub,
        /** min(куп,прод) × (средняя цена продажи − средняя цена покупки), руб. */
        double realizedPriceDiffRub,
        /**
         * Оценка остатка в руб.: long — нетто-лоты × средняя цена покупки; short — |нетто| × средняя цена продажи.
         */
        double remainderRub
) {
}
