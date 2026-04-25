package org.example.summary;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Инкрементальная таблица сводки по инструменту.
 */
public final class SummaryTableModel extends AbstractTableModel {

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Integer> idx = new LinkedHashMap<>();
    private final String[] columns = {
            "SEC_CODE", "SUM_BUY", "SUM_SELL", "QTY_BUY", "QTY_SELL", "QTY_DELTA",
            "PNL_FIFO", "OPEN_QTY", "OPEN_COST"
    };

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    @Override
    public String getColumnName(int column) {
        return columns[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Row r = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> r.secCode;
            case 1 -> format2(r.buy);
            case 2 -> format2(r.sell);
            case 3 -> format2(r.buyQty);
            case 4 -> format2(r.sellQty);
            case 5 -> format2(r.buyQty.subtract(r.sellQty));
            case 6 -> format2(r.realizedPnl);
            case 7 -> format2(r.openQty);
            case 8 -> format2(r.openCost);
            default -> "";
        };
    }

    public void replaceAll(List<Row> snapshot) {
        rows.clear();
        idx.clear();
        List<Row> sorted = new ArrayList<>(snapshot);
        sorted.sort(Comparator.comparing(a -> a.secCode));
        for (Row r : sorted) {
            int i = rows.size();
            rows.add(new Row(
                    r.secCode, nz(r.buy), nz(r.sell), nz(r.buyQty), nz(r.sellQty),
                    nz(r.realizedPnl), nz(r.openQty), nz(r.openCost)));
            idx.put(r.secCode, i);
        }
        fireTableDataChanged();
    }

    public void upsertRow(Row row) {
        String key = row.secCode == null ? "" : row.secCode;
        Integer i = idx.get(key);
        if (i == null) {
            int insertAt = rows.size();
            rows.add(new Row(
                    key, row.buy, row.sell, row.buyQty, row.sellQty,
                    row.realizedPnl, row.openQty, row.openCost));
            idx.put(key, insertAt);
            fireTableRowsInserted(insertAt, insertAt);
            return;
        }
        Row dst = rows.get(i);
        dst.buy = nz(row.buy);
        dst.sell = nz(row.sell);
        dst.buyQty = nz(row.buyQty);
        dst.sellQty = nz(row.sellQty);
        dst.realizedPnl = nz(row.realizedPnl);
        dst.openQty = nz(row.openQty);
        dst.openCost = nz(row.openCost);
        fireTableRowsUpdated(i, i);
    }

    public void applyDelta(String secCode, BigDecimal buyDelta, BigDecimal sellDelta,
                           BigDecimal buyQtyDelta, BigDecimal sellQtyDelta,
                           BigDecimal pnlDelta, BigDecimal openQtyDelta, BigDecimal openCostDelta) {
        String key = secCode == null ? "" : secCode;
        BigDecimal b = nz(buyDelta);
        BigDecimal s = nz(sellDelta);
        BigDecimal bq = nz(buyQtyDelta);
        BigDecimal sq = nz(sellQtyDelta);
        BigDecimal pd = nz(pnlDelta);
        BigDecimal oqd = nz(openQtyDelta);
        BigDecimal ocd = nz(openCostDelta);
        Integer i = idx.get(key);
        if (i == null) {
            Row r = new Row(key, b, s, bq, sq, pd, oqd, ocd);
            int row = rows.size();
            rows.add(r);
            idx.put(key, row);
            fireTableRowsInserted(row, row);
            return;
        }
        Row r = rows.get(i);
        r.buy = nz(r.buy).add(b);
        r.sell = nz(r.sell).add(s);
        r.buyQty = nz(r.buyQty).add(bq);
        r.sellQty = nz(r.sellQty).add(sq);
        r.realizedPnl = nz(r.realizedPnl).add(pd);
        r.openQty = nz(r.openQty).add(oqd);
        r.openCost = nz(r.openCost).add(ocd);
        fireTableRowsUpdated(i, i);
    }

    public static final class Row {
        public final String secCode;
        public BigDecimal buy;
        public BigDecimal sell;
        public BigDecimal buyQty;
        public BigDecimal sellQty;
        public BigDecimal realizedPnl;
        public BigDecimal openQty;
        public BigDecimal openCost;

        public Row(String secCode, BigDecimal buy, BigDecimal sell, BigDecimal buyQty, BigDecimal sellQty,
                   BigDecimal realizedPnl, BigDecimal openQty, BigDecimal openCost) {
            this.secCode = secCode == null ? "" : secCode;
            this.buy = nz(buy);
            this.sell = nz(sell);
            this.buyQty = nz(buyQty);
            this.sellQty = nz(sellQty);
            this.realizedPnl = nz(realizedPnl);
            this.openQty = nz(openQty);
            this.openCost = nz(openCost);
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static final ThreadLocal<DecimalFormat> DF = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.US);
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator('.');
        DecimalFormat f = new DecimalFormat("#,##0.##", s);
        f.setGroupingUsed(true);
        return f;
    });

    private static String format2(BigDecimal v) {
        BigDecimal n = nz(v);
        if (BigDecimal.ZERO.compareTo(n) == 0) {
            return "0";
        }
        return DF.get().format(n);
    }
}
