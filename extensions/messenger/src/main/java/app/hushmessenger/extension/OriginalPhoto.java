package app.hushmessenger.extension;

import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * "Send photos at original quality". In encrypted chats Messenger hands every photo to DefaultMediaTranscoder, which
 * re-encodes it even with HD on (a 6.4 MB 4032x3024 JPEG went out as about 1.8 MB). For an HD send of an upright JPEG
 * these prologues hand back a copy of the file itself, minus its location tags, and Messenger encrypts and uploads that.
 * Anything else, and any failure here, falls through to Messenger's own transcode.
 */
public final class OriginalPhoto {
    private OriginalPhoto() { }

    static final String KEY = "original_photo";
    /** Larger originals keep Messenger's transcode, which fits them under its send limit. */
    static final long MAX_BYTES = 20L * 1024 * 1024;
    /** Smaller targets are thumbnails and previews, never the photo that gets sent. */
    static final double MIN_TARGET = 1024;
    /** Completes async sends off the calling thread, the way Messenger's own transcoder does. */
    static Executor completion = Executors.newSingleThreadExecutor();
    /** Where the copies go; null is the app's cache, where Messenger's own transcoder writes its output too. */
    static File tempDir;

    /** A prepared send: the copy to upload and its size in pixels. */
    static final class Prepared {
        final File copy;
        final int width, height;

        Prepared(File copy, int width, int height) {
            this.copy = copy;
            this.width = width;
            this.height = height;
        }
    }

    /** DefaultMediaTranscoder.transcodeImage: the photo's own bytes, or null for Messenger's transcode. */
    public static byte[] sync(String url, double maxWidth, double maxHeight, String options, Map<?, ?> extras) {
        try {
            Prepared prepared = prepare(url, maxWidth, maxHeight, extras);
            if (prepared == null) return null;
            try {
                byte[] bytes = Files.readAllBytes(prepared.copy.toPath());
                Log.i("HushMessenger", "Original photo: " + bytes.length + " bytes, " + prepared.width + "x" + prepared.height);
                return bytes;
            } finally {
                prepared.copy.delete();
            }
        } catch (IOException | RuntimeException error) {
            Settings.hookFailed(KEY, "Original photo failed, Messenger's copy is sent instead", error);
            return null;
        }
    }

    /** DefaultMediaTranscoder.transcodeImageAsync: true once this send is handled, false for Messenger's transcode. */
    public static boolean async(String url, double maxWidth, double maxHeight, String options, Map<?, ?> extras, Object callback) {
        try {
            if (callback == null) return false;
            Method success = successMethod(callback.getClass());
            Prepared prepared = prepare(url, maxWidth, maxHeight, extras);
            if (prepared == null) return false;
            String uri = Uri.fromFile(prepared.copy).toString();
            double width = prepared.width, height = prepared.height;
            Log.i("HushMessenger", "Original photo: " + prepared.copy.length() + " bytes, " + prepared.width + "x" + prepared.height);
            // Same argument order as Messenger's own success call: output URI, source size, output size, quality,
            // PSNR (not measured), rotated, then fields its wrapper zeroes anyway.
            completion.execute(() -> {
                try {
                    success.invoke(callback, uri, width, height, width, height, 100.0, -1.0, false, 0, false, 0.0, 0.0, 0.0);
                } catch (ReflectiveOperationException | RuntimeException error) {
                    Settings.hookFailed(KEY, "Original photo couldn't report its copy", error);
                }
            });
            return true;
        } catch (IOException | ReflectiveOperationException | RuntimeException error) {
            Settings.hookFailed(KEY, "Original photo failed, Messenger's copy is sent instead", error);
            return false;
        }
    }

    static Method successMethod(Class<?> callback) throws NoSuchMethodException {
        return callback.getMethod("success", String.class, double.class, double.class, double.class, double.class,
            double.class, double.class, boolean.class, int.class, boolean.class, double.class, double.class, double.class);
    }

    /** A location-free copy of the photo when this send can use the original, else null. */
    static Prepared prepare(String url, double maxWidth, double maxHeight, Map<?, ?> extras) throws IOException {
        if (extras == null || !Boolean.TRUE.equals(extras.get("IS_HD")) || Boolean.TRUE.equals(extras.get("IS_PREVIEW"))) return null;
        double longTarget = Math.max(maxWidth, maxHeight), shortTarget = Math.min(maxWidth, maxHeight);
        // Zero means no limit.
        if (longTarget > 0 && longTarget < MIN_TARGET) return skip("preview size " + (int) maxWidth + "x" + (int) maxHeight);
        String path = url == null ? null : url.startsWith("file:") ? Uri.parse(url).getPath() : new File(url).isAbsolute() ? url : null;
        if (path == null) return skip("not a file");
        File file = new File(path);
        long size = file.length();
        if (!file.isFile() || size <= 0) return skip("unreadable file");
        if (size > MAX_BYTES) return skip("over " + MAX_BYTES / 1024 / 1024 + " MB");
        if (!startsLikeJpeg(file)) return skip("not a JPEG");
        // A rotation tag would leave the receiver to turn the photo, and Messenger's metadata assumes it's upright.
        int orientation = new ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) return skip("rotation tag " + orientation);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return skip("no image size");
        int longSide = Math.max(bounds.outWidth, bounds.outHeight), shortSide = Math.min(bounds.outWidth, bounds.outHeight);
        if (longTarget > 0 && (longSide > longTarget || shortSide > shortTarget)) {
            return skip(bounds.outWidth + "x" + bounds.outHeight + " is larger than " + (int) maxWidth + "x" + (int) maxHeight);
        }
        // Checked last, so "Used" on the settings screen means a photo really went out as is.
        if (!Settings.enabled(KEY)) return null;
        File copy = File.createTempFile("hush-photo", ".jpg", tempDir);
        try {
            Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
            removeLocation(copy);
            return new Prepared(copy, bounds.outWidth, bounds.outHeight);
        } catch (IOException | RuntimeException error) {
            copy.delete();
            throw error;
        }
    }

    /** Says why an HD photo keeps Messenger's transcode; the reason never includes the file's name or contents. */
    static Prepared skip(String reason) {
        android.content.SharedPreferences prefs = Settings.preferences;
        if (prefs != null && prefs.getBoolean(KEY, false)) Log.i("HushMessenger", "Original photo skipped: " + reason);
        return null;
    }

    static boolean startsLikeJpeg(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return in.read() == 0xFF && in.read() == 0xD8;
        }
    }

    /** Every GPS tag ExifInterface knows, so a new Android version's additions are covered too. */
    static List<String> gpsTags() {
        List<String> tags = new ArrayList<>();
        for (Field field : ExifInterface.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != String.class || !field.getName().startsWith("TAG_GPS_")) continue;
            try {
                tags.add((String) field.get(null));
            } catch (IllegalAccessException ignored) {
                // Public fields are readable; nothing to add for one that isn't.
            }
        }
        return tags;
    }

    /** Messenger's own transcode drops the photo's location, so the original goes out without it too. */
    static void removeLocation(File copy) throws IOException {
        ExifInterface exif = new ExifInterface(copy.getPath());
        boolean found = false;
        for (String tag : gpsTags()) {
            if (exif.getAttribute(tag) == null) continue;
            exif.setAttribute(tag, null);
            found = true;
        }
        if (found) exif.saveAttributes();
    }
}
