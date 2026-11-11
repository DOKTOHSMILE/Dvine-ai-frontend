package ai.ibiko.chat.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import ai.ibiko.R;
import ai.ibiko.main.AppFiles;

/**
 * The first start: the files the app needs are downloaded before it opens ("Downloading app files", with no file
 * named). When the download fails the app does not open: it says so and offers Try again or Close.
 */
public final class AppFilesActivity extends Activity {
    private TextView title, line;
    private ProgressBar bar;
    private Button retry, close;
    private volatile boolean gone;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(getColor(R.color.bg_main));
        getWindow().setNavigationBarColor(getColor(R.color.bg_main));
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(getColor(R.color.bg_main));
        int pad = (int) (32 * dp);
        root.setPadding(pad, pad, pad, pad);
        android.widget.ImageView icon = new android.widget.ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        root.addView(icon, new LinearLayout.LayoutParams((int) (88 * dp), (int) (88 * dp)));
        title = text(20, R.color.text_primary);
        title.setText(AppFiles.TITLE);
        root.addView(title, wide((int) (24 * dp)));
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(true);
        root.addView(bar, wide((int) (16 * dp)));
        line = text(14, R.color.text_secondary);
        line.setText("Ibiko opens once they are in place.");
        root.addView(line, wide((int) (12 * dp)));
        // something to read while it downloads: what Ibiko can do, a quote, a fact, running across the screen
        tickerKind = text(12, R.color.accent_cyan);
        tickerKind.setAllCaps(true);
        tickerKind.setLetterSpacing(0.12f);
        root.addView(tickerKind, wide((int) (40 * dp)));
        ticker = text(16, R.color.text_primary);
        ticker.setMinLines(3);
        root.addView(ticker, wide((int) (6 * dp)));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        close = new Button(this);
        close.setText("Close");
        close.setOnClickListener(v -> finishAndRemoveTask());
        retry = new Button(this);
        retry.setText("Try again");
        retry.setOnClickListener(v -> start());
        buttons.addView(close);
        buttons.addView(retry);
        root.addView(buttons, wide((int) (24 * dp)));
        setContentView(root);
        start();
        clock.post(nextLine);
    }

    private TextView tickerKind, ticker;
    private final ai.ibiko.main.WaitLines lines = new ai.ibiko.main.WaitLines(new java.util.Random());
    private final android.os.Handler clock = new android.os.Handler(android.os.Looper.getMainLooper());
    /** How long each line stays, and how long it takes to run in and out. */
    static final long SHOW_MS = 6000, MOVE_MS = 650;

    /** The next line runs in from the right, stays, then runs out to the left. */
    private final Runnable nextLine = new Runnable() {
        @Override
        public void run() {
            if (gone) {
                return;
            }
            final float w = Math.max(1, ticker.getRootView().getWidth());
            ai.ibiko.main.WaitLines.Line l = lines.next();
            tickerKind.setText(l.kind);
            ticker.setText(l.text);
            for (View v : new View[] {tickerKind, ticker}) {
                v.animate().cancel();
                v.setTranslationX(w);
                v.setAlpha(0f);
                v.animate().translationX(0).alpha(1f).setDuration(MOVE_MS)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }
            clock.postDelayed(() -> {
                if (gone) {
                    return;
                }
                for (View v : new View[] {tickerKind, ticker}) {
                    v.animate().translationX(-w).alpha(0f).setDuration(MOVE_MS)
                            .setInterpolator(new android.view.animation.AccelerateInterpolator()).start();
                }
                clock.postDelayed(nextLine, MOVE_MS);
            }, SHOW_MS);
        }
    };

    private TextView text(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(sp);
        t.setTextColor(getColor(color));
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private static LinearLayout.LayoutParams wide(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = top;
        return p;
    }

    private void start() {
        title.setText(AppFiles.TITLE);
        line.setText("Ibiko opens once they are in place.");
        bar.setIndeterminate(true);
        retry.setVisibility(android.view.View.GONE);
        close.setVisibility(android.view.View.GONE);
        final java.io.File files = getFilesDir();
        if ("robolectric".equals(android.os.Build.FINGERPRINT)) {
            return; // tests look at the screen; they never download the real files
        }
        Thread t = new Thread(() -> {
            final int[] shown = {-2};
            try {
                AppFiles.fetch(files, AppFiles.WEB, (pct, line) -> {
                    if (pct == shown[0]) {
                        return;
                    }
                    shown[0] = pct;
                    // the screen and the notification bar show the same: a percent and the size, never a file's name
                    ai.ibiko.net.Transfers.progress("appfiles", AppFiles.TITLE, line, pct);
                    runOnUiThread(() -> {
                        bar.setIndeterminate(false);
                        bar.setProgress(pct);
                        line(pct + "% \u00b7 " + line);
                    });
                });
                ai.ibiko.net.Transfers.end("appfiles", AppFiles.TITLE, "Done", true);
                runOnUiThread(this::open);
            } catch (Exception e) {
                ai.ibiko.net.Transfers.end("appfiles", AppFiles.TITLE, "Couldn't download the app files", false);
                runOnUiThread(this::failed);
            }
        }, "ibiko-app-files");
        t.setDaemon(true);
        t.start();
    }

    private void line(String text) {
        if (!gone) {
            line.setText(text);
        }
    }

    private void failed() {
        if (gone) {
            return;
        }
        title.setText("Couldn't download the app files");
        line.setText("Ibiko can't open without them. Check the internet connection and try again.");
        bar.setIndeterminate(false);
        bar.setProgress(0);
        retry.setVisibility(android.view.View.VISIBLE);
        close.setVisibility(android.view.View.VISIBLE);
    }

    private void open() {
        if (gone) {
            return;
        }
        Intent i = new Intent(this, ai.ibiko.MainActivity.class);
        Intent from = getIntent() == null ? null : getIntent().getParcelableExtra("then", Intent.class);
        if (from != null) {
            i = from;
        }
        startActivity(i);
        finish();
    }

    @Override
    protected void onDestroy() {
        gone = true;
        clock.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
