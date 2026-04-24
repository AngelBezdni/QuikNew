package org.example.scripts;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.example.quik.dto.QuikMessage;
import org.example.quik.rpc.QuikRpcClient;

import java.io.IOException;

/**
 * Штатная команда QUIK# {@code get_trades}: пустой {@code data} — все строки таблицы «Сделки».
 */
public final class GetTradesScript {

    private final QuikRpcClient rpc;

    public GetTradesScript(QuikRpcClient rpc) {
        this.rpc = rpc;
    }

    public QuikMessage runAll() throws IOException {
        QuikMessage req = new QuikMessage();
        req.setCmd("get_trades");
        req.setData(JsonNodeFactory.instance.textNode(""));
        return rpc.invoke(req);
    }
}
