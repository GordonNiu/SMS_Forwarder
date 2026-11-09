package com.smsforwarder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class TelegramClient {
    private TelegramClient() {
    }

    public static boolean sendMessage(String token, String chatId, String message) throws Exception {
        URL url = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

        String body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8")
                + "&text=" + URLEncoder.encode(message, "UTF-8");
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = connection.getResponseCode();
        InputStream responseStream = responseCode < 400
                ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder responseBody = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }
        } finally {
            connection.disconnect();
        }

        if (responseCode >= 200 && responseCode < 300 && responseBody.toString().contains("\"ok\":true")) {
            return true;
        }
        return false;
    }
}
