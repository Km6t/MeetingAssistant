package com.xjtucsse.meetingassistant;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class AttendeeInfo {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PRESENT = "PRESENT";
    public static final String STATUS_LATE = "LATE";
    public static final String STATUS_ABSENT = "ABSENT";

    private static final String FIELD_DELIMITER = "|";
    private static final String ITEM_DELIMITER = "\n";

    public String username;
    public String name;
    public String status;
    public String checkInTime;

    public AttendeeInfo(String attendeeName) {
        this(attendeeName, attendeeName, STATUS_PENDING, "");
    }

    public AttendeeInfo(String attendeeName, String attendeeStatus, String attendeeCheckInTime) {
        this(attendeeName, attendeeName, attendeeStatus, attendeeCheckInTime);
    }

    public AttendeeInfo(String attendeeUsername, String attendeeName, String attendeeStatus, String attendeeCheckInTime) {
        username = safe(attendeeUsername);
        name = safe(attendeeName);
        status = normalizeStatus(attendeeStatus);
        checkInTime = safe(attendeeCheckInTime);
        if (username.trim().isEmpty()) {
            username = name;
        }
        if (name.trim().isEmpty()) {
            name = username;
        }
    }

    public String serialize() {
        return encode(username)
                + FIELD_DELIMITER
                + encode(name)
                + FIELD_DELIMITER
                + encode(status)
                + FIELD_DELIMITER
                + encode(checkInTime);
    }

    public String getDisplayName() {
        if (username.trim().isEmpty() || username.equalsIgnoreCase(name)) {
            return name;
        }
        return name + " (@" + username + ")";
    }

    public static String normalizeStatus(String rawStatus) {
        String normalized = safe(rawStatus).trim();
        if (normalized.isEmpty()) {
            return STATUS_PENDING;
        }
        if ("未签到".equals(normalized)) {
            return STATUS_PENDING;
        }
        if ("已签到".equals(normalized)) {
            return STATUS_PRESENT;
        }
        if ("迟到".equals(normalized)) {
            return STATUS_LATE;
        }
        if ("缺席".equals(normalized)) {
            return STATUS_ABSENT;
        }
        return normalized.toUpperCase();
    }

    public static String getDisplayStatus(String rawStatus) {
        String normalized = normalizeStatus(rawStatus);
        if (STATUS_PRESENT.equals(normalized)) {
            return "已签到";
        }
        if (STATUS_LATE.equals(normalized)) {
            return "迟到";
        }
        if (STATUS_ABSENT.equals(normalized)) {
            return "缺席";
        }
        return "未签到";
    }

    public static AttendeeInfo deserialize(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return new AttendeeInfo("");
        }
        String[] fields = rawValue.split("\\|", -1);
        if (fields.length >= 4) {
            return new AttendeeInfo(
                    decode(fields[0]),
                    decode(fields[1]),
                    decode(fields[2]),
                    decode(fields[3])
            );
        }
        String attendeeName = fields.length > 0 ? decode(fields[0]) : "";
        String attendeeStatus = fields.length > 1 ? decode(fields[1]) : STATUS_PENDING;
        String attendeeCheckInTime = fields.length > 2 ? decode(fields[2]) : "";
        return new AttendeeInfo(attendeeName, attendeeName, attendeeStatus, attendeeCheckInTime);
    }

    public static String serializeList(List<AttendeeInfo> attendees) {
        StringBuilder builder = new StringBuilder();
        if (attendees == null) {
            return "";
        }
        for (AttendeeInfo attendee : attendees) {
            if (attendee == null) {
                continue;
            }
            String username = safe(attendee.username).trim();
            String name = safe(attendee.name).trim();
            if (username.isEmpty() && name.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(ITEM_DELIMITER);
            }
            builder.append(attendee.serialize());
        }
        return builder.toString();
    }

    public static List<AttendeeInfo> deserializeList(String rawValue) {
        List<AttendeeInfo> attendees = new ArrayList<>();
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return attendees;
        }
        String[] items = rawValue.split(ITEM_DELIMITER, -1);
        for (String item : items) {
            AttendeeInfo attendee = deserialize(item);
            if (!safe(attendee.username).trim().isEmpty() || !safe(attendee.name).trim().isEmpty()) {
                attendees.add(attendee);
            }
        }
        return attendees;
    }

    private static String encode(String rawValue) {
        try {
            return URLEncoder.encode(safe(rawValue), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static String decode(String encodedValue) {
        try {
            return URLDecoder.decode(safe(encodedValue), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
