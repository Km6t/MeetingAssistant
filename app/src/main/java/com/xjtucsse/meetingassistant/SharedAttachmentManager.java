package com.xjtucsse.meetingassistant;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;

import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SharedAttachmentManager {
    private static final int MAX_SHARED_ATTACHMENT_BYTES = 24 * 1024 * 1024;

    private SharedAttachmentManager() {
    }

    public static String readAttachmentAsBase64(Context context, Uri uri) throws IOException {
        if (uri == null) {
            throw new IOException("请选择要共享的资料文件。");
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int totalBytes = 0;
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("无法读取所选文件。");
            }
            int readCount;
            while ((readCount = inputStream.read(buffer)) >= 0) {
                totalBytes += readCount;
                if (totalBytes > MAX_SHARED_ATTACHMENT_BYTES) {
                    throw new IOException("共享资料请控制在 24 MB 以内。");
                }
                outputStream.write(buffer, 0, readCount);
            }
        }
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    public static void syncSharedMaterials(Context context, MeetingInfo meeting, MeetingInfo previousMeeting) {
        if (meeting == null) {
            return;
        }
        meeting.ensureSharedMaterials();
        Map<String, SharedMaterialInfo> previousMaterials = new HashMap<>();
        if (previousMeeting != null && previousMeeting.sharedMaterials != null) {
            for (SharedMaterialInfo previousMaterial : previousMeeting.sharedMaterials) {
                if (previousMaterial != null && !TextUtils.isEmpty(previousMaterial.materialId)) {
                    previousMaterials.put(previousMaterial.materialId, previousMaterial);
                }
            }
        }

        Set<String> incomingMaterialIds = new HashSet<>();
        for (SharedMaterialInfo material : meeting.sharedMaterials) {
            if (material == null || !material.hasFile()) {
                continue;
            }
            incomingMaterialIds.add(material.materialId);
            SharedMaterialInfo previousMaterial = previousMaterials.get(material.materialId);
            syncSharedMaterial(context, meeting, material, previousMaterial);
        }

        for (SharedMaterialInfo previousMaterial : previousMaterials.values()) {
            if (previousMaterial == null || incomingMaterialIds.contains(previousMaterial.materialId)) {
                continue;
            }
            deleteManagedMaterial(previousMaterial);
        }
        meeting.syncLegacyAttachmentFieldsFromMaterials();
    }

    public static Uri resolveShareableUri(Context context, String attachmentUri, String authority) {
        if (TextUtils.isEmpty(attachmentUri)) {
            return null;
        }
        if (isManagedAttachmentPath(attachmentUri)) {
            File file = new File(attachmentUri);
            if (file.exists()) {
                return FileProvider.getUriForFile(context, authority, file);
            }
        }
        return Uri.parse(attachmentUri);
    }

    private static void syncSharedMaterial(Context context, MeetingInfo meeting, SharedMaterialInfo material, SharedMaterialInfo previousMaterial) {
        if (!material.available) {
            deleteManagedMaterial(previousMaterial);
            material.localUri = "";
            material.contentBase64 = "";
            return;
        }

        if (!TextUtils.isEmpty(material.contentBase64)) {
            try {
                File targetFile = getManagedAttachmentFile(context, meeting, material);
                byte[] bytes = Base64.decode(material.contentBase64, Base64.DEFAULT);
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                    outputStream.write(bytes);
                }
                material.localUri = targetFile.getAbsolutePath();
            } catch (Exception ignored) {
                material.localUri = "";
            } finally {
                material.contentBase64 = "";
            }
            return;
        }

        if (previousMaterial != null && isManagedAttachmentPath(previousMaterial.localUri)) {
            File previousFile = new File(previousMaterial.localUri);
            if (previousFile.exists()) {
                material.localUri = previousFile.getAbsolutePath();
                return;
            }
        }
        material.localUri = "";
    }

    private static File getManagedAttachmentFile(Context context, MeetingInfo meeting, SharedMaterialInfo material) {
        File root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (root == null) {
            root = context.getCacheDir();
        }
        File directory = new File(root, "shared_meeting_files");
        String fileName = sanitizeFileName(
                meeting.meetingID + "-" + material.materialId + "-" + material.name
        );
        return new File(directory, fileName);
    }

    private static String sanitizeFileName(String rawValue) {
        String normalized = rawValue == null ? "meeting-file" : rawValue.trim();
        normalized = normalized.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (normalized.isEmpty()) {
            normalized = "meeting-file";
        }
        if (normalized.length() > 96) {
            normalized = normalized.substring(0, 96);
        }
        return normalized.toLowerCase(Locale.US);
    }

    private static void deleteManagedMaterial(SharedMaterialInfo material) {
        if (material == null || !isManagedAttachmentPath(material.localUri)) {
            return;
        }
        File file = new File(material.localUri);
        if (file.exists()) {
            file.delete();
        }
    }

    private static boolean isManagedAttachmentPath(String attachmentUri) {
        return !TextUtils.isEmpty(attachmentUri)
                && !attachmentUri.contains("://")
                && attachmentUri.contains("shared_meeting_files");
    }
}
