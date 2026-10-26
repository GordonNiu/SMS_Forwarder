package com.smsforwarder;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText tokenInput;
    private EditText chatIdInput;
    private TextView statusText;
    private Button forwardingButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("Telegram 连接测试");
        title.setTextSize(24f);
        layout.addView(title);

        tokenInput = new EditText(this);
        tokenInput.setHint("Bot Token（不会显示明文）");
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(tokenInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        chatIdInput = new EditText(this);
        chatIdInput.setHint("Chat ID（数字）");
        chatIdInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        layout.addView(chatIdInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button testButton = new Button(this);
        testButton.setText("保存并发送测试消息");
        testButton.setOnClickListener(view -> sendTestMessage());
        layout.addView(testButton);

        forwardingButton = new Button(this);
        forwardingButton.setOnClickListener(view -> toggleForwarding());
        layout.addView(forwardingButton);

        statusText = new TextView(this);
        statusText.setText("填写 Token 和 Chat ID 后，可以测试 Telegram 连接。");
        statusText.setTextColor(Color.DKGRAY);
        layout.addView(statusText);

        try {
            tokenInput.setText(SettingsStore.getToken(this));
            chatIdInput.setText(SettingsStore.getChatId(this));
        } catch (Exception exception) {
            statusText.setText("无法读取本机加密配置，请重新填写并测试。");
        }

        updateForwardingButton();
        setContentView(layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateForwardingButton();
    }

    private void updateForwardingButton() {
        if (forwardingButton != null) {
            forwardingButton.setText(SettingsStore.isForwardingEnabled(this)
                    ? "关闭短信自动转发" : "开启短信自动转发");
        }
    }

    private void toggleForwarding() {
        if (SettingsStore.isForwardingEnabled(this)) {
            SettingsStore.setForwardingEnabled(this, false);
            updateForwardingButton();
            statusText.setText("短信自动转发已关闭。");
            return;
        }

        try {
            if (SettingsStore.getToken(this).isEmpty() || SettingsStore.getChatId(this).isEmpty()) {
                statusText.setText("请先填写配置并成功发送测试消息。");
                return;
            }
        } catch (Exception exception) {
            statusText.setText("无法读取加密配置，请重新填写并测试。");
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
        statusText.setText("短信自动转发已开启。新收到的短信会发送到你的 Telegram。");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableForwarding();
            } else {
                statusText.setText("未获得短信权限，自动转发没有开启。");
            }
        }
    }

    private void sendTestMessage() {
        String token = tokenInput.getText().toString().trim();
        String chatId = chatIdInput.getText().toString().trim();
        if (token.isEmpty() || chatId.isEmpty()) {
            statusText.setText("请先填写 Bot Token 和 Chat ID。");
            return;
        }

        try {
            SettingsStore.save(this, token, chatId);
        } catch (Exception exception) {
            statusText.setText("配置无法安全保存，请重启 App 后重试。");
            return;
        }

        statusText.setText("正在发送测试消息……");
        executor.execute(() -> {
            String result;
            try {
                result = TelegramClient.sendMessage(token, chatId, "SMS Forwarder App 测试消息");
            } catch (Exception exception) {
                result = "发送失败，请检查网络和 Telegram 配置。";
            }
            String finalResult = result;
            runOnUiThread(() -> statusText.setText(finalResult));
        });
    }

}
