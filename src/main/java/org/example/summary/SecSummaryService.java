package org.example.summary;

import org.example.storage.H2TradeStore;
import org.example.trade.TradeRecord;

import javax.swing.SwingUtilities;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис сводки по sec_code: начальная загрузка из H2 + инкрементальные апдейты от новых сделок.
 */
public final class SecSummaryService {

    private static final long SELL_FLAG_BIT = 0x4L;

    private final SummaryTableModel model;
    private final Map<String, FifoState> states = new LinkedHashMap<>();

    public SecSummaryService(SummaryTableModel model) {
        this.model = model;
    }

    public void loadInitial(H2TradeStore store) throws SQLException {
        states.clear();
        List<H2TradeStore.TradeForSummary> trades = store.loadTradesForSummary();
        for (H2TradeStore.TradeForSummary t : trades) {
            String sec = t.secCode() == null ? "" : t.secCode();
            FifoState st = states.computeIfAbsent(sec, k -> new FifoState());
            st.apply(resolveSide(t.operation(), t.flags()), nz(t.qty()), tradeAmount(t.qty(), t.price(), t.valueRub()), priceOf(t.qty(), t.price(), t.valueRub()));
        }
        List<SummaryTableModel.Row> uiRows = new ArrayList<>(states.size());
        for (Map.Entry<String, FifoState> e : states.entrySet()) {
            uiRows.add(e.getValue().toRow(e.getKey()));
        }
        runOnEdt(() -> model.replaceAll(uiRows));
    }

    public void onInsertedTrade(TradeRecord t) {
        BigDecimal amount = tradeAmount(t);
        BigDecimal price = priceOf(t.qty(), t.price(), t.valueRub());
        String side = resolveSide(t);
        BigDecimal qty = tradeQty(t);
        String sec = t.secCode() == null ? "" : t.secCode();
        FifoState st = states.computeIfAbsent(sec, k -> new FifoState());
        st.apply(side, qty, amount, price);
        SummaryTableModel.Row row = st.toRow(sec);
        runOnEdt(() -> model.upsertRow(row));
    }

    public void clearAll() {
        states.clear();
        runOnEdt(() -> model.replaceAll(List.of()));
    }

    private static String resolveSide(String operation, Long flags) {
        if (operation != null && !operation.isBlank()) {
            String u = operation.trim().toUpperCase();
            if (u.startsWith("B")) {
                return "B";
            }
            if (u.startsWith("S")) {
                return "S";
            }
        }
        if (flags != null) {
            return (flags & SELL_FLAG_BIT) != 0 ? "S" : "B";
        }
        return "U";
    }

    private static String resolveSide(TradeRecord t) {
        return resolveSide(t.operation(), t.flags());
    }

    private static BigDecimal tradeAmount(TradeRecord t) {
        if (t.valueRub() != null) {
            return t.valueRub();
        }
        if (t.qty() != null && t.price() != null) {
            return t.qty().multiply(t.price());
        }
        if (t.qty() != null) {
            return t.qty();
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal tradeAmount(BigDecimal qty, BigDecimal price, BigDecimal valueRub) {
        if (valueRub != null) {
            return valueRub;
        }
        if (qty != null && price != null) {
            return qty.multiply(price);
        }
        if (qty != null) {
            return qty;
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal tradeQty(TradeRecord t) {
        if (t.qty() != null) {
            return t.qty();
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal priceOf(BigDecimal qty, BigDecimal price, BigDecimal valueRub) {
        if (price != null) {
            return price;
        }
        if (valueRub != null && qty != null && qty.compareTo(BigDecimal.ZERO) != 0) {
            return valueRub.divide(qty, 10, java.math.RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static void runOnEdt(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }

    private static final class FifoState {
        private final Deque<Lot> longLots = new ArrayDeque<>();
        private final Deque<Lot> shortLots = new ArrayDeque<>();
        private BigDecimal sumBuy = BigDecimal.ZERO;
        private BigDecimal sumSell = BigDecimal.ZERO;
        private BigDecimal qtyBuy = BigDecimal.ZERO;
        private BigDecimal qtySell = BigDecimal.ZERO;
        private BigDecimal realizedPnl = BigDecimal.ZERO;
        private BigDecimal openQty = BigDecimal.ZERO;
        private BigDecimal openCost = BigDecimal.ZERO;

        void apply(String side, BigDecimal qty, BigDecimal amount, BigDecimal unitPrice) {
            BigDecimal q = nz(qty);
            if (q.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            BigDecimal px = nz(unitPrice);
            if ("B".equals(side)) {
                sumBuy = sumBuy.add(nz(amount));
                qtyBuy = qtyBuy.add(q);
                // Сначала закрываем шорт-лоты (FIFO)
                BigDecimal remaining = q;
                while (remaining.compareTo(BigDecimal.ZERO) > 0 && !shortLots.isEmpty()) {
                    Lot lot = shortLots.peekFirst();
                    BigDecimal matched = lot.qty.min(remaining);
                    // Для short PnL = цена продажи шорта - цена откупа
                    realizedPnl = realizedPnl.add(matched.multiply(lot.price.subtract(px)));
                    lot.qty = lot.qty.subtract(matched);
                    remaining = remaining.subtract(matched);
                    openQty = openQty.add(matched);
                    openCost = openCost.add(matched.multiply(lot.price));
                    if (lot.qty.compareTo(BigDecimal.ZERO) == 0) {
                        shortLots.removeFirst();
                    }
                }
                // Остаток открывает long
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    longLots.addLast(new Lot(remaining, px));
                    openQty = openQty.add(remaining);
                    openCost = openCost.add(remaining.multiply(px));
                }
                return;
            }
            if ("S".equals(side)) {
                sumSell = sumSell.add(nz(amount));
                qtySell = qtySell.add(q);
                BigDecimal remaining = q;
                // Сначала закрываем long-лоты (FIFO)
                while (remaining.compareTo(BigDecimal.ZERO) > 0 && !longLots.isEmpty()) {
                    Lot lot = longLots.peekFirst();
                    BigDecimal matched = lot.qty.min(remaining);
                    realizedPnl = realizedPnl.add(matched.multiply(px.subtract(lot.price)));
                    lot.qty = lot.qty.subtract(matched);
                    remaining = remaining.subtract(matched);
                    openQty = openQty.subtract(matched);
                    openCost = openCost.subtract(matched.multiply(lot.price));
                    if (lot.qty.compareTo(BigDecimal.ZERO) == 0) {
                        longLots.removeFirst();
                    }
                }
                // Остаток открывает short
                if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                    shortLots.addLast(new Lot(remaining, px));
                    openQty = openQty.subtract(remaining);
                    openCost = openCost.subtract(remaining.multiply(px));
                }
            }
        }

        SummaryTableModel.Row toRow(String sec) {
            return new SummaryTableModel.Row(sec, sumBuy, sumSell, qtyBuy, qtySell, realizedPnl, openQty, openCost);
        }
    }

    private static final class Lot {
        private BigDecimal qty;
        private final BigDecimal price;

        private Lot(BigDecimal qty, BigDecimal price) {
            this.qty = qty;
            this.price = price;
        }
    }
}
