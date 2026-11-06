import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import kotlin.Unit;
import snd.webview.KomeliaWebview;
import snd.webview.ResourceLoadResult;
import snd.webview.WebviewPanel;
import snd.webview.WebviewSharedLibraries;

/** Exercise navigation, resource interception and both native binding result paths. */
public final class DesktopWebviewSmoke {
    private static String encoded(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) {
        Thread.ofPlatform().daemon().start(() -> {
            try { Thread.sleep(60_000); } catch (InterruptedException ignored) { return; }
            System.err.println("Native WebView smoke test timed out");
            System.exit(1);
        });
        try {
            verify();
            System.out.println("NATIVE_WEBVIEW_SMOKE_PASSED");
            System.exit(0); // The native event-loop executor lives for the application lifetime.
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }

    private static void verify() throws Exception {
        WebviewSharedLibraries.INSTANCE.load();
        var created = new CompletableFuture<KomeliaWebview>();
        var reported = new CompletableFuture<String>();
        var frame = new JFrame("Komelia synthetic WebView test");
        SwingUtilities.invokeAndWait(() -> {
            frame.add(new WebviewPanel(view -> { created.complete(view); return Unit.INSTANCE; }));
            frame.setSize(640, 480);
            frame.setVisible(true);
        });
        try (var view = created.get(15, TimeUnit.SECONDS)) {
            view.registerRequestInterceptor((request, continuation) -> {
                String path = request.getUrl().getEncodedPath();
                if (path.equals("/resource.txt")) {
                    return new ResourceLoadResult("book resource".getBytes(StandardCharsets.UTF_8), "text/plain");
                }
                if (!path.equals("/index.html")) return null;
                String html = """
                    <meta charset="UTF-8"><h1>Synthetic reader</h1><script>
                    Promise.all([
                      window.hello().then(value => value.result),
                      window.fail().then(() => 'unexpected success', error => error.message || error.result),
                      fetch('/resource.txt').then(response => response.text())
                    ]).then(values => window.report(values.join('|')))
                      .catch(error => window.report('unexpected error: ' + error.message));
                    </script>
                    """;
                return new ResourceLoadResult(html.getBytes(StandardCharsets.UTF_8), "text/html");
            });
            view.registerBinding("hello", (id, request) -> view.returnBindingResult(id, encoded("{\"result\":\"中文\"}")));
            view.registerBinding("fail", (id, request) -> view.rejectBindingResult(id, "{\"result\":\"synthetic failure\"}"));
            view.registerBinding("report", (id, request) -> {
                view.returnBindingResult(id, encoded("{\"result\":\"\"}"));
                reported.complete(request);
            });
            view.navigateManaged("http://komelia/index.html");
            view.start();
            String result = reported.get(30, TimeUnit.SECONDS);
            if (!result.equals("[\"中文|synthetic failure|book resource\"]")) throw new AssertionError(result);
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
