import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import snd.komelia.DesktopAppModuleKt;
import snd.komelia.image.VipsImage;
import snd.komelia.onnxruntime.JvmOnnxRuntime;
import snd.komelia.onnxruntime.JvmOnnxRuntimeRfDetr;
import snd.komelia.onnxruntime.JvmOnnxRuntimeUpscaler;
import snd.komelia.onnxruntime.OnnxRuntimeExecutionProvider;
import snd.komelia.onnxruntime.OnnxRuntimeSharedLibraries;

/** Test the bundled macOS CPU runtime with a synthetic identity model. */
public final class DesktopOnnxSmoke {
    private static final String IDENTITY_MODEL =
            "CAg6iAEKGQoFaW5wdXQSBm91dHB1dCIISWRlbnRpdHkSEnN5bnRoZXRpY19pZGVudGl0eVoqCgVpbnB1dBIhCh8IARIbCgIIAQoCCAMKCBIGaGVpZ2h0CgcSBXdpZHRoYisKBm91dHB1dBIhCh8IARIbCgIIAQoCCAMKCBIGaGVpZ2h0CgcSBXdpZHRoQgQKABAR";

    private static void completes(Runnable action) throws Exception {
        var executor = Executors.newSingleThreadExecutor(runnable -> {
            var thread = new Thread(runnable, "onnx-smoke");
            thread.setDaemon(true);
            return thread;
        });
        try {
            executor.submit(action).get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid model unexpectedly succeeded");
        } catch (snd.komelia.onnxruntime.OnnxRuntimeException expected) {
            // The next operation must still be able to use the same runtime instance.
        }
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("komelia-onnx-smoke-");
        Path model = directory.resolve("identity.onnx");
        Files.write(model, Base64.getDecoder().decode(IDENTITY_MODEL));
        DesktopAppModuleKt.loadVipsLibraries();
        OnnxRuntimeSharedLibraries.INSTANCE.load();
        if (!OnnxRuntimeSharedLibraries.INSTANCE.isAvailable() ||
                OnnxRuntimeSharedLibraries.INSTANCE.getExecutionProvider() != OnnxRuntimeExecutionProvider.CPU) {
            throw new AssertionError("Bundled ONNX CPU runtime did not initialize");
        }
        var fixture = new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 6; y++) for (int x = 0; x < 8; x++) fixture.setRGB(x, y, 0x3366cc);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(fixture, "png", bytes);
        try (var runtime = JvmOnnxRuntime.create(directory.toString());
             var image = VipsImage.decode(bytes.toByteArray(), null);
             var upscaler = JvmOnnxRuntimeUpscaler.Companion.create(runtime);
             var detector = JvmOnnxRuntimeRfDetr.Companion.create(runtime)) {
            if (!runtime.enumerateDevices().isEmpty()) throw new AssertionError("CPU reported a GPU device");
            upscaler.setExecutionProvider(OnnxRuntimeExecutionProvider.CPU, 0);
            expectFailure(() -> upscaler.upscale(image));
            completes(() -> upscaler.setModelPath(directory.resolve("missing.onnx").toString()));
            expectFailure(() -> upscaler.upscale(image));
            completes(() -> upscaler.setModelPath(model.toString()));
            try (var result = upscaler.upscale(image)) {
                if (result.getWidth() != 8 || result.getHeight() != 6) {
                    throw new AssertionError("Identity model returned incorrect dimensions");
                }
                var pixels = result.getBytes();
                for (int i = 0; i < pixels.length; i += result.getBands()) {
                    if ((pixels[i] & 255) != 0x33 || (pixels[i + 1] & 255) != 0x66 || (pixels[i + 2] & 255) != 0xcc) {
                        throw new AssertionError("Identity model changed a synthetic pixel");
                    }
                }
            }
            detector.setExecutionProvider(OnnxRuntimeExecutionProvider.CPU, 0);
            expectFailure(() -> detector.detect(image));
            completes(() -> detector.setModelPath(directory.resolve("missing.onnx").toString()));
            expectFailure(() -> detector.detect(image));
            completes(() -> detector.setModelPath(model.toString()));
        }
        Files.delete(model);
        Files.delete(directory);
        System.out.println("MACOS_ONNX_CPU_SMOKE_PASSED");
    }
}
