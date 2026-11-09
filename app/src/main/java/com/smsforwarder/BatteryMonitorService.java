package com.smsforwarder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatteryMonitorService extends Service {
    private static final String CHANNEL_ID = "low_battery_monitor";
    private static final int NOTIFICATION_ID = 2001;

    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private boolean receiverRegistered;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleBatteryChanged(intent);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!SettingsStore.isLowBatteryAlertEnabled(this)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }

        updateForegroundNotification();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent currentBattery;
        if (!receiverRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                currentBattery = registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                currentBattery = registerReceiver(batteryReceiver, filter);
            }
            receiverRegistered = true;
        } else {
            currentBattery = registerReceiver(null, filter);
        }
        if (currentBattery != null) {
            handleBatteryChanged(currentBattery);
        }
        return START_STICKY;
    }

    private void handleBatteryChanged(Intent intent) {
        if (intent == null) {
            return;
        }
        int level = intent.getIntExtra("level", -1);
        int scale = intent.getIntExtra("scale", -1);
        if (level < 0 || scale <= 0) {
            return;
        }

        int percent = Math.round(level * 100f / scale);
        int threshold = SettingsStore.getLowBatteryThreshold(this);
        int status = intent.getIntExtra("status", -1);
        boolean charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                || status == android.os.BatteryManager.BATTERY_STATUS_FULL;

        if (charging) {
            if (percent > threshold) {
                SettingsStore.setLowBatteryAlertSent(this, false);
            }
            return;
        }

        if (percent <= threshold && !SettingsStore.isLowBatteryAlertSent(this)) {
            SettingsStore.setLowBatteryAlertSent(this, true);
            sendLowBatteryAlert(percent, threshold);
        }
    }

    private void sendLowBatteryAlert(int percent, int threshold) {
        sender.execute(() -> {
            try {
                String token = SettingsStore.getToken(this);
                String chatId = SettingsStore.getChatId(this);
                if (!token.isEmpty() && !chatId.isEmpty()) {
                    String message = getString(R.string.low_battery_alert_message, percent, threshold);
                    if (!TelegramClient.sendMessage(token, chatId, message)) {
                        Log.e("SmsForwarder", "Could not send low battery alert");
                    }
                }
            } catch (Exception exception) {
                Log.e("SmsForwarder", "Could not send low battery alert");
            }
        });
    }

    private void updateForegroundNotification() {
        int threshold = SettingsStore.getLowBatteryThreshold(this);
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.low_battery_monitor_title))
                .setContentText(getString(R.string.low_battery_monitor_text, threshold))
                .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
                .setContentIntent(contentIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
            getString(R.string.low_battery_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.low_battery_channel_description));
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(batteryReceiver);
            receiverRegistered = false;
        }
        sender.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
