package org.example.quik;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Один JSON на строку, завершение {@code \n}. Потоковое чтение длинной строки.
 * <p>
 * Максимальная длина строки по умолчанию — {@value #DEFAULT_MAX_JSON_LINE_CHARS} символов.
 * Для ещё больших ответов (например, огромный {@code get_trades}) задайте
 * свойство JVM {@code quik.max.json.chars} (целое число символов; верхняя граница около {@code Integer.MAX_VALUE}).
 */
public final class QuikLineProtocol {

    /** Лимит по умолчанию: ~200 МБ текста в одной строке UTF-16 (учтите память JVM: {@code -Xmx}). */
    public static final int DEFAULT_MAX_JSON_LINE_CHARS = 200_000_000;

    private static final int MIN_MAX_JSON_LINE_CHARS = 1_000_000;

    private QuikLineProtocol() {
    }

    /**
     * Эффективный лимит длины одной JSON-строки (символов {@code char}).
     */
    public static int maxJsonLineChars() {
        String p = System.getProperty("quik.max.json.chars");
        if (p == null || p.isBlank()) {
            return DEFAULT_MAX_JSON_LINE_CHARS;
        }
        try {
            int v = Integer.parseInt(p.trim());
            int cap = Integer.MAX_VALUE - 1024;
            return Math.max(MIN_MAX_JSON_LINE_CHARS, Math.min(v, cap));
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_JSON_LINE_CHARS;
        }
    }

    public static Socket connect(String host, int port, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        socket.setSoTimeout(readTimeoutMs);
        return socket;
    }

    public static void writeJsonLine(Socket socket, String jsonLine) throws IOException {
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        out.write(jsonLine);
        out.write('\n');
        out.flush();
    }

    public static String readJsonLineStreaming(Socket socket) throws IOException {
        Reader in = new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8);
        return readJsonLineStreaming(in);
    }

    public static String readJsonLineStreaming(Reader in) throws IOException {
        int maxChars = maxJsonLineChars();
        StringBuilder sb = new StringBuilder(64 * 1024);
        char[] buf = new char[4096];
        while (true) {
            int n = in.read(buf);
            if (n == -1) {
                if (sb.isEmpty()) {
                    throw new IOException("Соединение закрыто без данных.");
                }
                break;
            }
            for (int i = 0; i < n; i++) {
                char c = buf[i];
                if (c == '\n') {
                    return sb.toString();
                }
                if (c != '\r') {
                    sb.append(c);
                }
                if (sb.length() > maxChars) {
                    throw new IOException("Строка ответа слишком большая (>" + maxChars + " символов). "
                            + "Увеличьте лимит: -Dquik.max.json.chars=<число> и при необходимости -Xmx для кучи JVM.");
                }
            }
        }
        return sb.toString();
    }
}
