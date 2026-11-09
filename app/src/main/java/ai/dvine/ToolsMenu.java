package ai.dvine;

/**
 * The tools button by the microphone: each tool of this chat with its icon and switch, its own settings from its name, and the GitHub repositories the chat works on.
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
        int pad = (int) (16 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, pad);
        final android.app.Dialog[] sheet = new android.app.Dialog[1];
        for (final ai.dvine.suit.ChatTools.Tool t : ai.dvine.suit.ChatTools.offered(persona)) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(a);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(pad * 4);
            // the tool's icon, tinted like the rest of the app
            android.widget.ImageView icon = new android.widget.ImageView(a);
            icon.setImageResource(toolIcon(t));
            icon.setColorFilter(a.getColor(R.color.text_primary));
            android.widget.LinearLayout.LayoutParams ip = new android.widget.LinearLayout.LayoutParams(a.dp(22), a.dp(22));
            ip.rightMargin = a.dp(14);
            row.addView(icon, ip);
            android.widget.LinearLayout words = new android.widget.LinearLayout(a);
            words.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.TextView name = new android.widget.TextView(a);
            name.setText(t.title);
            name.setTextSize(16f);
            name.setTextColor(a.getColor(R.color.text_primary));
            words.addView(name);
            final android.widget.TextView about = new android.widget.TextView(a);
            about.setTextSize(13f);
            about.setTextColor(a.getColor(R.color.text_secondary));
            about.setText(t == ai.dvine.suit.ChatTools.Tool.GITHUB && !tools.repos().isEmpty()
                    ? String.join(", ", tools.repos()) + "  \u00B7  change" : t.about);
            words.addView(about);
            row.addView(words, new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final Runnable inner = toolSettings(t, persona, tools, about, sheet);
            if (inner != null) {
                // a tool with settings of its own: its name opens them (the switch still turns it on and off)
                name.setText(t.title + "  \u203A");
                words.setOnClickListener(v -> inner.run());
                words.setContentDescription(t.title + " settings");
                android.util.TypedValue ripple = new android.util.TypedValue();
                a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
                words.setBackgroundResource(ripple.resourceId);
            }
            android.widget.Switch sw = new android.widget.Switch(a);
            sw.setChecked(tools.isOn(t));
            sw.setContentDescription(t.title);
            sw.setOnCheckedChangeListener((b, on) -> {
                tools.set(t, on);
                a.prefs.edit().putString(a.toolsKey(persona), tools.write()).apply();
                if (on && t == ai.dvine.suit.ChatTools.Tool.GITHUB && tools.repos().isEmpty()) {
                    chooseRepos(persona, tools, about);
                }
            });
            row.addView(sw);
            box.addView(row);
        }
        String who = persona.substring(0, 1).toUpperCase(java.util.Locale.US) + persona.substring(1);
        sheet[0] = ai.dvine.chat.ui.BottomSheet.showView(a, who + "'s tools in this chat", box);
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
            case SHELL:
                return () -> a.startActivity(new android.content.Intent(a, ai.dvine.chat.ui.SettingsActivity.class));
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
