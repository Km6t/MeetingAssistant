package com.xjtucsse.meetingassistant;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String EXTRA_MEETING_ID = "extra_meeting_id";
    private static final String CHANNEL_ID = "meeting_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String meetingId = intent.getStringExtra(EXTRA_MEETING_ID);
        if (meetingId == null || meetingId.trim().isEmpty()) {
            return;
        }

        MeetingInfo meeting = new DatabaseDAO(context).getMeeting(meetingId);
        if (meeting == null) {
            return;
        }

        showReminderNotification(context, meeting);
    }

    public static void showReminderNotification(Context context, MeetingInfo meeting) {
        if (context == null || meeting == null || meeting.meetingID == null || meeting.meetingID.trim().isEmpty()) {
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "会议提醒",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        Intent openMeetingIntent = new Intent(context, MeetingActivity.class);
        openMeetingIntent.putExtra("meeting_id", meeting.meetingID);
        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent openMeetingPendingIntent = PendingIntent.getActivity(
                context,
                meeting.meetingID.hashCode(),
                openMeetingIntent,
                pendingIntentFlags
        );

        String contentText = meeting.meetingTopic + " 将于 " + meeting.getStartTimeText() + " 开始";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("会议提醒")
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setAutoCancel(true)
                .setContentIntent(openMeetingPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        notificationManager.notify(meeting.meetingID.hashCode(), builder.build());
    }
}
