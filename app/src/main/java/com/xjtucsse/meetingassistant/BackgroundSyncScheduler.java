package com.xjtucsse.meetingassistant;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class BackgroundSyncScheduler {
    private static final String PERIODIC_WORK_NAME = "meeting_cloud_periodic_sync";
    private static final String IMMEDIATE_WORK_NAME = "meeting_cloud_immediate_sync";

    private BackgroundSyncScheduler() {
    }

    public static void schedule(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(
                CloudSyncWorker.class,
                15,
                TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .build();

        WorkManager workManager = WorkManager.getInstance(appContext);
        workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
        );

        OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(CloudSyncWorker.class)
                .setConstraints(constraints)
                .build();
        workManager.enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeWorkRequest
        );
    }

    public static void cancel(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        WorkManager workManager = WorkManager.getInstance(appContext);
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME);
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME);
    }
}
