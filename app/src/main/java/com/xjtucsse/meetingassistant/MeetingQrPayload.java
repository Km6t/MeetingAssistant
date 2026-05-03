package com.xjtucsse.meetingassistant;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;

public class MeetingQrPayload {
    private static final String PREFIX = "MTAS3";
    private static final String DELIMITER = "$,!$";

    public final String meetingId;
    public final String shareCode;

    public MeetingQrPayload(String id, String code) {
        meetingId = id == null ? "" : id;
        shareCode = code == null ? "" : code;
    }

    public static String encode(String meetingId, String shareCode) {
        return PREFIX + DELIMITER + encodePart(meetingId) + DELIMITER + encodePart(shareCode);
    }

    public static MeetingQrPayload decode(String payload) {
        String normalized = normalizePayload(payload);
        if (normalized.isEmpty()) {
            return null;
        }
        String[] parts = normalized.split("\\$,!\\$", -1);
        if (parts.length == 3 && PREFIX.equalsIgnoreCase(parts[0])) {
            return new MeetingQrPayload(decodePart(parts[1]), decodePart(parts[2]));
        }
        if (looksLikeShareCode(normalized)) {
            return new MeetingQrPayload("", normalized.toUpperCase(Locale.US));
        }
        return null;
    }

    private static String encodePart(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static String decodePart(String value) {
        try {
            return URLDecoder.decode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static boolean looksLikeShareCode(String value) {
        return value.matches("(?i)[a-f0-9]{8}");
    }

    private static String normalizePayload(String payload) {
        if (payload == null) {
            return "";
        }
        return payload
                .replace("\uFEFF", "")
                .replace("\u200B", "")
                .replace("\u200C", "")
                .replace("\u200D", "")
                .replace("\u2060", "")
                .trim();
    }
}
