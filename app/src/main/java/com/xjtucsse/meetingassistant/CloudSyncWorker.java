package com.xjtucsse.meetingassistant;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class CloudSyncWorker extends Worker {
    public CloudSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        SessionManager sessionManager = new SessionManager(getApplicationContext());
        if (!sessionManager.isLoggedIn()) {
            return Result.success();
        }

        try {
            CloudSyncManager.syncMeetings(getApplicationContext());
            return Result.success();
        } catch (CloudException e) {
            return Result.retry();
        }
    }
}
