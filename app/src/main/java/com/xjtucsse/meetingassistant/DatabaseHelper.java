package com.xjtucsse.meetingassistant;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

public class DatabaseHelper extends SQLiteOpenHelper {
    public DatabaseHelper(@Nullable Context context) {
        super(context, DatabaseInfo.DB_NAME, null, DatabaseInfo.DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createMeetingsTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        int version = oldVersion;
        if (version < 2) {
            db.execSQL("ALTER TABLE " + DatabaseInfo.TABLE_NAME + " RENAME TO " + DatabaseInfo.TABLE_NAME + "_legacy");
            createMeetingsTable(db);
            db.execSQL(
                    "INSERT INTO " + DatabaseInfo.TABLE_NAME + " ("
                            + DatabaseInfo.COLUMN_MEETING_ID + ","
                            + DatabaseInfo.COLUMN_TOPIC + ","
                            + DatabaseInfo.COLUMN_START_TIME + ","
                            + DatabaseInfo.COLUMN_END_TIME + ","
                            + DatabaseInfo.COLUMN_NOTE + ") "
                            + "SELECT "
                            + DatabaseInfo.COLUMN_MEETING_ID + ","
                            + DatabaseInfo.COLUMN_TOPIC + ","
                            + DatabaseInfo.COLUMN_START_TIME + ","
                            + DatabaseInfo.COLUMN_END_TIME + ","
                            + DatabaseInfo.COLUMN_NOTE
                            + " FROM " + DatabaseInfo.TABLE_NAME + "_legacy"
            );
            db.execSQL("DROP TABLE " + DatabaseInfo.TABLE_NAME + "_legacy");
            version = 2;
        }
        if (version < 3) {
            db.execSQL("ALTER TABLE " + DatabaseInfo.TABLE_NAME + " ADD COLUMN " + DatabaseInfo.COLUMN_SHARE_CODE + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + DatabaseInfo.TABLE_NAME + " ADD COLUMN " + DatabaseInfo.COLUMN_MEMBER_ROLE + " TEXT DEFAULT '" + MeetingInfo.ROLE_ATTENDEE + "'");
            version = 3;
        }
        if (version < 4) {
            db.execSQL("ALTER TABLE " + DatabaseInfo.TABLE_NAME + " ADD COLUMN " + DatabaseInfo.COLUMN_ATTACHMENT_ACCESS_MODE + " TEXT DEFAULT '" + MeetingInfo.ATTACHMENT_ACCESS_ALL_ATTENDEES + "'");
            db.execSQL("ALTER TABLE " + DatabaseInfo.TABLE_NAME + " ADD COLUMN " + DatabaseInfo.COLUMN_ATTACHMENT_ALLOWED_USERS + " TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE " + DatabaseInfo.TABLE_NAME + " ADD COLUMN " + DatabaseInfo.COLUMN_ATTACHMENT_AVAILABLE + " INTEGER DEFAULT 0");
            version = 4;
        }
        if (version < 5) {
            db.execSQL("ALTER TABLE " + DatabaseInfo.TABLE_NAME + " ADD COLUMN " + DatabaseInfo.COLUMN_SHARED_MATERIALS + " TEXT DEFAULT ''");
        }
    }

    private void createMeetingsTable(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + DatabaseInfo.TABLE_NAME + " ("
                        + DatabaseInfo.COLUMN_MEETING_ID + " TEXT PRIMARY KEY,"
                        + DatabaseInfo.COLUMN_TOPIC + " TEXT,"
                        + DatabaseInfo.COLUMN_START_TIME + " TEXT,"
                        + DatabaseInfo.COLUMN_END_TIME + " TEXT,"
                        + DatabaseInfo.COLUMN_NOTE + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_ORGANIZER + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_LOCATION_NAME + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_LATITUDE + " REAL,"
                        + DatabaseInfo.COLUMN_LONGITUDE + " REAL,"
                        + DatabaseInfo.COLUMN_CHECK_IN_RADIUS + " INTEGER DEFAULT 100,"
                        + DatabaseInfo.COLUMN_REMINDER_MINUTES + " INTEGER DEFAULT 15,"
                        + DatabaseInfo.COLUMN_ATTENDEES + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_IMAGE_URI + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_AUDIO_PATH + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_ATTACHMENT_URI + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_ATTACHMENT_NAME + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_ATTACHMENT_MIME + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_ATTACHMENT_ACCESS_MODE + " TEXT DEFAULT '" + MeetingInfo.ATTACHMENT_ACCESS_ALL_ATTENDEES + "',"
                        + DatabaseInfo.COLUMN_ATTACHMENT_ALLOWED_USERS + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_ATTACHMENT_AVAILABLE + " INTEGER DEFAULT 0,"
                        + DatabaseInfo.COLUMN_SHARED_MATERIALS + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_SELF_CHECK_IN_STATUS + " TEXT DEFAULT '" + AttendeeInfo.STATUS_PENDING + "',"
                        + DatabaseInfo.COLUMN_SELF_CHECK_IN_TIME + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_SHARE_CODE + " TEXT DEFAULT '',"
                        + DatabaseInfo.COLUMN_MEMBER_ROLE + " TEXT DEFAULT '" + MeetingInfo.ROLE_ATTENDEE + "'"
                        + ")"
        );
    }
}
