package com.xjtucsse.meetingassistant;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class AddMeetingActivity extends AppCompatActivity {
    private static final int REQUEST_LOCATION_PERMISSION = 3001;

    private EditText topicInput;
    private EditText organizerInput;
    private EditText locationInput;
    private EditText durationHourInput;
    private EditText durationMinuteInput;
    private EditText attendeesInput;
    private EditText latitudeInput;
    private EditText longitudeInput;
    private EditText radiusInput;
    private CalendarView dateView;
    private TimePicker timePicker;
    private Spinner reminderSpinner;
    private Button saveButton;
    private Button fillLocationButton;

    private final Calendar selectedStartTime = Calendar.getInstance();
    private MeetingInfo editingMeeting;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_meeting);

        sessionManager = new SessionManager(this);
        bindViews();
        setupForm();
        loadEditingMeeting();
    }

    private void bindViews() {
        topicInput = findViewById(R.id.con_topic);
        organizerInput = findViewById(R.id.con_organizer);
        locationInput = findViewById(R.id.con_location_name);
        durationHourInput = findViewById(R.id.con_hour);
        durationMinuteInput = findViewById(R.id.con_min);
        attendeesInput = findViewById(R.id.con_attendees);
        latitudeInput = findViewById(R.id.con_latitude);
        longitudeInput = findViewById(R.id.con_longitude);
        radiusInput = findViewById(R.id.con_radius);
        dateView = findViewById(R.id.con_date);
        timePicker = findViewById(R.id.con_time);
        reminderSpinner = findViewById(R.id.con_reminder);
        saveButton = findViewById(R.id.add_submit);
        fillLocationButton = findViewById(R.id.btn_fill_location);
    }

    private void setupForm() {
        organizerInput.setText(sessionManager.getDisplayName() + " (@" + sessionManager.getUsername() + ")");
        organizerInput.setEnabled(false);

        selectedStartTime.set(Calendar.SECOND, 0);
        selectedStartTime.set(Calendar.MILLISECOND, 0);
        dateView.setDate(selectedStartTime.getTimeInMillis(), false, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.setHour(selectedStartTime.get(Calendar.HOUR_OF_DAY));
            timePicker.setMinute(selectedStartTime.get(Calendar.MINUTE));
        } else {
            timePicker.setCurrentHour(selectedStartTime.get(Calendar.HOUR_OF_DAY));
            timePicker.setCurrentMinute(selectedStartTime.get(Calendar.MINUTE));
        }
        timePicker.setIs24HourView(true);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.reminder_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reminderSpinner.setAdapter(adapter);

        dateView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            selectedStartTime.set(Calendar.YEAR, year);
            selectedStartTime.set(Calendar.MONTH, month);
            selectedStartTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        });

        timePicker.setOnTimeChangedListener((view, hourOfDay, minute) -> {
            selectedStartTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
            selectedStartTime.set(Calendar.MINUTE, minute);
        });

        fillLocationButton.setOnClickListener(v -> fillCurrentLocation());
        saveButton.setOnClickListener(v -> saveMeeting());
    }

    private void loadEditingMeeting() {
        String meetingId = getIntent().getStringExtra("meeting_id");
        if (TextUtils.isEmpty(meetingId)) {
            reminderSpinner.setSelection(1);
            radiusInput.setText("100");
            durationHourInput.setText("1");
            durationMinuteInput.setText("0");
            return;
        }

        editingMeeting = new DatabaseDAO(this).getMeeting(meetingId);
        if (editingMeeting == null) {
            Toast.makeText(this, "未找到要编辑的会议。", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        topicInput.setText(editingMeeting.meetingTopic);
        locationInput.setText(editingMeeting.locationName);
        attendeesInput.setText(buildAttendeesText(editingMeeting.attendees));
        radiusInput.setText(String.valueOf(editingMeeting.checkInRadiusMeters));
        if (!Double.isNaN(editingMeeting.latitude)) {
            latitudeInput.setText(formatCoordinate(editingMeeting.latitude));
        }
        if (!Double.isNaN(editingMeeting.longitude)) {
            longitudeInput.setText(formatCoordinate(editingMeeting.longitude));
        }

        selectedStartTime.setTimeInMillis(editingMeeting.meetingStartTime.getTimeInMillis());
        dateView.setDate(selectedStartTime.getTimeInMillis(), false, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            timePicker.setHour(selectedStartTime.get(Calendar.HOUR_OF_DAY));
            timePicker.setMinute(selectedStartTime.get(Calendar.MINUTE));
        } else {
            timePicker.setCurrentHour(selectedStartTime.get(Calendar.HOUR_OF_DAY));
            timePicker.setCurrentMinute(selectedStartTime.get(Calendar.MINUTE));
        }

        long durationMillis = editingMeeting.meetingEndTime.getTimeInMillis() - editingMeeting.meetingStartTime.getTimeInMillis();
        int durationMinutes = (int) (durationMillis / (60 * 1000));
        durationHourInput.setText(String.valueOf(Math.max(durationMinutes, 0) / 60));
        durationMinuteInput.setText(String.valueOf(Math.max(durationMinutes, 0) % 60));
        reminderSpinner.setSelection(getReminderSelection(editingMeeting.reminderMinutes));
        saveButton.setText("更新共享会议");
    }

    private void saveMeeting() {
        String topic = topicInput.getText().toString().trim();
        if (topic.isEmpty()) {
            Toast.makeText(this, "请填写会议主题。", Toast.LENGTH_SHORT).show();
            return;
        }

        int durationHours = parseInteger(durationHourInput.getText().toString().trim(), 0);
        int durationMinutes = parseInteger(durationMinuteInput.getText().toString().trim(), 0);
        if (durationHours == 0 && durationMinutes == 0) {
            Toast.makeText(this, "请填写会议持续时长。", Toast.LENGTH_SHORT).show();
            return;
        }
        if (durationMinutes >= 60) {
            Toast.makeText(this, "分钟数请控制在 0 到 59 之间。", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar startTime = MeetingInfo.cloneCalendar(selectedStartTime);
        startTime.set(Calendar.SECOND, 0);
        startTime.set(Calendar.MILLISECOND, 0);
        Calendar endTime = MeetingInfo.cloneCalendar(startTime);
        endTime.add(Calendar.HOUR_OF_DAY, durationHours);
        endTime.add(Calendar.MINUTE, durationMinutes);

        MeetingInfo meeting = editingMeeting == null ? new MeetingInfo() : editingMeeting;
        meeting.meetingTopic = topic;
        meeting.locationName = locationInput.getText().toString().trim();
        meeting.meetingStartTime = startTime;
        meeting.meetingEndTime = endTime;
        meeting.reminderMinutes = getReminderMinutes(reminderSpinner.getSelectedItemPosition());
        meeting.checkInRadiusMeters = parseInteger(radiusInput.getText().toString().trim(), 100);
        meeting.latitude = parseCoordinate(latitudeInput.getText().toString().trim());
        meeting.longitude = parseCoordinate(longitudeInput.getText().toString().trim());
        meeting.attendees = parseAttendees(attendeesInput.getText().toString(), editingMeeting == null ? null : editingMeeting.attendees);
        meeting.ensureMeetingId();

        saveButton.setEnabled(false);
        saveButton.setText("正在提交到服务器...");
        new Thread(() -> {
            try {
                ApiClient apiClient = new ApiClient(AddMeetingActivity.this);
                MeetingInfo cloudMeeting = editingMeeting == null
                        ? apiClient.createMeeting(meeting)
                        : apiClient.updateMeeting(meeting);
                cloudMeeting.copyLocalArtifactsFrom(editingMeeting);
                DatabaseDAO dao = new DatabaseDAO(AddMeetingActivity.this);
                dao.saveCloudMeeting(cloudMeeting);
                ReminderScheduler.scheduleReminder(AddMeetingActivity.this, cloudMeeting);
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    Toast.makeText(AddMeetingActivity.this, "共享会议已保存。", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (CloudException e) {
                runOnUiThread(() -> {
                    saveButton.setEnabled(true);
                    saveButton.setText(editingMeeting == null ? "保存共享会议" : "更新共享会议");
                    Toast.makeText(AddMeetingActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void fillCurrentLocation() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION_PERMISSION
            );
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) {
            Toast.makeText(this, "当前设备不可用定位服务。", Toast.LENGTH_SHORT).show();
            return;
        }

        Location bestLocation = null;
        try {
            List<String> providers = locationManager.getProviders(true);
            for (String provider : providers) {
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

        if (bestLocation == null) {
            Toast.makeText(this, "暂时获取不到当前位置，请稍后再试或手动填写。", Toast.LENGTH_SHORT).show();
            return;
        }

        latitudeInput.setText(formatCoordinate(bestLocation.getLatitude()));
        longitudeInput.setText(formatCoordinate(bestLocation.getLongitude()));
        if (TextUtils.isEmpty(locationInput.getText().toString().trim())) {
            locationInput.setText("当前会议地点");
        }
        Toast.makeText(this, "已填入当前位置。", Toast.LENGTH_SHORT).show();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION_PERMISSION) {
            return;
        }
        boolean granted = true;
        for (int result : grantResults) {
            granted = granted && result == PackageManager.PERMISSION_GRANTED;
        }
        if (granted) {
            fillCurrentLocation();
        } else {
            Toast.makeText(this, "没有定位权限，无法自动填充经纬度。", Toast.LENGTH_SHORT).show();
        }
    }

    private List<AttendeeInfo> parseAttendees(String rawValue, List<AttendeeInfo> currentAttendees) {
        LinkedHashSet<String> attendeeUsernames = new LinkedHashSet<>();
        String normalized = rawValue == null ? "" : rawValue.replace("，", ",");
        String[] lines = normalized.split("[\\n,]");
        for (String line : lines) {
            String username = line.trim().toLowerCase();
            if (!username.isEmpty()) {
                attendeeUsernames.add(username);
            }
        }

        Map<String, AttendeeInfo> existingAttendees = new LinkedHashMap<>();
        if (currentAttendees != null) {
            for (AttendeeInfo attendee : currentAttendees) {
                existingAttendees.put(attendee.username, attendee);
            }
        }

        List<AttendeeInfo> attendees = new ArrayList<>();
        for (String attendeeUsername : attendeeUsernames) {
            if (existingAttendees.containsKey(attendeeUsername)) {
                attendees.add(existingAttendees.get(attendeeUsername));
            } else {
                attendees.add(new AttendeeInfo(attendeeUsername, attendeeUsername, AttendeeInfo.STATUS_PENDING, ""));
            }
        }
        return attendees;
    }

    private int parseInteger(String rawValue, int defaultValue) {
        try {
            return Integer.parseInt(rawValue);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private double parseCoordinate(String rawValue) {
        try {
            if (rawValue.isEmpty()) {
                return Double.NaN;
            }
            return Double.parseDouble(rawValue);
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private int getReminderMinutes(int selection) {
        if (selection == 2) {
            return 30;
        }
        if (selection == 1) {
            return 15;
        }
        return 0;
    }

    private int getReminderSelection(int minutes) {
        if (minutes >= 30) {
            return 2;
        }
        if (minutes >= 15) {
            return 1;
        }
        return 0;
    }

    private String buildAttendeesText(List<AttendeeInfo> attendees) {
        if (attendees == null || attendees.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (AttendeeInfo attendee : attendees) {
            if (attendee == null || attendee.username == null) {
                continue;
            }
            if (attendee.username.equalsIgnoreCase(sessionManager.getUsername())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(attendee.username);
        }
        return builder.toString();
    }

    private String formatCoordinate(double value) {
        return new DecimalFormat("0.000000").format(value);
    }
}
