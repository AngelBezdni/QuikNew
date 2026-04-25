package org.example.storage;

import org.example.trade.TradeRecord;
import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
                        flags BIGINT,
                        qty DECIMAL(30, 10),
                        price DECIMAL(30, 10),
                        value_rub DECIMAL(30, 10),
                        raw_json CLOB NOT NULL,
                        received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                    )
                    """);
        }
    }

    /**
     * @return true если строка вставлена, false если дубликат по {@code dedup_key}
     */
    public boolean insertIfAbsent(TradeRecord t) throws SQLException {
        Objects.requireNonNull(t, "trade");
        String key = dedupKey(t);
        String sql = """
                INSERT INTO trade_fact (dedup_key, source, class_code, sec_code, trade_num, order_num, flags, qty, price, value_rub, raw_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, t.source());
            ps.setString(3, emptyToNull(t.classCode()));
            ps.setString(4, emptyToNull(t.secCode()));
            ps.setString(5, t.tradeNum());
            ps.setString(6, emptyToNull(t.orderNum()));
            if (t.flags() != null) {
                ps.setLong(7, t.flags());
            } else {
                ps.setObject(7, null);
            }
            ps.setObject(8, t.qty());
            ps.setObject(9, t.price());
            ps.setObject(10, t.valueRub());
            ps.setString(11, t.rawJson());
            ps.executeUpdate();
            return true;
        } catch (JdbcSQLIntegrityConstraintViolationException e) {
            return false;
        }
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
