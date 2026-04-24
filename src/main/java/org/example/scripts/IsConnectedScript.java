package org.example.scripts;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.example.quik.dto.QuikMessage;
import org.example.quik.rpc.QuikRpcClient;

import java.io.IOException;

/**
 * Состояние связи терминала с сервером QUIK (qsfunctions.isConnected).
 */
public final class IsConnectedScript {

    private final QuikRpcClient rpc;

    public IsConnectedScript(QuikRpcClient rpc) {
        this.rpc = rpc;
    }

    public QuikMessage run() throws IOException {
        QuikMessage req = new QuikMessage();
        req.setCmd("isConnected");
        req.setData(JsonNodeFactory.instance.textNode(""));
        return rpc.invoke(req);
    }
}
