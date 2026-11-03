package ai.dvine.chat.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import ai.dvine.social.Platform;
import ai.dvine.social.SocialAccounts;
import ai.dvine.social.SocialLimits;
import ai.dvine.social.SocialWeb;

/**
 * The user's social accounts: several on each network, one in use at a time (tap another to switch), each signed in on
 * the site itself; what DVINE has done with each today against its limits, and a Resume when the site showed a
 * warning and the account was paused; the pace (careful or normal) and whether fact checks look at what people post.
 */
public final class SocialActivity extends AppCompatActivity {
    private LinearLayout body;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = getSharedPreferences("dvine", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 16);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 8));
        TextView close = Ui.text(this, "✕", 22, Ui.TEXT, false);
        close.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        close.setContentDescription("Close");
        close.setOnClickListener(v -> finish());
        head.addView(close);
        TextView title = Ui.text(this, "Social accounts", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(new android.view.View(this), new LinearLayout.LayoutParams(Ui.dp(this, 34), 1));
        root.addView(head);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        Ui.fitInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fill(); // back from signing in
    }

    private void fill() {
        body.removeAllViews();
        TextView intro = Ui.text(this, "Sign in on the sites themselves. DVINE reads with your accounts when you ask (“search X"
                + " for …”, “what are people saying on Reddit about …”) and in fact checks, a little at a"
                + " time like a person, and stops if a site shows a warning.", 14, Ui.SUB, false);
        intro.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), 0);
        body.addView(intro);
        SocialAccounts accounts = SocialWeb.accounts(this);
        SocialLimits limits = SocialWeb.limits(this);
        long now = System.currentTimeMillis();
        for (final Platform p : Platform.values()) {
            LinearLayout card = section(p.shown);
            List<SocialAccounts.Account> on = accounts.on(p);
            for (final SocialAccounts.Account a : on) {
                String paused = limits.pausedWhy(a.id, now);
                String state = paused != null ? "Paused: " + paused
                        : !a.signedIn ? "Signed out: tap Add account to sign in again"
                        : (a.inUse ? "In use · " : "Tap to use · ") + limits.used(a.id, SocialLimits.Kind.READ, now)
                                + " of " + limits.cap(p, SocialLimits.Kind.READ, true) + " reads today";
                LinearLayout row = row(a.label, a.inUse, state, () -> remove(a), () -> rename(a));
                row.setOnClickListener(v -> {
                    if (!a.inUse) {
                        boolean ok = SocialWeb.switchTo(this, a.id);
                        android.widget.Toast.makeText(this, ok ? "Using " + a.label + " on " + p.shown
                                : "That sign-in wasn't kept; sign in again with Add account.", android.widget.Toast.LENGTH_SHORT).show();
                        fill();
                    }
                });
                card.addView(row);
                if (paused != null) {
                    TextView resume = Ui.text(this, "Resume " + a.label, 15, Ui.ACCENT, false);
                    resume.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 10));
                    resume.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setMessage("Open " + p.shown + " in your browser or its app first and clear the warning or check."
                                    + " Resume " + a.label + " now?")
                            .setPositiveButton("Resume", (d, w) -> {
                                limits.resume(a.id);
                                SocialWeb.keepLimits(this);
                                fill();
                            })
                            .setNegativeButton("Not yet", null)
                            .show());
                    card.addView(resume);
                }
            }
            TextView add = Ui.text(this, on.isEmpty() ? "Sign in to " + p.shown : "Add account", 15, Ui.ACCENT, false);
            add.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            add.setOnClickListener(v -> startActivity(new Intent(this, SocialLoginActivity.class)
                    .putExtra(SocialLoginActivity.EXTRA_PLATFORM, p.name())));
            card.addView(add);
        }

        LinearLayout safe = section("Keeping accounts safe");
        safe.addView(Ui.text(this, "Pace", 16, Ui.TEXT, false), Ui.wide(this, 8));
        LinearLayout pace = new LinearLayout(this);
        for (final String[] c : new String[][]{{"Careful", "careful"}, {"Normal", "normal"}}) {
            boolean picked = c[1].equals(prefs.getString("social_pace", "normal"));
            TextView pill = Ui.text(this, c[0], 14, picked ? Ui.BG : Ui.TEXT, picked);
            pill.setBackground(Ui.round(this, picked ? Ui.TEXT : Ui.FIELD, 0, 16));
            pill.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
            pill.setOnClickListener(v -> {
                prefs.edit().putString("social_pace", c[1]).apply();
                SocialWeb.limits(this);
                fill();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(this, 8);
            pace.addView(pill, lp);
        }
        safe.addView(pace, Ui.wide(this, 6));
        safe.addView(Ui.text(this, "Normal keeps each account to about a third of what an active person does by hand, with"
                + " gaps that vary like a person's; Careful halves that. Limits an hour and a day, for reads (X: "
                + limits.cap(Platform.X, SocialLimits.Kind.READ, false) + " an hour, " + limits.cap(Platform.X, SocialLimits.Kind.READ, true)
                + " a day). When a site asks for a check or shows a warning, that account stops for 24 hours or until you"
                + " resume it.", 13, Ui.SUB, false), Ui.wide(this, 8));
        safe.addView(toggle("Look at what people post in fact checks", "Online fact checks also search your signed-in accounts;"
                + " posts count as what people say, not as proof.", "social_factcheck", true), Ui.wide(this, 12));
        TextView only = Ui.text(this, "For now DVINE only reads. Posting, replies and likes, asked first or approved"
                + " automatically, come next, within the same limits.", 13, Ui.SUB, false);
        only.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 10));
        safe.addView(only);
    }

    /** A new name for the account ("Work", "Shop page"): how it shows here, in Connectors and in replies. */
    private void rename(final SocialAccounts.Account a) {
        final android.widget.EditText field = Ui.field(this, "Name", a.label, false, false);
        LinearLayout box = new LinearLayout(this);
        int p = Ui.dp(this, 20);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        box.addView(field, new LinearLayout.LayoutParams(-1, -2));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Rename " + a.platform.shown + " account")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    SocialWeb.accounts(this).rename(a.id, field.getText().toString());
                    fill();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void remove(final SocialAccounts.Account a) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage("Remove " + a.label + " (" + a.platform.shown + ") from DVINE? Its sign-in on this phone is deleted.")
                .setPositiveButton("Remove", (d, w) -> {
                    SocialWeb.remove(this, a.id);
                    ai.dvine.act.ActionLog.add(getFilesDir(), "sign-out", a.platform.shown + " · " + a.label, "user", "removed", "");
                    fill();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout section(String name) {
        TextView t = Ui.text(this, name, 15, Ui.SUB, false);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 4));
        body.addView(t);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(this, Ui.SURFACE, 0, 20));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        body.addView(card, Ui.wide(this, 4));
        return card;
    }

    private LinearLayout row(String what, boolean inUse, String under, final Runnable remove, final Runnable rename) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(this, 52));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout name = new LinearLayout(this);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.addView(Ui.text(this, what, 15, Ui.TEXT, false));
        if (inUse) {
            ImageView check = new ImageView(this); // the standard check mark: the account in use
            check.setImageResource(ai.dvine.R.drawable.ic_check);
            check.setColorFilter(Ui.ACCENT);
            check.setContentDescription("In use");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16));
            lp.leftMargin = Ui.dp(this, 8);
            name.addView(check, lp);
        }
        words.addView(name);
        words.addView(Ui.text(this, under, 13, Ui.SUB, false));
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView edit = new ImageView(this);
        edit.setImageResource(ai.dvine.R.drawable.ic_edit);
        edit.setColorFilter(Ui.SUB);
        int ep = Ui.dp(this, 12);
        edit.setPadding(ep, ep, ep, ep);
        edit.setContentDescription("Rename " + what);
        edit.setOnClickListener(v -> rename.run());
        r.addView(edit, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        ImageView x = new ImageView(this);
        x.setImageResource(ai.dvine.R.drawable.ic_close);
        x.setColorFilter(Ui.SUB);
        int p = Ui.dp(this, 12);
        x.setPadding(p, p, p, p);
        x.setContentDescription("Remove " + what);
        x.setOnClickListener(v -> remove.run());
        r.addView(x, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44)));
        return r;
    }

    private android.view.View toggle(String title, String about, final String key, boolean def) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.addView(Ui.text(this, title, 16, Ui.TEXT, false));
        words.addView(Ui.text(this, about, 13, Ui.SUB, false));
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Ui.CORAL, Ui.FIELD}));
        sw.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(key, on).apply());
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        return r;
    }
}
