package com.xjtucsse.meetingassistant;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MeetingInfo {
    public static final String ROLE_ORGANIZER = "organizer";
    public static final String ROLE_ATTENDEE = "attendee";
    public static final String ATTACHMENT_ACCESS_ORGANIZER = "uploader_only";
    public static final String ATTACHMENT_ACCESS_ALL_ATTENDEES = "all_attendees";
    public static final String ATTACHMENT_ACCESS_SELECTED_ATTENDEES = "selected_attendees";

    private static final String LEGACY_ATTACHMENT_ACCESS_ORGANIZER = "organizer_only";
    private static final String QR_PREFIX = "MTAS2";
    private static final String QR_DELIMITER = "$,!$";
    private static final Locale DEFAULT_LOCALE = Locale.CHINA;

    public String meetingID;
    public String meetingTopic;
    public String organizer;
    public String locationName;
    public Calendar meetingStartTime;
    public Calendar meetingEndTime;
    public String meetingNote;
    public int reminderMinutes;
    public int checkInRadiusMeters;
    public double latitude;
    public double longitude;
    public List<AttendeeInfo> attendees;
    public String imageUri;
    public String audioPath;

    // Legacy single-attachment fields kept for local migration/backward compatibility.
    public String attachmentUri;
    public String attachmentName;
    public String attachmentMime;
    public String attachmentAccessMode;
    public List<String> attachmentAllowedUsers;
    public boolean attachmentAvailable;
    public String attachmentContentBase64;

    public List<SharedMaterialInfo> sharedMaterials;
    public String selfCheckInStatus;
    public String selfCheckInTime;
    public String shareCode;
    public String memberRole;

    public MeetingInfo() {
        meetingStartTime = Calendar.getInstance();
        meetingEndTime = Calendar.getInstance();
        meetingNote = "";
        organizer = "";
        locationName = "";
        reminderMinutes = 15;
        checkInRadiusMeters = 100;
        latitude = Double.NaN;
        longitude = Double.NaN;
        attendees = new ArrayList<>();
        imageUri = "";
        audioPath = "";
        attachmentUri = "";
        attachmentName = "";
        attachmentMime = "";
        attachmentAccessMode = ATTACHMENT_ACCESS_ALL_ATTENDEES;
        attachmentAllowedUsers = new ArrayList<>();
        attachmentAvailable = false;
        attachmentContentBase64 = "";
        sharedMaterials = new ArrayList<>();
        selfCheckInStatus = AttendeeInfo.STATUS_PENDING;
        selfCheckInTime = "";
        shareCode = "";
        memberRole = ROLE_ATTENDEE;
    }

    public MeetingInfo(String topic, Calendar startTime, Calendar endTime, String note) {
        this();
        meetingTopic = topic;
        meetingStartTime = cloneCalendar(startTime);
        meetingEndTime = cloneCalendar(endTime);
        meetingNote = note;
        ensureMeetingId();
    }

    public void ensureMeetingId() {
        if (meetingID == null || meetingID.trim().isEmpty()) {
            meetingID = getMeetingID(safe(meetingTopic) + getStartTimeText() + getEndTimeText());
        }
    }

    public String getStartTimeText() {
        return formatDateTime(meetingStartTime);
    }

    public String getEndTimeText() {
        return formatDateTime(meetingEndTime);
    }

    public boolean hasLocationGate() {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude) && checkInRadiusMeters > 0;
    }

    public int getExpectedCount() {
        return attendees.size();
    }

    public int getPresentCount() {
        return countByStatus(AttendeeInfo.STATUS_PRESENT);
    }

    public int getLateCount() {
        return countByStatus(AttendeeInfo.STATUS_LATE);
    }

    public int getAbsentCount() {
        return countByStatus(AttendeeInfo.STATUS_ABSENT);
    }

    public boolean isOrganizer() {
        return ROLE_ORGANIZER.equalsIgnoreCase(safe(memberRole));
    }

    public boolean hasSharedAttachment() {
        return hasSharedMaterials() || !safe(attachmentName).trim().isEmpty();
    }

    public boolean hasSharedMaterials() {
        return sharedMaterials != null && !sharedMaterials.isEmpty();
    }

    public boolean canOpenAttachment() {
        if (hasSharedMaterials()) {
            for (SharedMaterialInfo material : sharedMaterials) {
                if (material != null && material.canOpen()) {
                    return true;
                }
            }
            return false;
        }
        return !safe(attachmentName).trim().isEmpty()
                && attachmentAvailable
                && !safe(attachmentUri).trim().isEmpty();
    }

    public String getAttachmentAccessLabel() {
        return getAttachmentAccessLabel(attachmentAccessMode);
    }

    public static String getAttachmentAccessLabel(String rawValue) {
        String normalized = normalizeAttachmentAccessMode(rawValue);
        if (ATTACHMENT_ACCESS_ORGANIZER.equals(normalized)) {
            return "仅上传者可查看";
        }
        if (ATTACHMENT_ACCESS_SELECTED_ATTENDEES.equals(normalized)) {
            return "指定参会人可查看";
        }
        return "所有参会人可查看";
    }

    public void copyLocalArtifactsFrom(MeetingInfo existingMeeting) {
        if (existingMeeting == null) {
            return;
        }
        if (safe(imageUri).isEmpty()) {
            imageUri = safe(existingMeeting.imageUri);
        }
        if (safe(audioPath).isEmpty()) {
            audioPath = safe(existingMeeting.audioPath);
        }
    }

    public void syncLegacyAttachmentFieldsFromMaterials() {
        ensureSharedMaterials();
        SharedMaterialInfo firstMaterial = sharedMaterials.isEmpty() ? null : sharedMaterials.get(0);
        if (firstMaterial == null || !firstMaterial.hasFile()) {
            attachmentUri = "";
            attachmentName = "";
            attachmentMime = "";
            attachmentAccessMode = ATTACHMENT_ACCESS_ALL_ATTENDEES;
            attachmentAllowedUsers = new ArrayList<>();
            attachmentAvailable = false;
            attachmentContentBase64 = "";
            return;
        }
        attachmentUri = safe(firstMaterial.localUri);
        attachmentName = safe(firstMaterial.name);
        attachmentMime = safe(firstMaterial.mimeType);
        attachmentAccessMode = normalizeAttachmentAccessMode(firstMaterial.accessMode);
        attachmentAllowedUsers = new ArrayList<>(firstMaterial.allowedUsers);
        attachmentAvailable = firstMaterial.available;
        attachmentContentBase64 = safe(firstMaterial.contentBase64);
    }

    public void ensureSharedMaterials() {
        if (sharedMaterials == null) {
            sharedMaterials = new ArrayList<>();
        }
    }

    public String toQrPayload() {
        ensureMeetingId();
        String[] parts = new String[]{
                QR_PREFIX,
                meetingID,
                safe(meetingTopic),
                safe(organizer),
                safe(locationName),
                getStartTimeText(),
                getEndTimeText(),
                String.valueOf(reminderMinutes),
                String.valueOf(checkInRadiusMeters),
                Double.isNaN(latitude) ? "" : String.valueOf(latitude),
                Double.isNaN(longitude) ? "" : String.valueOf(longitude)
        };
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append(QR_DELIMITER);
            }
            builder.append(encode(parts[i]));
        }
        return builder.toString();
    }

    public static MeetingInfo fromQrPayload(String payload) {
        String normalizedPayload = normalizePayload(payload);
        if (normalizedPayload.isEmpty()) {
            return null;
        }
        String[] parts = normalizedPayload.split("\\$,!\\$", -1);
        if (parts.length < 11) {
            return null;
        }
        if (!QR_PREFIX.equals(decode(parts[0]).trim())) {
            return null;
        }

        MeetingInfo info = new MeetingInfo();
        info.meetingID = decode(parts[1]);
        info.meetingTopic = decode(parts[2]);
        info.organizer = decode(parts[3]);
        info.locationName = decode(parts[4]);
        info.meetingStartTime = parseDateTime(decode(parts[5]));
        info.meetingEndTime = parseDateTime(decode(parts[6]));
        info.reminderMinutes = parseInteger(decode(parts[7]), 15);
        info.checkInRadiusMeters = parseInteger(decode(parts[8]), 100);
        info.latitude = parseDouble(decode(parts[9]));
        info.longitude = parseDouble(decode(parts[10]));
        info.ensureMeetingId();
        return info;
    }

    public static Calendar parseDateTime(String dateTime) {
        Calendar calendar = Calendar.getInstance();
        if (dateTime == null || dateTime.trim().isEmpty()) {
            return calendar;
        }
        try {
            calendar.setTime(getDateFormat().parse(dateTime));
        } catch (ParseException ignored) {
        }
        return calendar;
    }

    public static String formatDateTime(Calendar calendar) {
        Calendar safeCalendar = calendar == null ? Calendar.getInstance() : calendar;
        return getDateFormat().format(safeCalendar.getTime());
    }

    public static String getMeetingID(String key) {
        char[] hexDigits = {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'A', 'B', 'C', 'D', 'E', 'F'
        };
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(key.getBytes());
            byte[] digest = messageDigest.digest();
            char[] result = new char[digest.length * 2];
            int index = 0;
            for (byte value : digest) {
                result[index++] = hexDigits[(value >>> 4) & 0x0F];
                result[index++] = hexDigits[value & 0x0F];
            }
            return new String(result);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static Calendar cloneCalendar(Calendar original) {
        Calendar copy = Calendar.getInstance();
        if (original != null) {
            copy.setTimeInMillis(original.getTimeInMillis());
        }
        return copy;
    }

    private int countByStatus(String status) {
        int count = 0;
        for (AttendeeInfo attendee : attendees) {
            if (status.equals(AttendeeInfo.normalizeStatus(attendee.status))) {
                count++;
            }
        }
        return count;
    }

    private static int parseInteger(String rawValue, int defaultValue) {
        try {
            return Integer.parseInt(rawValue);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static double parseDouble(String rawValue) {
        try {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return Double.NaN;
            }
            return Double.parseDouble(rawValue);
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static String normalizeAttachmentAccessMode(String rawValue) {
        String value = safe(rawValue).trim();
        if (LEGACY_ATTACHMENT_ACCESS_ORGANIZER.equalsIgnoreCase(value)) {
            return ATTACHMENT_ACCESS_ORGANIZER;
        }
        if (ATTACHMENT_ACCESS_ORGANIZER.equalsIgnoreCase(value)) {
            return ATTACHMENT_ACCESS_ORGANIZER;
        }
        if (ATTACHMENT_ACCESS_SELECTED_ATTENDEES.equalsIgnoreCase(value)) {
            return ATTACHMENT_ACCESS_SELECTED_ATTENDEES;
        }
        return ATTACHMENT_ACCESS_ALL_ATTENDEES;
    }

    public static String serializeUsernames(List<String> usernames) {
        StringBuilder builder = new StringBuilder();
        if (usernames == null) {
            return "";
        }
        for (String username : usernames) {
            String normalized = safe(username).trim().toLowerCase(DEFAULT_LOCALE);
            if (normalized.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(normalized);
        }
        return builder.toString();
    }

    public static List<String> deserializeUsernames(String rawValue) {
        List<String> usernames = new ArrayList<>();
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return usernames;
        }
        String[] items = rawValue.split("[\\n,]");
        for (String item : items) {
            String normalized = safe(item).trim().toLowerCase(DEFAULT_LOCALE);
            if (!normalized.isEmpty() && !usernames.contains(normalized)) {
                usernames.add(normalized);
            }
        }
        return usernames;
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

    private static SimpleDateFormat getDateFormat() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", DEFAULT_LOCALE);
    }
}
