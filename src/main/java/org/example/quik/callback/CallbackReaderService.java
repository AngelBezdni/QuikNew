package org.example.quik.callback;

import org.example.quik.dto.QuikMessage;
import org.example.quik.session.QuikSharpSession;
import org.example.quik.transport.JsonLineCodec;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Фоновое чтение callback-сокета (OnOrder, OnTransReply и т.д. из qscallbacks.lua).
 */
public final class CallbackReaderService implements AutoCloseable {

    private final QuikSharpSession session;
    private final Consumer<QuikMessage> consumer;
    private final JsonLineCodec codec = new JsonLineCodec();
    private volatile Thread thread;
    private volatile boolean running;

    public CallbackReaderService(QuikSharpSession session, Consumer<QuikMessage> consumer) {
        this.session = Objects.requireNonNull(session, "session");
        this.consumer = Objects.requireNonNull(consumer, "consumer");
    }

    public void start(String threadName) {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::runLoop, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void runLoop() {
        while (running) {
            try {
                QuikMessage msg = codec.read(session.callbackReader());
                consumer.accept(msg);
            } catch (IOException e) {
                if (running) {
                    consumer.accept(errorMessage(e));
                }
                break;
            }
        }
    }

    private static QuikMessage errorMessage(IOException e) {
        QuikMessage m = new QuikMessage();
        m.setCmd("java_callback_io_error");
        m.setLuaError(e.getMessage());
        return m;
    }

    @Override
    public void close() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }
}
