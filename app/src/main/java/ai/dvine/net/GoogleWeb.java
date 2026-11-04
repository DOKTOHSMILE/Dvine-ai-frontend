package ai.dvine.net;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Google search with no key and no project: google.com's own results page, opened in a browser on the device that
 * isn't shown (Google's results need JavaScript, so they can't be fetched as plain HTML), and read: each result's
 * title, address and snippet. When Google asks to prove the user isn't a robot (its "sorry" page), it gives nothing
 * and the search goes on with the next one. Only google.com pages load in it.
 */
public final class GoogleWeb {
    static final long PAGE_MS = 20_000L, SETTLE_MS = 2_000L;
    private static final Object ONE_AT_A_TIME = new Object();

    private GoogleWeb() {
    }

    /** The JavaScript that reads the results: a JSON array of {t, u, s}. */
    static final String READ = "(function(){var out=[],seen={};"
            + "document.querySelectorAll('a h3').forEach(function(h){var a=h.closest('a');if(!a)return;var u=a.href||'';"
            + "if(!/^https?:/.test(u)||seen[u])return;"
            + "if(/^https?:\\/\\/([a-z]+\\.)?google\\.[a-z.]+\\//.test(u)&&u.indexOf('/url?')<0)return;"
            + "seen[u]=1;var box=a.closest('div[data-hveid],div.g,div.MjjYud')||a.parentElement;"
            + "var s=box?(box.innerText||'').replace(h.innerText,'').trim():'';"
            + "out.push({t:h.innerText,u:u,s:s.slice(0,400)});});"
            + "return JSON.stringify({at:location.href,r:out.slice(0,12)});})()";

    /** Up to {@code most} results for {@code q}, or none (no browser, the robot check, nothing found). */
    public static List<WebSearch.Hit> search(Context c, String q, int most) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("searching blocks: call it off the main thread");
        }
        synchronized (ONE_AT_A_TIME) {
            String json = load(c, url(q));
            return parse(json, most);
        }
    }

    static String url(String q) {
        try {
            return "https://www.google.com/search?hl=en&num=10&q=" + URLEncoder.encode(q == null ? "" : q, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            return "https://www.google.com/search?q=" + q;
        }
    }

    /** The results the page script gave ({"at": the page's address, "r": [{t, u, s}]}). Pure Java. */
    static List<WebSearch.Hit> parse(String json, int most) {
        List<WebSearch.Hit> out = new ArrayList<WebSearch.Hit>();
        if (json == null || json.isEmpty()) {
            return out;
        }
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            if (o.optString("at").contains("/sorry/")) {
                return out; // Google's robot check: nothing to read
            }
            org.json.JSONArray r = o.optJSONArray("r");
            for (int i = 0; r != null && i < r.length() && out.size() < most; i++) {
                org.json.JSONObject h = r.getJSONObject(i);
                String u = real(h.optString("u"));
                String t = h.optString("t").trim();
                if (u.isEmpty() || t.isEmpty()) {
                    continue;
                }
                out.add(new WebSearch.Hit(t, u, h.optString("s").replaceAll("\\s+", " ").trim()));
            }
        } catch (org.json.JSONException e) {
            return out;
        }
        return out;
    }

    /** A Google redirect ("/url?q=https://…&sa=…") as the page it leads to. */
    static String real(String u) {
        if (u == null) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[?&](?:q|url)=(https?[^&]+)").matcher(u);
        if (u.contains("google.") && u.contains("/url?") && m.find()) {
            try {
                return java.net.URLDecoder.decode(m.group(1), StandardCharsets.UTF_8.name());
            } catch (java.io.UnsupportedEncodingException | IllegalArgumentException e) {
                return m.group(1);
            }
        }
        return u;
    }

    private static String load(Context c, final String url) {
        final Context app = c.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final CountDownLatch done = new CountDownLatch(1);
        final String[] out = new String[1];
        final WebView[] holder = new WebView[1];
        main.post(() -> {
            try {
                WebView w = new WebView(app);
                holder[0] = w;
                WebSettings s = w.getSettings();
                s.setJavaScriptEnabled(true);
                s.setDomStorageEnabled(true);
                s.setAllowFileAccess(false);
                s.setAllowContentAccess(false);
                s.setUserAgentString(WebSettings.getDefaultUserAgent(app).replace("; wv)", ")"));
                w.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY));
                w.layout(0, 0, 1080, 2400);
                final boolean[] read = {false};
                w.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        String to = request.getUrl().getHost();
                        return to == null || !(to.endsWith("google.com") || to.contains(".google."));
                    }

                    @Override
                    public void onPageFinished(WebView view, String at) {
                        if (read[0]) {
                            return;
                        }
                        read[0] = true;
                        main.postDelayed(() -> view.evaluateJavascript(READ, v -> {
                            out[0] = unjson(v);
                            done.countDown();
                        }), SETTLE_MS);
                    }
                });
                w.loadUrl(url);
            } catch (RuntimeException e) {
                done.countDown(); // no browser on this device
            }
        });
        try {
            done.await(PAGE_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        main.post(() -> {
            if (holder[0] != null) {
                holder[0].stopLoading();
                holder[0].destroy();
            }
        });
        return out[0];
    }

    private static String unjson(String v) {
        if (v == null || v.equals("null")) {
            return "";
        }
        try {
            return new org.json.JSONArray("[" + v + "]").getString(0);
        } catch (org.json.JSONException e) {
            return v;
        }
    }
}
