package org.example.quik.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Конверт сообщения QUIK#: поля {@code cmd} и {@code data} соответствуют таблице Lua в qsfunctions.dispatch_and_process.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuikMessage {

    private String cmd;
    private JsonNode data;
    private Long id;
    private Double t;
    @JsonProperty("lua_error")
    private String luaError;

    public QuikMessage() {
    }

    public QuikMessage(String cmd, JsonNode data) {
        this.cmd = cmd;
        this.data = data;
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getT() {
        return t;
    }

    public void setT(Double t) {
        this.t = t;
    }

    public String getLuaError() {
        return luaError;
    }

    public void setLuaError(String luaError) {
        this.luaError = luaError;
    }
}
