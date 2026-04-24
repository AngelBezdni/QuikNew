package org.example.scripts;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.example.quik.dto.QuikMessage;
import org.example.quik.rpc.QuikRpcClient;

import java.io.IOException;

/**
 * Все сделки из таблицы «Сделки» QUIK по фильтру (команда Lua {@code get_trades_filtered}).
 * <p>
 * Формат строки фильтра (как в {@code qsfunctions.get_trades_filtered}):
 * <ul>
 *   <li>{@code uid|&lt;число&gt;} — по {@code on_behalf_of_uid} (или {@code userid}, если есть)</li>
 *   <li>{@code order_num|&lt;номер заявки&gt;}</li>
 *   <li>{@code class_sec|&lt;CLASS&gt;|&lt;SEC&gt;} — например {@code class_sec|TQBR|SBER}</li>
 * </ul>
 * На стороне QUIK нужна обновлённая {@code qsfunctions.lua} с этой командой.
 */
public final class GetTradesFilteredScript {

    private final QuikRpcClient rpc;

    public GetTradesFilteredScript(QuikRpcClient rpc) {
        this.rpc = rpc;
    }

    public QuikMessage run(String filterExpression) throws IOException {
        QuikMessage req = new QuikMessage();
        req.setCmd("get_trades_filtered");
        req.setData(JsonNodeFactory.instance.textNode(filterExpression));
        return rpc.invoke(req);
    }

    /** Сделки по UID пользователя (поле сделки в терминале QUIK 8+ — преимущественно {@code on_behalf_of_uid}). */
    public QuikMessage runByUid(long uid) throws IOException {
        return run("uid|" + uid);
    }

    public QuikMessage runByOrderNum(long orderNum) throws IOException {
        return run("order_num|" + orderNum);
    }

    public QuikMessage runByClassAndSec(String classCode, String secCode) throws IOException {
        return run("class_sec|" + classCode + "|" + secCode);
    }
}
