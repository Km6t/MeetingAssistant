package com.xjtucsse.meetingassistant;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.xjtucsse.meetingassistant.ui.MeetingsAhead.MeetingsAheadFragment;
import com.xjtucsse.meetingassistant.ui.MeetingsBefore.MeetingsBeforeFragment;
import com.xjtucsse.meetingassistant.ui.MeetingsNow.MeetingsNowFragment;
import com.yzq.zxinglibrary.android.CaptureActivity;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_SCAN_QR = 8453;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 8454;
    private static final int REQUEST_PICK_QR_IMAGE = 8455;
    private static final long FOREGROUND_SYNC_INTERVAL_MS = 5000L;

    private FloatingActionButton scanQrButton;
    private FloatingActionButton addMeetingButton;
    private SessionManager sessionManager;
    private final Handler foregroundSyncHandler = new Handler(Looper.getMainLooper());
    private final Runnable foregroundSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (sessionManager != null && sessionManager.isLoggedIn()) {
                syncMeetings(false);
                foregroundSyncHandler.postDelayed(this, FOREGROUND_SYNC_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            sessionManager = new SessionManager(this);
            if (!sessionManager.isLoggedIn()) {
                openAuthScreen();
                return;
            }

            setContentView(R.layout.activity_main);
            setupNavigation();
            ensureNotificationPermission();

            new DatabaseHelper(this).getWritableDatabase().close();
            ReminderScheduler.rescheduleAll(this);
            BackgroundSyncScheduler.schedule(this);
            updateActionBarSubtitle();

            scanQrButton = findViewById(R.id.scan_qr);
            addMeetingButton = findViewById(R.id.add_meeting);

            scanQrButton.setOnClickListener(view -> showScanModeDialog());

            addMeetingButton.setOnClickListener(view -> {
                Intent intent = new Intent(MainActivity.this, AddMeetingActivity.class);
                startActivity(intent);
            });
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "onCreate crash", e);
            Toast.makeText(this, "启动失败: " + e.getClass().getSimpleName() + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager != null && sessionManager.isLoggedIn()) {
            syncMeetings(false);
            foregroundSyncHandler.removeCallbacks(foregroundSyncRunnable);
            foregroundSyncHandler.postDelayed(foregroundSyncRunnable, FOREGROUND_SYNC_INTERVAL_MS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        foregroundSyncHandler.removeCallbacks(foregroundSyncRunnable);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_refresh) {
            syncMeetings(true);
            return true;
        }
        if (itemId == R.id.action_logout) {
            logout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_QR_IMAGE) {
            handleImagePickResult(resultCode, data);
            return;
        }
        if (requestCode != REQUEST_SCAN_QR || resultCode != RESULT_OK || data == null) {
            return;
        }

        String content = data.getStringExtra(com.yzq.zxinglibrary.common.Constant.CODED_CONTENT);
        MeetingQrPayload cloudQr = MeetingQrPayload.decode(content);
        if (cloudQr != null && !cloudQr.shareCode.trim().isEmpty()) {
            if (openLocalMeetingFromQr(cloudQr)) {
                resolveCloudQr(cloudQr.shareCode, false);
            } else {
                resolveCloudQr(cloudQr.shareCode, true);
            }
            return;
        }

        MeetingInfo importedMeeting = MeetingInfo.fromQrPayload(content);
        if (importedMeeting == null) {
            Toast.makeText(this, "扫描内容无法识别。", Toast.LENGTH_LONG).show();
            return;
        }

        DatabaseDAO dao = new DatabaseDAO(this);
        boolean exists = dao.exists(importedMeeting.meetingID);
        dao.save(importedMeeting);
        ReminderScheduler.scheduleReminder(this, importedMeeting);
        Toast.makeText(
                this,
                exists ? "会议已存在，已为你打开详情。" : "会议已通过二维码导入到本地缓存。",
                Toast.LENGTH_SHORT
        ).show();

        openMeetingDetail(importedMeeting.meetingID);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATION_PERMISSION) {
            return;
        }
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            Toast.makeText(this, "未开启通知权限，会议提醒可能无法正常弹出。", Toast.LENGTH_LONG).show();
        }
    }

    private void setupNavigation() {
        BottomNavigationView navView = findViewById(R.id.nav_view);
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_meetings_ahead,
                R.id.navigation_meetings_now,
                R.id.navigation_meetings_before
        ).build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);
    }

    private void updateActionBarSubtitle() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(sessionManager.getDisplayName() + "  @" + sessionManager.getUsername());
        }
    }

    private void syncMeetings(boolean showSuccessToast) {
        new Thread(() -> {
            try {
                CloudSyncManager.syncMeetings(MainActivity.this);
                runOnUiThread(() -> {
                    refreshMeetingFragments();
                    if (showSuccessToast) {
                        Toast.makeText(MainActivity.this, "云端会议已同步。", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (CloudException e) {
                if (showSuccessToast) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }

    private boolean openLocalMeetingFromQr(MeetingQrPayload cloudQr) {
        if (cloudQr == null || TextUtils.isEmpty(cloudQr.meetingId)) {
            return false;
        }
        DatabaseDAO dao = new DatabaseDAO(this);
        MeetingInfo localMeeting = dao.getMeeting(cloudQr.meetingId);
        if (localMeeting == null) {
            return false;
        }
        if (TextUtils.isEmpty(localMeeting.shareCode) && !TextUtils.isEmpty(cloudQr.shareCode)) {
            localMeeting.shareCode = cloudQr.shareCode;
            dao.save(localMeeting);
        }
        Toast.makeText(this, "已打开本地会议详情，并正在同步最新扫码数据。", Toast.LENGTH_SHORT).show();
        openMeetingDetail(localMeeting.meetingID);
        return true;
    }

    private void resolveCloudQr(String shareCode, boolean openAfterResolve) {
        new Thread(() -> {
            try {
                ApiClient apiClient = new ApiClient(MainActivity.this);
                MeetingInfo meeting = apiClient.resolveMeetingByShareCode(shareCode);
                DatabaseDAO dao = new DatabaseDAO(MainActivity.this);
                dao.saveCloudMeeting(meeting);
                ReminderScheduler.scheduleReminder(MainActivity.this, meeting);
                runOnUiThread(() -> {
                    refreshMeetingFragments();
                    Toast.makeText(MainActivity.this, "已识别签到二维码并同步最新会议数据。", Toast.LENGTH_SHORT).show();
                    if (openAfterResolve) {
                        openMeetingDetail(meeting.meetingID);
                    }
                });
            } catch (CloudException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS")
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{"android.permission.POST_NOTIFICATIONS"},
                REQUEST_NOTIFICATION_PERMISSION
        );
    }

    private void logout() {
        new Thread(() -> {
            try {
                new ApiClient(MainActivity.this).logout();
            } catch (CloudException ignored) {
            }

            DatabaseDAO dao = new DatabaseDAO(MainActivity.this);
            List<MeetingInfo> meetings = dao.listAllMeetings();
            for (MeetingInfo meeting : meetings) {
                ReminderScheduler.cancelReminder(MainActivity.this, meeting.meetingID);
            }
            dao.clearAll();
            BackgroundSyncScheduler.cancel(MainActivity.this);
            sessionManager.clearSession();
            runOnUiThread(this::openAuthScreen);
        }).start();
    }

    private void openMeetingDetail(String meetingId) {
        Intent detailIntent = new Intent(this, MeetingActivity.class);
        detailIntent.putExtra("meeting_id", meetingId);
        startActivity(detailIntent);
    }

    private void openAuthScreen() {
        startActivity(new Intent(this, AuthActivity.class));
        finish();
    }

    private void refreshMeetingFragments() {
        Fragment navHostFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) {
            return;
        }
        List<Fragment> childFragments = navHostFragment.getChildFragmentManager().getFragments();
        for (Fragment fragment : childFragments) {
            if (fragment instanceof MeetingsAheadFragment) {
                ((MeetingsAheadFragment) fragment).reloadFromLocalCache();
            } else if (fragment instanceof MeetingsNowFragment) {
                ((MeetingsNowFragment) fragment).reloadFromLocalCache();
            } else if (fragment instanceof MeetingsBeforeFragment) {
                ((MeetingsBeforeFragment) fragment).reloadFromLocalCache();
            }
        }
    }

    private void showScanModeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("选择扫描方式")
                .setItems(new String[]{"相机扫描", "相册选择"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            startCameraScan();
                        } else {
                            startImagePick();
                        }
                    }
                })
                .show();
    }

    private void startCameraScan() {
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        startActivityForResult(intent, REQUEST_SCAN_QR);
    }

    private void startImagePick() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_QR_IMAGE);
    }

    private void handleImagePickResult(int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri imageUri = data.getData();
        new Thread(() -> {
            try {
                String content = QrImageDecoder.decode(MainActivity.this, imageUri);
                runOnUiThread(() -> processQrContent(content));
            } catch (com.google.zxing.NotFoundException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "图片中未识别到二维码。", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "图片解析失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void processQrContent(String content) {
        MeetingQrPayload cloudQr = MeetingQrPayload.decode(content);
        if (cloudQr != null && !cloudQr.shareCode.trim().isEmpty()) {
            if (openLocalMeetingFromQr(cloudQr)) {
                resolveCloudQr(cloudQr.shareCode, false);
            } else {
                resolveCloudQr(cloudQr.shareCode, true);
            }
            return;
        }

        MeetingInfo importedMeeting = MeetingInfo.fromQrPayload(content);
        if (importedMeeting == null) {
            Toast.makeText(this, "扫描内容无法识别。", Toast.LENGTH_LONG).show();
            return;
        }

        DatabaseDAO dao = new DatabaseDAO(this);
        boolean exists = dao.exists(importedMeeting.meetingID);
        dao.save(importedMeeting);
        ReminderScheduler.scheduleReminder(this, importedMeeting);
        Toast.makeText(
                this,
                exists ? "会议已存在，已为你打开详情。" : "会议已通过二维码导入到本地缓存。",
                Toast.LENGTH_SHORT
        ).show();

        openMeetingDetail(importedMeeting.meetingID);
    }
}
