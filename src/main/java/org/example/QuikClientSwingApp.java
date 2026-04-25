package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.quik.ConnSettings;
import org.example.quik.QuikSocketPair;
import org.example.storage.H2TradeStore;
import org.example.summary.SecSummaryService;
import org.example.summary.SummaryTableModel;
import org.example.sync.TradeBootstrap;
import org.example.sync.TradeCallbackListener;
import org.example.trade.TradeFilterSpec;
import org.example.trade.TradeRecord;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.sql.SQLException;
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
    private final JTextField clientCodeField = new JTextField("TIL000000001", 12);
    private final JTextField uidField = new JTextField("115", 12);
    private final JTextField firmIdField = new JTextField("", 12);
    private final JTextField orderNumField = new JTextField("", 12);
    private final JTextField trdAccField = new JTextField("", 12);

    private final JTextArea logArea = new JTextArea();
    private final JButton pingButton = new JButton("Ping");
    private final JButton syncButton = new JButton("Синхронизация сделок");
    private final JButton stopLiveButton = new JButton("Стоп live");
    private final JButton clearTableButton = new JButton("Очистить таблицу");
    private final JLabel liveStatusLabel = new JLabel("LIVE: OFF");
    private final JLabel liveCountersLabel = new JLabel("handled=0, inserted=0, dedup=0, filtered=0, parseErr=0");

    private final SummaryTableModel summaryModel = new SummaryTableModel();
    private final SecSummaryService summaryService = new SecSummaryService(summaryModel);
    private final JTable summaryTable = new JTable(summaryModel);

    private H2TradeStore liveStore;
    private QuikSocketPair livePair;
    private TradeCallbackListener liveListener;
    private Thread liveThread;
    private Timer uiStatsTimer;

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
        buttons.add(stopLiveButton);
        buttons.add(clearTableButton);
        stopLiveButton.setEnabled(false);
        root.add(buttons);

        JPanel liveInfo = new JPanel();
        liveInfo.add(liveStatusLabel);
        liveInfo.add(liveCountersLabel);
        root.add(liveInfo);

        logArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(860, 260));
        JScrollPane summaryScroll = new JScrollPane(summaryTable);
        summaryScroll.setPreferredSize(new Dimension(860, 220));
        summaryTable.setFillsViewportHeight(true);

        frame.add(root, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(summaryScroll, BorderLayout.CENTER);
        center.add(scroll, BorderLayout.SOUTH);
        frame.add(center, BorderLayout.CENTER);
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
            runSyncAndStartLive();
        }));

        stopLiveButton.addActionListener(e -> runAsync("stop-live", this::stopLive));
        clearTableButton.addActionListener(e -> runAsync("clear-table", this::clearTradeTable));
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
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    log("Ошибка: " + c.getMessage());
                } finally {
                    pingButton.setEnabled(true);
                    syncButton.setEnabled(true);
                    clearTableButton.setEnabled(true);
                    stopLiveButton.setEnabled(liveThread != null && liveThread.isAlive());
                    log("Операция завершена: " + op);
                }
            }
        }.execute();
    }

    private void runSyncAndStartLive() throws Exception {
        stopLive();
        TradeFilterSpec liveFilterSpec = buildFilterSpec();
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

        ConnSettings s = ConnSettings.fromArgs(connArgs, ConnSettings.DEFAULT_READ_TIMEOUT_TRADES_MS, 2);
        liveStore = H2TradeStore.fromPropertyOrDefault();
        liveStore.init();
        summaryService.loadInitial(liveStore);

        livePair = QuikSocketPair.open(s);
        liveListener = new TradeCallbackListener(livePair.callback(), liveStore, this::onInsertedTrade, liveFilterSpec);
        liveThread = new Thread(liveListener, "quik-ontrade-ui");
        liveThread.setDaemon(true);
        liveThread.start();
        startUiStatsTimer();
        liveStatusLabel.setText("LIVE: ON");
        log("Live OnTrade запущен.");
    }

    private void onInsertedTrade(TradeRecord t) {
        summaryService.onInsertedTrade(t);
        log("OnTrade inserted: sec=" + safe(t.secCode()) + ", num=" + safe(t.tradeNum()));
    }

    private void stopLive() throws Exception {
        stopUiStatsTimer();
        if (liveListener != null) {
            liveListener.requestStop();
        }
        if (liveThread != null) {
            try {
                liveThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (livePair != null) {
            try {
                livePair.close();
            } catch (Exception ignored) {
            }
        }
        if (liveStore != null) {
            try {
                liveStore.close();
            } catch (SQLException ignored) {
            }
        }
        liveListener = null;
        liveThread = null;
        livePair = null;
        liveStore = null;
        liveStatusLabel.setText("LIVE: OFF");
        liveCountersLabel.setText("handled=0, inserted=0, dedup=0, filtered=0, parseErr=0");
        log("Live OnTrade остановлен.");
    }

    private void clearTradeTable() throws Exception {
        boolean wasLive = liveThread != null && liveThread.isAlive();
        if (wasLive) {
            stopLive();
        }
        try (H2TradeStore store = H2TradeStore.fromPropertyOrDefault()) {
            store.init();
            int deleted = store.clearAllTrades();
            summaryService.clearAll();
            log("Таблица trade_fact очищена, удалено строк: " + deleted);
        }
        if (wasLive) {
            log("Live был остановлен перед очисткой. Нажмите 'Синхронизация сделок' для перезапуска.");
        }
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

    private TradeFilterSpec buildFilterSpec() {
        String classCode = classCodeField.getText().trim();
        String secCode = secCodeField.getText().trim();
        String clientCode = clientCodeField.getText().trim();
        String firmId = firmIdField.getText().trim();
        String trdacc = trdAccField.getText().trim();
        Long uid = parseLongOrNull(uidField.getText());
        Long orderNum = parseLongOrNull(orderNumField.getText());
        return new TradeFilterSpec(classCode, secCode, clientCode, firmId, trdacc, uid, orderNum);
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

    private static Long parseLongOrNull(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) {
            return null;
        }
        return Long.parseLong(v);
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private void startUiStatsTimer() {
        stopUiStatsTimer();
        uiStatsTimer = new Timer(500, e -> {
            TradeCallbackListener l = liveListener;
            if (l != null) {
                liveCountersLabel.setText("handled=" + l.onTradeHandled()
                        + ", inserted=" + l.inserted()
                        + ", dedup=" + l.skippedDup()
                        + ", filtered=" + l.filteredOut()
                        + ", parseErr=" + l.parseErrors());
            }
        });
        uiStatsTimer.start();
    }

    private void stopUiStatsTimer() {
        if (uiStatsTimer != null) {
            uiStatsTimer.stop();
            uiStatsTimer = null;
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
