package com.xjtucsse.meetingassistant;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREFS_NAME = "meeting_assistant_cloud";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DISPLAY_NAME = "display_name";

    public static final String DEFAULT_SERVER_URL = "http://159.75.95.19";

    private final SharedPreferences preferences;

    public SessionManager(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getServerUrl() {
        return normalizeServerUrl(preferences.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL));
    }

    public void saveServerUrl(String serverUrl) {
        preferences.edit().putString(KEY_SERVER_URL, normalizeServerUrl(serverUrl)).apply();
    }

    public boolean isLoggedIn() {
        return !getAuthToken().isEmpty() && !getUsername().isEmpty();
    }

    public String getAuthToken() {
        return safe(preferences.getString(KEY_TOKEN, ""));
    }

    public String getUsername() {
        return safe(preferences.getString(KEY_USERNAME, ""));
    }

    public String getDisplayName() {
        String displayName = safe(preferences.getString(KEY_DISPLAY_NAME, ""));
        return displayName.isEmpty() ? getUsername() : displayName;
    }

    public void saveSession(AuthSession session) {
        preferences.edit()
                .putString(KEY_TOKEN, safe(session.token))
                .putString(KEY_USERNAME, safe(session.username))
                .putString(KEY_DISPLAY_NAME, safe(session.displayName))
                .apply();
    }

    public void clearSession() {
        preferences.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USERNAME)
                .remove(KEY_DISPLAY_NAME)
                .apply();
    }

    private String normalizeServerUrl(String rawUrl) {
        String normalized = safe(rawUrl);
        if (normalized.isEmpty()) {
            normalized = DEFAULT_SERVER_URL;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
