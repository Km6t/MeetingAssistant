package com.xjtucsse.meetingassistant;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SharedMaterialInfo {
    public String materialId;
    public String name;
    public String mimeType;
    public String accessMode;
    public List<String> allowedUsers;
    public boolean available;
    public String contentBase64;
    public String localUri;
    public String uploaderUsername;
    public String uploaderDisplayName;
    public boolean canManage;
    public String uploadedAt;
    public String updatedAt;

    public SharedMaterialInfo() {
        materialId = "";
        name = "";
        mimeType = "";
        accessMode = MeetingInfo.ATTACHMENT_ACCESS_ALL_ATTENDEES;
        allowedUsers = new ArrayList<>();
        available = false;
        contentBase64 = "";
        localUri = "";
        uploaderUsername = "";
        uploaderDisplayName = "";
        canManage = false;
        uploadedAt = "";
        updatedAt = "";
    }

    public boolean hasFile() {
        return !safe(name).trim().isEmpty();
    }

    public boolean canOpen() {
        return hasFile() && available && !safe(localUri).trim().isEmpty();
    }

    public String getAccessLabel() {
        return MeetingInfo.getAttachmentAccessLabel(accessMode);
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        JSONArray allowedUsersArray = new JSONArray();
        try {
            object.put("materialId", safe(materialId));
            object.put("name", safe(name));
            object.put("mimeType", safe(mimeType));
            object.put("accessMode", MeetingInfo.normalizeAttachmentAccessMode(accessMode));
            for (String username : allowedUsers) {
                String normalized = safe(username).trim();
                if (!normalized.isEmpty()) {
                    allowedUsersArray.put(normalized);
                }
            }
            object.put("allowedUsers", allowedUsersArray);
            object.put("available", available);
            object.put("contentBase64", safe(contentBase64));
            object.put("localUri", safe(localUri));
            object.put("uploaderUsername", safe(uploaderUsername));
            object.put("uploaderDisplayName", safe(uploaderDisplayName));
            object.put("canManage", canManage);
            object.put("uploadedAt", safe(uploadedAt));
            object.put("updatedAt", safe(updatedAt));
        } catch (Exception ignored) {
        }
        return object;
    }

    public static JSONArray toJsonArray(List<SharedMaterialInfo> materials) {
        JSONArray array = new JSONArray();
        if (materials == null) {
            return array;
        }
        for (SharedMaterialInfo material : materials) {
            if (material == null || !material.hasFile()) {
                continue;
            }
            array.put(material.toJson());
        }
        return array;
    }

    public static List<SharedMaterialInfo> fromJsonArray(JSONArray array) {
        List<SharedMaterialInfo> materials = new ArrayList<>();
        if (array == null) {
            return materials;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            SharedMaterialInfo material = new SharedMaterialInfo();
            material.materialId = item.optString("materialId");
            material.name = item.optString("name");
            material.mimeType = item.optString("mimeType");
            material.accessMode = MeetingInfo.normalizeAttachmentAccessMode(item.optString("accessMode"));
            material.allowedUsers = MeetingInfo.deserializeUsernames(jsonArrayToText(item.optJSONArray("allowedUsers")));
            material.available = item.optBoolean("available", false);
            material.contentBase64 = item.optString("contentBase64");
            material.localUri = item.optString("localUri");
            material.uploaderUsername = item.optString("uploaderUsername");
            material.uploaderDisplayName = item.optString("uploaderDisplayName");
            material.canManage = item.optBoolean("canManage", false);
            material.uploadedAt = item.optString("uploadedAt");
            material.updatedAt = item.optString("updatedAt");
            if (material.hasFile()) {
                materials.add(material);
            }
        }
        return materials;
    }

    public static String serializeList(List<SharedMaterialInfo> materials) {
        return toJsonArray(materials).toString();
    }

    public static List<SharedMaterialInfo> deserializeList(String rawValue) {
        try {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return new ArrayList<>();
            }
            return fromJsonArray(new JSONArray(rawValue));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static String jsonArrayToText(JSONArray items) {
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
