package snd.komelia;

import android.graphics.Bitmap;
import android.util.Base64;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import snd.komelia.image.VipsImage;
import snd.komelia.image.VipsSharedLibrariesLoader;
import snd.komelia.onnxruntime.JvmOnnxRuntime;
import snd.komelia.onnxruntime.JvmOnnxRuntimeUpscaler;
import snd.komelia.onnxruntime.JvmOnnxRuntimeRfDetr;
import snd.komelia.onnxruntime.OnnxRuntimeException;
import snd.komelia.onnxruntime.OnnxRuntimeExecutionProvider;
import snd.komelia.onnxruntime.OnnxRuntimeSharedLibraries;

/** Verify that the installed APK can recover from native model-loading errors. */
public final class AndroidOnnxRuntimeRecoveryTest {
    private static final String IDENTITY_MODEL = "CAg6iAEKGQoFaW5wdXQSBm91dHB1dCIISWRlbnRpdHkSEnN5bnRoZXRpY19pZGVudGl0eVoqCgVpbnB1dBIhCh8IARIbCgIIAQoCCAMKCBIGaGVpZ2h0CgcSBXdpZHRoYisKBm91dHB1dBIhCh8IARIbCgIIAQoCCAMKCBIGaGVpZ2h0CgcSBXdpZHRoQgQKABAR";

    @Test public void decodesJpegWithPackagedImageLibraries() {
        assertDecodedImage(Bitmap.CompressFormat.JPEG, 3);
    }

    @Test public void decodesPngWithPackagedImageLibraries() {
        assertDecodedImage(Bitmap.CompressFormat.PNG, 0);
    }

    private static void assertDecodedImage(Bitmap.CompressFormat format, int tolerance) {
        VipsSharedLibrariesLoader.INSTANCE.load();
        var bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        byte[] encoded;
        try {
            bitmap.eraseColor(0xff3366cc);
            var bytes = new ByteArrayOutputStream();
            assertTrue(bitmap.compress(format, 100, bytes));
            encoded = bytes.toByteArray();
        } finally {
            bitmap.recycle();
        }
        try (var image = VipsImage.decode(encoded, null)) {
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
            assertTrue("RGB output is required", image.getBands() >= 3);
            byte[] pixels = image.getBytes();
            assertEquals(16 * 16 * image.getBands(), pixels.length);
            assertTrue(Math.abs((pixels[0] & 255) - 0x33) <= tolerance);
            assertTrue(Math.abs((pixels[1] & 255) - 0x66) <= tolerance);
            assertTrue(Math.abs((pixels[2] & 255) - 0xcc) <= tolerance);
        }
    }

    @Test public void nativeErrorsAllowRetryAndIdentityInference() throws Exception {
        Path directory = Files.createTempDirectory(
            InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath(), "onnx-package-");
        var executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "onnx-package-probe");
            thread.setDaemon(true);
            return thread;
        });
        try {
            executor.submit(() -> { verify(directory); return null; }).get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new)) Files.deleteIfExists(path);
            }
        }
    }

    private static void verify(Path directory) throws Exception {
        Path model = directory.resolve("identity.onnx");
        Path missing = directory.resolve("missing.onnx");
        Files.write(model, Base64.decode(IDENTITY_MODEL, Base64.DEFAULT));
        VipsSharedLibrariesLoader.INSTANCE.load();
        OnnxRuntimeSharedLibraries.INSTANCE.load();
        assertTrue(OnnxRuntimeSharedLibraries.INSTANCE.isAvailable());
        var bitmap = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888);
        byte[] png;
        try {
            bitmap.eraseColor(0xff3366cc);
            var bytes = new ByteArrayOutputStream();
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes));
            png = bytes.toByteArray();
        } finally {
            bitmap.recycle();
        }
        try (var runtime = JvmOnnxRuntime.create(directory.toString());
             var image = VipsImage.decode(png, null);
             var upscaler = JvmOnnxRuntimeUpscaler.Companion.create(runtime);
             var detector = JvmOnnxRuntimeRfDetr.Companion.create(runtime)) {
            upscaler.setExecutionProvider(OnnxRuntimeExecutionProvider.CPU, 0);
            assertThrows(OnnxRuntimeException.class, () -> upscaler.upscale(image));
            upscaler.setModelPath(missing.toString());
            assertThrows(OnnxRuntimeException.class, () -> upscaler.upscale(image));
            upscaler.setModelPath(model.toString());
            try (var result = upscaler.upscale(image)) {
                assertEquals(8, result.getWidth());
                assertEquals(6, result.getHeight());
                assertTrue("RGB output is required", result.getBands() >= 3);
                byte[] pixels = result.getBytes();
                assertEquals(8 * 6 * result.getBands(), pixels.length);
                for (int offset = 0; offset < pixels.length; offset += result.getBands()) {
                    assertEquals(0x33, pixels[offset] & 255);
                    assertEquals(0x66, pixels[offset + 1] & 255);
                    assertEquals(0xcc, pixels[offset + 2] & 255);
                }
            }
            detector.setExecutionProvider(OnnxRuntimeExecutionProvider.CPU, 0);
            assertThrows(OnnxRuntimeException.class, () -> detector.detect(image));
            detector.setModelPath(missing.toString());
            assertThrows(OnnxRuntimeException.class, () -> detector.detect(image));
            detector.setModelPath(model.toString());
        }
    }
}
