package ai.dvine.chat.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ai.dvine.social.Platform;
import ai.dvine.social.SocialAccounts;
import ai.dvine.social.SocialWeb;

/**
 * Signing in to a social network on the site itself, inside the app: the password goes only to the site. When the
 * site says the account is signed in, the user names it and its sign-in is kept, encrypted, on this phone. Adding a
 * second account on a network keeps the first one's sign-in aside and starts from a signed-out page.
 */
public final class SocialLoginActivity extends AppCompatActivity {
    public static final String EXTRA_PLATFORM = "platform";
    private WebView web;
    private Platform platform;
    private SocialAccounts.Account before;
    private boolean saved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        platform = Platform.valueOf(getIntent().getStringExtra(EXTRA_PLATFORM));
        SocialAccounts accounts = SocialWeb.accounts(this);
        before = accounts.inUse(platform);
        if (before != null && SocialWeb.signedInNow(platform)) {
            SocialWeb.keep(this, before); // kept aside: this page starts signed out, for the new account
        }
        SocialWeb.clear(platform);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 12);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, Ui.dp(this, 14), pad, Ui.dp(this, 6));
        TextView close = Ui.text(this, "✕", 22, Ui.TEXT, false);
        close.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        close.setContentDescription("Close");
        close.setOnClickListener(v -> finish());
        head.addView(close);
        TextView title = Ui.text(this, "Sign in to " + platform.shown, 18, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView done = Ui.text(this, "Done", 16, Ui.ACCENT, true);
        done.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        done.setOnClickListener(v -> {
            if (SocialWeb.signedInNow(platform)) {
                name();
            } else {
                android.widget.Toast.makeText(this, "Not signed in yet. Sign in on the page first.", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        head.addView(done);
        page.addView(head);
        TextView note = Ui.text(this, "Your password goes only to " + platform.shown + ". DVINE keeps the sign-in on this device,"
                + " encrypted, and uses it within safe limits. Sign in with your username or email and password; “Continue"
                + " with Google” doesn't work inside apps.", 12, Ui.SUB, false);
        note.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), Ui.dp(this, 8));
        page.addView(note);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setUserAgentString(SocialWeb.userAgent(this));
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                return !"https".equals(scheme); // no app links, no plain http
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!saved && SocialWeb.signedInNow(platform)) {
                    name();
                }
            }
        });
        page.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
        Ui.fitInsets(page);
        web.loadUrl(platform.login);
    }

    /** Signed in: the user names the account (a blank name becomes "X 2"), and it is kept. */
    private void name() {
        if (saved || isFinishing()) {
            return;
        }
        saved = true;
        final android.widget.EditText field = Ui.field(this, "A name for this account (e.g. Work)", "", false, false);
        LinearLayout box = new LinearLayout(this);
        int p = Ui.dp(this, 20);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        box.addView(field, new LinearLayout.LayoutParams(-1, -2));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Signed in to " + platform.shown)
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("Keep", (d, w) -> {
                    SocialAccounts.Account a = SocialWeb.accounts(this).add(platform, field.getText().toString(), System.currentTimeMillis());
                    SocialWeb.keep(this, a);
                    ai.dvine.act.ActionLog.add(getFilesDir(), "sign-in", platform.shown + " · " + a.label, "user", "added", "");
                    finish();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        if (!saved && before != null) {
            SocialWeb.restore(this, before); // no new account: the one in use comes back
        }
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
