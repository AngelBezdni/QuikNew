package org.example.quik.session;

import org.example.quik.config.ConnectionSettings;
import org.example.quik.transport.JsonLineCodec;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Две независимые TCP-сессии, как в qsutils.connect: response (запрос/ответ) и callback (пуши из QUIK).
 * Порядок подключения важен: сначала response, затем callback — иначе Lua зависнет на accept.
 */
public final class QuikSharpSession implements AutoCloseable {

    private final Socket responseSocket;
    private final Socket callbackSocket;
    private final BufferedWriter responseOut;
    private final BufferedReader responseIn;
    private final BufferedWriter callbackOut;
    private final BufferedReader callbackIn;
    private final JsonLineCodec codec = new JsonLineCodec();

    private QuikSharpSession(
            Socket responseSocket,
            Socket callbackSocket,
            BufferedWriter responseOut,
            BufferedReader responseIn,
            BufferedWriter callbackOut,
            BufferedReader callbackIn) {
        this.responseSocket = responseSocket;
        this.callbackSocket = callbackSocket;
        this.responseOut = responseOut;
        this.responseIn = responseIn;
        this.callbackOut = callbackOut;
        this.callbackIn = callbackIn;
    }

    public static QuikSharpSession open(ConnectionSettings settings) throws IOException {
        Socket response = new Socket();
        response.connect(
                new InetSocketAddress(settings.host(), settings.responsePort()),
                settings.connectTimeoutMs());
        response.setSoTimeout(settings.readTimeoutMs());

        Socket callback = new Socket();
        callback.connect(
                new InetSocketAddress(settings.host(), settings.callbackPort()),
                settings.connectTimeoutMs());
        callback.setSoTimeout(0);

        BufferedWriter ro = JsonLineCodec.utf8Writer(response.getOutputStream());
        BufferedReader ri = JsonLineCodec.utf8Reader(response.getInputStream());
        BufferedWriter co = JsonLineCodec.utf8Writer(callback.getOutputStream());
        BufferedReader ci = JsonLineCodec.utf8Reader(callback.getInputStream());

        return new QuikSharpSession(response, callback, ro, ri, co, ci);
    }

    public BufferedWriter responseWriter() {
        return responseOut;
    }

    public BufferedReader responseReader() {
        return responseIn;
    }

    public BufferedReader callbackReader() {
        return callbackIn;
    }

    @SuppressWarnings("unused")
    public BufferedWriter callbackWriter() {
        return callbackOut;
    }

    public JsonLineCodec codec() {
        return codec;
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        try {
            responseSocket.close();
        } catch (IOException e) {
            first = e;
        }
        try {
            callbackSocket.close();
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
