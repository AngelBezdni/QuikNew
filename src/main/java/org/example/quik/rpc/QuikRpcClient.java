package org.example.quik.rpc;

import org.example.quik.dto.QuikMessage;
import org.example.quik.session.QuikSharpSession;
import org.example.quik.transport.JsonLineCodec;

import java.io.IOException;

/**
 * Синхронный RPC по сокету response: один поток — одна пара запрос/ответ (qsutils.sendResponse после dispatch).
 */
public final class QuikRpcClient {

    private final QuikSharpSession session;
    private final JsonLineCodec codec;

    public QuikRpcClient(QuikSharpSession session) {
        this.session = session;
        this.codec = session.codec();
    }

    public synchronized QuikMessage invoke(QuikMessage request) throws IOException {
        codec.write(session.responseWriter(), request);
        return codec.read(session.responseReader());
    }
}
