package org.example.scripts;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.example.quik.dto.QuikMessage;
import org.example.quik.rpc.QuikRpcClient;

import java.io.IOException;

/**
 * Проверка живости канала (qsfunctions.ping).
 */
public final class PingScript {

    private final QuikRpcClient rpc;

    public PingScript(QuikRpcClient rpc) {
        this.rpc = rpc;
    }

    public QuikMessage run() throws IOException {
        QuikMessage req = new QuikMessage();
        req.setCmd("ping");
        req.setData(JsonNodeFactory.instance.textNode("Ping"));
        return rpc.invoke(req);
    }
}
