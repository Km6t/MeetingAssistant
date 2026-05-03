package com.xjtucsse.meetingassistant;

public final class DatabaseInfo {
    public static final String DB_NAME = "MeetingInfo.db";
    public static final int DB_VERSION = 5;
    public static final String TABLE_NAME = "Meetings";

    public static final String COLUMN_MEETING_ID = "meetingID";
    public static final String COLUMN_TOPIC = "topic";
    public static final String COLUMN_START_TIME = "startTime";
    public static final String COLUMN_END_TIME = "endTime";
    public static final String COLUMN_NOTE = "note";
    public static final String COLUMN_ORGANIZER = "organizer";
    public static final String COLUMN_LOCATION_NAME = "locationName";
    public static final String COLUMN_LATITUDE = "latitude";
    public static final String COLUMN_LONGITUDE = "longitude";
    public static final String COLUMN_CHECK_IN_RADIUS = "checkInRadius";
    public static final String COLUMN_REMINDER_MINUTES = "reminderMinutes";
    public static final String COLUMN_ATTENDEES = "attendees";
    public static final String COLUMN_IMAGE_URI = "imageUri";
    public static final String COLUMN_AUDIO_PATH = "audioPath";
    public static final String COLUMN_ATTACHMENT_URI = "attachmentUri";
    public static final String COLUMN_ATTACHMENT_NAME = "attachmentName";
    public static final String COLUMN_ATTACHMENT_MIME = "attachmentMime";
    public static final String COLUMN_ATTACHMENT_ACCESS_MODE = "attachmentAccessMode";
    public static final String COLUMN_ATTACHMENT_ALLOWED_USERS = "attachmentAllowedUsers";
    public static final String COLUMN_ATTACHMENT_AVAILABLE = "attachmentAvailable";
    public static final String COLUMN_SHARED_MATERIALS = "sharedMaterials";
    public static final String COLUMN_SELF_CHECK_IN_STATUS = "selfCheckInStatus";
    public static final String COLUMN_SELF_CHECK_IN_TIME = "selfCheckInTime";
    public static final String COLUMN_SHARE_CODE = "shareCode";
    public static final String COLUMN_MEMBER_ROLE = "memberRole";

    private DatabaseInfo() {
    }
}
