package app.hushmessenger.extension;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

// Real JPEG encoding and decoding need the native graphics stack; the legacy stand-in writes no image data.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 36})
public class OriginalPhotoTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private Executor completion;

    /** Stands in for Messenger's TranscodeImageCompletionCallback, with the same success signature. */
    public static final class Callback {
        final List<Object[]> successes = new ArrayList<>();

        public void success(String uri, double sourceWidth, double sourceHeight, double width, double height, double quality,
                            double psnr, boolean rotated, int a, boolean b, double c, double d, double e) {
            successes.add(new Object[] {uri, sourceWidth, sourceHeight, width, height, quality, rotated});
        }
    }

    @Before public void reset() {
        Settings.initialize(RuntimeEnvironment.getApplication());
        Settings.preferences.edit().clear().commit();
        Settings.activeAt.clear();
        Settings.hookErrors.clear();
        completion = OriginalPhoto.completion;
        OriginalPhoto.completion = Runnable::run;
        OriginalPhoto.tempDir = folder.getRoot().toPath().resolve("copies").toFile();
        assertTrue(OriginalPhoto.tempDir.mkdirs());
    }

    @After public void restore() {
        OriginalPhoto.completion = completion;
        OriginalPhoto.tempDir = null;
    }

    private File jpeg(int width, int height) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Random random = new Random(580);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) row[x] = 0xFF000000 | random.nextInt(0x1000000);
            bitmap.setPixels(row, 0, width, 0, y, width, 1);
        }
        File file = folder.newFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out));
        }
        return file;
    }

    private static Map<String, Object> hd() {
        Map<String, Object> extras = new HashMap<>();
        extras.put("IS_HD", true);
        extras.put("IS_ARMADILLO", true);
        return extras;
    }

    private static void switchOn() { Settings.preferences.edit().putBoolean(OriginalPhoto.KEY, true).commit(); }

    @Test public void anHdSendOfAnUprightJpegGoesOutAsTheFileItself() throws Exception {
        File photo = jpeg(1600, 1200);
        switchOn();
        assertArrayEquals(Files.readAllBytes(photo.toPath()), OriginalPhoto.sync(photo.getPath(), 4096, 4096, null, hd()));
        assertEquals("the copy is deleted once read", 0, OriginalPhoto.tempDir.listFiles().length);
        assertTrue(Settings.lastActive(OriginalPhoto.KEY) > 0);
        // Zero means Messenger set no size limit.
        assertNotNull(OriginalPhoto.sync(photo.getPath(), 0, 0, null, hd()));
    }

    @Test public void everythingElseKeepsMessengersTranscodeAndDoesntCountAsAUse() throws Exception {
        File photo = jpeg(1600, 1200);
        assertNull("switch off", OriginalPhoto.sync(photo.getPath(), 4096, 4096, null, hd()));
        switchOn();
        Map<String, Object> standard = hd();
        standard.put("IS_HD", false);
        assertNull("HD off", OriginalPhoto.sync(photo.getPath(), 4096, 4096, null, standard));
        assertNull("no extras", OriginalPhoto.sync(photo.getPath(), 4096, 4096, null, null));
        Map<String, Object> preview = hd();
        preview.put("IS_PREVIEW", true);
        assertNull("preview", OriginalPhoto.sync(photo.getPath(), 4096, 4096, null, preview));
        assertNull("thumbnail size", OriginalPhoto.sync(photo.getPath(), 512, 512, null, hd()));
        assertNull("bigger than the target", OriginalPhoto.sync(photo.getPath(), 1280, 1280, null, hd()));
        assertNull("relative path", OriginalPhoto.sync("photo.jpg", 4096, 4096, null, hd()));
        assertNull("missing file", OriginalPhoto.sync(new File(folder.getRoot(), "gone.jpg").getPath(), 4096, 4096, null, hd()));

        File png = folder.newFile();
        try (FileOutputStream out = new FileOutputStream(png)) {
            Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        assertNull("PNG", OriginalPhoto.sync(png.getPath(), 4096, 4096, null, hd()));

        File huge = folder.newFile();
        try (RandomAccessFile file = new RandomAccessFile(huge, "rw")) {
            file.write(new byte[] {(byte) 0xFF, (byte) 0xD8});
            file.setLength(OriginalPhoto.MAX_BYTES + 1);
        }
        assertNull("over the size cap", OriginalPhoto.sync(huge.getPath(), 4096, 4096, null, hd()));

        File rotated = jpeg(1600, 1200);
        ExifInterface exif = new ExifInterface(rotated.getPath());
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
        exif.saveAttributes();
        assertNull("rotation tag", OriginalPhoto.sync(rotated.getPath(), 4096, 4096, null, hd()));

        assertEquals(0, Settings.lastActive(OriginalPhoto.KEY));
        assertTrue(Settings.hookErrors.isEmpty());
    }

    @Test public void thePhotosLocationIsRemovedAndNothingElseChanges() throws Exception {
        File photo = jpeg(1600, 1200);
        ExifInterface exif = new ExifInterface(photo.getPath());
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "40/1,44/1,5424/100");
        exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N");
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "73/1,59/1,834/100");
        exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W");
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "443/1");
        exif.setAttribute(ExifInterface.TAG_MAKE, "HushCam");
        exif.saveAttributes();
        assertTrue(new ExifInterface(photo.getPath()).getLatLong(new float[2]));
        long original = photo.length();
        byte[] photoBefore = Files.readAllBytes(photo.toPath());
        switchOn();

        byte[] sent = OriginalPhoto.sync(photo.getPath(), 4096, 4096, null, hd());
        File out = folder.newFile();
        Files.write(out.toPath(), sent);
        ExifInterface result = new ExifInterface(out.getPath());
        assertFalse(result.getLatLong(new float[2]));
        for (String tag : OriginalPhoto.gpsTags()) assertNull(tag, result.getAttribute(tag));
        assertEquals("HushCam", result.getAttribute(ExifInterface.TAG_MAKE));
        assertTrue(Math.abs(sent.length - original) <= original / 20);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(out.getPath(), bounds);
        assertEquals(1600, bounds.outWidth);
        assertEquals(1200, bounds.outHeight);
        // The user's own file is never touched.
        assertArrayEquals(photoBefore, Files.readAllBytes(photo.toPath()));
        assertTrue(OriginalPhoto.gpsTags().contains(ExifInterface.TAG_GPS_LATITUDE));
    }

    @Test public void anAsyncSendReportsACopyOfTheFileAtItsOwnSize() throws Exception {
        File photo = jpeg(1600, 1200);
        switchOn();
        Callback callback = new Callback();
        assertTrue(OriginalPhoto.async(photo.getPath(), 4096, 4096, null, hd(), callback));
        assertEquals(1, callback.successes.size());
        Object[] call = callback.successes.get(0);
        File[] copies = OriginalPhoto.tempDir.listFiles();
        assertEquals(1, copies.length);
        File copy = copies[0];
        assertTrue((String) call[0], ((String) call[0]).startsWith("file:") && ((String) call[0]).endsWith(copy.getName()));
        assertArrayEquals(Files.readAllBytes(photo.toPath()), Files.readAllBytes(copy.toPath()));
        assertEquals(1600.0, call[1]);
        assertEquals(1200.0, call[2]);
        assertEquals(1600.0, call[3]);
        assertEquals(1200.0, call[4]);
        assertEquals(100.0, call[5]);
        assertEquals(false, call[6]);
        copy.delete();
    }

    @Test public void anAsyncSendItCantHandleIsLeftToMessenger() throws Exception {
        File photo = jpeg(1600, 1200);
        Callback callback = new Callback();
        assertFalse("switch off", OriginalPhoto.async(photo.getPath(), 4096, 4096, null, hd(), callback));
        switchOn();
        assertFalse("preview size", OriginalPhoto.async(photo.getPath(), 256, 256, null, hd(), callback));
        assertTrue(callback.successes.isEmpty());
        // A callback without Messenger's success method is a hook failure, and the send still goes out through Messenger.
        assertFalse(OriginalPhoto.async(photo.getPath(), 4096, 4096, null, hd(), new Object()));
        assertTrue(Settings.hookErrors.get(OriginalPhoto.KEY).startsWith("java.lang.NoSuchMethodException at "));
    }
}
