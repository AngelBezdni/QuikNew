package org.example;

import org.example.quik.ConnSettings;
import org.example.quik.QuikSocketPair;
import org.example.storage.H2TradeStore;
import org.example.sync.TradeBootstrap;
import org.example.sync.TradeCallbackListener;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Сценарий: загрузка истории {@code get_trades} в H2, затем прослушивание {@code OnTrade}.
 */
public final class TradeSyncApp {

    private TradeSyncApp() {
    }

    public static void main(String[] args) throws Exception {
        run(args);
    }

    public static void run(String[] args) throws SQLException, IOException {
        ConnSettings conn = ConnSettings.fromArgs(args, ConnSettings.DEFAULT_READ_TIMEOUT_MS, 2);
        System.out.println("Синхронизация сделок: " + conn.host() + ":" + conn.responsePort()
                + " / " + conn.callbackPort() + " (read timeout " + conn.readTimeoutMs() + " ms)");

        try (H2TradeStore store = H2TradeStore.fromPropertyOrDefault()) {
            store.init();
            TradeBootstrap.BootstrapResult boot = TradeBootstrap.run(conn, store);
            System.out.println("Bootstrap get_trades: всего элементов=" + boot.totalRows()
                    + ", вставлено=" + boot.inserted() + ", дубликатов=" + boot.skippedDuplicates());

            try (QuikSocketPair pair = QuikSocketPair.open(conn)) {
                TradeCallbackListener listener = new TradeCallbackListener(pair.callback(), store);
                Thread t = new Thread(listener, "quik-ontrade");
                t.setDaemon(true);
                t.start();
                System.out.println("Слушатель OnTrade запущен. Нажмите Enter для выхода...");
                System.in.read();
                listener.requestStop();
                t.interrupt();
                try {
                    t.join(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("OnTrade: обработано=" + listener.onTradeHandled()
                        + ", вставлено=" + listener.inserted() + ", дубликатов=" + listener.skippedDup());
            }
        }
    }
}
