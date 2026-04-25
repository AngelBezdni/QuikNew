package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.sync.TradeBootstrap;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Небольшой UI для ручной проверки ping и bootstrap-синхронизации сделок.
 */
public final class QuikClientSwingApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final JTextField hostField = new JTextField("127.0.0.1", 16);
    private final JTextField responsePortField = new JTextField("34130", 8);
    private final JTextField callbackPortField = new JTextField("34131", 8);
    private final JTextField readTimeoutField = new JTextField("1200000", 10);
    private final JTextField connectTimeoutField = new JTextField("10000", 8);
    private final JTextField attemptsField = new JTextField("2", 4);

    private final JTextField classCodeField = new JTextField("", 10);
    private final JTextField secCodeField = new JTextField("", 10);
    private final JTextField clientCodeField = new JTextField("", 12);
    private final JTextField uidField = new JTextField("", 12);
    private final JTextField firmIdField = new JTextField("", 12);
    private final JTextField orderNumField = new JTextField("", 12);
    private final JTextField trdAccField = new JTextField("", 12);

    private final JTextArea logArea = new JTextArea();
    private final JButton pingButton = new JButton("Ping");
    private final JButton syncButton = new JButton("Синхронизация сделок");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new QuikClientSwingApp().show());
    }

    private void show() {
        JFrame frame = new JFrame("QUIK Client UI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(8, 8));

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        root.add(section("Подключение", List.of(
                field("Host", hostField),
                field("Response port", responsePortField),
                field("Callback port", callbackPortField),
                field("Read timeout ms", readTimeoutField),
                field("Connect timeout ms", connectTimeoutField),
                field("Attempts", attemptsField)
        )));

        root.add(section("Фильтр get_trades_filtered (пустые поля игнорируются)", List.of(
                field("class_code", classCodeField),
                field("sec_code", secCodeField),
                field("client_code", clientCodeField),
                field("uid", uidField),
                field("firm_id", firmIdField),
                field("order_num", orderNumField),
                field("trdacc_id", trdAccField)
        )));

        JPanel buttons = new JPanel();
        buttons.add(pingButton);
        buttons.add(syncButton);
        root.add(buttons);

        logArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(860, 260));

        frame.add(root, BorderLayout.NORTH);
        frame.add(scroll, BorderLayout.CENTER);
        bindActions();

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        log("UI запущен.");
    }

    private JPanel section(String title, List<JComponent> rows) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        for (JComponent c : rows) {
            panel.add(c);
        }
        return panel;
    }

    private JComponent field(String label, JTextField tf) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(tf, BorderLayout.CENTER);
        return row;
    }

    private void bindActions() {
        pingButton.addActionListener(e -> runAsync("ping", () -> {
            String[] connArgs = buildConnArgs();
            new LuaConnectionTest().run(connArgs);
            log("Ping: OK");
        }));

        syncButton.addActionListener(e -> runAsync("sync", () -> {
            String[] connArgs = buildConnArgs();
            String prevFilterJson = System.getProperty("quik.get_trades.filter_json");
            String prevFilterFile = System.getProperty("quik.get_trades.filter_file");
            try {
                String filterJson = buildFilterJsonOrNull();
                if (filterJson != null) {
                    System.setProperty("quik.get_trades.filter_json", filterJson);
                    System.clearProperty("quik.get_trades.filter_file");
                    log("Фильтр: " + filterJson);
                } else {
                    System.clearProperty("quik.get_trades.filter_json");
                    System.clearProperty("quik.get_trades.filter_file");
                    log("Фильтр не задан: будет mode=get_trades (все сделки).");
                }
                TradeBootstrap.BootstrapResult r = TradeSyncApp.runBootstrapOnly(connArgs);
                log("Синхронизация завершена: всего=" + r.totalRows() + ", вставлено=" + r.inserted() + ", дубликатов=" + r.skippedDuplicates());
            } finally {
                restoreProperty("quik.get_trades.filter_json", prevFilterJson);
                restoreProperty("quik.get_trades.filter_file", prevFilterFile);
            }
        }));
    }

    private void runAsync(String op, ThrowingRunnable action) {
        pingButton.setEnabled(false);
        syncButton.setEnabled(false);
        log("Старт операции: " + op);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    log("Ошибка: " + ex.getCause().getMessage());
                } finally {
                    pingButton.setEnabled(true);
                    syncButton.setEnabled(true);
                    log("Операция завершена: " + op);
                }
            }
        }.execute();
    }

    private String[] buildConnArgs() {
        return new String[]{
                hostField.getText().trim(),
                responsePortField.getText().trim(),
                callbackPortField.getText().trim(),
                readTimeoutField.getText().trim(),
                connectTimeoutField.getText().trim(),
                attemptsField.getText().trim()
        };
    }

    private String buildFilterJsonOrNull() throws Exception {
        ObjectNode n = MAPPER.createObjectNode();
        putIfNotBlank(n, "class_code", classCodeField.getText());
        putIfNotBlank(n, "sec_code", secCodeField.getText());
        putIfNotBlank(n, "client_code", clientCodeField.getText());
        putIfNotBlank(n, "firm_id", firmIdField.getText());
        putIfNotBlank(n, "trdacc_id", trdAccField.getText());

        putLongIfNotBlank(n, "uid", uidField.getText());
        putLongIfNotBlank(n, "order_num", orderNumField.getText());

        if (n.isEmpty()) {
            return null;
        }
        return MAPPER.writeValueAsString(n);
    }

    private static void putIfNotBlank(ObjectNode n, String key, String value) {
        String v = value == null ? "" : value.trim();
        if (!v.isEmpty()) {
            n.put(key, v);
        }
    }

    private static void putLongIfNotBlank(ObjectNode n, String key, String value) {
        String v = value == null ? "" : value.trim();
        if (!v.isEmpty()) {
            n.put(key, Long.parseLong(v));
        }
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private void log(String text) {
        String line = "[" + LocalDateTime.now().format(TS) + "] " + text + "\n";
        logArea.append(line);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
