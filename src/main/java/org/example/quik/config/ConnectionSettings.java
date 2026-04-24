package org.example.quik.config;

import java.util.Objects;

/**
 * Параметры TCP, совпадающие с {@code config.json} / дефолтами QUIK# (QuikSharp.lua).
 * Клиент подключается к уже запущенному Lua: сначала response, затем callback.
 */
public final class ConnectionSettings {

    private final String host;
    private final int responsePort;
    private final int callbackPort;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public ConnectionSettings(
            String host,
            int responsePort,
            int callbackPort,
            int connectTimeoutMs,
            int readTimeoutMs) {
        this.host = Objects.requireNonNull(host, "host");
        this.responsePort = responsePort;
        this.callbackPort = callbackPort;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public static ConnectionSettings localhostDefaults() {
        return new ConnectionSettings("127.0.0.1", 34130, 34131, 15_000, 120_000);
    }

    public String host() {
        return host;
    }

    public int responsePort() {
        return responsePort;
    }

    public int callbackPort() {
        return callbackPort;
    }

    public int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int readTimeoutMs() {
        return readTimeoutMs;
    }
}
