package ai.dvine.chat.ui;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ai.dvine.R;

/**
 * Renders one HTML page the brain already wrote. JavaScript can run inside the page.
 * There is no bridge back into the app.
 */
public final class HtmlViewActivity extends AppCompatActivity {
    public static final String EXTRA_HTML = "html";

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_html_view);
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
        web.loadDataWithBaseURL("https://dvine.local/", page(getIntent().getStringExtra(EXTRA_HTML)),
                "text/html", "utf-8", null);
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
