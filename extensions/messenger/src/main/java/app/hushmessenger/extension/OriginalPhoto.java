package app.hushmessenger.extension;

import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * "Send photos at original quality". In encrypted chats Messenger hands every photo to DefaultMediaTranscoder, which
 * re-encodes it even with HD on (a 6.4 MB 4032x3024 JPEG went out as about 1.8 MB). For an HD send of an upright JPEG
 * these prologues hand back a copy of the photo's own image data, without its metadata, and Messenger encrypts and
 * uploads that. Anything else, and any failure here, falls through to Messenger's own transcode.
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

    /** A file that isn't a JPEG this can pass through; Messenger's transcode handles it instead. */
    static final class NotPassable extends IOException {
        NotPassable(String reason) { super(reason); }
    }

    /** DefaultMediaTranscoder.transcodeImage: the photo's own image data, or null for Messenger's transcode. */
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
        } catch (IOException | RuntimeException | OutOfMemoryError error) {
            Settings.hookFailedPrivately(KEY, "Original photo failed, Messenger's copy is sent instead", error);
            return null;
        }
    }

    /** DefaultMediaTranscoder.transcodeImageAsync: true once this send is handled, false for Messenger's transcode. */
    public static boolean async(String url, double maxWidth, double maxHeight, String options, Map<?, ?> extras, Object callback) {
        if (callback == null) return false;
        Prepared prepared = null;
        try {
            Method success = successMethod(callback.getClass());
            Method failure = failureMethod(callback.getClass());
            prepared = prepare(url, maxWidth, maxHeight, extras);
            if (prepared == null) return false;
            Prepared sent = prepared;
            Log.i("HushMessenger", "Original photo: " + sent.copy.length() + " bytes, " + sent.width + "x" + sent.height);
            completion.execute(() -> report(callback, success, failure, sent));
            return true;
        } catch (IOException | ReflectiveOperationException | RuntimeException | OutOfMemoryError error) {
            if (prepared != null) prepared.copy.delete();
            Settings.hookFailedPrivately(KEY, "Original photo failed, Messenger's copy is sent instead", error);
            return false;
        }
    }

    /** Hands the copy to Messenger with the same arguments its own transcoder uses, or reports a failure so the send ends. */
    static void report(Object callback, Method success, Method failure, Prepared sent) {
        double width = sent.width, height = sent.height;
        try {
            // Output URI, source size, output size, quality, PSNR (-1 is Messenger's "not measured"), rotated, then
            // fields its wrapper zeroes anyway.
            success.invoke(callback, Uri.fromFile(sent.copy).toString(), width, height, width, height, 100.0, -1.0,
                false, 0, false, 0.0, 0.0, 0.0);
        } catch (ReflectiveOperationException | RuntimeException error) {
            sent.copy.delete();
            Settings.hookFailedPrivately(KEY, "Original photo couldn't hand over its copy", error);
            try {
                failure.invoke(callback, width, height, new IOException("HushMessenger couldn't hand over the original photo"));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Messenger's own failure path threw too; the error above is already recorded.
            }
        }
    }

    static Method successMethod(Class<?> callback) throws NoSuchMethodException {
        return callback.getMethod("success", String.class, double.class, double.class, double.class, double.class,
            double.class, double.class, boolean.class, int.class, boolean.class, double.class, double.class, double.class);
    }

    static Method failureMethod(Class<?> callback) throws NoSuchMethodException {
        return callback.getMethod("failure", double.class, double.class, Throwable.class);
    }

    /** A metadata-free copy of the photo when this send can use the original, else null. */
    static Prepared prepare(String url, double maxWidth, double maxHeight, Map<?, ?> extras) throws IOException {
        if (extras == null || !Boolean.TRUE.equals(extras.get("IS_HD")) || Boolean.TRUE.equals(extras.get("IS_PREVIEW"))) return null;
        // Nothing below touches the file while the switch is off, paused or in safe mode.
        if (!Settings.wouldUse(KEY)) return null;
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
        int[] bounds = bounds(path);
        if (bounds == null) return skip("no image size");
        int longSide = Math.max(bounds[0], bounds[1]), shortSide = Math.min(bounds[0], bounds[1]);
        if (longTarget > 0 && (longSide > longTarget || shortSide > shortTarget)) {
            return skip(bounds[0] + "x" + bounds[1] + " is larger than " + (int) maxWidth + "x" + (int) maxHeight);
        }
        File copy = File.createTempFile("hush-photo", ".jpg", tempDir);
        try {
            copyImageData(file, copy);
            int[] copied = bounds(copy.getPath());
            if (copied == null || copied[0] != bounds[0] || copied[1] != bounds[1]) throw new NotPassable("copy decodes differently");
        } catch (NotPassable error) {
            copy.delete();
            return skip(error.getMessage());
        } catch (IOException | RuntimeException | OutOfMemoryError error) {
            copy.delete();
            throw error;
        }
        // Recorded last, so "Used" on the settings screen means a photo really went out as is.
        if (!Settings.enabled(KEY)) {
            copy.delete();
            return null;
        }
        return new Prepared(copy, bounds[0], bounds[1]);
    }

    static int[] bounds(String path) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        // Its own stream, closed here, so the copy can be deleted right after on any filesystem.
        try (InputStream in = new BufferedInputStream(new FileInputStream(path))) {
            BitmapFactory.decodeStream(in, null, options);
        }
        return options.outWidth > 0 && options.outHeight > 0 ? new int[] {options.outWidth, options.outHeight} : null;
    }

    /** Says why an HD photo keeps Messenger's transcode; the reason never includes the file's name or contents. */
    static Prepared skip(String reason) {
        Log.i("HushMessenger", "Original photo skipped: " + reason);
        return null;
    }

    static boolean startsLikeJpeg(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return in.read() == 0xFF && in.read() == 0xD8;
        }
    }

    /**
     * Copies a JPEG's image data and nothing else, the way Messenger's own re-encode leaves out the metadata. JFIF, the
     * ICC color profile and Adobe's color transform stay, since they change how the pixels look. EXIF (location, camera,
     * time and its thumbnail), XMP, IPTC, comments, JFXX thumbnails, multi-picture data and anything after the end of the
     * image, such as a motion photo's video, are left out. The scan data is copied byte for byte.
     */
    static void copyImageData(File source, File target) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(source), 65536));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(target), 65536)) {
            if (in.readUnsignedByte() != 0xFF || in.readUnsignedByte() != 0xD8) throw new NotPassable("not a JPEG");
            out.write(0xFF);
            out.write(0xD8);
            int marker = nextMarker(in);
            while (true) {
                if (marker == 0xD9) {
                    out.write(0xFF);
                    out.write(0xD9);
                    return;
                }
                if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) throw new NotPassable("stray marker");
                int length = in.readUnsignedShort();
                if (length < 2) throw new NotPassable("bad segment length");
                byte[] payload = new byte[length - 2];
                in.readFully(payload);
                if (keep(marker, payload)) {
                    out.write(0xFF);
                    out.write(marker);
                    out.write(length >> 8);
                    out.write(length & 0xFF);
                    out.write(payload);
                }
                marker = marker == 0xDA ? copyScan(in, out) : nextMarker(in);
            }
        } catch (EOFException truncated) {
            throw new NotPassable("ends early");
        }
    }

    /** Reads the next marker code, skipping the fill bytes JPEG allows before it. */
    static int nextMarker(DataInputStream in) throws IOException {
        if (in.readUnsignedByte() != 0xFF) throw new NotPassable("expected a marker");
        int marker;
        do marker = in.readUnsignedByte(); while (marker == 0xFF);
        if (marker == 0x00) throw new NotPassable("expected a marker");
        return marker;
    }

    /** Copies entropy-coded data, with its stuffed bytes and restart markers, and returns the marker that ends it. */
    static int copyScan(DataInputStream in, OutputStream out) throws IOException {
        while (true) {
            int value = in.readUnsignedByte();
            if (value != 0xFF) {
                out.write(value);
                continue;
            }
            int next;
            do next = in.readUnsignedByte(); while (next == 0xFF);
            if (next == 0x00 || (next >= 0xD0 && next <= 0xD7)) {
                out.write(0xFF);
                out.write(next);
                continue;
            }
            return next;
        }
    }

    static boolean keep(int marker, byte[] payload) {
        if (marker == 0xE0) return startsWith(payload, "JFIF\0");
        if (marker == 0xE2) return startsWith(payload, "ICC_PROFILE\0");
        if (marker == 0xEE) return startsWith(payload, "Adobe");
        // Every other APPn segment and comments are metadata; tables, frame and scan headers are the image.
        return !(marker >= 0xE0 && marker <= 0xEF) && marker != 0xFE;
    }

    static boolean startsWith(byte[] payload, String prefix) {
        byte[] expected = prefix.getBytes(StandardCharsets.US_ASCII);
        if (payload.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (payload[i] != expected[i]) return false;
        return true;
    }
}
