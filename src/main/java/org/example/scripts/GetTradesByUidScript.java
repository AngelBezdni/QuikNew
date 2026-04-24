package org.example.scripts;

import org.example.quik.dto.QuikMessage;
import org.example.quik.rpc.QuikRpcClient;

import java.io.IOException;

/**
 * Все строки таблицы «Сделки» QUIK, у которых в полях UID совпадает заданное значение (команда {@code get_trades_filtered} с {@code uid|…}).
 * На стороне QUIK должна быть актуальная {@code qsfunctions.lua} с {@code get_trades_filtered}.
 */
public final class GetTradesByUidScript {

    public static final long DEFAULT_UID = 115L;

    private final GetTradesFilteredScript delegate;

    public GetTradesByUidScript(QuikRpcClient rpc) {
        this.delegate = new GetTradesFilteredScript(rpc);
    }

    /** Все сделки по UID (число в строке фильтра {@code uid|&lt;uid&gt;}). */
    public QuikMessage run(long uid) throws IOException {
        return delegate.runByUid(uid);
    }

    public QuikMessage runDefault() throws IOException {
        return run(DEFAULT_UID);
    }
}
