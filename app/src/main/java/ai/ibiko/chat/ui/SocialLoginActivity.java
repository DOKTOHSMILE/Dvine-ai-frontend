package ai.ibiko.chat.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import ai.ibiko.social.Platform;
import ai.ibiko.social.SocialAccounts;
import ai.ibiko.social.SocialWeb;

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
        android.widget.ImageView close = Ui.icon(this, ai.ibiko.R.drawable.ic_close, Ui.TEXT, 22);
        close.setContentDescription("Close");
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
            if (platform == Platform.TELEGRAM) {
                checkTelegram(true);
            } else if (SocialWeb.signedInNow(platform)) {
                name();
            } else {
                android.widget.Toast.makeText(this, "Not signed in yet. Sign in on the page first.", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        head.addView(done);
        page.addView(head);
        TextView note = Ui.text(this, platform == Platform.TELEGRAM
                ? "Sign in on Telegram's own page: scan the QR code with Telegram on another device (Settings › Devices › Link"
                        + " Desktop Device), or use your number and the code Telegram sends. Ibiko reads your chats only when you"
                        + " ask, and never sends a message without your yes."
                : "Your password goes only to " + platform.shown + ". Ibiko keeps the sign-in on this device,"
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
        if (platform == Platform.TELEGRAM) {
            web.postDelayed(telegramWatch, 3000); // Telegram signs in inside its page (a QR scan): watched, not a page load
        }
    }

    /** While Telegram's page is open: every few seconds, whether it is signed in now. */
    private final Runnable telegramWatch = new Runnable() {
        @Override
        public void run() {
            if (saved || isFinishing() || web == null) {
                return;
            }
            checkTelegram(false);
            web.postDelayed(this, 2500);
        }
    };

    private void checkTelegram(final boolean asked) {
        if (web == null) {
            return;
        }
        web.evaluateJavascript(SocialWeb.TELEGRAM_SIGNED_IN, v -> {
            boolean in = "true".equals(v);
            if (in) {
                SocialWeb.telegramSignedIn(this, true);
                name();
            } else if (asked) {
                android.widget.Toast.makeText(this, "Not signed in yet. Sign in on the page first.", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
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
                .setNegativeButton("Not yet", (d, w) -> {
                    saved = false; // not signed in after all: the page stays open to sign in
                    if (platform == Platform.TELEGRAM && web != null) {
                        SocialWeb.telegramSignedIn(this, false);
                        web.removeCallbacks(telegramWatch);
                        web.postDelayed(telegramWatch, 8000); // asked again only after a real sign-in
                    }
                })
                .setPositiveButton("Keep", (d, w) -> {
                    SocialAccounts.Account a = SocialWeb.accounts(this).add(platform, field.getText().toString(), System.currentTimeMillis());
                    SocialWeb.keep(this, a);
                    ai.ibiko.act.ActionLog.add(getFilesDir(), "sign-in", platform.shown + " · " + a.label, "user", "added", "");
                    finish();
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.removeCallbacks(telegramWatch);
        }
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
