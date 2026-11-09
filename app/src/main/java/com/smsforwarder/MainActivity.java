package com.smsforwarder;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText tokenInput;
    private EditText chatIdInput;
    private TextView statusText;
    private Button forwardingButton;
    private Switch lowBatterySwitch;
    private SeekBar batteryThresholdSeekBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText(R.string.screen_title);
        title.setTextSize(24f);
        layout.addView(title);

        tokenInput = new EditText(this);
        tokenInput.setHint(R.string.bot_token_hint);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(tokenInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        chatIdInput = new EditText(this);
        chatIdInput.setHint(R.string.chat_id_hint);
        chatIdInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        layout.addView(chatIdInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView timezoneTitle = new TextView(this);
        timezoneTitle.setText(R.string.sms_time_display);
        timezoneTitle.setTextSize(18f);
        layout.addView(timezoneTitle);

        Switch extraTimezoneSwitch = new Switch(this);
        extraTimezoneSwitch.setText(R.string.extra_timezone_toggle);
        extraTimezoneSwitch.setChecked(SettingsStore.isExtraTimezoneEnabled(this));
        layout.addView(extraTimezoneSwitch);

        Button timezoneInput = new Button(this);
        String savedTimezone = SettingsStore.getExtraTimezone(this);
        timezoneInput.setText(TimezoneUtils.formatTimezone(savedTimezone, System.currentTimeMillis()));
        timezoneInput.setEnabled(extraTimezoneSwitch.isChecked());
        timezoneInput.setOnClickListener(view -> showTimezonePicker(timezoneInput));
        extraTimezoneSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            timezoneInput.setEnabled(isChecked);
            SettingsStore.setExtraTimezoneEnabled(this, isChecked);
        });
        layout.addView(timezoneInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        lowBatterySwitch = new Switch(this);
        lowBatterySwitch.setText(R.string.low_battery_toggle);
        lowBatterySwitch.setChecked(SettingsStore.isLowBatteryAlertEnabled(this));
        layout.addView(lowBatterySwitch);

        TextView batteryThresholdText = new TextView(this);
        int savedThreshold = SettingsStore.getLowBatteryThreshold(this);
        batteryThresholdText.setText(getString(R.string.low_battery_threshold, savedThreshold));
        layout.addView(batteryThresholdText);

        batteryThresholdSeekBar = new SeekBar(this);
        batteryThresholdSeekBar.setMin(1);
        batteryThresholdSeekBar.setMax(100);
        batteryThresholdSeekBar.setProgress(savedThreshold);
        batteryThresholdSeekBar.setEnabled(lowBatterySwitch.isChecked());
        batteryThresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                batteryThresholdText.setText(getString(R.string.low_battery_threshold, progress));
                SettingsStore.setLowBatteryThreshold(MainActivity.this, progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                refreshBatteryMonitorNotification();
            }
        });
        layout.addView(batteryThresholdSeekBar);
        lowBatterySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            batteryThresholdSeekBar.setEnabled(isChecked);
            if (isChecked) {
                if (!hasTelegramSettings()) {
                    statusText.setText(R.string.low_battery_config_missing);
                    lowBatterySwitch.setChecked(false);
                    return;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                    return;
                }
                startBatteryMonitoring();
            } else {
                SettingsStore.setLowBatteryAlertEnabled(this, false);
                stopService(new Intent(this, BatteryMonitorService.class));
                statusText.setText(R.string.low_battery_monitoring_off);
            }
        });

        Button testButton = new Button(this);
        testButton.setText(R.string.save_test_message);
        testButton.setOnClickListener(view -> sendTestMessage());
        layout.addView(testButton);

        forwardingButton = new Button(this);
        forwardingButton.setOnClickListener(view -> toggleForwarding());
        layout.addView(forwardingButton);

        statusText = new TextView(this);
        statusText.setText(R.string.status_config_hint);
        statusText.setTextColor(Color.DKGRAY);
        layout.addView(statusText);

        try {
            tokenInput.setText(SettingsStore.getToken(this));
            chatIdInput.setText(SettingsStore.getChatId(this));
        } catch (Exception exception) {
            statusText.setText(R.string.status_config_read_error);
        }

        updateForwardingButton();
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(layout);
        setContentView(scrollView);
        restoreBatteryMonitoring();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateForwardingButton();
    }

    private void updateForwardingButton() {
        if (forwardingButton != null) {
            forwardingButton.setText(SettingsStore.isForwardingEnabled(this)
                    ? R.string.forwarding_enabled_button : R.string.forwarding_disabled_button);
        }
    }

    private void toggleForwarding() {
        if (SettingsStore.isForwardingEnabled(this)) {
            SettingsStore.setForwardingEnabled(this, false);
            updateForwardingButton();
            statusText.setText(R.string.status_forwarding_off);
            return;
        }

        try {
            if (SettingsStore.getToken(this).isEmpty() || SettingsStore.getChatId(this).isEmpty()) {
                statusText.setText(R.string.status_config_missing);
                return;
            }
        } catch (Exception exception) {
            statusText.setText(R.string.status_config_read_error);
            return;
        }

        if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            enableForwarding();
        } else {
            requestPermissions(new String[]{android.Manifest.permission.RECEIVE_SMS}, 100);
        }
    }

    private void enableForwarding() {
        SettingsStore.setForwardingEnabled(this, true);
        updateForwardingButton();
        statusText.setText(R.string.status_forwarding_on);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableForwarding();
            } else {
                statusText.setText(R.string.status_sms_permission_denied);
            }
        } else if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBatteryMonitoring();
            } else {
                lowBatterySwitch.setChecked(false);
                statusText.setText(R.string.notification_permission_needed);
            }
        }
    }

    private boolean hasTelegramSettings() {
        try {
            return !SettingsStore.getToken(this).isEmpty() && !SettingsStore.getChatId(this).isEmpty();
        } catch (Exception exception) {
            return false;
        }
    }

    private void startBatteryMonitoring() {
        SettingsStore.setLowBatteryAlertEnabled(this, true);
        Intent serviceIntent = new Intent(this, BatteryMonitorService.class);
        try {
            startForegroundService(serviceIntent);
            statusText.setText(R.string.low_battery_monitoring_on);
        } catch (RuntimeException exception) {
            SettingsStore.setLowBatteryAlertEnabled(this, false);
            lowBatterySwitch.setChecked(false);
            statusText.setText(R.string.low_battery_monitor_start_failed);
        }
    }

    private void stopBatteryMonitoring() {
        SettingsStore.setLowBatteryAlertEnabled(this, false);
        stopService(new Intent(this, BatteryMonitorService.class));
    }

    private void restoreBatteryMonitoring() {
        if (!SettingsStore.isLowBatteryAlertEnabled(this)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            SettingsStore.setLowBatteryAlertEnabled(this, false);
            lowBatterySwitch.setChecked(false);
            statusText.setText(R.string.notification_permission_needed);
            return;
        }
        startBatteryMonitoring();
    }

    private void refreshBatteryMonitorNotification() {
        if (SettingsStore.isLowBatteryAlertEnabled(this)) {
            Intent serviceIntent = new Intent(this, BatteryMonitorService.class);
            startForegroundService(serviceIntent);
        }
    }

    private void sendTestMessage() {
        String token = tokenInput.getText().toString().trim();
        String chatId = chatIdInput.getText().toString().trim();
        if (token.isEmpty() || chatId.isEmpty()) {
            statusText.setText(R.string.status_config_missing);
            return;
        }

        try {
            SettingsStore.save(this, token, chatId);
        } catch (Exception exception) {
            statusText.setText(R.string.status_config_save_error);
            return;
        }

        statusText.setText(R.string.status_test_sending);
        executor.execute(() -> {
            String result;
            try {
                boolean sent = TelegramClient.sendMessage(token, chatId, getString(R.string.test_message));
                result = getString(sent ? R.string.status_test_sent : R.string.status_test_failed);
            } catch (Exception exception) {
                result = getString(R.string.status_test_failed);
            }
            String finalResult = result;
            runOnUiThread(() -> statusText.setText(finalResult));
        });
    }

    private void showTimezonePicker(Button timezoneButton) {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        dialogLayout.setPadding(padding, padding, padding, padding);

        EditText searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint(R.string.timezone_search_hint);
        String savedTimezone = SettingsStore.getExtraTimezone(this);
        searchInput.setText(TimezoneUtils.formatTimezone(savedTimezone, System.currentTimeMillis()));
        dialogLayout.addView(searchInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView timezoneList = new ListView(this);
        timezoneList.setFastScrollEnabled(true);
        timezoneList.setFastScrollAlwaysVisible(true);
        timezoneList.setVerticalScrollBarEnabled(true);
        timezoneList.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        int fastScrollTouchWidth = (int) (48 * getResources().getDisplayMetrics().density + 0.5f);
        timezoneList.setScrollBarSize(dp(16));
        timezoneList.setPadding(
                timezoneList.getPaddingLeft(),
                timezoneList.getPaddingTop(),
                fastScrollTouchWidth,
                timezoneList.getPaddingBottom());
        List<TimezoneUtils.TimezoneOption> timezoneOptions = TimezoneUtils.getAvailableOptions();
        TimezoneAdapter timezoneAdapter = new TimezoneAdapter(this, timezoneOptions);
        timezoneList.setAdapter(timezoneAdapter);
        boolean[] draggingFastScrollArea = {false};
        timezoneList.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                draggingFastScrollArea[0] = event.getX() >= view.getWidth() - fastScrollTouchWidth;
            }
            if (!draggingFastScrollArea[0]) {
                return false;
            }

            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                int itemCount = timezoneAdapter.getCount();
                if (itemCount > 0) {
                    float availableHeight = Math.max(1, view.getHeight()
                            - view.getPaddingTop() - view.getPaddingBottom());
                    float fraction = (event.getY() - view.getPaddingTop()) / availableHeight;
                    fraction = Math.max(0f, Math.min(1f, fraction));
                    timezoneList.setSelection(Math.round(fraction * (itemCount - 1)));
                }
            }

            if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                draggingFastScrollArea[0] = false;
            }
            return true;
        });
        dialogLayout.addView(timezoneList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.timezone_dialog_title)
                .setView(dialogLayout)
            .setNegativeButton(R.string.cancel, null)
                .create();
        timezoneList.setOnItemClickListener((parent, view, position, id) -> {
            TimezoneUtils.TimezoneOption option = timezoneAdapter.getItem(position);
            if (option != null) {
                SettingsStore.setExtraTimezone(this, option.id);
                timezoneButton.setText(option.label);
                dialog.dismiss();
            }
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                timezoneAdapter.getFilter().filter(text);
            }

            @Override
            public void afterTextChanged(Editable text) {
            }
        });
        timezoneAdapter.getFilter().filter(searchInput.getText());
        dialog.setOnShowListener(ignored -> {
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            dialog.getWindow().setLayout((int) (width * 0.92), (int) (height * 0.75));
        });
        dialog.show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class TimezoneAdapter extends ArrayAdapter<TimezoneUtils.TimezoneOption>
            implements Filterable {
        private final List<TimezoneUtils.TimezoneOption> allOptions;
        private final Filter timezoneFilter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String query = constraint == null ? "" : constraint.toString()
                        .replace("*", "")
                        .trim()
                        .toLowerCase(Locale.ROOT);
                List<TimezoneUtils.TimezoneOption> matches = new ArrayList<>();
                for (TimezoneUtils.TimezoneOption option : allOptions) {
                    if (query.isEmpty() || option.label.toLowerCase(Locale.ROOT).contains(query)) {
                        matches.add(option);
                    }
                }

                FilterResults results = new FilterResults();
                results.values = matches;
                results.count = matches.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                clear();
                addAll((List<TimezoneUtils.TimezoneOption>) results.values);
                notifyDataSetChanged();
            }
        };

        private TimezoneAdapter(Context context, List<TimezoneUtils.TimezoneOption> options) {
            super(context, android.R.layout.simple_list_item_1, options);
            allOptions = new ArrayList<>(options);
        }

        @Override
        public Filter getFilter() {
            return timezoneFilter;
        }
    }

}
