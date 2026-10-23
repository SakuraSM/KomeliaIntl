import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import snd.komelia.DesktopAppModuleKt;
import snd.komelia.image.VipsImage;
import snd.komelia.image.VipsSharedLibraries;
import snd.webview.WebviewSharedLibraries;

/** Run with the installed application's jars and resource directory. */
public final class DesktopNativeSmoke {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !(args[0].equals("system-webview") || args[0].equals("bundled"))) {
            throw new IllegalArgumentException("Choose system-webview or bundled");
        }
        if (args[0].equals("system-webview")) WebviewSharedLibraries.INSTANCE.load();
        DesktopAppModuleKt.loadVipsLibraries();
        if (!VipsSharedLibraries.INSTANCE.isAvailable()) {
            throw new AssertionError("The installed image decoder did not initialize",
                    VipsSharedLibraries.INSTANCE.getLoadError());
        }

        var source = new BufferedImage(16, 12, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) source.setRGB(x, y, 0x3366CC);
        }
        var bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(source, "png", bytes)) throw new AssertionError("PNG fixture encoding failed");
        try (var decoded = VipsImage.decode(bytes.toByteArray(), null)) {
            if (decoded.getWidth() != 16 || decoded.getHeight() != 12) {
                throw new AssertionError("Decoded dimensions differ from the fixture");
            }
            try (var resized = decoded.resize(8, 6, "LANCZOS3", false)) {
                byte[] pixels = resized.getBytes();
                if (resized.getWidth() != 8 || resized.getHeight() != 6 || resized.getBands() != 4 || pixels.length != 8 * 6 * 4) {
                    throw new AssertionError("Resized dimensions or pixel count differ from the fixture");
                }
                for (int i = 0; i < pixels.length; i += 4) {
                    if ((pixels[i] & 255) != 0x33 || (pixels[i + 1] & 255) != 0x66 || (pixels[i + 2] & 255) != 0xCC || (pixels[i + 3] & 255) != 255) {
                        throw new AssertionError("Native decoding or resizing changed a solid-color pixel");
                    }
                }
            }
            byte[] identityLut = new byte[256 * decoded.getBands()];
            for (int value = 0; value < 256; value++) {
                for (int band = 0; band < decoded.getBands(); band++) {
                    identityLut[value * decoded.getBands() + band] = (byte) value;
                }
            }
            try (var mapped = decoded.mapLookupTable(identityLut)) {
                if (mapped.getWidth() != decoded.getWidth() || mapped.getHeight() != decoded.getHeight()) {
                    throw new AssertionError("Lookup-table construction changed image dimensions");
                }
            }
        }
        try (var thumbnail = VipsImage.thumbnailBuffer(bytes.toByteArray(), 8, 6, false)) {
            if (thumbnail.getWidth() != 8 || thumbnail.getHeight() != 6 || thumbnail.getBytes().length != 8 * 6 * 4) {
                throw new AssertionError("Cover thumbnail dimensions or pixel count differ from the fixture");
            }
        }
        var jpeg = new ByteArrayOutputStream();
        if (!ImageIO.write(source, "jpeg", jpeg)) throw new AssertionError("JPEG fixture encoding failed");
        try (var thumbnail = VipsImage.thumbnailBuffer(jpeg.toByteArray(), 8, 6, false)) {
            if (thumbnail.getWidth() != 8 || thumbnail.getHeight() != 6 || thumbnail.getBytes().length != 8 * 6 * 4) {
                throw new AssertionError("JPEG cover thumbnail dimensions or pixel count differ from the fixture");
            }
        }
        System.out.println("NATIVE_MEDIA_SMOKE_PASSED " + args[0]);
    }
}
