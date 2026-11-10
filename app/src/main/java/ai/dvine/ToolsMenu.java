package ai.dvine;

/**
 * The Tools sheet: a grid of every tool of this chat, each a card with its icon, a small toggle and, when it has settings, a "Set up" link; and the GitHub repositories the chat works on.
 */
final class ToolsMenu {
    private final MainActivity a;

    ToolsMenu(MainActivity a) {
        this.a = a;
    }

    /**
     * The Tools sheet: every tool of this chat as a card in a two-column grid. A tap anywhere on a card turns the tool
     * on or off (its small toggle, top right, shows which); a tool with its own settings has a separate "Set up" link
     * at the foot of its card, so a tap never does two things. A line at the top says how many are on.
     */
    void showToolsSheet() {
        final String persona = a.stage.agent();
        final ai.dvine.suit.ChatTools tools = a.chatTools(persona);
        final android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(a.dp(14), 0, a.dp(14), a.dp(20));
        final android.app.Dialog[] sheet = new android.app.Dialog[1];
        final java.util.List<ai.dvine.suit.ChatTools.Tool> all = ai.dvine.suit.ChatTools.offered(persona);
        final android.widget.TextView summary = new android.widget.TextView(a);
        summary.setTextSize(13f);
        summary.setTextColor(a.getColor(R.color.text_secondary));
        summary.setPadding(a.dp(4), 0, a.dp(4), a.dp(12));
        final Runnable count = () -> {
            int on = 0;
            for (ai.dvine.suit.ChatTools.Tool t : all) {
                on += tools.isOn(t) ? 1 : 0;
            }
            summary.setText(on + " of " + all.size() + " on in this chat. Tap a card to turn it on or off.");
        };
        count.run();
        box.addView(summary);
        // how the agents work on real jobs (Auto, Accept edits, Plan): one row, opening the same choice as the header pill
        ai.dvine.agent.IbikoMode m = ai.dvine.agent.IbikoMode.of(a.prefs);
        android.widget.LinearLayout mode = new android.widget.LinearLayout(a);
        mode.setGravity(android.view.Gravity.CENTER_VERTICAL);
        mode.setMinimumHeight(a.dp(56));
        mode.setPadding(a.dp(14), a.dp(8), a.dp(14), a.dp(8));
        android.graphics.drawable.GradientDrawable mbg = new android.graphics.drawable.GradientDrawable();
        mbg.setCornerRadius(a.dp(16));
        mbg.setColor(a.getColor(R.color.card));
        mbg.setStroke(a.dp(1), a.getColor(R.color.line));
        mode.setBackground(mbg);
        android.widget.ImageView mi = new android.widget.ImageView(a);
        mi.setImageResource(m == ai.dvine.agent.IbikoMode.AUTO ? R.drawable.ic_mode_auto : m == ai.dvine.agent.IbikoMode.PLAN
                ? R.drawable.ic_mode_plan : R.drawable.ic_mode_edits);
        mi.setColorFilter(a.getColor(R.color.accent));
        android.widget.LinearLayout.LayoutParams mip = new android.widget.LinearLayout.LayoutParams(a.dp(20), a.dp(20));
        mip.rightMargin = a.dp(12);
        mode.addView(mi, mip);
        android.widget.LinearLayout mw = new android.widget.LinearLayout(a);
        mw.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.TextView mt = new android.widget.TextView(a);
        mt.setText("Agent mode: " + m.label);
        mt.setTextSize(14.5f);
        mt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        mt.setTextColor(a.getColor(R.color.text_primary));
        mw.addView(mt);
        android.widget.TextView ms = new android.widget.TextView(a);
        ms.setText(m.about);
        ms.setTextSize(12f);
        ms.setMaxLines(2);
        ms.setTextColor(a.getColor(R.color.text_secondary));
        mw.addView(ms);
        mode.addView(mw, new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        android.widget.TextView change = new android.widget.TextView(a);
        change.setText("Change  \u203A");
        change.setTextSize(12.5f);
        change.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        change.setTextColor(a.getColor(R.color.accent));
        mode.addView(change);
        mode.setContentDescription("Agent mode: " + m.label + ". Tap to change.");
        final android.app.Dialog[] modeSheet = new android.app.Dialog[1];
        mode.setOnClickListener(v -> {
            if (modeSheet[0] != null) {
                modeSheet[0].dismiss();
            }
            a.chooseIbikoMode();
        });
        android.widget.LinearLayout.LayoutParams mlp = new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.setMargins(a.dp(5), 0, a.dp(5), a.dp(10));
        box.addView(mode, mlp);
        final int columns = 2;
        android.widget.LinearLayout line = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % columns == 0) {
                line = new android.widget.LinearLayout(a);
                line.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                box.addView(line, new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            android.widget.LinearLayout.LayoutParams cp = new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            cp.setMargins(a.dp(5), a.dp(5), a.dp(5), a.dp(5));
            line.addView(cell(all.get(i), persona, tools, sheet, count), cp);
        }
        if (all.size() % columns != 0 && line != null) {
            android.widget.LinearLayout.LayoutParams gap = new android.widget.LinearLayout.LayoutParams(0, 1, 1f);
            gap.setMargins(a.dp(5), a.dp(5), a.dp(5), a.dp(5));
            line.addView(new android.view.View(a), gap); // the last row keeps its column width
        }
        sheet[0] = ai.dvine.chat.ui.BottomSheet.showView(a, "Tools", box);
        modeSheet[0] = sheet[0];
    }

    /** A small on/off toggle drawn to fit a card's corner: a 36 by 20 dp track and a round thumb. */
    private android.widget.FrameLayout toggle(boolean on) {
        android.widget.FrameLayout track = new android.widget.FrameLayout(a);
        android.view.View thumb = new android.view.View(a);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(0xFFFFFFFF);
        thumb.setBackground(dot);
        track.addView(thumb, new android.widget.FrameLayout.LayoutParams(a.dp(16), a.dp(16)));
        paintToggle(track, on);
        return track;
    }

    private void paintToggle(android.widget.FrameLayout track, boolean on) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(a.dp(10));
        bg.setColor(a.getColor(on ? R.color.accent : R.color.track));
        track.setBackground(bg);
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) track.getChildAt(0).getLayoutParams();
        lp.gravity = (on ? android.view.Gravity.END : android.view.Gravity.START) | android.view.Gravity.CENTER_VERTICAL;
        lp.setMargins(a.dp(2), 0, a.dp(2), 0);
        track.getChildAt(0).setLayoutParams(lp);
    }

    /** One tool's card: its icon in a round badge, the toggle, its name, what it does, and "Set up" when it has settings. */
    private android.view.View cell(final ai.dvine.suit.ChatTools.Tool t, final String persona, final ai.dvine.suit.ChatTools tools,
            final android.app.Dialog[] sheet, final Runnable count) {
        final android.widget.LinearLayout card = new android.widget.LinearLayout(a);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setPadding(a.dp(14), a.dp(14), a.dp(12), a.dp(10));
        card.setMinimumHeight(a.dp(112));
        android.util.TypedValue ripple = new android.util.TypedValue();
        a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        card.setForeground(a.getDrawable(ripple.resourceId));
        // the top row: the icon's badge on the left, the toggle on the right
        android.widget.LinearLayout top = new android.widget.LinearLayout(a);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        final android.widget.FrameLayout badge = new android.widget.FrameLayout(a);
        final android.widget.ImageView icon = new android.widget.ImageView(a);
        icon.setImageResource(toolIcon(t));
        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        android.widget.FrameLayout.LayoutParams ip = new android.widget.FrameLayout.LayoutParams(a.dp(18), a.dp(18));
        ip.gravity = android.view.Gravity.CENTER;
        badge.addView(icon, ip);
        top.addView(badge, new android.widget.LinearLayout.LayoutParams(a.dp(34), a.dp(34)));
        top.addView(new android.view.View(a), new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
        final android.widget.FrameLayout sw = toggle(tools.isOn(t));
        sw.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO); // the card speaks for it
        top.addView(sw, new android.widget.LinearLayout.LayoutParams(a.dp(36), a.dp(20)));
        card.addView(top);
        android.widget.TextView name = new android.widget.TextView(a);
        name.setText(t.title);
        name.setTextSize(14.5f);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setTextColor(a.getColor(R.color.text_primary));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setPadding(0, a.dp(10), 0, 0);
        card.addView(name);
        final android.widget.TextView about = new android.widget.TextView(a);
        about.setTextSize(12f);
        about.setLineSpacing(0, 1.1f);
        about.setTextColor(a.getColor(R.color.text_secondary));
        about.setMaxLines(2);
        about.setEllipsize(android.text.TextUtils.TruncateAt.END);
        about.setText(t == ai.dvine.suit.ChatTools.Tool.GITHUB && !tools.repos().isEmpty() ? String.join(", ", tools.repos()) : t.about);
        about.setPadding(0, a.dp(3), 0, 0);
        card.addView(about, new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        final Runnable inner = toolSettings(t, persona, tools, about, sheet);
        if (inner != null) {
            android.widget.TextView setUp = new android.widget.TextView(a);
            setUp.setText("Set up  \u203A");
            setUp.setTextSize(12.5f);
            setUp.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            setUp.setTextColor(a.getColor(R.color.accent));
            setUp.setGravity(android.view.Gravity.CENTER_VERTICAL);
            setUp.setMinHeight(a.dp(32));
            setUp.setContentDescription(t.title + " settings");
            setUp.setOnClickListener(v -> inner.run()); // its own tap: never also turns the tool on or off
            card.addView(setUp);
        }
        final Runnable paint = () -> {
            boolean on = tools.isOn(t);
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(a.dp(18));
            bg.setColor(a.getColor(on ? R.color.card_on : R.color.card));
            int edge = a.getColor(on ? R.color.accent : R.color.line);
            bg.setStroke(a.dp(1), on ? (edge & 0x00FFFFFF) | 0x8C000000 : edge); // a quiet edge: the toggle and the badge carry the state
            card.setBackground(bg);
            android.graphics.drawable.GradientDrawable round = new android.graphics.drawable.GradientDrawable();
            round.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            int lit = a.getColor(R.color.accent);
            round.setColor(on ? (lit & 0x00FFFFFF) | 0x33000000 : a.getColor(R.color.track)); // a soft tint behind the icon
            badge.setBackground(round);
            icon.setColorFilter(a.getColor(on ? R.color.accent : R.color.icon));
            paintToggle(sw, on);
            card.setContentDescription(t.title);
            card.setStateDescription(on ? "On" : "Off");
        };
        paint.run();
        final Runnable flip = () -> {
            boolean on = !tools.isOn(t);
            tools.set(t, on);
            a.prefs.edit().putString(a.toolsKey(persona), tools.write()).apply();
            paint.run();
            count.run();
            if (on && t == ai.dvine.suit.ChatTools.Tool.GITHUB && tools.repos().isEmpty()) {
                chooseRepos(persona, tools, about);
            }
        };
        card.setOnClickListener(v -> flip.run());
        sw.setOnClickListener(v -> flip.run());
        return card;
    }

    /** Each tool's icon. */
    static int toolIcon(ai.dvine.suit.ChatTools.Tool t) {
        switch (t) {
            case GITHUB: return R.drawable.ic_branch;
            case SHELL: return R.drawable.ic_terminal;
            case WEB: return R.drawable.ic_search;
            case CONNECTORS: return R.drawable.ic_plug;
            case PHOTOS: return R.drawable.ic_image;
            case DESIGN3D: return R.drawable.ic_cube;
            case FILES: return R.drawable.ic_file;
            case SHARE: return R.drawable.ic_share;
            case SOCIAL: return R.drawable.ic_people;
            case WORKERS: return R.drawable.ic_workers;
            default: return R.drawable.ic_deep;
        }
    }

    /** What a tool's name opens: its own settings, or null when it has none. */
    Runnable toolSettings(final ai.dvine.suit.ChatTools.Tool t, final String persona, final ai.dvine.suit.ChatTools tools,
            final android.widget.TextView about, final android.app.Dialog[] sheet) {
        switch (t) {
            case GITHUB:
                return () -> chooseRepos(persona, tools, about); // the repositories this chat works on
            case CONNECTORS:
                return () -> a.startActivity(new android.content.Intent(a, ai.dvine.chat.ui.ConnectorsActivity.class));
            case WEB:
                return () -> a.startActivity(new android.content.Intent(a, ai.dvine.chat.ui.SettingsActivity.class));
            case SHELL:
                return () -> a.startActivity(new android.content.Intent(a, ai.dvine.chat.ui.TerminalActivity.class));
            case SOCIAL:
                return () -> ai.dvine.chat.ui.BottomSheet.show(a, "Social accounts", new ai.dvine.chat.ui.BottomSheet.Item[] {
                        new ai.dvine.chat.ui.BottomSheet.Item(R.drawable.ic_people, "Your accounts", "Sign in, switch or remove accounts"),
                        new ai.dvine.chat.ui.BottomSheet.Item(R.drawable.ic_send, "Scheduled posts", "See or cancel what is planned") }, which -> {
                            if (sheet[0] != null) {
                                sheet[0].dismiss();
                            }
                            if (which == 0) {
                                a.startActivity(new android.content.Intent(a, ai.dvine.chat.ui.SocialActivity.class));
                            } else {
                                a.showScheduledPosts();
                            }
                        });
            case SHARE:
                return () -> {
                    final android.widget.Switch auto = new android.widget.Switch(a);
                    auto.setText("Share and post without asking me first");
                    auto.setChecked(a.prefs.getBoolean("actions_auto", false));
                    auto.setPadding(a.dp(20), a.dp(12), a.dp(20), a.dp(4));
                    new androidx.appcompat.app.AlertDialog.Builder(a)
                            .setTitle("Share, post or copy")
                            .setMessage("Off (recommended): each share, post or copy is shown first and waits for your yes.")
                            .setView(auto)
                            .setPositiveButton("Save", (d, w) -> a.prefs.edit().putBoolean("actions_auto", auto.isChecked()).apply())
                            .setNegativeButton("Cancel", null)
                            .show();
                };
            default:
                return null;
        }
    }

    /** The account's repositories, to choose the ones this chat works on (several at once). */
    void chooseRepos(final String persona, final ai.dvine.suit.ChatTools tools, final android.widget.TextView about) {
        final ai.dvine.connect.ConnectorStore store = new ai.dvine.connect.ConnectorStore(a);
        final String token = store.token(ai.dvine.connect.ConnectorStore.Kind.GITHUB);
        if (token == null || token.isEmpty()) {
            a.note("Sign in to GitHub first: Connectors \u203A GitHub.");
            a.startActivity(new android.content.Intent(a, ai.dvine.chat.ui.ConnectorsActivity.class));
            return;
        }
        a.note("Fetching your repositories\u2026");
        a.hub.worker.execute(() -> {
            final java.util.List<String> names = new java.util.ArrayList<String>();
            String why = null;
            try {
                for (int page = 1; page <= 5; page++) {
                    String list = ai.dvine.connect.Connectors.github(ai.dvine.connect.Connectors.https(), token, "github.repos",
                            String.valueOf(page));
                    int before = names.size();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^- ([\\w.-]+/[\\w.-]+)").matcher(list);
                    while (m.find()) {
                        names.add(m.group(1));
                    }
                    if (names.size() - before < 100) {
                        break;
                    }
                }
            } catch (java.io.IOException | RuntimeException e) {
                why = e.getMessage();
            }
            final String problem = why;
            a.handler.post(() -> {
                if (a.isFinishing()) {
                    return;
                }
                if (names.isEmpty()) {
                    a.note(problem == null ? "No repositories on this account." : "GitHub didn't answer: " + problem);
                    return;
                }
                final String[] items = names.toArray(new String[0]);
                final boolean[] picked = new boolean[items.length];
                java.util.List<String> had = tools.repos();
                for (int i = 0; i < items.length; i++) {
                    picked[i] = had.contains(items[i]);
                }
                new androidx.appcompat.app.AlertDialog.Builder(a)
                        .setTitle("Repositories for this chat")
                        .setMultiChoiceItems(items, picked, (d, i, on) -> picked[i] = on)
                        .setPositiveButton("Use these", (d, w) -> {
                            java.util.List<String> chosen = new java.util.ArrayList<String>();
                            for (int i = 0; i < items.length; i++) {
                                if (picked[i]) {
                                    chosen.add(items[i]);
                                }
                            }
                            tools.setRepos(chosen);
                            tools.set(ai.dvine.suit.ChatTools.Tool.GITHUB, !chosen.isEmpty() || tools.isOn(ai.dvine.suit.ChatTools.Tool.GITHUB));
                            a.prefs.edit().putString(a.toolsKey(persona), tools.write()).apply();
                            about.setText(chosen.isEmpty() ? ai.dvine.suit.ChatTools.Tool.GITHUB.about
                                    : String.join(", ", chosen));
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        });
    }

}
