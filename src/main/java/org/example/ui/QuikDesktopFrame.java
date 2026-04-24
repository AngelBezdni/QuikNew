package org.example.ui;

import org.example.quik.config.ConnectionSettings;
import org.example.quik.dto.QuikMessage;
import org.example.quik.json.QuikJson;
import org.example.quik.rpc.QuikRpcClient;
import org.example.quik.session.QuikSharpSession;
import org.example.scripts.GetTradesFilteredScript;
import org.example.scripts.IsConnectedScript;
import org.example.scripts.LogCallbackScript;
import org.example.scripts.PingScript;
import org.example.scripts.SendTransactionScript;
import org.example.storage.H2QuikRepository;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Swing UI: подключение к QUIK#, отдельные кнопки под каждый сценарий, вывод JSON и запись в локальный H2.
 */
public final class QuikDesktopFrame extends JFrame {

    private final H2QuikRepository repository;
    private final List<JButton> remoteActionButtons = new ArrayList<>();

    private final JTextField hostField = new JTextField("127.0.0.1", 12);
    private final JTextField responsePortField = new JTextField("34130", 6);
    private final JTextField callbackPortField = new JTextField("34131", 6);

    private final JTextArea transactionArea = new JTextArea(4, 40);
    private final JTextField uidField = new JTextField(12);
    private final JTextField orderNumField = new JTextField(12);
    private final JTextField classField = new JTextField("TQBR", 8);
    private final JTextField secField = new JTextField("SBER", 8);
    private final JTextField rawFilterField = new JTextField("uid|0", 24);

    private final JTextArea resultArea = new JTextArea();
    private final JTextArea callbackArea = new JTextArea();

    private final JButton connectBtn = new JButton("Подключиться");
    private final JButton disconnectBtn = new JButton("Отключиться");

    private final AtomicReference<QuikSharpSession> sessionRef = new AtomicReference<>();
    private final AtomicReference<QuikRpcClient> rpcRef = new AtomicReference<>();
    private LogCallbackScript callbackScript;

    public QuikDesktopFrame(String[] startupArgs) throws Exception {
        super("QUIK# клиент");
        this.repository = new H2QuikRepository();
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                shutdown();
            }
        });

        applyStartupArgs(startupArgs);

        setLayout(new BorderLayout(8, 8));
        add(buildNorthPanel(), BorderLayout.NORTH);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildSouthPanel(), BorderLayout.SOUTH);

        disconnectBtn.setEnabled(false);
        setRemoteButtonsEnabled(false);

        setPreferredSize(new Dimension(980, 720));
        pack();
        setLocationRelativeTo(null);
    }

    private void applyStartupArgs(String[] args) {
        if (args == null || args.length < 2) {
            return;
        }
        hostField.setText(args[0]);
        responsePortField.setText(args[1]);
        if (args.length >= 3) {
            callbackPortField.setText(args[2]);
        } else {
            try {
                int r = Integer.parseInt(args[1].trim());
                callbackPortField.setText(String.valueOf(r + 1));
            } catch (NumberFormatException ignored) {
                // оставить поле как есть
            }
        }
    }

    private JPanel buildNorthPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.setBorder(BorderFactory.createTitledBorder("Соединение с Lua QUIK#"));
        p.add(new JLabel("Хост:"));
        p.add(hostField);
        p.add(new JLabel("Порт response:"));
        p.add(responsePortField);
        p.add(new JLabel("Порт callback:"));
        p.add(callbackPortField);
        p.add(connectBtn);
        p.add(disconnectBtn);
        connectBtn.addActionListener(e -> connect());
        disconnectBtn.addActionListener(e -> disconnect());
        return p;
    }

    private JPanel buildCenterPanel() {
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(BorderFactory.createTitledBorder("Вызовы"));
        registerAction("Ping", () -> runRpc("ping", "", new PingScript(rpcRef.get()).run()));
        registerAction("isConnected", () -> runRpc("isConnected", "", new IsConnectedScript(rpcRef.get()).run()));
        registerAction("sendTransaction", () -> {
            String body = transactionArea.getText().trim();
            return runRpc("sendTransaction", body, new SendTransactionScript(rpcRef.get()).run(body));
        });
        registerAction("Сделки по UID", () -> {
            long uid = Long.parseLong(uidField.getText().trim());
            return runRpc("get_trades_filtered", "uid|" + uid, new GetTradesFilteredScript(rpcRef.get()).runByUid(uid));
        });
        registerAction("Сделки по фильтру (строка)", () -> {
            String f = rawFilterField.getText().trim();
            return runRpc("get_trades_filtered", f, new GetTradesFilteredScript(rpcRef.get()).run(f));
        });
        registerAction("Сделки по номеру заявки", () -> {
            long n = Long.parseLong(orderNumField.getText().trim());
            return runRpc("get_trades_filtered", "order_num|" + n, new GetTradesFilteredScript(rpcRef.get()).runByOrderNum(n));
        });
        registerAction("Сделки class|sec", () -> {
            String cc = classField.getText().trim();
            String sc = secField.getText().trim();
            String req = "class_sec|" + cc + "|" + sc;
            return runRpc("get_trades_filtered", req, new GetTradesFilteredScript(rpcRef.get()).runByClassAndSec(cc, sc));
        });
        for (JButton b : remoteActionButtons) {
            left.add(b);
            left.add(Box.createVerticalStrut(4));
        }
        left.add(Box.createVerticalGlue());

        JPanel params = new JPanel();
        params.setLayout(new BoxLayout(params, BoxLayout.Y_AXIS));
        params.setBorder(BorderFactory.createTitledBorder("Параметры"));
        params.add(new JLabel("Транзакция (строка KEY=VALUE;...):"));
        transactionArea.setLineWrap(true);
        params.add(new JScrollPane(transactionArea));
        params.add(Box.createVerticalStrut(6));
        params.add(labeledRow("UID (сделки):", uidField));
        params.add(Box.createVerticalStrut(4));
        params.add(labeledRow("Номер заявки:", orderNumField));
        params.add(Box.createVerticalStrut(4));
        params.add(labeledRow("Класс:", classField));
        params.add(labeledRow("Бумага:", secField));
        params.add(Box.createVerticalStrut(4));
        params.add(new JLabel("Сырой фильтр (uid|… / order_num|… / class_sec|…):"));
        params.add(rawFilterField);

        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        callbackArea.setEditable(false);
        callbackArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Ответ RPC", new JScrollPane(resultArea));
        tabs.addTab("Колбеки", new JScrollPane(callbackArea));

        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, params, tabs);
        vertical.setResizeWeight(0.32);
        JSplitPane horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, vertical);
        horizontal.setResizeWeight(0.24);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(horizontal, BorderLayout.CENTER);
        return wrap;
    }

    private static JPanel labeledRow(String title, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(new JLabel(title), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private void registerAction(String title, RpcRunnable action) {
        JButton b = new JButton(title);
        remoteActionButtons.add(b);
        b.addActionListener(e -> {
            if (rpcRef.get() == null) {
                JOptionPane.showMessageDialog(this, "Сначала подключитесь к QUIK#.", "Нет соединения", JOptionPane.WARNING_MESSAGE);
                return;
            }
            b.setEnabled(false);
            new SwingWorker<QuikMessage, Void>() {
                @Override
                protected QuikMessage doInBackground() throws Exception {
                    return action.run();
                }

                @Override
                protected void done() {
                    b.setEnabled(true);
                    try {
                        QuikMessage msg = get();
                        String pretty = QuikJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(msg);
                        resultArea.setText(pretty);
                        resultArea.setCaretPosition(0);
                    } catch (ExecutionException ex) {
                        Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                        resultArea.setText(stackTrace(c));
                    } catch (Exception ex) {
                        resultArea.setText(stackTrace(ex));
                    }
                }
            }.execute();
        });
    }

    private JPanel buildSouthPanel() {
        JLabel db = new JLabel("H2 (файл на диске): " + repository.jdbcUrl());
        db.setFont(db.getFont().deriveFont(11f));
        JPanel p = new JPanel(new BorderLayout());
        p.add(db, BorderLayout.WEST);
        return p;
    }

    private QuikMessage runRpc(String operation, String requestPayload, QuikMessage response) throws IOException, SQLException {
        repository.saveRpc(operation, requestPayload, response);
        return response;
    }

    private void connect() {
        connectBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                ConnectionSettings settings = readSettings();
                QuikSharpSession session;
                try {
                    session = QuikSharpSession.open(settings);
                } catch (ConnectException e) {
                    throw new IOException("Отказ в подключении. Запущен ли скрипт QUIK# и верны ли порты?", e);
                }
                QuikRpcClient rpc = new QuikRpcClient(session);
                sessionRef.set(session);
                rpcRef.set(rpc);
                callbackScript = new LogCallbackScript(session, msg -> {
                    try {
                        repository.saveCallback(msg);
                        String line = QuikJson.mapper().writeValueAsString(msg) + "\n";
                        SwingUtilities.invokeLater(() -> {
                            callbackArea.append(line);
                            callbackArea.setCaretPosition(callbackArea.getDocument().getLength());
                        });
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> callbackArea.append("[ошибка записи БД] " + ex.getMessage() + "\n"));
                    }
                });
                callbackScript.start();
                return null;
            }

            @Override
            protected void done() {
                connectBtn.setEnabled(true);
                try {
                    get();
                    connectBtn.setEnabled(false);
                    disconnectBtn.setEnabled(true);
                    setRemoteButtonsEnabled(true);
                    resultArea.setText("Подключено.\nОтветы RPC и колбеки сохраняются в H2 (~/.quikclient/quik.mv.db).");
                } catch (Exception e) {
                    sessionRef.set(null);
                    rpcRef.set(null);
                    JOptionPane.showMessageDialog(QuikDesktopFrame.this, stackTrace(e), "Ошибка подключения", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void disconnect() {
        disconnectBtn.setEnabled(false);
        LogCallbackScript cb = callbackScript;
        callbackScript = null;
        if (cb != null) {
            cb.close();
        }
        QuikSharpSession s = sessionRef.getAndSet(null);
        rpcRef.set(null);
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
        connectBtn.setEnabled(true);
        setRemoteButtonsEnabled(false);
        resultArea.setText("Отключено.");
    }

    private void setRemoteButtonsEnabled(boolean on) {
        for (JButton b : remoteActionButtons) {
            b.setEnabled(on);
        }
    }

    private ConnectionSettings readSettings() {
        String host = hostField.getText().trim();
        int rp = Integer.parseInt(responsePortField.getText().trim());
        int cp = Integer.parseInt(callbackPortField.getText().trim());
        return new ConnectionSettings(host, rp, cp, 15_000, 120_000);
    }

    private void shutdown() {
        try {
            disconnect();
            repository.close();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, stackTrace(e), "Ошибка при закрытии", JOptionPane.ERROR_MESSAGE);
        }
        dispose();
    }

    private static String stackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    @FunctionalInterface
    private interface RpcRunnable {
        QuikMessage run() throws Exception;
    }
}
