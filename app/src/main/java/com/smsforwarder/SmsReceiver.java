package com.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

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

        String smsText = "收到短信\n发件人：" + (sender == null ? "未知" : sender)
                + "\n内容：\n" + body;
        if (smsText.codePointCount(0, smsText.length()) > 3500) {
            int end = smsText.offsetByCodePoints(0, 3500);
            smsText = smsText.substring(0, end) + "…（内容过长，已截断）";
        }

        PendingResult pendingResult = goAsync();
        Context appContext = context.getApplicationContext();
        String finalSmsText = smsText;
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
