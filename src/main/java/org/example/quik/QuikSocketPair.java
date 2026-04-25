package org.example.quik;

import java.io.IOException;
import java.net.Socket;

/**
 * Два сокета к Lua: response и callback (порядок подключения важен для QUIK#).
 */
public final class QuikSocketPair implements AutoCloseable {

    private final Socket response;
    private final Socket callback;

    public QuikSocketPair(Socket response, Socket callback) {
        this.response = response;
        this.callback = callback;
    }

    public static QuikSocketPair open(ConnSettings s) throws IOException {
        Socket response = QuikLineProtocol.connect(s.host(), s.responsePort(), s.connectTimeoutMs(), s.readTimeoutMs());
        Socket callback = QuikLineProtocol.connect(s.host(), s.callbackPort(), s.connectTimeoutMs(), 0);
        return new QuikSocketPair(response, callback);
    }

    public Socket response() {
        return response;
    }

    public Socket callback() {
        return callback;
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        try {
            response.close();
        } catch (IOException e) {
            first = e;
        }
        try {
            callback.close();
        } catch (IOException e) {
            if (first == null) {
                first = e;
            } else {
                first.addSuppressed(e);
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
