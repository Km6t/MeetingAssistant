package com.xjtucsse.meetingassistant;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AuthActivity extends AppCompatActivity {
    private EditText serverUrlInput;
    private EditText usernameInput;
    private EditText displayNameInput;
    private EditText passwordInput;
    private TextView statusText;
    private Button loginButton;
    private Button registerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        SessionManager sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            openMainScreen();
            return;
        }

        bindViews();
        serverUrlInput.setText(sessionManager.getServerUrl());
        loginButton.setOnClickListener(v -> submit(false));
        registerButton.setOnClickListener(v -> submit(true));
    }

    private void bindViews() {
        serverUrlInput = findViewById(R.id.auth_server_url);
        usernameInput = findViewById(R.id.auth_username);
        displayNameInput = findViewById(R.id.auth_display_name);
        passwordInput = findViewById(R.id.auth_password);
        statusText = findViewById(R.id.auth_status);
        loginButton = findViewById(R.id.auth_login);
        registerButton = findViewById(R.id.auth_register);
    }

    private void submit(boolean registerMode) {
        final String serverUrl = safe(serverUrlInput.getText().toString());
        final String username = safe(usernameInput.getText().toString()).toLowerCase();
        final String displayName = safe(displayNameInput.getText().toString());
        final String password = passwordInput.getText().toString();

        if (serverUrl.isEmpty() || username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请填写服务器地址、用户名和密码。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (registerMode && TextUtils.isEmpty(displayName)) {
            Toast.makeText(this, "注册时请填写显示名称。", Toast.LENGTH_SHORT).show();
            return;
        }

        setBusyState(true, registerMode ? "正在注册并同步会议..." : "正在登录并同步会议...");
        new Thread(() -> {
            try {
                ApiClient apiClient = new ApiClient(AuthActivity.this);
                AuthSession session = registerMode
                        ? apiClient.register(serverUrl, username, password, displayName)
                        : apiClient.login(serverUrl, username, password);
                SessionManager sessionManager = new SessionManager(AuthActivity.this);
                sessionManager.saveServerUrl(serverUrl);
                sessionManager.saveSession(session);
                CloudSyncManager.syncMeetings(AuthActivity.this);
                BackgroundSyncScheduler.schedule(AuthActivity.this);
                runOnUiThread(this::openMainScreen);
            } catch (CloudException e) {
                runOnUiThread(() -> {
                    setBusyState(false, e.getMessage());
                    Toast.makeText(AuthActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void openMainScreen() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void setBusyState(boolean busy, String message) {
        loginButton.setEnabled(!busy);
        registerButton.setEnabled(!busy);
        statusText.setText(message);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
