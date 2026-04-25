package org.example.storage;

import org.example.trade.TradeRecord;
import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Локальная БД H2: сделки с дедупом по вычисляемому {@code dedup_key}.
 */
public final class H2TradeStore implements AutoCloseable {

    private final String jdbcUrl;
    private Connection connection;

    public H2TradeStore(Path h2FileBase) {
        Path parent = h2FileBase.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new IllegalStateException("Не удалось создать каталог для H2: " + parent, e);
            }
        }
        String abs = h2FileBase.toAbsolutePath().normalize().toString().replace('\\', '/');
        this.jdbcUrl = "jdbc:h2:file:" + abs + ";DB_CLOSE_DELAY=-1;AUTO_SERVER=FALSE";
    }

    public static H2TradeStore fromPropertyOrDefault() {
        String prop = System.getProperty("quik.h2.file");
        Path base = prop != null && !prop.isBlank()
                ? Path.of(prop)
                : Path.of("data", "quik_trades");
        return new H2TradeStore(base);
    }

    public void init() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }
        connection = DriverManager.getConnection(jdbcUrl);
        connection.setAutoCommit(true);
        try (var st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS trade_fact (
                        dedup_key VARCHAR(384) NOT NULL PRIMARY KEY,
                        source VARCHAR(16) NOT NULL,
                        class_code VARCHAR(128),
                        sec_code VARCHAR(128),
                        trade_num VARCHAR(128) NOT NULL,
                        order_num VARCHAR(128),
                        operation VARCHAR(16),
                        flags BIGINT,
                        qty DECIMAL(30, 10),
                        price DECIMAL(30, 10),
                        value_rub DECIMAL(30, 10),
                        raw_json CLOB NOT NULL,
                        received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                    )
                    """);
            st.execute("ALTER TABLE trade_fact ADD COLUMN IF NOT EXISTS operation VARCHAR(16)");
        }
    }

    /**
     * @return true если строка вставлена, false если дубликат по {@code dedup_key}
     */
    public boolean insertIfAbsent(TradeRecord t) throws SQLException {
        Objects.requireNonNull(t, "trade");
        String key = dedupKey(t);
        String sql = """
                INSERT INTO trade_fact (dedup_key, source, class_code, sec_code, trade_num, order_num, operation, flags, qty, price, value_rub, raw_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, t.source());
            ps.setString(3, emptyToNull(t.classCode()));
            ps.setString(4, emptyToNull(t.secCode()));
            ps.setString(5, t.tradeNum());
            ps.setString(6, emptyToNull(t.orderNum()));
            ps.setString(7, emptyToNull(t.operation()));
            if (t.flags() != null) {
                ps.setLong(8, t.flags());
            } else {
                ps.setObject(8, null);
            }
            ps.setObject(9, t.qty());
            ps.setObject(10, t.price());
            ps.setObject(11, t.valueRub());
            ps.setString(12, t.rawJson());
            ps.executeUpdate();
            return true;
        } catch (JdbcSQLIntegrityConstraintViolationException e) {
            return false;
        }
    }

    public List<SecSummaryRow> loadSecSummaryRows() throws SQLException {
        String sql = """
                SELECT
                    COALESCE(sec_code, '') AS sec_code,
                    SUM(CASE
                        WHEN UPPER(COALESCE(operation, '')) = 'B' THEN COALESCE(value_rub, COALESCE(qty, 0) * COALESCE(price, 0))
                        WHEN operation IS NULL OR operation = '' THEN
                            CASE WHEN BITAND(COALESCE(flags, 0), 4) = 0
                                THEN COALESCE(value_rub, COALESCE(qty, 0) * COALESCE(price, 0))
                                ELSE 0 END
                        ELSE 0
                    END) AS buy_sum,
                    SUM(CASE
                        WHEN UPPER(COALESCE(operation, '')) = 'S' THEN COALESCE(value_rub, COALESCE(qty, 0) * COALESCE(price, 0))
                        WHEN operation IS NULL OR operation = '' THEN
                            CASE WHEN BITAND(COALESCE(flags, 0), 4) <> 0
                                THEN COALESCE(value_rub, COALESCE(qty, 0) * COALESCE(price, 0))
                                ELSE 0 END
                        ELSE 0
                    END) AS sell_sum
                FROM trade_fact
                GROUP BY COALESCE(sec_code, '')
                ORDER BY COALESCE(sec_code, '')
                """;
        List<SecSummaryRow> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new SecSummaryRow(
                        rs.getString("sec_code"),
                        rs.getBigDecimal("buy_sum"),
                        rs.getBigDecimal("sell_sum")));
            }
        }
        return rows;
    }

    public static String dedupKey(TradeRecord t) {
        String cc = t.classCode() == null ? "" : t.classCode();
        String sc = t.secCode() == null ? "" : t.secCode();
        String tn = t.tradeNum() == null ? "" : t.tradeNum();
        String raw = t.rawJson() == null ? "" : t.rawJson();
        if (tn.isEmpty()) {
            tn = "H" + Integer.toHexString(raw.hashCode());
        }
        String key = cc + "|" + sc + "|" + tn;
        if (key.length() > 380) {
            key = key.substring(0, 380);
        }
        return key;
    }

    public record SecSummaryRow(String secCode, java.math.BigDecimal buySum, java.math.BigDecimal sellSum) {
    }

    private static String emptyToNull(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return s;
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}
