package com.xjtucsse.meetingassistant;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DatabaseDAO {
    private final Context context;
    private final DatabaseHelper dbHelper;

    public DatabaseDAO(Context context) {
        this.context = context.getApplicationContext();
        dbHelper = new DatabaseHelper(context);
    }

    public void save(MeetingInfo meeting) {
        if (meeting == null) {
            return;
        }
        meeting.ensureMeetingId();
        if (exists(meeting.meetingID)) {
            update(meeting);
        } else {
            insert(meeting);
        }
    }

    public void saveCloudMeeting(MeetingInfo meeting) {
        if (meeting == null) {
            return;
        }
        MeetingInfo existing = getMeeting(meeting.meetingID);
        if (existing != null) {
            meeting.copyLocalArtifactsFrom(existing);
        }
        SharedAttachmentManager.syncSharedMaterials(context, meeting, existing);
        save(meeting);
    }

    public void replaceWithCloudMeetings(List<MeetingInfo> meetings) {
        List<MeetingInfo> safeMeetings = meetings == null ? new ArrayList<>() : meetings;
        Set<String> incomingIds = new HashSet<>();
        for (MeetingInfo meeting : safeMeetings) {
            if (meeting == null) {
                continue;
            }
            incomingIds.add(meeting.meetingID);
            saveCloudMeeting(meeting);
        }
        for (MeetingInfo existingMeeting : listAllMeetings()) {
            if (!incomingIds.contains(existingMeeting.meetingID)) {
                delete(existingMeeting.meetingID);
                ReminderScheduler.cancelReminder(context, existingMeeting.meetingID);
            }
        }
    }

    public void insert(MeetingInfo meeting) {
        if (meeting == null) {
            return;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            db.insertWithOnConflict(
                    DatabaseInfo.TABLE_NAME,
                    null,
                    toValues(meeting),
                    SQLiteDatabase.CONFLICT_REPLACE
            );
        } finally {
            db.close();
        }
    }

    public void insert(String meetingId, String topic, String startTime, String endTime, String note) {
        MeetingInfo meeting = new MeetingInfo();
        meeting.meetingID = meetingId;
        meeting.meetingTopic = topic;
        meeting.meetingStartTime = MeetingInfo.parseDateTime(startTime);
        meeting.meetingEndTime = MeetingInfo.parseDateTime(endTime);
        meeting.meetingNote = note;
        insert(meeting);
    }

    public void update(MeetingInfo meeting) {
        if (meeting == null) {
            return;
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            db.update(
                    DatabaseInfo.TABLE_NAME,
                    toValues(meeting),
                    DatabaseInfo.COLUMN_MEETING_ID + "=?",
                    new String[]{meeting.meetingID}
            );
        } finally {
            db.close();
        }
    }

    public void update(String meetingId, String note) {
        updateNote(meetingId, note);
    }

    public void updateNote(String meetingId, String note) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            ContentValues values = new ContentValues();
            values.put(DatabaseInfo.COLUMN_NOTE, note == null ? "" : note);
            db.update(
                    DatabaseInfo.TABLE_NAME,
                    values,
                    DatabaseInfo.COLUMN_MEETING_ID + "=?",
                    new String[]{meetingId}
            );
        } finally {
            db.close();
        }
    }

    public void delete(String meetingId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            db.delete(
                    DatabaseInfo.TABLE_NAME,
                    DatabaseInfo.COLUMN_MEETING_ID + "=?",
                    new String[]{meetingId}
            );
        } finally {
            db.close();
        }
    }

    public void clearAll() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        try {
            db.delete(DatabaseInfo.TABLE_NAME, null, null);
        } finally {
            db.close();
        }
    }

    public boolean exists(String meetingId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseInfo.TABLE_NAME,
                    new String[]{DatabaseInfo.COLUMN_MEETING_ID},
                    DatabaseInfo.COLUMN_MEETING_ID + "=?",
                    new String[]{meetingId},
                    null,
                    null,
                    null
            );
            return cursor.moveToFirst();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
    }

    public MeetingInfo getMeeting(String meetingId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseInfo.TABLE_NAME,
                    null,
                    DatabaseInfo.COLUMN_MEETING_ID + "=?",
                    new String[]{meetingId},
                    null,
                    null,
                    null
            );
            if (cursor.moveToFirst()) {
                return readMeeting(cursor);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
    }

    public List<MeetingInfo> listUpcomingMeetings() {
        String now = MeetingInfo.formatDateTime(Calendar.getInstance());
        return queryMeetings(
                DatabaseInfo.COLUMN_START_TIME + ">?",
                new String[]{now},
                DatabaseInfo.COLUMN_START_TIME + " ASC"
        );
    }

    public List<MeetingInfo> listOngoingMeetings() {
        String now = MeetingInfo.formatDateTime(Calendar.getInstance());
        return queryMeetings(
                DatabaseInfo.COLUMN_START_TIME + "<=? AND " + DatabaseInfo.COLUMN_END_TIME + ">=?",
                new String[]{now, now},
                DatabaseInfo.COLUMN_START_TIME + " ASC"
        );
    }

    public List<MeetingInfo> listPastMeetings() {
        String now = MeetingInfo.formatDateTime(Calendar.getInstance());
        return queryMeetings(
                DatabaseInfo.COLUMN_END_TIME + "<?",
                new String[]{now},
                DatabaseInfo.COLUMN_START_TIME + " DESC"
        );
    }

    public List<MeetingInfo> listAllMeetings() {
        return queryMeetings(null, null, DatabaseInfo.COLUMN_START_TIME + " ASC");
    }

    public int query_items(String condition) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + DatabaseInfo.TABLE_NAME + " WHERE " + condition,
                    null
            );
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
    }

    public void query() {
        listAllMeetings();
    }

    public void query(String condition) {
        queryMeetings(condition, null, DatabaseInfo.COLUMN_START_TIME + " ASC");
    }

    private List<MeetingInfo> queryMeetings(String selection, String[] selectionArgs, String orderBy) {
        List<MeetingInfo> meetings = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(
                    DatabaseInfo.TABLE_NAME,
                    null,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    orderBy
            );
            while (cursor.moveToNext()) {
                meetings.add(readMeeting(cursor));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            db.close();
        }
        return meetings;
    }

    private MeetingInfo readMeeting(Cursor cursor) {
        MeetingInfo meeting = new MeetingInfo();
        meeting.meetingID = getString(cursor, DatabaseInfo.COLUMN_MEETING_ID);
        meeting.meetingTopic = getString(cursor, DatabaseInfo.COLUMN_TOPIC);
        meeting.meetingStartTime = MeetingInfo.parseDateTime(getString(cursor, DatabaseInfo.COLUMN_START_TIME));
        meeting.meetingEndTime = MeetingInfo.parseDateTime(getString(cursor, DatabaseInfo.COLUMN_END_TIME));
        meeting.meetingNote = getString(cursor, DatabaseInfo.COLUMN_NOTE);
        meeting.organizer = getString(cursor, DatabaseInfo.COLUMN_ORGANIZER);
        meeting.locationName = getString(cursor, DatabaseInfo.COLUMN_LOCATION_NAME);
        meeting.checkInRadiusMeters = getInt(cursor, DatabaseInfo.COLUMN_CHECK_IN_RADIUS);
        meeting.reminderMinutes = getInt(cursor, DatabaseInfo.COLUMN_REMINDER_MINUTES);
        if (!cursor.isNull(cursor.getColumnIndex(DatabaseInfo.COLUMN_LATITUDE))) {
            meeting.latitude = cursor.getDouble(cursor.getColumnIndex(DatabaseInfo.COLUMN_LATITUDE));
        }
        if (!cursor.isNull(cursor.getColumnIndex(DatabaseInfo.COLUMN_LONGITUDE))) {
            meeting.longitude = cursor.getDouble(cursor.getColumnIndex(DatabaseInfo.COLUMN_LONGITUDE));
        }
        meeting.attendees = AttendeeInfo.deserializeList(getString(cursor, DatabaseInfo.COLUMN_ATTENDEES));
        meeting.imageUri = getString(cursor, DatabaseInfo.COLUMN_IMAGE_URI);
        meeting.audioPath = getString(cursor, DatabaseInfo.COLUMN_AUDIO_PATH);
        meeting.attachmentUri = getString(cursor, DatabaseInfo.COLUMN_ATTACHMENT_URI);
        meeting.attachmentName = getString(cursor, DatabaseInfo.COLUMN_ATTACHMENT_NAME);
        meeting.attachmentMime = getString(cursor, DatabaseInfo.COLUMN_ATTACHMENT_MIME);
        meeting.attachmentAccessMode = MeetingInfo.normalizeAttachmentAccessMode(getString(cursor, DatabaseInfo.COLUMN_ATTACHMENT_ACCESS_MODE));
        meeting.attachmentAllowedUsers = MeetingInfo.deserializeUsernames(getString(cursor, DatabaseInfo.COLUMN_ATTACHMENT_ALLOWED_USERS));
        meeting.attachmentAvailable = getInt(cursor, DatabaseInfo.COLUMN_ATTACHMENT_AVAILABLE) == 1;
        meeting.attachmentContentBase64 = "";
        meeting.sharedMaterials = SharedMaterialInfo.deserializeList(getString(cursor, DatabaseInfo.COLUMN_SHARED_MATERIALS));
        if (meeting.sharedMaterials.isEmpty() && !meeting.attachmentName.trim().isEmpty()) {
            SharedMaterialInfo legacyMaterial = new SharedMaterialInfo();
            legacyMaterial.materialId = "legacy-" + meeting.meetingID;
            legacyMaterial.name = meeting.attachmentName;
            legacyMaterial.mimeType = meeting.attachmentMime;
            legacyMaterial.accessMode = MeetingInfo.normalizeAttachmentAccessMode(meeting.attachmentAccessMode);
            legacyMaterial.allowedUsers = new ArrayList<>(meeting.attachmentAllowedUsers);
            legacyMaterial.available = meeting.attachmentAvailable;
            legacyMaterial.localUri = meeting.attachmentUri;
            legacyMaterial.uploaderUsername = meeting.isOrganizer() ? "organizer" : "";
            legacyMaterial.uploaderDisplayName = meeting.organizer;
            legacyMaterial.canManage = meeting.isOrganizer();
            meeting.sharedMaterials.add(legacyMaterial);
        }
        meeting.syncLegacyAttachmentFieldsFromMaterials();
        meeting.selfCheckInStatus = AttendeeInfo.normalizeStatus(getString(cursor, DatabaseInfo.COLUMN_SELF_CHECK_IN_STATUS));
        meeting.selfCheckInTime = getString(cursor, DatabaseInfo.COLUMN_SELF_CHECK_IN_TIME);
        meeting.shareCode = getString(cursor, DatabaseInfo.COLUMN_SHARE_CODE);
        meeting.memberRole = getString(cursor, DatabaseInfo.COLUMN_MEMBER_ROLE);
        return meeting;
    }

    private ContentValues toValues(MeetingInfo meeting) {
        ContentValues values = new ContentValues();
        meeting.ensureMeetingId();
        meeting.ensureSharedMaterials();
        meeting.syncLegacyAttachmentFieldsFromMaterials();
        values.put(DatabaseInfo.COLUMN_MEETING_ID, meeting.meetingID);
        values.put(DatabaseInfo.COLUMN_TOPIC, safe(meeting.meetingTopic));
        values.put(DatabaseInfo.COLUMN_START_TIME, meeting.getStartTimeText());
        values.put(DatabaseInfo.COLUMN_END_TIME, meeting.getEndTimeText());
        values.put(DatabaseInfo.COLUMN_NOTE, safe(meeting.meetingNote));
        values.put(DatabaseInfo.COLUMN_ORGANIZER, safe(meeting.organizer));
        values.put(DatabaseInfo.COLUMN_LOCATION_NAME, safe(meeting.locationName));
        if (Double.isNaN(meeting.latitude)) {
            values.putNull(DatabaseInfo.COLUMN_LATITUDE);
        } else {
            values.put(DatabaseInfo.COLUMN_LATITUDE, meeting.latitude);
        }
        if (Double.isNaN(meeting.longitude)) {
            values.putNull(DatabaseInfo.COLUMN_LONGITUDE);
        } else {
            values.put(DatabaseInfo.COLUMN_LONGITUDE, meeting.longitude);
        }
        values.put(DatabaseInfo.COLUMN_CHECK_IN_RADIUS, meeting.checkInRadiusMeters);
        values.put(DatabaseInfo.COLUMN_REMINDER_MINUTES, meeting.reminderMinutes);
        values.put(DatabaseInfo.COLUMN_ATTENDEES, AttendeeInfo.serializeList(meeting.attendees));
        values.put(DatabaseInfo.COLUMN_IMAGE_URI, safe(meeting.imageUri));
        values.put(DatabaseInfo.COLUMN_AUDIO_PATH, safe(meeting.audioPath));
        values.put(DatabaseInfo.COLUMN_ATTACHMENT_URI, safe(meeting.attachmentUri));
        values.put(DatabaseInfo.COLUMN_ATTACHMENT_NAME, safe(meeting.attachmentName));
        values.put(DatabaseInfo.COLUMN_ATTACHMENT_MIME, safe(meeting.attachmentMime));
        values.put(DatabaseInfo.COLUMN_ATTACHMENT_ACCESS_MODE, MeetingInfo.normalizeAttachmentAccessMode(meeting.attachmentAccessMode));
        values.put(DatabaseInfo.COLUMN_ATTACHMENT_ALLOWED_USERS, MeetingInfo.serializeUsernames(meeting.attachmentAllowedUsers));
        values.put(DatabaseInfo.COLUMN_ATTACHMENT_AVAILABLE, meeting.attachmentAvailable ? 1 : 0);
        values.put(DatabaseInfo.COLUMN_SHARED_MATERIALS, SharedMaterialInfo.serializeList(meeting.sharedMaterials));
        values.put(DatabaseInfo.COLUMN_SELF_CHECK_IN_STATUS, AttendeeInfo.normalizeStatus(meeting.selfCheckInStatus));
        values.put(DatabaseInfo.COLUMN_SELF_CHECK_IN_TIME, safe(meeting.selfCheckInTime));
        values.put(DatabaseInfo.COLUMN_SHARE_CODE, safe(meeting.shareCode));
        values.put(DatabaseInfo.COLUMN_MEMBER_ROLE, safe(meeting.memberRole));
        return values;
    }

    private String getString(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndex(columnName);
        if (columnIndex < 0) {
            return "";
        }
        return cursor.getString(columnIndex);
    }

    private int getInt(Cursor cursor, String columnName) {
        int columnIndex = cursor.getColumnIndex(columnName);
        if (columnIndex < 0) {
            return 0;
        }
        return cursor.getInt(columnIndex);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
