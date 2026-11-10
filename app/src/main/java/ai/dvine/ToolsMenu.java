package ai.dvine;

/**
 * The tools button by the microphone: a grid of every tool of this chat, each a card with its icon and a small switch, its own settings from its name, and the GitHub repositories the chat works on.
 */
final class ToolsMenu {
    private final MainActivity a;

    ToolsMenu(MainActivity a) {
        this.a = a;
    }

    /**
     * The tools button beside the microphone: the tools this persona offers, each with a switch, for this chat page
     * only. GitHub opens a list of the account's repositories to choose from (several at once).
     */
    void showToolsSheet() {
        final String persona = a.stage.agent();
        final ai.dvine.suit.ChatTools tools = a.chatTools(persona);
        final android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = a.dp(12);
        box.setPadding(pad, 0, pad, pad);
        final android.app.Dialog[] sheet = new android.app.Dialog[1];
        final java.util.List<ai.dvine.suit.ChatTools.Tool> all = ai.dvine.suit.ChatTools.offered(persona);
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
            cp.setMargins(a.dp(4), a.dp(4), a.dp(4), a.dp(4));
            line.addView(cell(all.get(i), persona, tools, sheet), cp);
        }
        if (all.size() % columns != 0 && line != null) {
            line.addView(new android.view.View(a), new android.widget.LinearLayout.LayoutParams(0, 1, 1f)); // the last row keeps its column width
        }
        sheet[0] = ai.dvine.chat.ui.BottomSheet.showView(a, "Tools in this chat", box);
    }

    /**
     * One tool as a card in the grid: its icon top left, a small switch top right, then its name and what it does. The
     * switch turns the tool on and off; a card whose tool has settings of its own opens them on a tap (marked \u203A).
     */
    private android.view.View cell(final ai.dvine.suit.ChatTools.Tool t, final String persona, final ai.dvine.suit.ChatTools tools,
            final android.app.Dialog[] sheet) {
        android.widget.LinearLayout card = new android.widget.LinearLayout(a);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(a.dp(14));
        bg.setColor(0x14FFFFFF);
        bg.setStroke(a.dp(1), 0x1FFFFFFF);
        card.setBackground(bg);
        card.setPadding(a.dp(12), a.dp(8), a.dp(6), a.dp(12));
        card.setMinimumHeight(a.dp(104));
        android.widget.LinearLayout top = new android.widget.LinearLayout(a);
        top.setGravity(android.view.Gravity.CENTER_VERTICAL);
        android.widget.ImageView icon = new android.widget.ImageView(a);
        icon.setImageResource(toolIcon(t));
        icon.setColorFilter(a.getColor(R.color.text_primary));
        top.addView(icon, new android.widget.LinearLayout.LayoutParams(a.dp(22), a.dp(22)));
        top.addView(new android.view.View(a), new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
        final android.widget.Switch sw = new android.widget.Switch(a);
        sw.setScaleX(0.72f); // small, in the card's corner
        sw.setScaleY(0.72f);
        sw.setPivotX(a.dp(48));
        sw.setChecked(tools.isOn(t));
        sw.setContentDescription(t.title);
        top.addView(sw, new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, a.dp(40)));
        card.addView(top);
        final android.widget.TextView name = new android.widget.TextView(a);
        name.setTextSize(15f);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setTextColor(a.getColor(R.color.text_primary));
        name.setMaxLines(2);
        card.addView(name);
        final android.widget.TextView about = new android.widget.TextView(a);
        about.setTextSize(12f);
        about.setTextColor(a.getColor(R.color.text_secondary));
        about.setMaxLines(3);
        about.setEllipsize(android.text.TextUtils.TruncateAt.END);
        about.setText(t == ai.dvine.suit.ChatTools.Tool.GITHUB && !tools.repos().isEmpty()
                ? String.join(", ", tools.repos()) + "  \u00B7  change" : t.about);
        about.setPadding(0, a.dp(2), a.dp(6), 0);
        card.addView(about);
        final Runnable inner = toolSettings(t, persona, tools, about, sheet);
        name.setText(inner == null ? t.title : t.title + "  \u203A");
        if (inner != null) {
            card.setOnClickListener(v -> inner.run());
            card.setContentDescription(t.title + " settings");
        } else {
            card.setOnClickListener(v -> sw.toggle()); // nothing to open: the whole card is the switch
            card.setContentDescription(t.title);
        }
        card.setAlpha(tools.isOn(t) ? 1f : 0.6f);
        sw.setOnCheckedChangeListener((b, on) -> {
            tools.set(t, on);
            a.prefs.edit().putString(a.toolsKey(persona), tools.write()).apply();
            card.setAlpha(on ? 1f : 0.6f);
            if (on && t == ai.dvine.suit.ChatTools.Tool.GITHUB && tools.repos().isEmpty()) {
                chooseRepos(persona, tools, about);
            }
        });
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
            case WORKERS: return R.drawable.ic_people;
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
                                    : String.join(", ", chosen) + "  \u00B7  change");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        });
    }

}
