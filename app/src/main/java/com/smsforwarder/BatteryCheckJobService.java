package com.smsforwarder;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BatteryCheckJobService extends JobService {
    private static final int JOB_ID_PERIODIC = 2001;
    private static final int JOB_ID_IMMEDIATE = 2002;
    private static final long CHECK_INTERVAL_MILLIS = 15 * 60 * 1000L;
    private static final long RETRY_COOLDOWN_MILLIS = 5 * 60 * 1000L;
    private static final int RESET_HYSTERESIS_PERCENT = 5;
    private static final int CRITICAL_BATTERY_PERCENT = 5;
    private static final long[] RETRY_DELAYS_MILLIS = {2000L, 5000L};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Future<?> runningTask;

    public static boolean schedule(Context context, boolean runImmediateCheck) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            return false;
        }

        ComponentName serviceComponent = new ComponentName(context, BatteryCheckJobService.class);
        if (scheduler.getPendingJob(JOB_ID_PERIODIC) == null) {
            JobInfo periodicJob = new JobInfo.Builder(JOB_ID_PERIODIC, serviceComponent)
                    .setPeriodic(CHECK_INTERVAL_MILLIS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build();
            int result = scheduler.schedule(periodicJob);
            if (result != JobScheduler.RESULT_SUCCESS) {
                return false;
            }
        }

        if (runImmediateCheck) {
            scheduleImmediateCheck(context);
        }
        return true;
    }

    public static void scheduleImmediateCheck(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) {
            return;
        }
        ComponentName serviceComponent = new ComponentName(context, BatteryCheckJobService.class);
        JobInfo immediateJob = new JobInfo.Builder(JOB_ID_IMMEDIATE, serviceComponent)
                .setMinimumLatency(0L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(60_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        scheduler.schedule(immediateJob);
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) {
            scheduler.cancel(JOB_ID_PERIODIC);
            scheduler.cancel(JOB_ID_IMMEDIATE);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!SettingsStore.isLowBatteryAlertEnabled(this)) {
            cancel(this);
            return false;
        }

        BatterySnapshot snapshot = readBatterySnapshot();
        if (snapshot == null) {
            return false;
        }

        int threshold = SettingsStore.getLowBatteryThreshold(this);
        if (hasRecovered(snapshot.percent, snapshot.charging, threshold)) {
            SettingsStore.setLowBatteryAlertSent(this, false);
            SettingsStore.setLowBatteryCriticalSent(this, false);
            SettingsStore.setLowBatteryLastAttempt(this, 0L);
            return false;
        }

        if (snapshot.charging) {
            return false;
        }

        boolean shouldSendCritical = snapshot.percent <= CRITICAL_BATTERY_PERCENT
                && threshold > CRITICAL_BATTERY_PERCENT
                && !SettingsStore.isLowBatteryCriticalSent(this);
        boolean shouldSendNormal = snapshot.percent <= threshold
                && !SettingsStore.isLowBatteryAlertSent(this);

        if (!shouldSendCritical && !shouldSendNormal) {
            return false;
        }

        long now = System.currentTimeMillis();
        long lastAttempt = SettingsStore.getLowBatteryLastAttempt(this);
        if (now < lastAttempt) {
            lastAttempt = 0L;
            SettingsStore.setLowBatteryLastAttempt(this, 0L);
        }
        if (now - lastAttempt < RETRY_COOLDOWN_MILLIS) {
            return false;
        }

        SettingsStore.setLowBatteryLastAttempt(this, now);
        boolean sendCritical = shouldSendCritical;
        int percent = snapshot.percent;
        boolean isImmediateJob = params.getJobId() == JOB_ID_IMMEDIATE;

        runningTask = executor.submit(() -> {
            boolean needsReschedule = false;
            try {
                String token = SettingsStore.getToken(this);
                String chatId = SettingsStore.getChatId(this);
                if (token.isEmpty() || chatId.isEmpty()) {
                    return;
                }

                String message = sendCritical
                        ? getString(R.string.low_battery_critical_alert_message, percent)
                        : getString(R.string.low_battery_alert_message, percent, threshold);

                if (sendWithRetries(token, chatId, message)) {
                    SettingsStore.setLowBatteryAlertSent(this, true);
                    if (sendCritical) {
                        SettingsStore.setLowBatteryCriticalSent(this, true);
                    }
                } else {
                    Log.e("SmsForwarder", "Low battery alert could not be sent after retries");
                    needsReschedule = isImmediateJob;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                needsReschedule = true;
            } catch (Exception exception) {
                Log.e("SmsForwarder", "Could not prepare low battery alert");
            } finally {
                jobFinished(params, needsReschedule);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Future<?> task = runningTask;
        if (task != null) {
            task.cancel(true);
        }
        return true;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private boolean sendWithRetries(String token, String chatId, String message)
            throws InterruptedException {
        for (int attempt = 0; attempt <= RETRY_DELAYS_MILLIS.length; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            try {
                if (TelegramClient.sendMessage(token, chatId, message)) {
                    return true;
                }
            } catch (Exception exception) {
                Log.w("SmsForwarder", "Low battery alert attempt " + (attempt + 1) + " failed");
            }
            if (attempt < RETRY_DELAYS_MILLIS.length) {
                Thread.sleep(RETRY_DELAYS_MILLIS[attempt]);
            }
        }
        return false;
    }

    private boolean hasRecovered(int percent, boolean charging, int threshold) {
        if (charging && percent > threshold) {
            return true;
        }
        if (percent == 100 && threshold < 100) {
            return true;
        }
        int hysteresisTarget = Math.min(95, threshold + RESET_HYSTERESIS_PERCENT);
        return !charging && percent > hysteresisTarget;
    }

    private BatterySnapshot readBatterySnapshot() {
        Intent batteryIntent = registerReceiver(
                null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            if (level >= 0 && scale > 0) {
                int percent = Math.round(level * 100f / scale);
                boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                return new BatterySnapshot(percent, charging);
            }
        }

        BatteryManager manager = getSystemService(BatteryManager.class);
        if (manager != null) {
            int capacity = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (capacity >= 0 && capacity <= 100) {
                return new BatterySnapshot(capacity, manager.isCharging());
            }
        }
        return null;
    }

    private static final class BatterySnapshot {
        final int percent;
        final boolean charging;

        private BatterySnapshot(int percent, boolean charging) {
            this.percent = percent;
            this.charging = charging;
        }
    }
}
