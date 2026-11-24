package com.smsforwarder;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

public final class TelegramClient {
    private static final Pattern BOT_TOKEN_PATTERN =
            Pattern.compile("^[0-9]{5,20}:[A-Za-z0-9_-]{20,128}$");
    private static final Pattern CHAT_ID_PATTERN =
            Pattern.compile("^-?[0-9]{1,20}$");
    private static final int MAX_RESPONSE_CHARS = 8192;

    private TelegramClient() {
    }

    public static boolean isValidToken(String token) {
        return token != null && BOT_TOKEN_PATTERN.matcher(token).matches();
    }

    public static boolean isValidChatId(String chatId) {
        return chatId != null && CHAT_ID_PATTERN.matcher(chatId).matches();
    }

    public static boolean sendMessage(String token, String chatId, String message) throws Exception {
        if (!isValidToken(token) || !isValidChatId(chatId) || message == null) {
            throw new IllegalArgumentException("Invalid Telegram parameters");
        }

        URL url = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
        URLConnection rawConnection = url.openConnection();
        if (!(rawConnection instanceof HttpsURLConnection)) {
            throw new IllegalStateException("HTTPS connection required");
        }

        HttpsURLConnection connection = (HttpsURLConnection) rawConnection;
        try {
            connection.setRequestMethod("POST");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(4000);
            connection.setDoOutput(true);
            connection.setRequestProperty(
                    "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            String body = "chat_id=" + URLEncoder.encode(chatId, "UTF-8")
                    + "&text=" + URLEncoder.encode(message, "UTF-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = connection.getResponseCode();
            InputStream responseStream = responseCode < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder responseBody = new StringBuilder();
            if (responseStream != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
                    char[] buffer = new char[1024];
                    int read;
                    while ((read = reader.read(buffer)) != -1
                            && responseBody.length() < MAX_RESPONSE_CHARS) {
                        int remaining = MAX_RESPONSE_CHARS - responseBody.length();
                        responseBody.append(buffer, 0, Math.min(read, remaining));
                    }
                }
            }

            return responseCode >= 200
                    && responseCode < 300
                    && responseBody.toString().contains("\"ok\":true");
        } finally {
            connection.disconnect();
        }
    }
}
