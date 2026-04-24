package org.example.scripts;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.example.quik.dto.QuikMessage;
import org.example.quik.rpc.QuikRpcClient;

import java.io.IOException;

/**
 * Отправка транзакции в QUIK (qsfunctions.sendTransaction). В {@code data} передаётся строка в формате
 * справки QUIK: {@code KEY=VALUE;KEY2=VALUE2;...} — как ожидает Lua {@code sendTransaction(msg.data)}.
 */
public final class SendTransactionScript {

    private final QuikRpcClient rpc;

    public SendTransactionScript(QuikRpcClient rpc) {
        this.rpc = rpc;
    }

    public QuikMessage run(String transactionString) throws IOException {
        QuikMessage req = new QuikMessage();
        req.setCmd("sendTransaction");
        req.setData(JsonNodeFactory.instance.textNode(transactionString));
        return rpc.invoke(req);
    }
}
