package ai.ibiko.chat.ui;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ai.ibiko.R;

/**
 * Renders one HTML page the brain already wrote. JavaScript can run inside the page.
 * There is no bridge back into the app.
 */
public final class HtmlViewActivity extends AppCompatActivity {
    public static final String EXTRA_HTML = "html";
    /** A page too big for an Intent, in a file (read here, off nothing else). */
    public static final String EXTRA_FILE = "file";

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_html_view);
        Ui.fitInsets(((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0)); // below the status bar, above the navigation bar
        web = findViewById(R.id.html_web);
        TextView back = findViewById(R.id.html_back);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }
        if (web == null) {
            return;
        }
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false);
        String html = getIntent().getStringExtra(EXTRA_HTML);
        String path = getIntent().getStringExtra(EXTRA_FILE);
        if (html == null && path != null) {
            try {
                html = new String(java.nio.file.Files.readAllBytes(new java.io.File(path).toPath()), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException | OutOfMemoryError e) {
                html = "<p>The page couldn't be opened.</p>";
            }
        }
        web.loadDataWithBaseURL("https://ibiko.local/", page(html), "text/html", "utf-8", null);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private static String page(String html) {
        if (html == null) {
            return "";
        }
        return html;
    }
}
