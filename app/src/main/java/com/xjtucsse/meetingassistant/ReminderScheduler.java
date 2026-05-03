package com.xjtucsse.meetingassistant;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.List;

public final class ReminderScheduler {
    private ReminderScheduler() {
    }

    public static void scheduleReminder(Context context, MeetingInfo meeting) {
        if (context == null || meeting == null) {
            return;
        }
        cancelReminder(context, meeting.meetingID);
        if (meeting.reminderMinutes <= 0) {
            return;
        }

        long triggerAtMillis = meeting.meetingStartTime.getTimeInMillis() - (meeting.reminderMinutes * 60L * 1000L);
        long now = System.currentTimeMillis();
        if (triggerAtMillis <= now) {
            if (meeting.meetingStartTime.getTimeInMillis() > now) {
                ReminderReceiver.showReminderNotification(context, meeting);
            }
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = createPendingIntent(context, meeting.meetingID);
        if (canScheduleExactAlarms(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    public static void cancelReminder(Context context, String meetingId) {
        if (context == null || meetingId == null || meetingId.trim().isEmpty()) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        alarmManager.cancel(createPendingIntent(context, meetingId));
    }

    public static void rescheduleAll(Context context) {
        if (context == null) {
            return;
        }
        List<MeetingInfo> meetings = new DatabaseDAO(context).listAllMeetings();
        for (MeetingInfo meeting : meetings) {
            scheduleReminder(context, meeting);
        }
    }

    private static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    private static PendingIntent createPendingIntent(Context context, String meetingId) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra(ReminderReceiver.EXTRA_MEETING_ID, meetingId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(context, meetingId.hashCode(), intent, flags);
    }
}
