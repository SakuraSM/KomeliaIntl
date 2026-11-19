import snd.komelia.DesktopAppModuleKt;
import snd.komelia.onnxruntime.JvmOnnxRuntime;
import snd.komelia.onnxruntime.OnnxRuntimeSharedLibraries;

/** Exercise process exit while the application-owned runtime is still reachable. */
public final class DesktopOnnxExitSmoke {
    private static JvmOnnxRuntime runtime;

    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Expected a temporary data directory");
        DesktopAppModuleKt.loadVipsLibraries();
        OnnxRuntimeSharedLibraries.INSTANCE.load();
        if (!OnnxRuntimeSharedLibraries.INSTANCE.isAvailable()) throw new AssertionError("ONNX Runtime unavailable");
        runtime = JvmOnnxRuntime.create(args[0]);
        System.out.println("MACOS_ONNX_EXIT_SMOKE_READY");
        System.exit(0);
    }
}
