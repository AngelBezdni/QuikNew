package org.example.quik.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.quik.dto.QuikMessage;
import org.example.quik.json.QuikJson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Одна строка — один JSON (как {@code to_json(...)..'\n'} на стороне Lua).
 */
public final class JsonLineCodec {

    private final ObjectMapper mapper = QuikJson.mapper();

    public void write(BufferedWriter writer, QuikMessage message) throws IOException {
        String line = mapper.writeValueAsString(message);
        writer.write(line);
        writer.write('\n');
        writer.flush();
    }

    public QuikMessage read(BufferedReader reader) throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Поток ответа закрыт (EOF до JSON)");
        }
        try {
            return mapper.readValue(line, QuikMessage.class);
        } catch (JsonProcessingException e) {
            throw new IOException("Некорректный JSON в строке: " + line, e);
        }
    }

    public static BufferedWriter utf8Writer(java.io.OutputStream out) {
        return new BufferedWriter(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    public static BufferedReader utf8Reader(java.io.InputStream in) {
        return new BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
