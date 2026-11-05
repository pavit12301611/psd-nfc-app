package com.psd.nfcapp;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * ID card photo helpers: pick -> downscale -> JPEG -> base64 (stored with the card),
 * decode for display, and a full-screen viewer that shows the whole photo (not cropped).
 */
public final class PhotoUtil {
    /** Long side of the stored photo. Big enough to stay sharp when shown full screen. */
    private static final int STORE_MAX_SIDE = 1024;
    private static final int JPEG_QUALITY = 85;

    private PhotoUtil() {
    }

    /** Reads a picked image into a JPEG encoded as base64. Returns null on failure. */
    public static String fromUri(Context ctx, Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            while (largest / (sample * 2) >= STORE_MAX_SIDE) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return null;

            int w = bmp.getWidth();
            int h = bmp.getHeight();
            int max = Math.max(w, h);
            if (max > STORE_MAX_SIDE) {
                float scale = (float) STORE_MAX_SIDE / max;
                bmp = Bitmap.createScaledBitmap(bmp, Math.round(w * scale), Math.round(h * scale), true);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bos);
            return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /** Decodes a stored photo at full size. Returns null if there is no usable photo. */
    public static Bitmap decode(String base64) {
        return decode(base64, Integer.MAX_VALUE);
    }

    /** Decodes a stored photo, sub-sampled so its long side is about maxSide (for thumbnails). */
    public static Bitmap decode(String base64, int maxSide) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] b = Base64.decode(base64, Base64.DEFAULT);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(b, 0, b.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            while (largest / (sample * 2) >= maxSide) sample *= 2;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(b, 0, b.length, opts);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Opens the whole photo full screen on a black background, scaled to fit so nothing is
     * cropped. Tap anywhere to close.
     */
    public static void showFullScreen(Activity activity, String base64) {
        Bitmap bmp = decode(base64);
        if (bmp == null || activity == null || activity.isFinishing()) return;
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(activity);
        iv.setBackgroundColor(0xFF000000);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(bmp);
        iv.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(iv, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dialog.show();
    }
}
