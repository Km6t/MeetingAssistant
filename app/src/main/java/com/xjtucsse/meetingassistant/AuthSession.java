package com.xjtucsse.meetingassistant;

public class AuthSession {
    public final String token;
    public final String username;
    public final String displayName;

    public AuthSession(String sessionToken, String sessionUsername, String sessionDisplayName) {
        token = sessionToken == null ? "" : sessionToken;
        username = sessionUsername == null ? "" : sessionUsername;
        displayName = sessionDisplayName == null ? "" : sessionDisplayName;
    }
}
