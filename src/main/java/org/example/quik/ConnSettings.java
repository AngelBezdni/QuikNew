package org.example.quik;

/**
 * Параметры TCP к Lua-серверу QUIK# (response + callback).
 */
public final class ConnSettings {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_RESPONSE_PORT = 34130;
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 60_000;

    private final String host;
    private final int responsePort;
    private final int callbackPort;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final int attempts;

    public ConnSettings(String host, int responsePort, int callbackPort,
                        int connectTimeoutMs, int readTimeoutMs, int attempts) {
        this.host = host;
        this.responsePort = responsePort;
        this.callbackPort = callbackPort;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.attempts = Math.max(1, attempts);
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

    public int attempts() {
        return attempts;
    }

    /**
     * args: host [response] [callback] [readTimeout] [connectTimeout] [attempts]
     */
    public static ConnSettings fromArgs(String[] args, int defaultReadTimeoutMs, int defaultAttempts) {
        if (args == null || args.length == 0) {
            return new ConnSettings(
                    DEFAULT_HOST,
                    DEFAULT_RESPONSE_PORT,
                    DEFAULT_RESPONSE_PORT + 1,
                    DEFAULT_CONNECT_TIMEOUT_MS,
                    defaultReadTimeoutMs,
                    defaultAttempts);
        }
        String host = args[0];
        int response = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_RESPONSE_PORT;
        int callback = args.length >= 3 ? Integer.parseInt(args[2]) : response + 1;
        int readTimeout = args.length >= 4 ? Integer.parseInt(args[3]) : defaultReadTimeoutMs;
        int connectTimeout = args.length >= 5 ? Integer.parseInt(args[4]) : DEFAULT_CONNECT_TIMEOUT_MS;
        int attempts = args.length >= 6 ? Integer.parseInt(args[5]) : defaultAttempts;
        return new ConnSettings(host, response, callback, connectTimeout, readTimeout, attempts);
    }
}
