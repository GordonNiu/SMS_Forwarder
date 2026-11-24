package com.smsforwarder;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
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
    private enum BannerStyle {
        INFO,
        SUCCESS,
        ERROR
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText tokenInput;
    private EditText chatIdInput;
    private TextView statusText;
    private TextView forwardingBadge;
    private TextView batteryBadge;
    private Button forwardingButton;
    private Switch lowBatterySwitch;
    private SeekBar batteryThresholdSeekBar;
    private TextView batteryThresholdValue;
    private TextView timezoneCityView;
    private TextView timezoneOffsetPill;
    private TextView timezoneDetailView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        LinearLayout rootContent = new LinearLayout(this);
        rootContent.setOrientation(LinearLayout.VERTICAL);

        rootContent.addView(buildHeaderSection());
        rootContent.addView(buildForwardingCard());
        rootContent.addView(buildTelegramCard());
        rootContent.addView(buildTimezoneCard());
        rootContent.addView(buildBatteryCard());

        try {
            if (!SettingsStore.getToken(this).isEmpty()) {
                tokenInput.setHint(R.string.bot_token_saved_hint);
            }
            chatIdInput.setText(SettingsStore.getChatId(this));
        } catch (Exception exception) {
            setStatusMessage(R.string.status_config_read_error, BannerStyle.ERROR);
        }

        updateForwardingUi();
        updateBatteryBadge();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setBackgroundColor(getColor(R.color.bg_window));
        scrollView.addView(rootContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int horizontalPadding = dp(16);
        int topBasePadding = dp(16);
        int bottomBasePadding = dp(24);
        rootContent.setPadding(horizontalPadding, topBasePadding, horizontalPadding, bottomBasePadding);
        scrollView.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            rootContent.setPadding(
                    horizontalPadding + left,
                    topBasePadding + top,
                    horizontalPadding + right,
                    bottomBasePadding + bottom);
            return insets;
        });

        setContentView(scrollView);
        restoreBatteryMonitoring();
    }

    private View buildHeaderSection() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerParams.bottomMargin = dp(16);
        header.setLayoutParams(headerParams);

        ImageView iconView = new ImageView(this);
        iconView.setImageResource(R.drawable.ic_header_logo);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView.setPadding(dp(7), dp(7), dp(7), dp(7));
        GradientDrawable iconBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0F766E, 0xFF14B8A6});
        iconBg.setCornerRadius(dp(14));
        iconView.setBackground(iconBg);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        iconParams.setMarginEnd(dp(14));
        header.addView(iconView, iconParams);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);

        TextView appTitle = new TextView(this);
        appTitle.setText(R.string.app_name);
        appTitle.setTextSize(22f);
        appTitle.setTypeface(Typeface.DEFAULT_BOLD);
        appTitle.setTextColor(getColor(R.color.text_primary));
        titleColumn.addView(appTitle);

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.app_subtitle);
        subtitle.setTextSize(13f);
        subtitle.setTextColor(getColor(R.color.text_secondary));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(2);
        titleColumn.addView(subtitle, subParams);

        header.addView(titleColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return header;
    }

    private View buildForwardingCard() {
        LinearLayout card = createCardContainer();

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView cardTitle = createSectionTitle(R.string.forwarding_card_title);
        topRow.addView(cardTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        forwardingBadge = createPillBadge();
        topRow.addView(forwardingBadge);
        card.addView(topRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        batteryBadge = createPillBadge();
        LinearLayout.LayoutParams batteryBadgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        batteryBadgeParams.topMargin = dp(8);
        card.addView(batteryBadge, batteryBadgeParams);

        forwardingButton = createActionButton();
        forwardingButton.setOnClickListener(view -> toggleForwarding());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        buttonParams.topMargin = dp(14);
        card.addView(forwardingButton, buttonParams);

        statusText = new TextView(this);
        statusText.setTextSize(13.5f);
        statusText.setLineSpacing(dp(2), 1f);
        statusText.setPadding(dp(14), dp(11), dp(14), dp(11));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(12);
        card.addView(statusText, statusParams);
        setStatusMessage(R.string.status_config_hint, BannerStyle.INFO);

        return card;
    }

    private View buildTelegramCard() {
        LinearLayout card = createCardContainer();
        card.addView(createSectionTitle(R.string.screen_title));

        card.addView(createFieldLabel(R.string.bot_token_label, dp(14)));
        tokenInput = createStyledInput(
                R.string.bot_token_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(tokenInput, createInputLayoutParams());

        card.addView(createFieldLabel(R.string.chat_id_label, dp(12)));
        chatIdInput = createStyledInput(
                R.string.chat_id_hint,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        card.addView(chatIdInput, createInputLayoutParams());

        Button testButton = createActionButton();
        testButton.setText(R.string.save_test_message);
        testButton.setBackgroundResource(R.drawable.bg_btn_secondary);
        testButton.setTextColor(getColor(R.color.brand_on_soft));
        testButton.setOnClickListener(view -> sendTestMessage());
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        testParams.topMargin = dp(16);
        card.addView(testButton, testParams);

        return card;
    }

    private View buildTimezoneCard() {
        LinearLayout card = createCardContainer();

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.addView(createSectionTitle(R.string.sms_time_display));

        TextView description = new TextView(this);
        description.setText(R.string.extra_timezone_description);
        description.setTextSize(12.5f);
        description.setTextColor(getColor(R.color.text_secondary));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = dp(2);
        titleColumn.addView(description, descParams);

        LinearLayout.LayoutParams titleColParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleColParams.setMarginEnd(dp(12));
        headerRow.addView(titleColumn, titleColParams);

        boolean enabled = SettingsStore.isExtraTimezoneEnabled(this);
        Switch extraTimezoneSwitch = createStyledSwitch(0, enabled);
        headerRow.addView(extraTimezoneSwitch);
        card.addView(headerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout detailsContainer = new LinearLayout(this);
        detailsContainer.setOrientation(LinearLayout.VERTICAL);
        detailsContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.divider));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(12);
        dividerParams.bottomMargin = dp(12);
        detailsContainer.addView(divider, dividerParams);

        LinearLayout selectorRow = new LinearLayout(this);
        selectorRow.setOrientation(LinearLayout.HORIZONTAL);
        selectorRow.setGravity(Gravity.CENTER_VERTICAL);
        selectorRow.setBackgroundResource(R.drawable.bg_input);
        selectorRow.setPadding(dp(14), dp(12), dp(14), dp(12));
        selectorRow.setClickable(true);
        selectorRow.setFocusable(true);

        LinearLayout infoColumn = new LinearLayout(this);
        infoColumn.setOrientation(LinearLayout.VERTICAL);

        LinearLayout cityRow = new LinearLayout(this);
        cityRow.setOrientation(LinearLayout.HORIZONTAL);
        cityRow.setGravity(Gravity.CENTER_VERTICAL);

        timezoneCityView = new TextView(this);
        timezoneCityView.setTextSize(15.5f);
        timezoneCityView.setTypeface(Typeface.DEFAULT_BOLD);
        timezoneCityView.setTextColor(getColor(R.color.text_primary));
        timezoneCityView.setSingleLine(true);
        timezoneCityView.setEllipsize(TextUtils.TruncateAt.END);
        cityRow.addView(timezoneCityView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        timezoneOffsetPill = createPillBadge();
        stylePillBadge(timezoneOffsetPill, R.color.brand_soft, R.color.brand_on_soft);
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pillParams.setMarginStart(dp(8));
        cityRow.addView(timezoneOffsetPill, pillParams);

        infoColumn.addView(cityRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        timezoneDetailView = new TextView(this);
        timezoneDetailView.setTextSize(12.5f);
        timezoneDetailView.setTextColor(getColor(R.color.text_secondary));
        timezoneDetailView.setSingleLine(true);
        timezoneDetailView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(4);
        infoColumn.addView(timezoneDetailView, detailParams);

        selectorRow.addView(infoColumn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextSize(22f);
        chevron.setTextColor(getColor(R.color.text_secondary));
        chevron.setPadding(dp(10), 0, dp(2), 0);
        selectorRow.addView(chevron);

        refreshTimezoneCard();
        selectorRow.setOnClickListener(view -> showTimezonePicker());
        extraTimezoneSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SettingsStore.setExtraTimezoneEnabled(this, isChecked);
            detailsContainer.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                refreshTimezoneCard();
            }
        });

        detailsContainer.addView(selectorRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(detailsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return card;
    }

    private void refreshTimezoneCard() {
        if (timezoneCityView == null || timezoneOffsetPill == null || timezoneDetailView == null) {
            return;
        }
        String savedTimezone = SettingsStore.getExtraTimezone(this);
        TimezoneUtils.TimezoneOption option =
                TimezoneUtils.createOption(savedTimezone, System.currentTimeMillis());
        timezoneCityView.setText(option.locationName);
        timezoneOffsetPill.setText(option.offsetText);
        timezoneDetailView.setText(option.id + "  ·  "
                + getString(R.string.timezone_current_time, option.currentTimeText));
    }

    private View buildBatteryCard() {
        LinearLayout card = createCardContainer();

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleColumn = new LinearLayout(this);
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.addView(createSectionTitle(R.string.low_battery_section_title));

        TextView description = new TextView(this);
        description.setText(R.string.low_battery_description);
        description.setTextSize(12.5f);
        description.setTextColor(getColor(R.color.text_secondary));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = dp(2);
        titleColumn.addView(description, descParams);

        LinearLayout.LayoutParams titleColParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleColParams.setMarginEnd(dp(12));
        headerRow.addView(titleColumn, titleColParams);

        boolean enabled = SettingsStore.isLowBatteryAlertEnabled(this);
        lowBatterySwitch = createStyledSwitch(0, enabled);
        headerRow.addView(lowBatterySwitch);
        card.addView(headerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout detailsContainer = new LinearLayout(this);
        detailsContainer.setOrientation(LinearLayout.VERTICAL);
        detailsContainer.setVisibility(enabled ? View.VISIBLE : View.GONE);

        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.divider));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(12);
        dividerParams.bottomMargin = dp(12);
        detailsContainer.addView(divider, dividerParams);

        LinearLayout thresholdRow = new LinearLayout(this);
        thresholdRow.setOrientation(LinearLayout.HORIZONTAL);
        thresholdRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView thresholdLabel = new TextView(this);
        thresholdLabel.setText(R.string.low_battery_threshold_label);
        thresholdLabel.setTextSize(14f);
        thresholdLabel.setTextColor(getColor(R.color.text_secondary));
        thresholdRow.addView(thresholdLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int savedThreshold = SettingsStore.getLowBatteryThreshold(this);
        batteryThresholdValue = createPillBadge();
        stylePillBadge(batteryThresholdValue, R.color.brand_soft, R.color.brand_on_soft);
        batteryThresholdValue.setText(String.format(Locale.ROOT, "%d%%", savedThreshold));
        thresholdRow.addView(batteryThresholdValue);
        detailsContainer.addView(thresholdRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        batteryThresholdSeekBar = new SeekBar(this);
        batteryThresholdSeekBar.setMin(1);
        batteryThresholdSeekBar.setMax(100);
        batteryThresholdSeekBar.setProgress(savedThreshold);
        ColorStateList accentTint = ColorStateList.valueOf(getColor(R.color.brand_primary));
        batteryThresholdSeekBar.setProgressTintList(accentTint);
        batteryThresholdSeekBar.setThumbTintList(accentTint);
        batteryThresholdSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                batteryThresholdValue.setText(String.format(Locale.ROOT, "%d%%", progress));
                SettingsStore.setLowBatteryThreshold(MainActivity.this, progress);
                updateBatteryBadge();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (SettingsStore.isLowBatteryAlertEnabled(MainActivity.this)) {
                    BatteryCheckJobService.scheduleImmediateCheck(MainActivity.this);
                }
            }
        });
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.topMargin = dp(8);
        detailsContainer.addView(batteryThresholdSeekBar, seekParams);

        TextView criticalHint = new TextView(this);
        criticalHint.setText(R.string.low_battery_critical_hint);
        criticalHint.setTextSize(12f);
        criticalHint.setTextColor(getColor(R.color.text_hint));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(6);
        detailsContainer.addView(criticalHint, hintParams);

        card.addView(detailsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        lowBatterySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!hasTelegramSettings()) {
                    setStatusMessage(R.string.low_battery_config_missing, BannerStyle.ERROR);
                    lowBatterySwitch.setChecked(false);
                    detailsContainer.setVisibility(View.GONE);
                    return;
                }
                detailsContainer.setVisibility(View.VISIBLE);
                startBatteryMonitoring(true);
            } else {
                detailsContainer.setVisibility(View.GONE);
                SettingsStore.setLowBatteryAlertEnabled(this, false);
                BatteryCheckJobService.cancel(this);
                updateBatteryBadge();
                setStatusMessage(R.string.low_battery_monitoring_off, BannerStyle.INFO);
            }
        });

        return card;
    }

    private LinearLayout createCardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        card.setLayoutParams(params);
        return card;
    }

    private TextView createSectionTitle(int textResId) {
        TextView title = new TextView(this);
        title.setText(textResId);
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getColor(R.color.text_primary));
        return title;
    }

    private TextView createFieldLabel(int textResId, int topMarginPx) {
        TextView label = new TextView(this);
        label.setText(textResId);
        label.setTextSize(13f);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setTextColor(getColor(R.color.text_secondary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMarginPx;
        params.bottomMargin = dp(6);
        label.setLayoutParams(params);
        return label;
    }

    private EditText createStyledInput(int hintResId, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hintResId);
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setTextSize(15f);
        input.setTextColor(getColor(R.color.text_primary));
        input.setHintTextColor(getColor(R.color.text_hint));
        input.setBackgroundResource(R.drawable.bg_input);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setMinHeight(dp(48));
        input.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setSaveEnabled(false);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        return input;
    }

    private LinearLayout.LayoutParams createInputLayoutParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private Button createActionButton() {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setStateListAnimator(null);
        button.setTextSize(15f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), 0, dp(16), 0);
        return button;
    }

    private Switch createStyledSwitch(int textResId, boolean checked) {
        Switch toggle = new Switch(this);
        if (textResId != 0) {
            toggle.setText(textResId);
        }
        toggle.setChecked(checked);
        toggle.setTextSize(14.5f);
        toggle.setTextColor(getColor(R.color.text_primary));
        toggle.setMinHeight(dp(40));
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        toggle.setThumbTintList(new ColorStateList(states, new int[]{
                getColor(R.color.brand_primary),
                getColor(R.color.text_hint)
        }));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{
                getColor(R.color.brand_soft_pressed),
                getColor(R.color.border_input)
        }));
        return toggle;
    }

    private TextView createPillBadge() {
        TextView badge = new TextView(this);
        badge.setTextSize(12f);
        badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        return badge;
    }

    private void stylePillBadge(TextView badge, int bgColorRes, int textColorRes) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(getColor(bgColorRes));
        background.setCornerRadius(dp(999));
        badge.setBackground(background);
        badge.setTextColor(getColor(textColorRes));
    }

    private void setStatusMessage(int messageResId, BannerStyle style) {
        setStatusMessage(getString(messageResId), style);
    }

    private void setStatusMessage(String message, BannerStyle style) {
        if (statusText == null) {
            return;
        }
        int bgColor;
        int borderColor;
        int textColor;
        if (style == BannerStyle.SUCCESS) {
            bgColor = getColor(R.color.banner_success_bg);
            borderColor = getColor(R.color.banner_success_border);
            textColor = getColor(R.color.banner_success_text);
        } else if (style == BannerStyle.ERROR) {
            bgColor = getColor(R.color.banner_error_bg);
            borderColor = getColor(R.color.banner_error_border);
            textColor = getColor(R.color.banner_error_text);
        } else {
            bgColor = getColor(R.color.banner_info_bg);
            borderColor = getColor(R.color.banner_info_border);
            textColor = getColor(R.color.banner_info_text);
        }

        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(bgColor);
        bannerBg.setStroke(dp(1), borderColor);
        bannerBg.setCornerRadius(dp(12));
        statusText.setBackground(bannerBg);
        statusText.setTextColor(textColor);
        statusText.setText(message);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateForwardingUi();
        updateBatteryBadge();
        refreshTimezoneCard();
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    private void updateForwardingUi() {
        boolean enabled = SettingsStore.isForwardingEnabled(this);
        if (forwardingButton != null) {
            forwardingButton.setText(enabled
                    ? R.string.forwarding_enabled_button : R.string.forwarding_disabled_button);
            forwardingButton.setBackgroundResource(
                    enabled ? R.drawable.bg_btn_danger : R.drawable.bg_btn_primary);
            forwardingButton.setTextColor(getColor(
                    enabled ? R.color.danger_text : R.color.text_on_primary));
        }
        if (forwardingBadge != null) {
            forwardingBadge.setText(enabled
                    ? "● " + getString(R.string.forwarding_badge_on)
                    : "○ " + getString(R.string.forwarding_badge_off));
            stylePillBadge(
                    forwardingBadge,
                    enabled ? R.color.status_on_bg : R.color.status_off_bg,
                    enabled ? R.color.status_on_text : R.color.status_off_text);
        }
    }

    private void updateBatteryBadge() {
        if (batteryBadge == null) {
            return;
        }
        boolean enabled = SettingsStore.isLowBatteryAlertEnabled(this);
        if (enabled) {
            int threshold = SettingsStore.getLowBatteryThreshold(this);
            batteryBadge.setText(getString(R.string.battery_badge_on, threshold));
            stylePillBadge(batteryBadge, R.color.brand_soft, R.color.brand_on_soft);
            batteryBadge.setVisibility(View.VISIBLE);
        } else {
            batteryBadge.setVisibility(View.GONE);
        }
    }

    private void toggleForwarding() {
        if (SettingsStore.isForwardingEnabled(this)) {
            SettingsStore.setForwardingEnabled(this, false);
            updateForwardingUi();
            setStatusMessage(R.string.status_forwarding_off, BannerStyle.INFO);
            return;
        }

        try {
            if (SettingsStore.getToken(this).isEmpty() || SettingsStore.getChatId(this).isEmpty()) {
                setStatusMessage(R.string.status_config_missing, BannerStyle.ERROR);
                return;
            }
        } catch (Exception exception) {
            setStatusMessage(R.string.status_config_read_error, BannerStyle.ERROR);
            return;
        }

        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            enableForwarding();
        } else {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 100);
        }
    }

    private void enableForwarding() {
        SettingsStore.setForwardingEnabled(this, true);
        updateForwardingUi();
        setStatusMessage(R.string.status_forwarding_on, BannerStyle.SUCCESS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableForwarding();
            } else {
                setStatusMessage(R.string.status_sms_permission_denied, BannerStyle.ERROR);
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

    private void startBatteryMonitoring(boolean showStatusFeedback) {
        SettingsStore.setLowBatteryAlertEnabled(this, true);
        boolean scheduled = BatteryCheckJobService.schedule(this, showStatusFeedback);
        if (scheduled) {
            updateBatteryBadge();
            if (showStatusFeedback) {
                setStatusMessage(R.string.low_battery_monitoring_on, BannerStyle.SUCCESS);
            }
        } else {
            SettingsStore.setLowBatteryAlertEnabled(this, false);
            lowBatterySwitch.setChecked(false);
            updateBatteryBadge();
            setStatusMessage(R.string.low_battery_monitor_start_failed, BannerStyle.ERROR);
        }
    }

    private void restoreBatteryMonitoring() {
        if (!SettingsStore.isLowBatteryAlertEnabled(this)) {
            return;
        }
        startBatteryMonitoring(false);
    }

    private void sendTestMessage() {
        String enteredToken = tokenInput.getText().toString().trim();
        String chatId = chatIdInput.getText().toString().trim();
        String token = enteredToken;
        if (token.isEmpty()) {
            try {
                token = SettingsStore.getToken(this);
            } catch (Exception exception) {
                setStatusMessage(R.string.status_config_read_error, BannerStyle.ERROR);
                return;
            }
        }
        if (token.isEmpty() || chatId.isEmpty()) {
            setStatusMessage(R.string.status_config_missing, BannerStyle.ERROR);
            return;
        }
        if (!TelegramClient.isValidToken(token)) {
            setStatusMessage(R.string.status_invalid_token, BannerStyle.ERROR);
            return;
        }
        if (!TelegramClient.isValidChatId(chatId)) {
            setStatusMessage(R.string.status_invalid_chat_id, BannerStyle.ERROR);
            return;
        }

        try {
            SettingsStore.save(this, token, chatId);
            tokenInput.setText("");
            tokenInput.setHint(R.string.bot_token_saved_hint);
        } catch (Exception exception) {
            setStatusMessage(R.string.status_config_save_error, BannerStyle.ERROR);
            return;
        }

        setStatusMessage(R.string.status_test_sending, BannerStyle.INFO);
        String tokenToSend = token;
        executor.execute(() -> {
            boolean sent = false;
            try {
                sent = TelegramClient.sendMessage(
                        tokenToSend, chatId, getString(R.string.test_message));
            } catch (Exception ignored) {
                sent = false;
            }
            boolean finalSent = sent;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    setStatusMessage(
                            finalSent ? R.string.status_test_sent : R.string.status_test_failed,
                            finalSent ? BannerStyle.SUCCESS : BannerStyle.ERROR);
                }
            });
        });
    }

    private void showTimezonePicker() {
        String savedTimezoneId = SettingsStore.getExtraTimezone(this);

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(18), dp(10), dp(18), dp(6));

        LinearLayout searchContainer = new LinearLayout(this);
        searchContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchContainer.setGravity(Gravity.CENTER_VERTICAL);
        searchContainer.setBackgroundResource(R.drawable.bg_input);
        searchContainer.setPadding(dp(12), dp(4), dp(8), dp(4));

        EditText searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint(R.string.timezone_search_hint);
        searchInput.setTextSize(14f);
        searchInput.setTextColor(getColor(R.color.text_primary));
        searchInput.setHintTextColor(getColor(R.color.text_hint));
        searchInput.setBackground(null);
        searchInput.setPadding(dp(2), dp(8), dp(4), dp(8));
        searchContainer.addView(searchInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clearButton = new TextView(this);
        clearButton.setText("×");
        clearButton.setTextSize(18f);
        clearButton.setTextColor(getColor(R.color.text_secondary));
        clearButton.setGravity(Gravity.CENTER);
        clearButton.setPadding(dp(8), dp(4), dp(8), dp(4));
        clearButton.setVisibility(View.GONE);
        clearButton.setOnClickListener(view -> searchInput.setText(""));
        searchContainer.addView(clearButton);

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.bottomMargin = dp(10);
        dialogLayout.addView(searchContainer, searchParams);

        TextView popularTitle = new TextView(this);
        popularTitle.setText(R.string.timezone_popular_label);
        popularTitle.setTextSize(12f);
        popularTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        popularTitle.setTextColor(getColor(R.color.text_secondary));
        LinearLayout.LayoutParams popularTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        popularTitleParams.bottomMargin = dp(6);
        dialogLayout.addView(popularTitle, popularTitleParams);

        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipScroll.addView(chipRow);
        LinearLayout.LayoutParams chipScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipScrollParams.bottomMargin = dp(10);
        dialogLayout.addView(chipScroll, chipScrollParams);

        View listDivider = new View(this);
        listDivider.setBackgroundColor(getColor(R.color.divider));
        dialogLayout.addView(listDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        ListView timezoneList = new ListView(this);
        timezoneList.setFastScrollEnabled(true);
        timezoneList.setVerticalScrollBarEnabled(true);
        timezoneList.setDivider(new ColorDrawable(getColor(R.color.divider)));
        timezoneList.setDividerHeight(dp(1));

        List<TimezoneUtils.TimezoneOption> timezoneOptions =
                TimezoneUtils.getAvailableOptions(savedTimezoneId);
        TimezoneAdapter timezoneAdapter =
                new TimezoneAdapter(this, timezoneOptions, savedTimezoneId);
        timezoneList.setAdapter(timezoneAdapter);

        TextView emptyView = new TextView(this);
        emptyView.setText(R.string.timezone_empty_result);
        emptyView.setTextSize(14f);
        emptyView.setTextColor(getColor(R.color.text_secondary));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(16), dp(32), dp(16), dp(32));
        emptyView.setVisibility(View.GONE);
        timezoneList.setEmptyView(emptyView);

        dialogLayout.addView(timezoneList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        dialogLayout.addView(emptyView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.timezone_dialog_title)
                .setView(dialogLayout)
                .setNegativeButton(R.string.cancel, null)
                .create();

        for (TimezoneUtils.TimezoneOption popular : TimezoneUtils.getPopularOptions()) {
            boolean selected = popular.id.equals(savedTimezoneId);
            TextView chip = new TextView(this);
            chip.setText(popular.locationName);
            chip.setTextSize(12.5f);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            chip.setPadding(dp(12), dp(6), dp(12), dp(6));
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setCornerRadius(dp(999));
            if (selected) {
                chipBg.setColor(getColor(R.color.brand_primary));
                chip.setTextColor(getColor(R.color.text_on_primary));
            } else {
                chipBg.setColor(getColor(R.color.brand_soft));
                chip.setTextColor(getColor(R.color.brand_on_soft));
            }
            chip.setBackground(chipBg);
            chip.setOnClickListener(view -> {
                SettingsStore.setExtraTimezone(this, popular.id);
                refreshTimezoneCard();
                dialog.dismiss();
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipParams.setMarginEnd(dp(8));
            chipRow.addView(chip, chipParams);
        }

        timezoneList.setOnItemClickListener((parent, view, position, id) -> {
            TimezoneUtils.TimezoneOption option = timezoneAdapter.getItem(position);
            if (option != null) {
                SettingsStore.setExtraTimezone(this, option.id);
                refreshTimezoneCard();
                dialog.dismiss();
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                boolean hasQuery = text != null && text.length() > 0;
                clearButton.setVisibility(hasQuery ? View.VISIBLE : View.GONE);
                popularTitle.setVisibility(hasQuery ? View.GONE : View.VISIBLE);
                chipScroll.setVisibility(hasQuery ? View.GONE : View.VISIBLE);
                timezoneAdapter.getFilter().filter(text);
            }

            @Override
            public void afterTextChanged(Editable text) {
            }
        });

        int initialIndex = -1;
        for (int i = 0; i < timezoneOptions.size(); i++) {
            if (timezoneOptions.get(i).id.equals(savedTimezoneId)) {
                initialIndex = i;
                break;
            }
        }
        int scrollTarget = initialIndex;
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                int width = getResources().getDisplayMetrics().widthPixels;
                int height = getResources().getDisplayMetrics().heightPixels;
                dialog.getWindow().setLayout((int) (width * 0.92), (int) (height * 0.78));
            }
            if (scrollTarget >= 0) {
                timezoneList.setSelection(Math.max(0, scrollTarget - 2));
            }
        });
        dialog.show();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class TimezoneAdapter extends ArrayAdapter<TimezoneUtils.TimezoneOption>
            implements Filterable {
        private final List<TimezoneUtils.TimezoneOption> allOptions;
        private final String selectedTimezoneId;
        private final Filter timezoneFilter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String query = constraint == null ? "" : constraint.toString()
                        .replace("*", "")
                        .trim()
                        .toLowerCase(Locale.ROOT);
                List<TimezoneUtils.TimezoneOption> matches = new ArrayList<>();
                for (TimezoneUtils.TimezoneOption option : allOptions) {
                    if (query.isEmpty() || option.searchText.contains(query)) {
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
                if (results.values != null) {
                    addAll((List<TimezoneUtils.TimezoneOption>) results.values);
                }
                notifyDataSetChanged();
            }
        };

        private TimezoneAdapter(
                Context context,
                List<TimezoneUtils.TimezoneOption> options,
                String selectedTimezoneId) {
            super(context, 0, new ArrayList<>(options));
            this.allOptions = new ArrayList<>(options);
            this.selectedTimezoneId = selectedTimezoneId;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder holder;
            View row = convertView;
            if (row == null) {
                LinearLayout container = new LinearLayout(getContext());
                container.setOrientation(LinearLayout.HORIZONTAL);
                container.setGravity(Gravity.CENTER_VERTICAL);
                container.setPadding(dp(10), dp(11), dp(10), dp(11));

                LinearLayout leftCol = new LinearLayout(getContext());
                leftCol.setOrientation(LinearLayout.VERTICAL);

                TextView cityView = new TextView(getContext());
                cityView.setTextSize(15f);
                cityView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                cityView.setSingleLine(true);
                cityView.setEllipsize(TextUtils.TruncateAt.END);
                leftCol.addView(cityView);

                TextView idView = new TextView(getContext());
                idView.setTextSize(12f);
                idView.setTextColor(getColor(R.color.text_secondary));
                idView.setSingleLine(true);
                idView.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams idParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                idParams.topMargin = dp(2);
                leftCol.addView(idView, idParams);

                container.addView(leftCol, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                LinearLayout rightCol = new LinearLayout(getContext());
                rightCol.setOrientation(LinearLayout.VERTICAL);
                rightCol.setGravity(Gravity.END);
                rightCol.setPadding(dp(8), 0, 0, 0);

                TextView offsetView = new TextView(getContext());
                offsetView.setTextSize(12.5f);
                offsetView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                rightCol.addView(offsetView);

                TextView timeView = new TextView(getContext());
                timeView.setTextSize(12f);
                timeView.setTextColor(getColor(R.color.text_secondary));
                LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                timeParams.topMargin = dp(2);
                rightCol.addView(timeView, timeParams);

                container.addView(rightCol);

                TextView checkView = new TextView(getContext());
                checkView.setText("✓");
                checkView.setTextSize(16f);
                checkView.setTypeface(Typeface.DEFAULT_BOLD);
                checkView.setTextColor(getColor(R.color.brand_primary));
                checkView.setPadding(dp(10), 0, dp(2), 0);
                container.addView(checkView);

                holder = new RowHolder(cityView, idView, offsetView, timeView, checkView);
                container.setTag(holder);
                row = container;
            } else {
                holder = (RowHolder) row.getTag();
            }

            TimezoneUtils.TimezoneOption option = getItem(position);
            if (option != null) {
                boolean selected = option.id.equals(selectedTimezoneId);
                holder.cityView.setText(option.locationName);
                holder.cityView.setTextColor(getColor(
                        selected ? R.color.brand_on_soft : R.color.text_primary));
                holder.idView.setText(option.id);
                holder.offsetView.setText(option.offsetText);
                holder.offsetView.setTextColor(getColor(
                        selected ? R.color.brand_primary : R.color.text_secondary));
                holder.timeView.setText(option.currentTimeText);
                holder.checkView.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
                row.setBackgroundColor(selected
                        ? getColor(R.color.brand_soft)
                        : android.graphics.Color.TRANSPARENT);
            }
            return row;
        }

        @Override
        public Filter getFilter() {
            return timezoneFilter;
        }
    }

    private static final class RowHolder {
        final TextView cityView;
        final TextView idView;
        final TextView offsetView;
        final TextView timeView;
        final TextView checkView;

        private RowHolder(
                TextView cityView,
                TextView idView,
                TextView offsetView,
                TextView timeView,
                TextView checkView) {
            this.cityView = cityView;
            this.idView = idView;
            this.offsetView = offsetView;
            this.timeView = timeView;
            this.checkView = checkView;
        }
    }
}
