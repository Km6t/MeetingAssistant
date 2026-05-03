package com.xjtucsse.meetingassistant;

import android.content.Context;

import java.util.List;

public class CloudSyncManager {
    public static void syncMeetings(Context context) throws CloudException {
        Context appContext = context.getApplicationContext();
        ApiClient apiClient = new ApiClient(appContext);
        DatabaseDAO dao = new DatabaseDAO(appContext);
        List<MeetingInfo> meetings = apiClient.fetchMeetings();
        dao.replaceWithCloudMeetings(meetings);
        for (MeetingInfo meeting : meetings) {
            ReminderScheduler.scheduleReminder(appContext, meeting);
        }
    }

    private CloudSyncManager() {
    }
}
