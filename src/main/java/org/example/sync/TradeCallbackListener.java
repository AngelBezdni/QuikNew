package org.example.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.quik.QuikLineProtocol;
import org.example.storage.H2TradeStore;
import org.example.trade.TradeFilterSpec;
import org.example.trade.TradeJsonMapper;
import org.example.trade.TradeRecord;

import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * Читает callback-сокет построчно; при {@code OnTrade} пишет сделку в H2.
 * <p>
 * Response-сокет должен оставаться открытым на время жизни callback (требование QUIK#).
 */
public final class TradeCallbackListener implements Runnable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Socket callbackSocket;
    private final H2TradeStore store;
    private final Consumer<TradeRecord> onInsertedTrade;
    private final TradeFilterSpec filterSpec;
    private volatile boolean running = true;
    private int onTradeHandled;
    private int inserted;
    private int skippedDup;
    private int parseErrors;
    private int filteredOut;

    public TradeCallbackListener(Socket callbackSocket, H2TradeStore store) {
        this(callbackSocket, store, null, TradeFilterSpec.empty());
    }

    public TradeCallbackListener(Socket callbackSocket, H2TradeStore store, Consumer<TradeRecord> onInsertedTrade) {
        this(callbackSocket, store, onInsertedTrade, TradeFilterSpec.empty());
    }

    public TradeCallbackListener(Socket callbackSocket, H2TradeStore store,
                                 Consumer<TradeRecord> onInsertedTrade,
                                 TradeFilterSpec filterSpec) {
        this.callbackSocket = callbackSocket;
        this.store = store;
        this.onInsertedTrade = onInsertedTrade;
        this.filterSpec = filterSpec == null ? TradeFilterSpec.empty() : filterSpec;
    }

    public void requestStop() {
        running = false;
        try {
            callbackSocket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run() {
        try {
            Reader callbackReader = new InputStreamReader(callbackSocket.getInputStream(), StandardCharsets.UTF_8);
            while (running) {
                String line;
                try {
                    line = QuikLineProtocol.readJsonLineStreaming(callbackReader);
                } catch (IOException e) {
                    if (!running) {
                        break;
                    }
                    throw new RuntimeException("Ошибка чтения callback", e);
                }
                JsonNode root;
                try {
                    root = MAPPER.readTree(line);
                } catch (JsonProcessingException e) {
                    parseErrors++;
                    continue;
                }
                String cmd = text(root, "cmd");
                if (!"OnTrade".equalsIgnoreCase(cmd)) {
                    continue;
                }
                JsonNode data = root.get("data");
                TradeRecord tr = TradeJsonMapper.fromNode("ontrade", data).orElse(null);
                if (tr == null) {
                    continue;
                }
                if (!filterSpec.matches(tr)) {
                    filteredOut++;
                    continue;
                }
                onTradeHandled++;
                try {
                    if (store.insertIfAbsent(tr)) {
                        inserted++;
                        if (onInsertedTrade != null) {
                            onInsertedTrade.accept(tr);
                        }
                    } else {
                        skippedDup++;
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Ошибка записи OnTrade в H2", e);
                }
            }
        } catch (IOException e) {
            if (running) {
                throw new RuntimeException("Чтение callback-сокета прервано", e);
            }
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    public int onTradeHandled() {
        return onTradeHandled;
    }

    public int inserted() {
        return inserted;
    }

    public int skippedDup() {
        return skippedDup;
    }

    public int parseErrors() {
        return parseErrors;
    }

    public int filteredOut() {
        return filteredOut;
    }
}
