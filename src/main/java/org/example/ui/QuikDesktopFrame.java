package org.example.ui;

import org.example.quik.config.ConnectionSettings;
import org.example.quik.dto.QuikMessage;
import org.example.quik.json.QuikJson;
import org.example.quik.rpc.QuikRpcClient;
import org.example.quik.session.QuikSharpSession;
import org.example.scripts.GetTradesByUidScript;
import org.example.scripts.LogCallbackScript;
import org.example.scripts.PingScript;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Минимальный UI: только Ping и выборка всех сделок по UID (по умолчанию 115).
 */
public final class QuikDesktopFrame extends JFrame {

    private final H2QuikRepository repository;
    private final List<JButton> remoteActionButtons = new ArrayList<>();

    private final JTextField hostField = new JTextField("127.0.0.1", 12);
    private final JTextField responsePortField = new JTextField("34130", 6);
    private final JTextField callbackPortField = new JTextField("34131", 6);

    /** UID для фильтра сделок (см. {@link GetTradesByUidScript#DEFAULT_UID}). */
    private final JTextField uidField = new JTextField(String.valueOf(GetTradesByUidScript.DEFAULT_UID), 8);

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

        setPreferredSize(new Dimension(880, 620));
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
        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        actions.setBorder(BorderFactory.createTitledBorder("Действия"));
        registerAction("Ping", () -> runRpc("ping", "", new PingScript(rpcRef.get()).run()));
        registerAction("Сделки по UID (все из таблицы «Сделки»)", () -> {
            long uid = Long.parseLong(uidField.getText().trim());
            String req = "uid|" + uid;
            QuikMessage resp = new GetTradesByUidScript(rpcRef.get()).run(uid);
            return runRpc("get_trades_filtered", req, resp);
        });
        for (JButton b : remoteActionButtons) {
            actions.add(b);
            actions.add(Box.createVerticalStrut(6));
        }
        actions.add(Box.createVerticalGlue());

        JPanel params = new JPanel();
        params.setLayout(new BoxLayout(params, BoxLayout.Y_AXIS));
        params.setBorder(BorderFactory.createTitledBorder("Параметр"));
        params.add(new JLabel("UID (число, по умолчанию " + GetTradesByUidScript.DEFAULT_UID + "):"));
        params.add(uidField);
        params.add(Box.createVerticalStrut(8));
        JLabel hint = new JLabel("<html><body style='width:280px'>Нужна команда <b>get_trades_filtered</b> в <code>qsfunctions.lua</code> на стороне QUIK. После обновления Lua перезапустите скрипт в терминале.</body></html>");
        hint.setFont(hint.getFont().deriveFont(11f));
        params.add(hint);

        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        callbackArea.setEditable(false);
        callbackArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Ответ RPC", new JScrollPane(resultArea));
        tabs.addTab("Колбеки", new JScrollPane(callbackArea));

        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, params, tabs);
        vertical.setResizeWeight(0.18);
        JSplitPane horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, actions, vertical);
        horizontal.setResizeWeight(0.28);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(horizontal, BorderLayout.CENTER);
        return wrap;
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
                        if ("lua_error".equals(msg.getCmd()) || msg.getLuaError() != null) {
                            JOptionPane.showMessageDialog(QuikDesktopFrame.this,
                                    "Ответ с ошибкой Lua:\n" + (msg.getLuaError() != null ? msg.getLuaError() : pretty),
                                    "QUIK#",
                                    JOptionPane.WARNING_MESSAGE);
                        }
                        appendResultSummary(msg, pretty);
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

    private void appendResultSummary(QuikMessage msg, String prettyJson) {
        StringBuilder head = new StringBuilder();
        if (msg.getData() != null && msg.getData().isArray()) {
            head.append("// Найдено сделок: ").append(msg.getData().size()).append("\n");
        }
        resultArea.setText(head + prettyJson);
        resultArea.setCaretPosition(0);
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
                    resultArea.setText("Подключено. Доступны: Ping и сделки по UID.\nОтветы и колбеки пишутся в H2.");
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
