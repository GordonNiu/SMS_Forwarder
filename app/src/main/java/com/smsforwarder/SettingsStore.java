package com.smsforwarder;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SettingsStore {
    private static final String KEY_ALIAS = "sms_forwarder_settings_key";
    private static final String PREFS_NAME = "encrypted_settings";

    private SettingsStore() {
    }

    public static void save(Context context, String token, String chatId) throws Exception {
        if (!TelegramClient.isValidToken(token) || !TelegramClient.isValidChatId(chatId)) {
            throw new IllegalArgumentException("Invalid Telegram credentials");
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String encryptedToken = encrypt(token);
        String encryptedChatId = encrypt(chatId);
        preferences.edit()
                .putString("token", encryptedToken)
                .putString("chat_id", encryptedChatId)
                .apply();
    }

    public static String getToken(Context context) throws Exception {
        return decrypt(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("token", ""));
    }

    public static String getChatId(Context context) throws Exception {
        return decrypt(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("chat_id", ""));
    }

    public static void setForwardingEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("forwarding_enabled", enabled)
                .apply();
    }

    public static boolean isForwardingEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("forwarding_enabled", false);
    }

    public static void setExtraTimezoneEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("extra_timezone_enabled", enabled)
                .apply();
    }

    public static boolean isExtraTimezoneEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("extra_timezone_enabled", false);
    }

    public static void setExtraTimezone(Context context, String timezoneId) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("extra_timezone", timezoneId)
                .apply();
    }

    public static String getExtraTimezone(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("extra_timezone", "Asia/Shanghai");
    }

    public static void setLowBatteryAlertEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("low_battery_alert_enabled", enabled)
                .apply();
    }

    public static boolean isLowBatteryAlertEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("low_battery_alert_enabled", false);
    }

    public static void setLowBatteryThreshold(Context context, int threshold) {
        int safeThreshold = Math.max(1, Math.min(100, threshold));
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt("low_battery_threshold", safeThreshold)
                .apply();
    }

    public static int getLowBatteryThreshold(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt("low_battery_threshold", 20);
    }

    public static void setLowBatteryAlertSent(Context context, boolean sent) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("low_battery_alert_sent", sent)
                .apply();
    }

    public static boolean isLowBatteryAlertSent(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("low_battery_alert_sent", false);
    }

    public static void setLowBatteryCriticalSent(Context context, boolean sent) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("low_battery_critical_sent", sent)
                .apply();
    }

    public static boolean isLowBatteryCriticalSent(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean("low_battery_critical_sent", false);
    }

    public static void setLowBatteryLastAttempt(Context context, long timestamp) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong("low_battery_last_attempt", timestamp)
                .apply();
    }

    public static long getLowBatteryLastAttempt(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong("low_battery_last_attempt", 0L);
    }

    private static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private static String decrypt(String value) throws Exception {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String[] parts = value.split(":", 2);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException("Corrupted encrypted setting");
        }
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static synchronized SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((SecretKey) keyStore.getKey(KEY_ALIAS, null));
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return keyGenerator.generateKey();
    }
}