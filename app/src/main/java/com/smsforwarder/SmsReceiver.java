package com.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsReceiver extends BroadcastReceiver {
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())
                || !SettingsStore.isForwardingEnabled(context)) {
            return;
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            return;
        }

        String sender = messages[0].getOriginatingAddress();
        StringBuilder body = new StringBuilder();
        for (SmsMessage message : messages) {
            body.append(message.getMessageBody());
        }

        Date receivedAt = new Date(System.currentTimeMillis());
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        StringBuilder smsText = new StringBuilder(context.getString(R.string.received_sms_title))
            .append("\n")
            .append(context.getString(R.string.sender_label))
            .append("：")
            .append(sender == null ? context.getString(R.string.unknown_sender) : sender);
        if (SettingsStore.isExtraTimezoneEnabled(context)) {
            String timezoneId = SettingsStore.getExtraTimezone(context);
            timeFormat.setTimeZone(TimeZone.getTimeZone(timezoneId));
            String extraTime = timeFormat.format(receivedAt);
            smsText.append("\n")
                    .append(TimezoneUtils.getTimeLabel(timezoneId))
                    .append("：")
                    .append(extraTime);
        }
        timeFormat.setTimeZone(TimeZone.getDefault());
        String localTime = timeFormat.format(receivedAt);
        smsText.append("\n")
                .append(context.getString(R.string.local_time_label))
            .append("：")
                .append(localTime)
            .append("\n")
                .append(context.getString(R.string.message_content_label))
            .append("：\n")
                .append(body);
        if (smsText.codePointCount(0, smsText.length()) > 3500) {
            int end = smsText.offsetByCodePoints(0, 3500);
            smsText.setLength(end);
            smsText.append(context.getString(R.string.message_truncated));
        }

        PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        String finalSmsText = smsText.toString();
        executor.execute(() -> {
            try {
                String token = SettingsStore.getToken(appContext);
                String chatId = SettingsStore.getChatId(appContext);
                if (!token.isEmpty() && !chatId.isEmpty()) {
                    TelegramClient.sendMessage(token, chatId, finalSmsText);
                }
            } catch (Exception exception) {
                Log.e("SmsForwarder", "Could not forward received SMS");
            } finally {
                pendingResult.finish();
            }
        });
    }
}
