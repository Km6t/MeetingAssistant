package com.xjtucsse.meetingassistant;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class QrImageDecoder {

    private static final int MAX_IMAGE_DIMENSION = 1024;

    public static String decode(Context context, Uri imageUri) throws Exception {
        Bitmap bitmap = loadSampledBitmap(context, imageUri, MAX_IMAGE_DIMENSION);
        if (bitmap == null) {
            throw new Exception("无法加载图片");
        }
        try {
            return decodeBitmap(bitmap);
        } finally {
            bitmap.recycle();
        }
    }

    private static Bitmap loadSampledBitmap(Context context, Uri uri, int maxDimension) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, options);
        }
        int inSampleSize = 1;
        if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
            int halfWidth = options.outWidth / 2;
            int halfHeight = options.outHeight / 2;
            while ((halfWidth / inSampleSize) >= maxDimension
                    || (halfHeight / inSampleSize) >= maxDimension) {
                inSampleSize *= 2;
            }
        }
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = inSampleSize;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, decodeOptions);
            if (bitmap == null) {
                throw new Exception("无法解码图片");
            }
            return bitmap;
        }
    }

    private static String decodeBitmap(Bitmap bitmap) throws NotFoundException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        return tryDecodeMultipleFilters(pixels, width, height);
    }

    private static String tryDecodeMultipleFilters(int[] pixels, int width, int height) throws NotFoundException {
        // Try 1: original pixels
        try {
            return tryDecode(pixels, width, height, false);
        } catch (NotFoundException ignored) {}

        // Try 2: inverted (for dark background QR codes)
        try {
            return tryDecode(pixels, width, height, true);
        } catch (NotFoundException ignored) {}

        // Try 3: grayscale binarize with aggressive threshold
        try {
            int[] binary = new int[pixels.length];
            int threshold = calculateOtsuThreshold(pixels);
            for (int i = 0; i < pixels.length; i++) {
                int gray = rgbToGray(pixels[i]);
                if (gray < threshold) {
                    binary[i] = 0xFF000000;
                } else {
                    binary[i] = 0xFFFFFFFF;
                }
            }
            return tryDecode(binary, width, height, false);
        } catch (NotFoundException ignored) {}

        // Try 4: inverted binarized
        try {
            int[] binary = new int[pixels.length];
            int threshold = calculateOtsuThreshold(pixels);
            for (int i = 0; i < pixels.length; i++) {
                int gray = rgbToGray(pixels[i]);
                if (gray < threshold) {
                    binary[i] = 0xFFFFFFFF;
                } else {
                    binary[i] = 0xFF000000;
                }
            }
            return tryDecode(binary, width, height, false);
        } catch (NotFoundException ignored) {}

        throw NotFoundException.getNotFoundInstance();
    }

    private static String tryDecode(int[] pixels, int width, int height, boolean invert) throws NotFoundException {
        int[] workingPixels = pixels;
        if (invert) {
            workingPixels = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                workingPixels[i] = pixels[i] ^ 0x00FFFFFF;
            }
        }
        LuminanceSource source = new RGBLuminanceSource(width, height, workingPixels);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS,
                java.util.Collections.singletonList(com.google.zxing.BarcodeFormat.QR_CODE));
        MultiFormatReader reader = new MultiFormatReader();
        reader.setHints(hints);
        try {
            Result result = reader.decode(binaryBitmap);
            return result.getText();
        } finally {
            reader.reset();
        }
    }

    private static int rgbToGray(int pixel) {
        int r = (pixel >> 16) & 0xFF;
        int g = (pixel >> 8) & 0xFF;
        int b = pixel & 0xFF;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    private static int calculateOtsuThreshold(int[] pixels) {
        int[] histogram = new int[256];
        for (int pixel : pixels) {
            int gray = rgbToGray(pixel);
            histogram[gray]++;
        }

        int total = pixels.length;
        double sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }

        double sumB = 0;
        int wB = 0;
        double maxVariance = 0;
        int threshold = 128;

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;

            sumB += (double) t * histogram[t];
            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;
            double between = (double) wB * wF * (mB - mF) * (mB - mF);

            if (between > maxVariance) {
                maxVariance = between;
                threshold = t;
            }
        }
        return threshold;
    }
}
