package org.example.summary;

import org.example.storage.H2TradeStore;
import org.example.trade.TradeRecord;

import javax.swing.SwingUtilities;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис сводки по sec_code: начальная загрузка из H2 + инкрементальные апдейты от новых сделок.
 */
public final class SecSummaryService {

    private static final long SELL_FLAG_BIT = 0x4L;

    private final SummaryTableModel model;

    public SecSummaryService(SummaryTableModel model) {
        this.model = model;
    }

    public void loadInitial(H2TradeStore store) throws SQLException {
        List<H2TradeStore.SecSummaryRow> dbRows = store.loadSecSummaryRows();
        List<SummaryTableModel.Row> uiRows = new ArrayList<>(dbRows.size());
        for (H2TradeStore.SecSummaryRow r : dbRows) {
            uiRows.add(new SummaryTableModel.Row(r.secCode(), nz(r.buySum()), nz(r.sellSum())));
        }
        runOnEdt(() -> model.replaceAll(uiRows));
    }

    public void onInsertedTrade(TradeRecord t) {
        BigDecimal amount = tradeAmount(t);
        String side = resolveSide(t);
        BigDecimal buyDelta = BigDecimal.ZERO;
        BigDecimal sellDelta = BigDecimal.ZERO;
        if ("B".equals(side)) {
            buyDelta = amount;
        } else if ("S".equals(side)) {
            sellDelta = amount;
        }
        String sec = t.secCode() == null ? "" : t.secCode();
        final BigDecimal b = buyDelta;
        final BigDecimal s = sellDelta;
        runOnEdt(() -> model.applyDelta(sec, b, s));
    }

    private static String resolveSide(TradeRecord t) {
        String op = t.operation();
        if (op != null && !op.isBlank()) {
            String u = op.trim().toUpperCase();
            if (u.startsWith("B")) {
                return "B";
            }
            if (u.startsWith("S")) {
                return "S";
            }
        }
        Long flags = t.flags();
        if (flags != null) {
            return (flags & SELL_FLAG_BIT) != 0 ? "S" : "B";
        }
        return "U";
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
}
