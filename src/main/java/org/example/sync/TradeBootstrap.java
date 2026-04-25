package org.example.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.quik.ConnSettings;
import org.example.quik.QuikLineProtocol;
import org.example.quik.QuikSocketPair;
import org.example.storage.H2TradeStore;
import org.example.trade.TradeJsonMapper;
import org.example.trade.TradeRecord;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;

/**
 * Однократная выгрузка истории через {@code get_trades} в H2.
 */
public final class TradeBootstrap {

    private static final String GET_TRADES = "{\"cmd\":\"get_trades\",\"data\":\"\"}";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TradeBootstrap() {
    }

    public static BootstrapResult run(ConnSettings settings, H2TradeStore store) throws IOException, SQLException {
        IOException last = null;
        for (int attempt = 1; attempt <= settings.attempts(); attempt++) {
            try (QuikSocketPair pair = QuikSocketPair.open(settings)) {
                QuikLineProtocol.writeJsonLine(pair.response(), GET_TRADES);
                String line = QuikLineProtocol.readJsonLineStreaming(pair.response());
                JsonNode root = MAPPER.readTree(line);
                JsonNode data = root.get("data");
                if (data == null || !data.isArray()) {
                    throw new IOException("Ожидался массив в поле data ответа get_trades, получено: " + line.substring(0, Math.min(200, line.length())));
                }
                int n = data.size();
                int inserted = 0;
                int skippedDup = 0;
                for (int i = 0; i < n; i++) {
                    JsonNode el = data.get(i);
                    TradeRecord tr = TradeJsonMapper.fromNode("bootstrap", el).orElse(null);
                    if (tr == null) {
                        continue;
                    }
                    if (store.insertIfAbsent(tr)) {
                        inserted++;
                    } else {
                        skippedDup++;
                    }
                }
                return new BootstrapResult(n, inserted, skippedDup);
            } catch (SocketTimeoutException e) {
                last = new IOException("Timeout get_trades (попытка " + attempt + "/" + settings.attempts() + ").", e);
            } catch (ConnectException e) {
                throw new IOException("Нет слушателя на " + settings.host() + ":" + settings.responsePort()
                        + " / " + settings.callbackPort(), e);
            }
            sleepQuiet(500L * attempt);
        }
        throw last != null ? last : new IOException("get_trades: неизвестная ошибка");
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record BootstrapResult(int totalRows, int inserted, int skippedDuplicates) {
    }
}
