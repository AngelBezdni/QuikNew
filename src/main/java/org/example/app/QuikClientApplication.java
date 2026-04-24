package org.example.app;

import org.example.quik.config.ConnectionSettings;
import org.example.quik.dto.QuikMessage;
import org.example.quik.json.QuikJson;
import org.example.quik.rpc.QuikRpcClient;
import org.example.quik.session.QuikSharpSession;
import org.example.scripts.IsConnectedScript;
import org.example.scripts.LogCallbackScript;
import org.example.scripts.PingScript;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Сценарий запуска: подключение, RPC-проверки, приём колбеков. Без торговых поручений — только демонстрация канала.
 */
public final class QuikClientApplication {

    public void run(String[] args) throws IOException {
        ConnectionSettings settings = parseArgs(args);
        try (QuikSharpSession session = QuikSharpSession.open(settings);
             LogCallbackScript callbacks = new LogCallbackScript(session, this::onCallback)) {

            callbacks.start();
            QuikRpcClient rpc = new QuikRpcClient(session);

            QuikMessage pong = new PingScript(rpc).run();
            System.out.println("[ping] cmd=" + pong.getCmd() + " data=" + pong.getData());

            QuikMessage connected = new IsConnectedScript(rpc).run();
            System.out.println("[isConnected] data=" + connected.getData());

            if (pong.getLuaError() != null || connected.getLuaError() != null) {
                System.err.println("Ошибки Lua: ping=" + pong.getLuaError() + " connected=" + connected.getLuaError());
            }

            System.out.println("Подключение установлено. Колбеки пишутся в stdout. Enter — выход.");
            waitForEnter();
        }
    }

    private void onCallback(QuikMessage msg) {
        try {
            String json = QuikJson.mapper().writeValueAsString(msg);
            System.out.println("[callback] " + json);
        } catch (Exception e) {
            System.out.println("[callback] cmd=" + msg.getCmd() + " err=" + e.getMessage());
        }
    }

    private static ConnectionSettings parseArgs(String[] args) {
        if (args.length >= 2) {
            String host = args[0];
            int response = Integer.parseInt(args[1]);
            int callback = args.length >= 3 ? Integer.parseInt(args[2]) : response + 1;
            return new ConnectionSettings(host, response, callback, 15_000, 120_000);
        }
        return ConnectionSettings.localhostDefaults();
    }

    private static void waitForEnter() throws IOException {
        new BufferedReader(new InputStreamReader(System.in)).readLine();
    }
}
