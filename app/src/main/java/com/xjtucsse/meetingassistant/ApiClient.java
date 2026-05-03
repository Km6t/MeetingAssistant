package com.xjtucsse.meetingassistant;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ApiClient {
    private final SessionManager sessionManager;

    public ApiClient(Context context) {
        sessionManager = new SessionManager(context);
    }

    public AuthSession register(String serverUrl, String username, String password, String displayName) throws CloudException {
        sessionManager.saveServerUrl(serverUrl);
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
            body.put("displayName", displayName);
            JSONObject response = request("POST", "/api/auth/register", body, null);
            return parseAuthSession(response);
        } catch (JSONException e) {
            throw new CloudException("Failed to build register request.", e);
        }
    }

    public AuthSession login(String serverUrl, String username, String password) throws CloudException {
        sessionManager.saveServerUrl(serverUrl);
        JSONObject body = new JSONObject();
        try {
            body.put("username", username);
            body.put("password", password);
            JSONObject response = request("POST", "/api/auth/login", body, null);
            return parseAuthSession(response);
        } catch (JSONException e) {
            throw new CloudException("Failed to build login request.", e);
        }
    }

    public void logout() throws CloudException {
        request("POST", "/api/auth/logout", new JSONObject(), requireToken());
    }

    public List<MeetingInfo> fetchMeetings() throws CloudException {
        JSONObject response = request("GET", "/api/meetings", null, requireToken());
        JSONArray meetingsArray = response.optJSONArray("meetings");
        List<MeetingInfo> meetings = new ArrayList<>();
        if (meetingsArray == null) {
            return meetings;
        }
        for (int i = 0; i < meetingsArray.length(); i++) {
            meetings.add(parseMeeting(meetingsArray.optJSONObject(i)));
        }
        return meetings;
    }

    public MeetingInfo getMeeting(String meetingId) throws CloudException {
        JSONObject response = request("GET", "/api/meetings/" + meetingId, null, requireToken());
        return parseMeeting(response.optJSONObject("meeting"));
    }

    public MeetingInfo createMeeting(MeetingInfo meeting) throws CloudException {
        JSONObject response = request("POST", "/api/meetings", buildMeetingPayload(meeting), requireToken());
        return parseMeeting(response.optJSONObject("meeting"));
    }

    public MeetingInfo updateMeeting(MeetingInfo meeting) throws CloudException {
        JSONObject response = request("PUT", "/api/meetings/" + meeting.meetingID, buildMeetingPayload(meeting), requireToken());
        return parseMeeting(response.optJSONObject("meeting"));
    }

    public MeetingInfo uploadSharedMaterial(String meetingId, SharedMaterialInfo material) throws CloudException {
        JSONObject response = request(
                "POST",
                "/api/meetings/" + meetingId + "/attachments",
                buildSharedMaterialPayload(material),
                requireToken()
        );
        return parseMeeting(response.optJSONObject("meeting"));
    }

    public MeetingInfo deleteSharedMaterial(String meetingId, String materialId) throws CloudException {
        JSONObject response = request(
                "DELETE",
                "/api/meetings/" + meetingId + "/attachments/" + materialId,
                null,
                requireToken()
        );
        return parseMeeting(response.optJSONObject("meeting"));
    }

    public void deleteOrLeaveMeeting(String meetingId) throws CloudException {
        request("DELETE", "/api/meetings/" + meetingId, null, requireToken());
    }

    public MeetingInfo checkIn(String meetingId) throws CloudException {
        JSONObject response = request("POST", "/api/meetings/" + meetingId + "/checkin", new JSONObject(), requireToken());
        return parseMeeting(response.optJSONObject("meeting"));
    }

    public MeetingInfo resolveMeetingByShareCode(String shareCode) throws CloudException {
        JSONObject body = new JSONObject();
        try {
            body.put("shareCode", shareCode);
            JSONObject response = request("POST", "/api/meetings/resolve-code", body, requireToken());
            return parseMeeting(response.optJSONObject("meeting"));
        } catch (JSONException e) {
            throw new CloudException("Failed to resolve QR code.", e);
        }
    }

    private JSONObject buildMeetingPayload(MeetingInfo meeting) throws CloudException {
        JSONObject body = new JSONObject();
        JSONArray invitees = new JSONArray();
        try {
            body.put("topic", safe(meeting.meetingTopic));
            body.put("locationName", safe(meeting.locationName));
            body.put("note", safe(meeting.meetingNote));
            body.put("reminderMinutes", meeting.reminderMinutes);
            body.put("checkInRadiusMeters", meeting.checkInRadiusMeters);
            if (!Double.isNaN(meeting.latitude)) {
                body.put("latitude", meeting.latitude);
            }
            if (!Double.isNaN(meeting.longitude)) {
                body.put("longitude", meeting.longitude);
            }
            body.put("startTime", meeting.getStartTimeText());
            body.put("endTime", meeting.getEndTimeText());
            for (AttendeeInfo attendee : meeting.attendees) {
                String username = safe(attendee.username);
                if (!username.isEmpty()) {
                    invitees.put(username);
                }
            }
            body.put("invitees", invitees);
            return body;
        } catch (JSONException e) {
            throw new CloudException("Failed to build meeting request.", e);
        }
    }

    private JSONObject buildSharedMaterialPayload(SharedMaterialInfo material) throws CloudException {
        JSONObject body = new JSONObject();
        JSONArray allowedUsers = new JSONArray();
        try {
            body.put("materialId", safe(material.materialId));
            body.put("name", safe(material.name));
            body.put("mimeType", safe(material.mimeType));
            body.put("accessMode", MeetingInfo.normalizeAttachmentAccessMode(material.accessMode));
            for (String username : material.allowedUsers) {
                String normalized = safe(username);
                if (!normalized.isEmpty()) {
                    allowedUsers.put(normalized);
                }
            }
            body.put("allowedUsers", allowedUsers);
            body.put("contentBase64", safe(material.contentBase64));
            return body;
        } catch (JSONException e) {
            throw new CloudException("Failed to build shared material request.", e);
        }
    }

    private AuthSession parseAuthSession(JSONObject response) throws CloudException {
        if (response == null) {
            throw new CloudException("Empty auth response.");
        }
        JSONObject user = response.optJSONObject("user");
        if (user == null) {
            throw new CloudException("Auth response is missing user info.");
        }
        return new AuthSession(
                response.optString("token"),
                user.optString("username"),
                user.optString("displayName")
        );
    }

    private MeetingInfo parseMeeting(JSONObject object) throws CloudException {
        if (object == null) {
            throw new CloudException("Meeting response is empty.");
        }
        MeetingInfo meeting = new MeetingInfo();
        meeting.meetingID = object.optString("meetingId");
        meeting.meetingTopic = object.optString("topic");
        meeting.organizer = object.optString("organizerName");
        meeting.locationName = object.optString("locationName");
        meeting.meetingNote = object.optString("note");
        meeting.reminderMinutes = object.optInt("reminderMinutes", 15);
        meeting.checkInRadiusMeters = object.optInt("checkInRadiusMeters", 100);
        if (object.has("latitude")) {
            meeting.latitude = object.optDouble("latitude", Double.NaN);
        }
        if (object.has("longitude")) {
            meeting.longitude = object.optDouble("longitude", Double.NaN);
        }
        meeting.meetingStartTime = MeetingInfo.parseDateTime(object.optString("startTime"));
        meeting.meetingEndTime = MeetingInfo.parseDateTime(object.optString("endTime"));
        meeting.shareCode = object.optString("shareCode");
        meeting.memberRole = object.optString("memberRole", MeetingInfo.ROLE_ATTENDEE);
        meeting.selfCheckInStatus = AttendeeInfo.normalizeStatus(object.optString("selfCheckInStatus"));
        meeting.selfCheckInTime = object.optString("selfCheckInTime");
        meeting.attendees = parseAttendees(object.optJSONArray("attendees"));
        parseAttachments(object.optJSONArray("attachments"), object.optJSONObject("attachment"), meeting);
        meeting.syncLegacyAttachmentFieldsFromMaterials();
        return meeting;
    }

    private void parseAttachments(JSONArray items, JSONObject legacyAttachment, MeetingInfo meeting) {
        if (meeting == null) {
            return;
        }
        meeting.sharedMaterials = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                SharedMaterialInfo material = parseSharedMaterial(item);
                if (material != null) {
                    meeting.sharedMaterials.add(material);
                }
            }
        }
        if (meeting.sharedMaterials.isEmpty()) {
            SharedMaterialInfo legacyMaterial = parseSharedMaterial(legacyAttachment);
            if (legacyMaterial != null) {
                meeting.sharedMaterials.add(legacyMaterial);
            }
        }
    }

    private SharedMaterialInfo parseSharedMaterial(JSONObject object) {
        if (object == null) {
            return null;
        }
        SharedMaterialInfo material = new SharedMaterialInfo();
        material.materialId = object.optString("materialId");
        material.name = object.optString("name");
        material.mimeType = object.optString("mimeType");
        material.accessMode = MeetingInfo.normalizeAttachmentAccessMode(object.optString("accessMode"));
        material.allowedUsers = MeetingInfo.deserializeUsernames(jsonArrayToText(object.optJSONArray("allowedUsers")));
        material.available = object.optBoolean("available", false);
        material.contentBase64 = object.optString("contentBase64");
        material.localUri = "";
        material.uploaderUsername = object.optString("uploaderUsername");
        material.uploaderDisplayName = object.optString("uploaderDisplayName");
        material.canManage = object.optBoolean("canManage", false);
        material.uploadedAt = object.optString("uploadedAt");
        material.updatedAt = object.optString("updatedAt");
        return material.hasFile() ? material : null;
    }

    private List<AttendeeInfo> parseAttendees(JSONArray attendeesArray) {
        List<AttendeeInfo> attendees = new ArrayList<>();
        if (attendeesArray == null) {
            return attendees;
        }
        for (int i = 0; i < attendeesArray.length(); i++) {
            JSONObject item = attendeesArray.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String username = item.optString("username");
            String displayName = item.optString("displayName");
            String status = item.optString("status");
            String checkInTime = item.optString("checkInTime");
            attendees.add(new AttendeeInfo(username, TextUtils.isEmpty(displayName) ? username : displayName, status, checkInTime));
        }
        return attendees;
    }

    private JSONObject request(String method, String path, JSONObject body, String token) throws CloudException {
        HttpURLConnection connection = null;
        try {
            boolean sharedMaterialUpload = path != null && path.contains("/attachments")
                    && body != null
                    && "POST".equals(method);
            URL url = new URL(sessionManager.getServerUrl() + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(sharedMaterialUpload ? 20000 : 10000);
            connection.setReadTimeout(sharedMaterialUpload ? 120000 : 10000);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            if (!TextUtils.isEmpty(token)) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (body != null && !"GET".equals(method) && !"DELETE".equals(method)) {
                connection.setDoOutput(true);
                if (sharedMaterialUpload) {
                    connection.setChunkedStreamingMode(8192);
                }
                OutputStream outputStream = connection.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                writer.write(body.toString());
                writer.flush();
                writer.close();
                outputStream.close();
            }

            int statusCode = connection.getResponseCode();
            String responseText = readResponse(
                    statusCode >= 200 && statusCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream()
            );
            JSONObject responseObject;
            try {
                responseObject = responseText.isEmpty() ? new JSONObject() : new JSONObject(responseText);
            } catch (JSONException parseException) {
                if (statusCode == 413) {
                    throw new CloudException("Shared material is too large for the current 24 MB upload limit.");
                }
                throw new CloudException("Server returned invalid data.", parseException);
            }
            if (statusCode >= 200 && statusCode < 300) {
                return responseObject;
            }
            throw new CloudException(responseObject.optString("error", "Request failed with status " + statusCode));
        } catch (SocketTimeoutException e) {
            throw new CloudException("Upload timed out. Check the file size, server deployment, or network speed.", e);
        } catch (IOException e) {
            if (path != null && path.contains("/attachments")) {
                throw new CloudException("Upload failed. The server may still be using the old upload limit or the network was interrupted.", e);
            }
            throw new CloudException("Cannot connect to server. Check the server address and network.", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String requireToken() throws CloudException {
        String token = sessionManager.getAuthToken();
        if (token.isEmpty()) {
            throw new CloudException("Please log in first.");
        }
        return token;
    }

    private String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        reader.close();
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String jsonArrayToText(JSONArray items) {
        if (items == null || items.length() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < items.length(); i++) {
            String value = items.optString(i).trim();
            if (value.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(value);
        }
        return builder.toString();
    }
}
