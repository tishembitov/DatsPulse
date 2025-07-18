package ru.tishembitov.api;

import ru.tishembitov.model.LogMessage;
import ru.tishembitov.model.PlayerMoveCommands;
import ru.tishembitov.model.PlayerResponse;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class ApiClient {
    private final String baseUrl;
    private final String token;

    public ApiClient(String baseUrl, String token) {
        this.baseUrl = baseUrl;
        this.token = token;
    }

    private String request(String method, String endpoint, String body) throws IOException {
        URL url = new URL(baseUrl + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("X-Auth-Token", token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoInput(true);
        if (body != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining("\n"));
        }
    }

    public PlayerResponse getArena() throws IOException {
        String json = request("GET", "/api/arena", null);
        return JsonUtils.fromJson(json, PlayerResponse.class);
    }

    public void sendMoves(PlayerMoveCommands moves) throws IOException {
        String json = JsonUtils.toJson(moves);
        request("POST", "/api/move", json);
    }

    public void register() throws IOException {
        request("POST", "/api/register", null);
    }

    public LogMessage[] getLogs() throws IOException {
        String json = request("GET", "/api/logs", null);
        return JsonUtils.fromJson(json, LogMessage[].class);
    }
} 