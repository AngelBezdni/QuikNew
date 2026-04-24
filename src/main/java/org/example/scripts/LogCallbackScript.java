package org.example.scripts;

import org.example.quik.callback.CallbackReaderService;
import org.example.quik.dto.QuikMessage;
import org.example.quik.session.QuikSharpSession;

import java.util.function.Consumer;

/**
 * Запускает фоновое чтение callback-порта и передаёт сообщения в переданный обработчик.
 */
public final class LogCallbackScript implements AutoCloseable {

    private final CallbackReaderService service;

    public LogCallbackScript(QuikSharpSession session, Consumer<QuikMessage> sink) {
        this.service = new CallbackReaderService(session, sink);
    }

    public void start() {
        service.start("quik-callback-reader");
    }

    @Override
    public void close() {
        service.close();
    }
}
