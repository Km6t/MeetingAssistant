package com.xjtucsse.meetingassistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.xjtucsse.meetingassistant.ui.AttendeeAdapter;
import com.yzq.zxinglibrary.encode.CodeCreator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MeetingActivity extends AppCompatActivity {
    private static final int REQUEST_EDIT_MEETING = 4101;
    private static final int REQUEST_PICK_IMAGE = 4102;
    private static final int REQUEST_PICK_FILE = 4103;
    private static final int REQUEST_AUDIO_PERMISSION = 4104;
    private static final int REQUEST_LOCATION_PERMISSION = 4105;
    private static final int REQUEST_PICK_SHARED_FILE = 4106;

    private TextView topicText;
    private TextView timeText;
    private TextView metaText;
    private TextView attendanceSummaryText;
    private TextView selfCheckInText;
    private TextView audioStatusText;
    private TextView imageStatusText;
    private TextView attachmentStatusText;
    private EditText noteEditText;
    private ImageView qrImageView;
    private ImageView imagePreview;
    private LinearLayout sharedMaterialsContainer;
    private RecyclerView attendeeRecyclerView;
    private Button saveNoteButton;
    private Button editMeetingButton;
    private Button checkInButton;
    private Button exportButton;
    private Button qrExpandButton;
    private Button qrShareButton;
    private Button attachImageButton;
    private Button openImageButton;
    private Button attachFileButton;
    private Button openFileButton;
    private Button recordAudioButton;
    private Button pauseAudioButton;
    private Button stopAudioButton;
    private Button playAudioButton;
    private Button deleteAudioButton;

    private AttendeeAdapter attendeeAdapter;
    private MeetingInfo meetingInfo;
    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private Bitmap qrBitmap;
    private boolean isRecording;
    private boolean isRecordingPaused;
    private boolean pendingCheckInAfterPermission;
    private boolean pendingRecordingAfterPermission;

    private void promptAttachmentAccess(Uri uri) {
        String[] options = new String[]{
                "仅组织者可查看",
                "所有参会人可查看",
                "指定参会人可查看"
        };
        new AlertDialog.Builder(this)
                .setTitle("设置资料查看权限")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        uploadSharedAttachment(uri, MeetingInfo.ATTACHMENT_ACCESS_ORGANIZER, new ArrayList<>());
                    } else if (which == 1) {
                        uploadSharedAttachment(uri, MeetingInfo.ATTACHMENT_ACCESS_ALL_ATTENDEES, new ArrayList<>());
                    } else {
                        promptCustomAttachmentUsers(uri);
                    }
                })
                .show();
    }

    private void promptCustomAttachmentUsers(Uri uri) {
        EditText input = new EditText(this);
        input.setMinLines(4);
        input.setHint("填写允许查看资料的用户名，每行一个");
        input.setText(TextUtils.join("\n", meetingInfo.attachmentAllowedUsers));
        new AlertDialog.Builder(this)
                .setTitle("指定可查看资料的参会人")
                .setView(input)
                .setPositiveButton("保存并上传", (dialog, which) -> {
                    List<String> allowedUsers = parseAttachmentAllowedUsers(input.getText().toString());
                    if (allowedUsers.isEmpty()) {
                        Toast.makeText(this, "请至少填写一个参会人用户名。", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    uploadSharedAttachment(uri, MeetingInfo.ATTACHMENT_ACCESS_SELECTED_ATTENDEES, allowedUsers);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private List<String> parseAttachmentAllowedUsers(String rawValue) {
        List<String> requestedUsers = MeetingInfo.deserializeUsernames(rawValue);
        Set<String> attendeeLookup = new HashSet<>();
        for (AttendeeInfo attendee : meetingInfo.attendees) {
            if (attendee != null && attendee.username != null) {
                attendeeLookup.add(attendee.username.trim().toLowerCase(Locale.CHINA));
            }
        }
        List<String> allowedUsers = new ArrayList<>();
        for (String username : requestedUsers) {
            if (attendeeLookup.contains(username)) {
                allowedUsers.add(username);
            }
        }
        return allowedUsers;
    }

    private void uploadSharedAttachment(Uri uri, String accessMode, List<String> allowedUsers) {
        attachFileButton.setEnabled(false);
        attachmentStatusText.setText("正在上传共享资料...");
        new Thread(() -> {
            try {
                meetingInfo.attachmentName = queryDisplayName(uri);
                String mimeType = getContentResolver().getType(uri);
                meetingInfo.attachmentMime = mimeType == null ? "application/octet-stream" : mimeType;
                meetingInfo.attachmentAccessMode = MeetingInfo.normalizeAttachmentAccessMode(accessMode);
                meetingInfo.attachmentAllowedUsers = new ArrayList<>(allowedUsers);
                meetingInfo.attachmentAvailable = true;
                meetingInfo.attachmentContentBase64 = SharedAttachmentManager.readAttachmentAsBase64(MeetingActivity.this, uri);

                MeetingInfo updatedMeeting = new ApiClient(MeetingActivity.this).updateMeeting(meetingInfo);
                updatedMeeting.copyLocalArtifactsFrom(meetingInfo);
                meetingInfo = updatedMeeting;
                new DatabaseDAO(MeetingActivity.this).saveCloudMeeting(updatedMeeting);
                runOnUiThread(() -> {
                    renderMeeting();
                    Toast.makeText(MeetingActivity.this, "会议资料已同步到云端。", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    renderMeeting();
                    Toast.makeText(MeetingActivity.this, "上传共享资料失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void promptSharedMaterialAccess(Uri uri) {
        String[] options = new String[]{
                "仅上传者可查看",
                "所有参会人可查看",
                "指定参会人可查看"
        };
        new AlertDialog.Builder(this)
                .setTitle("设置资料查看权限")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        uploadMeetingMaterial(uri, MeetingInfo.ATTACHMENT_ACCESS_ORGANIZER, new ArrayList<>());
                    } else if (which == 1) {
                        uploadMeetingMaterial(uri, MeetingInfo.ATTACHMENT_ACCESS_ALL_ATTENDEES, new ArrayList<>());
                    } else {
                        promptSharedMaterialUsers(uri);
                    }
                })
                .show();
    }

    private void promptSharedMaterialUsers(Uri uri) {
        EditText input = new EditText(this);
        input.setMinLines(4);
        input.setHint("填写允许查看资料的用户名，每行一个");
        new AlertDialog.Builder(this)
                .setTitle("指定可查看资料的参会人")
                .setView(input)
                .setPositiveButton("保存并上传", (dialog, which) -> {
                    List<String> allowedUsers = parseAttachmentAllowedUsers(input.getText().toString());
                    if (allowedUsers.isEmpty()) {
                        Toast.makeText(this, "请至少填写一个参会人用户名。", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    uploadMeetingMaterial(uri, MeetingInfo.ATTACHMENT_ACCESS_SELECTED_ATTENDEES, allowedUsers);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void uploadMeetingMaterial(Uri uri, String accessMode, List<String> allowedUsers) {
        attachFileButton.setEnabled(false);
        attachmentStatusText.setText("正在上传共享资料...");
        new Thread(() -> {
            try {
                SharedMaterialInfo material = new SharedMaterialInfo();
                material.name = queryDisplayName(uri);
                String mimeType = getContentResolver().getType(uri);
                material.mimeType = mimeType == null ? "application/octet-stream" : mimeType;
                material.accessMode = MeetingInfo.normalizeAttachmentAccessMode(accessMode);
                material.allowedUsers = new ArrayList<>(allowedUsers);
                material.available = true;
                material.contentBase64 = SharedAttachmentManager.readAttachmentAsBase64(MeetingActivity.this, uri);

                MeetingInfo updatedMeeting = new ApiClient(MeetingActivity.this)
                        .uploadSharedMaterial(meetingInfo.meetingID, material);
                updatedMeeting.copyLocalArtifactsFrom(meetingInfo);
                meetingInfo = updatedMeeting;
                new DatabaseDAO(MeetingActivity.this).saveCloudMeeting(updatedMeeting);
                runOnUiThread(() -> {
                    renderMeeting();
                    Toast.makeText(MeetingActivity.this, "共享资料已同步到云端。", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    renderMeeting();
                    Toast.makeText(MeetingActivity.this, "上传共享资料失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void deleteMeetingMaterial(SharedMaterialInfo material) {
        if (material == null || TextUtils.isEmpty(material.materialId)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除共享资料")
                .setMessage("确定删除自己上传的资料吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    attachmentStatusText.setText("正在删除共享资料...");
                    new Thread(() -> {
                        try {
                            MeetingInfo updatedMeeting = new ApiClient(MeetingActivity.this)
                                    .deleteSharedMaterial(meetingInfo.meetingID, material.materialId);
                            updatedMeeting.copyLocalArtifactsFrom(meetingInfo);
                            meetingInfo = updatedMeeting;
                            new DatabaseDAO(MeetingActivity.this).saveCloudMeeting(updatedMeeting);
                            runOnUiThread(() -> {
                                renderMeeting();
                                Toast.makeText(MeetingActivity.this, "共享资料已删除。", Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(
                                    MeetingActivity.this,
                                    "删除共享资料失败：" + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show());
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meeting);

        bindViews();
        setupRecyclerView();
        setupListeners();
        setupSharedMaterialListeners();
        loadMeeting();
    }

    private void bindViews() {
        topicText = findViewById(R.id.this_meeting_topic);
        timeText = findViewById(R.id.this_meeting_time);
        metaText = findViewById(R.id.meeting_meta);
        attendanceSummaryText = findViewById(R.id.meeting_attendance_summary);
        selfCheckInText = findViewById(R.id.meeting_self_status);
        audioStatusText = findViewById(R.id.audio_status);
        imageStatusText = findViewById(R.id.image_status);
        attachmentStatusText = findViewById(R.id.attachment_status);
        sharedMaterialsContainer = findViewById(R.id.shared_material_list);
        noteEditText = findViewById(R.id.et);
        qrImageView = findViewById(R.id.meeting_qr);
        imagePreview = findViewById(R.id.image_preview);
        attendeeRecyclerView = findViewById(R.id.attendee_list);
        saveNoteButton = findViewById(R.id.save_note);
        editMeetingButton = findViewById(R.id.edit_meeting);
        checkInButton = findViewById(R.id.check_in_btn);
        exportButton = findViewById(R.id.export_btn);
        qrExpandButton = findViewById(R.id.qr_expand);
        qrShareButton = findViewById(R.id.qr_share);
        attachImageButton = findViewById(R.id.attach_image);
        openImageButton = findViewById(R.id.open_image);
        attachFileButton = findViewById(R.id.attach_file);
        openFileButton = findViewById(R.id.open_file);
        openFileButton.setVisibility(View.GONE);
        recordAudioButton = findViewById(R.id.record_audio);
        pauseAudioButton = findViewById(R.id.pause_audio);
        stopAudioButton = findViewById(R.id.stop_audio);
        playAudioButton = findViewById(R.id.play_audio);
        deleteAudioButton = findViewById(R.id.delete_audio);
    }

    private void setupRecyclerView() {
        attendeeAdapter = new AttendeeAdapter(new ArrayList<>(), position -> showAttendeeSummary(position));
        attendeeRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        attendeeRecyclerView.setAdapter(attendeeAdapter);
    }

    private void setupListeners() {
        saveNoteButton.setOnClickListener(v -> saveNote());
        editMeetingButton.setOnClickListener(v -> openMeetingEditor());
        checkInButton.setOnClickListener(v -> performCheckIn());
        exportButton.setOnClickListener(v -> exportReport());
        qrExpandButton.setOnClickListener(v -> showLargeQrCode());
        qrShareButton.setOnClickListener(v -> shareQrCode());
        attachImageButton.setOnClickListener(v -> openDocumentPicker(new String[]{"image/*"}, REQUEST_PICK_IMAGE));
        openImageButton.setOnClickListener(v -> openImageAttachment());
        attachFileButton.setOnClickListener(v -> openDocumentPicker(
                new String[]{
                        "application/pdf",
                        "application/vnd.ms-powerpoint",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "*/*"
                },
                REQUEST_PICK_SHARED_FILE
        ));
        openFileButton.setOnClickListener(v -> openSharedAttachment());
        recordAudioButton.setOnClickListener(v -> startRecording());
        pauseAudioButton.setOnClickListener(v -> pauseOrResumeRecording());
        stopAudioButton.setOnClickListener(v -> stopRecording());
        playAudioButton.setOnClickListener(v -> playAudio());
        deleteAudioButton.setOnClickListener(v -> deleteAudio());
    }

    private void setupSharedMaterialListeners() {
        attachFileButton.setOnClickListener(v -> openDocumentPicker(
                new String[]{
                        "application/pdf",
                        "application/vnd.ms-powerpoint",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "*/*"
                },
                REQUEST_PICK_SHARED_FILE
        ));
        openFileButton.setVisibility(View.GONE);
    }

    private void loadMeeting() {
        String meetingId = getIntent().getStringExtra("meeting_id");
        if (TextUtils.isEmpty(meetingId)) {
            Toast.makeText(this, "会议不存在或已失效。", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        meetingInfo = new DatabaseDAO(this).getMeeting(meetingId);
        if (meetingInfo == null) {
            Toast.makeText(this, "本地缓存中没有找到该会议。", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        renderMeeting();
        syncMeetingFromCloud();
    }

    private void syncMeetingFromCloud() {
        new Thread(() -> {
            try {
                MeetingInfo latestMeeting = new ApiClient(MeetingActivity.this).getMeeting(meetingInfo.meetingID);
                latestMeeting.copyLocalArtifactsFrom(meetingInfo);
                meetingInfo = latestMeeting;
                new DatabaseDAO(MeetingActivity.this).saveCloudMeeting(latestMeeting);
                ReminderScheduler.scheduleReminder(MeetingActivity.this, latestMeeting);
                runOnUiThread(this::renderMeeting);
            } catch (CloudException ignored) {
            }
        }).start();
    }

    private void renderMeeting() {
        topicText.setText(meetingInfo.meetingTopic);
        timeText.setText("开始：" + meetingInfo.getStartTimeText() + "\n结束：" + meetingInfo.getEndTimeText());
        metaText.setText(buildMetaText());
        noteEditText.setText(meetingInfo.meetingNote);
        attendeeAdapter.updateItems(meetingInfo.attendees);
        renderQrCode();
        renderAttendanceSummary();
        renderSelfCheckInStatus();
        renderImageSection();
        renderSharedMaterialsSection();
        renderAudioSection();
        updateSharedMaterialControls();
        updateRecordingButtons();
    }

    private String buildMetaText() {
        List<String> details = new ArrayList<>();
        details.add("组织者：" + (TextUtils.isEmpty(meetingInfo.organizer) ? "未填写" : meetingInfo.organizer));
        details.add("地点：" + (TextUtils.isEmpty(meetingInfo.locationName) ? "未填写" : meetingInfo.locationName));
        details.add("提醒：" + (meetingInfo.reminderMinutes > 0 ? "提前 " + meetingInfo.reminderMinutes + " 分钟" : "关闭"));
        details.add(meetingInfo.hasLocationGate() ? "签到半径：" + meetingInfo.checkInRadiusMeters + " 米" : "签到位置校验：关闭");
        details.add(meetingInfo.isOrganizer() ? "身份：组织者" : "身份：参会人");
        details.add("共享方式：云端同步");
        return TextUtils.join("  |  ", details);
    }

    private void renderQrCode() {
        String payload = TextUtils.isEmpty(meetingInfo.shareCode)
                ? meetingInfo.toQrPayload()
                : MeetingQrPayload.encode(meetingInfo.meetingID, meetingInfo.shareCode);
        qrBitmap = CodeCreator.createQRCode(payload, 220, 220, null);
        qrImageView.setImageBitmap(qrBitmap);
    }

    private void renderAttendanceSummary() {
        int expected = meetingInfo.getExpectedCount();
        attendanceSummaryText.setText(
                "应到 " + expected
                        + " 人，已签到 " + meetingInfo.getPresentCount()
                        + " 人，迟到 " + meetingInfo.getLateCount()
                        + " 人，缺席 " + meetingInfo.getAbsentCount() + " 人"
        );
    }

    private void renderSelfCheckInStatus() {
        String displayStatus = AttendeeInfo.getDisplayStatus(meetingInfo.selfCheckInStatus);
        if (TextUtils.isEmpty(meetingInfo.selfCheckInTime)) {
            selfCheckInText.setText("我的签到状态：" + displayStatus);
        } else {
            selfCheckInText.setText("我的签到状态：" + displayStatus + "（" + meetingInfo.selfCheckInTime + "）");
        }
    }

    private void renderAccessControls() {
        boolean organizer = meetingInfo.isOrganizer();
        noteEditText.setEnabled(organizer);
        saveNoteButton.setEnabled(organizer);
        editMeetingButton.setEnabled(organizer);
        if (organizer) {
            saveNoteButton.setText("保存共享会议记录");
            noteEditText.setHint("组织者可维护云端同步的会议纪要。");
        } else {
            saveNoteButton.setText("仅组织者可编辑会议记录");
            noteEditText.setHint("当前账号为参会人，只能查看共享会议记录。");
        }
    }

    private void renderSharedAccessControls() {
        boolean organizer = meetingInfo.isOrganizer();
        noteEditText.setEnabled(organizer);
        saveNoteButton.setEnabled(organizer);
        editMeetingButton.setEnabled(organizer);
        attachFileButton.setEnabled(organizer);
        if (organizer) {
            saveNoteButton.setText("保存共享会议记录");
            noteEditText.setHint("组织者可在这里维护会议纪要、决议和待办事项");
            attachFileButton.setText(meetingInfo.hasSharedAttachment() ? "替换 / 重新授权资料" : "上传共享资料");
        } else {
            saveNoteButton.setText("仅组织者可编辑会议记录");
            noteEditText.setHint("当前账号为参会人，只能查看共享会议记录");
            attachFileButton.setText("仅组织者可上传资料");
        }
    }

    private void renderImageSection() {
        if (TextUtils.isEmpty(meetingInfo.imageUri)) {
            imageStatusText.setText("暂无图片记录");
            imagePreview.setVisibility(View.GONE);
            openImageButton.setEnabled(false);
            return;
        }

        imageStatusText.setText("已关联图片记录");
        imagePreview.setVisibility(View.VISIBLE);
        imagePreview.setImageURI(Uri.parse(meetingInfo.imageUri));
        openImageButton.setEnabled(true);
    }

    private void renderAttachmentSection() {
        if (TextUtils.isEmpty(meetingInfo.attachmentUri)) {
            attachmentStatusText.setText("暂无资料文件");
            openFileButton.setEnabled(false);
            return;
        }
        String name = TextUtils.isEmpty(meetingInfo.attachmentName) ? "未命名文件" : meetingInfo.attachmentName;
        attachmentStatusText.setText("已关联会议资料：" + name);
        openFileButton.setEnabled(true);
    }

    private void renderSharedAttachmentSection() {
        if (!meetingInfo.hasSharedAttachment()) {
            attachmentStatusText.setText("暂无共享资料文件");
            openFileButton.setEnabled(false);
            return;
        }
        String name = TextUtils.isEmpty(meetingInfo.attachmentName) ? "未命名文件" : meetingInfo.attachmentName;
        if (meetingInfo.canOpenAttachment()) {
            attachmentStatusText.setText("共享资料：" + name + " | 权限：" + meetingInfo.getAttachmentAccessLabel());
            openFileButton.setEnabled(true);
            return;
        }
        attachmentStatusText.setText("共享资料：" + name + " | 你当前没有查看权限");
        openFileButton.setEnabled(false);
    }

    private void updateSharedMaterialControls() {
        boolean organizer = meetingInfo.isOrganizer();
        noteEditText.setEnabled(organizer);
        saveNoteButton.setEnabled(organizer);
        editMeetingButton.setEnabled(organizer);
        attachFileButton.setEnabled(true);
        if (organizer) {
            saveNoteButton.setText("保存共享会议记录");
            noteEditText.setHint("组织者可在这里维护会议纪要、决议和待办事项");
        } else {
            saveNoteButton.setText("仅组织者可编辑会议记录");
            noteEditText.setHint("当前账号为参会人，只能查看共享会议记录");
        }
        attachFileButton.setText("上传共享资料");
    }

    private void renderSharedMaterialsSection() {
        meetingInfo.ensureSharedMaterials();
        sharedMaterialsContainer.removeAllViews();
        if (meetingInfo.sharedMaterials.isEmpty()) {
            attachmentStatusText.setText("暂无共享资料文件");
            return;
        }
        attachmentStatusText.setText("共享资料共 " + meetingInfo.sharedMaterials.size() + " 份");
        for (SharedMaterialInfo material : meetingInfo.sharedMaterials) {
            if (material == null || !material.hasFile()) {
                continue;
            }
            sharedMaterialsContainer.addView(createSharedMaterialView(material));
        }
    }

    private View createSharedMaterialView(SharedMaterialInfo material) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dpToPx(10);
        card.setLayoutParams(cardParams);
        card.setBackgroundColor(0xFFFFFFFF);
        int padding = dpToPx(12);
        card.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText(TextUtils.isEmpty(material.name) ? "未命名文件" : material.name);
        title.setTextSize(16f);
        title.setTextColor(0xFF073640);
        card.addView(title);

        TextView meta = new TextView(this);
        String uploaderName = TextUtils.isEmpty(material.uploaderDisplayName)
                ? material.uploaderUsername
                : material.uploaderDisplayName;
        meta.setText("上传者：" + uploaderName + " | 权限：" + material.getAccessLabel());
        meta.setTextColor(0xFF5B6B73);
        meta.setTextSize(13f);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaParams.topMargin = dpToPx(6);
        meta.setLayoutParams(metaParams);
        card.addView(meta);

        TextView status = new TextView(this);
        status.setText(material.canOpen() ? "你当前可以打开这份资料" : "你当前没有查看这份资料的权限");
        status.setTextColor(material.canOpen() ? 0xFF2A6D5A : 0xFF9A5B5B);
        status.setTextSize(13f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dpToPx(4);
        status.setLayoutParams(statusParams);
        card.addView(status);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actionsParams.topMargin = dpToPx(10);
        actions.setLayoutParams(actionsParams);

        Button openButton = new Button(this);
        openButton.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        openButton.setText("打开资料");
        openButton.setEnabled(material.canOpen());
        openButton.setOnClickListener(v -> openSharedMaterial(material));
        actions.addView(openButton);

        if (material.canManage) {
            Button deleteButton = new Button(this);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            deleteParams.leftMargin = dpToPx(10);
            deleteButton.setLayoutParams(deleteParams);
            deleteButton.setText("删除我的资料");
            deleteButton.setOnClickListener(v -> deleteMeetingMaterial(material));
            actions.addView(deleteButton);
        }

        card.addView(actions);
        return card;
    }

    private void openSharedMaterial(SharedMaterialInfo material) {
        if (material == null || !material.canOpen()) {
            Toast.makeText(this, "当前资料暂不可打开。", Toast.LENGTH_SHORT).show();
            return;
        }
        String mimeType = TextUtils.isEmpty(material.mimeType) ? "*/*" : material.mimeType;
        Uri uri = SharedAttachmentManager.resolveShareableUri(
                this,
                material.localUri,
                getPackageName() + ".fileprovider"
        );
        if (uri == null) {
            Toast.makeText(this, "当前资料文件不可用，请稍后重新同步。", Toast.LENGTH_SHORT).show();
            return;
        }
        openUri(uri, mimeType);
    }

    private void renderAudioSection() {
        if (isRecording) {
            audioStatusText.setText(isRecordingPaused ? "录音已暂停，可继续或保存。" : "正在录音中...");
            return;
        }

        if (TextUtils.isEmpty(meetingInfo.audioPath)) {
            audioStatusText.setText("暂无录音");
            return;
        }

        File audioFile = new File(meetingInfo.audioPath);
        if (!audioFile.exists()) {
            meetingInfo.audioPath = "";
            new DatabaseDAO(this).save(meetingInfo);
            audioStatusText.setText("录音文件不存在，请重新录制。");
            return;
        }

        audioStatusText.setText("已保存录音：" + audioFile.getName());
    }

    private void saveNote() {
        if (!meetingInfo.isOrganizer()) {
            Toast.makeText(this, "只有组织者可以修改共享会议记录。", Toast.LENGTH_SHORT).show();
            return;
        }
        meetingInfo.meetingNote = noteEditText.getText().toString().trim();
        saveNoteButton.setEnabled(false);
        new Thread(() -> {
            try {
                MeetingInfo updatedMeeting = new ApiClient(MeetingActivity.this).updateMeeting(meetingInfo);
                updatedMeeting.copyLocalArtifactsFrom(meetingInfo);
                meetingInfo = updatedMeeting;
                new DatabaseDAO(MeetingActivity.this).saveCloudMeeting(updatedMeeting);
                runOnUiThread(() -> {
                    saveNoteButton.setEnabled(true);
                    renderMeeting();
                    Toast.makeText(MeetingActivity.this, "共享会议记录已保存。", Toast.LENGTH_SHORT).show();
                });
            } catch (CloudException e) {
                runOnUiThread(() -> {
                    saveNoteButton.setEnabled(true);
                    Toast.makeText(MeetingActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void openMeetingEditor() {
        if (!meetingInfo.isOrganizer()) {
            Toast.makeText(this, "只有组织者可以编辑会议。", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, AddMeetingActivity.class);
        intent.putExtra("meeting_id", meetingInfo.meetingID);
        startActivityForResult(intent, REQUEST_EDIT_MEETING);
    }

    private void performCheckIn() {
        Calendar now = Calendar.getInstance();
        long nowMillis = now.getTimeInMillis();
        long earliestAllowed = meetingInfo.meetingStartTime.getTimeInMillis()
                - Math.max(meetingInfo.reminderMinutes, 15) * 60L * 1000L;
        if (nowMillis < earliestAllowed) {
            Toast.makeText(this, "签到时间还未开始。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nowMillis > meetingInfo.meetingEndTime.getTimeInMillis()) {
            Toast.makeText(this, "会议已结束，签到入口已关闭。", Toast.LENGTH_SHORT).show();
            return;
        }

        if (meetingInfo.hasLocationGate()) {
            if (!hasLocationPermission()) {
                pendingCheckInAfterPermission = true;
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_LOCATION_PERMISSION
                );
                return;
            }

            Location currentLocation = getBestLastKnownLocation();
            if (currentLocation == null) {
                Toast.makeText(this, "暂时无法获取当前位置，请稍后重试。", Toast.LENGTH_SHORT).show();
                return;
            }

            float[] results = new float[1];
            Location.distanceBetween(
                    meetingInfo.latitude,
                    meetingInfo.longitude,
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude(),
                    results
            );
            if (results[0] > meetingInfo.checkInRadiusMeters) {
                Toast.makeText(
                        this,
                        "当前位置距离会场约 " + Math.round(results[0]) + " 米，超出了签到半径。",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }
        }

        checkInButton.setEnabled(false);
        new Thread(() -> {
            try {
                MeetingInfo updatedMeeting = new ApiClient(MeetingActivity.this).checkIn(meetingInfo.meetingID);
                updatedMeeting.copyLocalArtifactsFrom(meetingInfo);
                meetingInfo = updatedMeeting;
                new DatabaseDAO(MeetingActivity.this).saveCloudMeeting(updatedMeeting);
                runOnUiThread(() -> {
                    checkInButton.setEnabled(true);
                    renderMeeting();
                    Toast.makeText(MeetingActivity.this, "签到成功。", Toast.LENGTH_SHORT).show();
                });
            } catch (CloudException e) {
                runOnUiThread(() -> {
                    checkInButton.setEnabled(true);
                    Toast.makeText(MeetingActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void exportReport() {
        try {
            File exportDirectory = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (exportDirectory == null) {
                exportDirectory = getCacheDir();
            }
            File reportDirectory = new File(exportDirectory, "reports");
            if (!reportDirectory.exists()) {
                reportDirectory.mkdirs();
            }

            File reportFile = new File(reportDirectory, meetingInfo.meetingID + "-report.csv");
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(reportFile), StandardCharsets.UTF_8);
            writer.write('\uFEFF');
            writer.write("模块,字段,内容\n");
            writeCsvRow(writer, "摘要", "会议主题", meetingInfo.meetingTopic);
            writeCsvRow(writer, "摘要", "组织者", meetingInfo.organizer);
            writeCsvRow(writer, "摘要", "会议地点", meetingInfo.locationName);
            writeCsvRow(writer, "摘要", "开始时间", meetingInfo.getStartTimeText());
            writeCsvRow(writer, "摘要", "结束时间", meetingInfo.getEndTimeText());
            writeCsvRow(writer, "摘要", "提醒设置", meetingInfo.reminderMinutes > 0 ? "提前 " + meetingInfo.reminderMinutes + " 分钟" : "关闭");
            writeCsvRow(writer, "摘要", "我的签到状态", AttendeeInfo.getDisplayStatus(meetingInfo.selfCheckInStatus));
            writeCsvRow(writer, "摘要", "我的签到时间", meetingInfo.selfCheckInTime);
            writeCsvRow(writer, "摘要", "应到人数", String.valueOf(meetingInfo.getExpectedCount()));
            writeCsvRow(writer, "摘要", "已签到人数", String.valueOf(meetingInfo.getPresentCount()));
            writeCsvRow(writer, "摘要", "迟到人数", String.valueOf(meetingInfo.getLateCount()));
            writeCsvRow(writer, "摘要", "缺席人数", String.valueOf(meetingInfo.getAbsentCount()));
            writeCsvRow(writer, "记录", "共享会议记录", meetingInfo.meetingNote);
            writeCsvRow(writer, "附件", "图片记录", meetingInfo.imageUri);
            writeCsvRow(writer, "附件", "音频记录", meetingInfo.audioPath);
            writeSharedMaterialRows(writer);
            for (AttendeeInfo attendee : meetingInfo.attendees) {
                writeCsvRow(
                        writer,
                        "参会人",
                        attendee.getDisplayName(),
                        AttendeeInfo.getDisplayStatus(attendee.status) + " " + attendee.checkInTime
                );
            }
            writer.flush();
            writer.close();

            Uri reportUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    reportFile
            );
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, meetingInfo.meetingTopic + " 会议报表");
            shareIntent.putExtra(Intent.EXTRA_STREAM, reportUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "分享会议报表"));
        } catch (Exception e) {
            Toast.makeText(this, "导出报表失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void writeCsvRow(OutputStreamWriter writer, String column1, String column2, String column3) throws IOException {
        writer.write(csv(column1));
        writer.write(",");
        writer.write(csv(column2));
        writer.write(",");
        writer.write(csv(column3));
        writer.write("\n");
    }

    private void writeSharedMaterialRows(OutputStreamWriter writer) throws IOException {
        meetingInfo.ensureSharedMaterials();
        if (meetingInfo.sharedMaterials.isEmpty()) {
            writeCsvRow(writer, "附件", "共享资料", "");
            return;
        }
        int index = 1;
        for (SharedMaterialInfo material : meetingInfo.sharedMaterials) {
            if (material == null || !material.hasFile()) {
                continue;
            }
            String uploader = TextUtils.isEmpty(material.uploaderDisplayName)
                    ? material.uploaderUsername
                    : material.uploaderDisplayName;
            String description = material.name
                    + " | uploader=" + uploader
                    + " | access=" + material.getAccessLabel();
            writeCsvRow(writer, "附件", "共享资料 " + index, description);
            index += 1;
        }
        if (index == 1) {
            writeCsvRow(writer, "附件", "共享资料", "");
        }
    }

    private String csv(String rawValue) {
        String value = rawValue == null ? "" : rawValue;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void showLargeQrCode() {
        String payload = TextUtils.isEmpty(meetingInfo.shareCode)
                ? meetingInfo.toQrPayload()
                : MeetingQrPayload.encode(meetingInfo.meetingID, meetingInfo.shareCode);
        Bitmap largeBitmap = CodeCreator.createQRCode(payload, 520, 520, null);
        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(largeBitmap);
        imageView.setAdjustViewBounds(true);
        int padding = dpToPx(12);
        imageView.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(this)
                .setTitle("签到二维码")
                .setView(imageView)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void shareQrCode() {
        if (qrBitmap == null) {
            Toast.makeText(this, "当前没有可导出的二维码。", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String payload = TextUtils.isEmpty(meetingInfo.shareCode)
                    ? meetingInfo.toQrPayload()
                    : MeetingQrPayload.encode(meetingInfo.meetingID, meetingInfo.shareCode);
            Bitmap shareBitmap = CodeCreator.createQRCode(payload, 800, 800, null);
            File exportFile = new File(getCacheDir(), meetingInfo.meetingID + "-qr.png");
            FileOutputStream outputStream = new FileOutputStream(exportFile);
            shareBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            outputStream.flush();
            outputStream.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", exportFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("image/png");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, meetingInfo.meetingTopic + " 签到二维码");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "导出 / 分享签到二维码"));
        } catch (Exception e) {
            Toast.makeText(this, "导出二维码失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openDocumentPicker(String[] mimeTypes, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    private void openImageAttachment() {
        if (TextUtils.isEmpty(meetingInfo.imageUri)) {
            Toast.makeText(this, "当前没有图片记录。", Toast.LENGTH_SHORT).show();
            return;
        }
        openUri(Uri.parse(meetingInfo.imageUri), "image/*");
    }

    private void openFileAttachment() {
        if (TextUtils.isEmpty(meetingInfo.attachmentUri)) {
            Toast.makeText(this, "当前没有会议资料。", Toast.LENGTH_SHORT).show();
            return;
        }
        String mimeType = TextUtils.isEmpty(meetingInfo.attachmentMime) ? "*/*" : meetingInfo.attachmentMime;
        openUri(Uri.parse(meetingInfo.attachmentUri), mimeType);
    }

    private void openSharedAttachment() {
        if (!meetingInfo.hasSharedAttachment()) {
            Toast.makeText(this, "当前没有会议资料。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!meetingInfo.canOpenAttachment()) {
            Toast.makeText(this, "组织者尚未授予你查看这份资料的权限。", Toast.LENGTH_SHORT).show();
            return;
        }
        String mimeType = TextUtils.isEmpty(meetingInfo.attachmentMime) ? "*/*" : meetingInfo.attachmentMime;
        Uri uri = SharedAttachmentManager.resolveShareableUri(
                this,
                meetingInfo.attachmentUri,
                getPackageName() + ".fileprovider"
        );
        if (uri == null) {
            Toast.makeText(this, "当前资料文件不可用，请稍后重新同步。", Toast.LENGTH_SHORT).show();
            return;
        }
        openUri(uri, mimeType);
    }

    private void openUri(Uri uri, String mimeType) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没有可打开该文件的应用。", Toast.LENGTH_SHORT).show();
        }
    }

    private void startRecording() {
        if (!hasAudioPermission()) {
            pendingRecordingAfterPermission = true;
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_AUDIO_PERMISSION
            );
            return;
        }

        if (isRecording) {
            Toast.makeText(this, "当前正在录音中。", Toast.LENGTH_SHORT).show();
            return;
        }

        releasePlayer();
        File recordingDirectory = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (recordingDirectory == null) {
            Toast.makeText(this, "无法创建录音目录。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!recordingDirectory.exists()) {
            recordingDirectory.mkdirs();
        }

        File audioFile = new File(recordingDirectory, meetingInfo.meetingID + ".m4a");
        meetingInfo.audioPath = audioFile.getAbsolutePath();

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setAudioSamplingRate(44100);
        mediaRecorder.setAudioEncodingBitRate(96000);
        mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            isRecordingPaused = false;
            renderAudioSection();
            updateRecordingButtons();
        } catch (IOException | RuntimeException e) {
            releaseRecorder();
            Toast.makeText(this, "录音启动失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void pauseOrResumeRecording() {
        if (!isRecording || mediaRecorder == null) {
            Toast.makeText(this, "当前没有正在录制的音频。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Toast.makeText(this, "当前系统版本不支持暂停后继续录音。", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (isRecordingPaused) {
                mediaRecorder.resume();
                isRecordingPaused = false;
            } else {
                mediaRecorder.pause();
                isRecordingPaused = true;
            }
            renderAudioSection();
            updateRecordingButtons();
        } catch (RuntimeException e) {
            Toast.makeText(this, "录音状态切换失败。", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        if (!isRecording || mediaRecorder == null) {
            Toast.makeText(this, "当前没有可保存的录音。", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            mediaRecorder.stop();
            new DatabaseDAO(this).save(meetingInfo);
            Toast.makeText(this, "录音已保存。", Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            if (!TextUtils.isEmpty(meetingInfo.audioPath)) {
                new File(meetingInfo.audioPath).delete();
            }
            meetingInfo.audioPath = "";
            Toast.makeText(this, "录音时间太短，未保存。", Toast.LENGTH_SHORT).show();
        } finally {
            releaseRecorder();
            isRecording = false;
            isRecordingPaused = false;
            renderAudioSection();
            updateRecordingButtons();
        }
    }

    private void playAudio() {
        if (TextUtils.isEmpty(meetingInfo.audioPath)) {
            Toast.makeText(this, "当前没有录音文件。", Toast.LENGTH_SHORT).show();
            return;
        }
        File audioFile = new File(meetingInfo.audioPath);
        if (!audioFile.exists()) {
            Toast.makeText(this, "录音文件不存在。", Toast.LENGTH_SHORT).show();
            return;
        }

        releasePlayer();
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(meetingInfo.audioPath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            audioStatusText.setText("正在播放录音...");
            mediaPlayer.setOnCompletionListener(mp -> renderAudioSection());
        } catch (IOException e) {
            releasePlayer();
            Toast.makeText(this, "录音播放失败。", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteAudio() {
        releasePlayer();
        if (!TextUtils.isEmpty(meetingInfo.audioPath)) {
            new File(meetingInfo.audioPath).delete();
        }
        meetingInfo.audioPath = "";
        new DatabaseDAO(this).save(meetingInfo);
        renderAudioSection();
        updateRecordingButtons();
        Toast.makeText(this, "录音已删除。", Toast.LENGTH_SHORT).show();
    }

    private void updateRecordingButtons() {
        recordAudioButton.setEnabled(!isRecording);
        pauseAudioButton.setEnabled(isRecording);
        stopAudioButton.setEnabled(isRecording);
        playAudioButton.setEnabled(!isRecording && !TextUtils.isEmpty(meetingInfo.audioPath));
        deleteAudioButton.setEnabled(!TextUtils.isEmpty(meetingInfo.audioPath));
    }

    private void showAttendeeSummary(int position) {
        if (position < 0 || position >= meetingInfo.attendees.size()) {
            return;
        }
        AttendeeInfo attendee = meetingInfo.attendees.get(position);
        String message = "状态：" + AttendeeInfo.getDisplayStatus(attendee.status)
                + "\n签到时间：" + (TextUtils.isEmpty(attendee.checkInTime) ? "暂无" : attendee.checkInTime);
        new AlertDialog.Builder(this)
                .setTitle(attendee.getDisplayName())
                .setMessage(message)
                .setPositiveButton("关闭", null)
                .show();
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private Location getBestLastKnownLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) {
            return null;
        }
        Location bestLocation = null;
        try {
            for (String provider : locationManager.getProviders(true)) {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location == null) {
                    continue;
                }
                if (bestLocation == null || location.getAccuracy() < bestLocation.getAccuracy()) {
                    bestLocation = location;
                }
            }
        } catch (SecurityException ignored) {
        }
        return bestLocation;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        if (requestCode == REQUEST_EDIT_MEETING) {
            loadMeeting();
            return;
        }

        if (data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        final int takeFlags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (SecurityException ignored) {
        }

        if (requestCode == REQUEST_PICK_IMAGE) {
            meetingInfo.imageUri = uri.toString();
            new DatabaseDAO(this).save(meetingInfo);
            renderMeeting();
            return;
        }

        if (requestCode == REQUEST_PICK_SHARED_FILE) {
            promptSharedMaterialAccess(uri);
            return;
        }

        if (requestCode == REQUEST_PICK_FILE) {
            if (!meetingInfo.isOrganizer()) {
                Toast.makeText(this, "仅组织者可以上传共享资料。", Toast.LENGTH_SHORT).show();
                return;
            }
            promptAttachmentAccess(uri);
        }
    }

    private String queryDisplayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (columnIndex >= 0) {
                    return cursor.getString(columnIndex);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "会议资料";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = true;
        for (int result : grantResults) {
            granted = granted && result == PackageManager.PERMISSION_GRANTED;
        }

        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (granted && pendingRecordingAfterPermission) {
                pendingRecordingAfterPermission = false;
                startRecording();
            } else {
                Toast.makeText(this, "没有录音权限，无法开始录音。", Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (granted && pendingCheckInAfterPermission) {
                pendingCheckInAfterPermission = false;
                performCheckIn();
            } else {
                Toast.makeText(this, "没有定位权限，无法完成位置校验签到。", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isRecording) {
            stopRecording();
        }
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseRecorder();
        releasePlayer();
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.reset();
            } catch (Exception ignored) {
            }
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private int dpToPx(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
