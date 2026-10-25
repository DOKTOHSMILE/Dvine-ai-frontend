package ai.dvine.face;

import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Loads the low-RAM 3D head. Controllers are set via JS. */
public final class HeadWebView extends WebView {
    private boolean ready;

    public HeadWebView(Context context) {
        super(context);
        WebSettings s = getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        setBackgroundColor(0xFF000000);
        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                ready = true;
            }
        });
        loadUrl("file:///android_asset/dvine-3d-head.html");
    }

    public void drive(boolean ivie, float smile, float jaw, float brow, float yaw) {
        if (!ready) return;
        String js = String.format(
                "document.getElementById('smile').value=%f;" +
                "document.getElementById('jaw').value=%f;" +
                "document.getElementById('brow').value=%f;" +
                "document.getElementById('yaw').value=%f;" +
                "if(%s) document.getElementById('ivie').click(); else document.getElementById('obi').click();",
                smile, jaw, brow, yaw, ivie ? "true" : "false");
        evaluateJavascript(js, null);
    }
}
