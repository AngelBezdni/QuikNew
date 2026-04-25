package org.example.summary;

import javax.swing.table.AbstractTableModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Инкрементальная таблица сводки по инструменту.
 */
public final class SummaryTableModel extends AbstractTableModel {

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Integer> idx = new LinkedHashMap<>();
    private final String[] columns = {"SEC_CODE", "SUM_BUY", "SUM_SELL"};

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
            case 1 -> r.buy;
            case 2 -> r.sell;
            default -> "";
        };
    }

    public void replaceAll(List<Row> snapshot) {
        rows.clear();
        idx.clear();
        for (Row r : snapshot) {
            int i = rows.size();
            rows.add(new Row(r.secCode, nz(r.buy), nz(r.sell)));
            idx.put(r.secCode, i);
        }
        fireTableDataChanged();
    }

    public void applyDelta(String secCode, BigDecimal buyDelta, BigDecimal sellDelta) {
        String key = secCode == null ? "" : secCode;
        BigDecimal b = nz(buyDelta);
        BigDecimal s = nz(sellDelta);
        Integer i = idx.get(key);
        if (i == null) {
            Row r = new Row(key, b, s);
            int row = rows.size();
            rows.add(r);
            idx.put(key, row);
            fireTableRowsInserted(row, row);
            return;
        }
        Row r = rows.get(i);
        r.buy = nz(r.buy).add(b);
        r.sell = nz(r.sell).add(s);
        fireTableRowsUpdated(i, i);
    }

    public static final class Row {
        public final String secCode;
        public BigDecimal buy;
        public BigDecimal sell;

        public Row(String secCode, BigDecimal buy, BigDecimal sell) {
            this.secCode = secCode == null ? "" : secCode;
            this.buy = nz(buy);
            this.sell = nz(sell);
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
