package ai.ibiko;

/**
 * The model menu under the title: Ibiko 1, the API models and the offline models, the one in use clearly marked; and the learning schedule.
 */
final class ModelMenu {
    private final MainActivity a;

    ModelMenu(MainActivity a) {
        this.a = a;
    }

    /** The learning schedule: on or off, the hours (any of the day), Wi-Fi only, charging only, and Learn now. */
    void showNightLearning() {
        final ai.ibiko.ibiko1.LearningSchedule.Plan was = ai.ibiko.ibiko1.LearningJob.plan(a);
        final int[] start = { was.start }, end = { was.end };
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(a.dp(20), a.dp(8), a.dp(20), 0);
        android.widget.TextView about = new android.widget.TextView(a);
        about.setText("At the hours you choose (any time of day), " + a.IBIKO_OWN + " goes online and learns only from how you use"
                + " it: the questions it couldn't answer or got wrong, what you asked about, what you're into, and the words in your"
                + " messages it doesn't know yet. It reads what it finds with its language formulas: sentences into who did"
                + " what to what, when and where, kept as facts it can answer from in its own sentences. Afterwards it tells you"
                + " what it learned, and asks you to check each answer the first time it uses it.");
        about.setTextColor(a.getColor(R.color.text_secondary));
        about.setTextSize(13.5f);
        about.setLineSpacing(a.dp(3), 1f); // airy: small explanations read easily
        about.setPadding(0, a.dp(3), 0, 0);
        box.addView(about);
        final android.widget.Switch on = new android.widget.Switch(a);
        on.setText("Learn on a schedule");
        on.setChecked(was.on);
        on.setTextSize(16f);
        on.setPadding(0, a.dp(16), 0, a.dp(8));
        box.addView(on);
        final android.widget.Button from = new android.widget.Button(a);
        final android.widget.Button to = new android.widget.Button(a);
        from.setText("Starts " + ai.ibiko.ibiko1.LearningSchedule.Plan.clock(start[0]));
        to.setText("Ends " + ai.ibiko.ibiko1.LearningSchedule.Plan.clock(end[0]));
        from.setOnClickListener(v -> new android.app.TimePickerDialog(a, (tp, h, m) -> {
            start[0] = h * 60 + m;
            from.setText("Starts " + ai.ibiko.ibiko1.LearningSchedule.Plan.clock(start[0]));
        }, start[0] / 60, start[0] % 60, true).show());
        to.setOnClickListener(v -> new android.app.TimePickerDialog(a, (tp, h, m) -> {
            end[0] = h * 60 + m;
            to.setText("Ends " + ai.ibiko.ibiko1.LearningSchedule.Plan.clock(end[0]));
        }, end[0] / 60, end[0] % 60, true).show());
        android.widget.LinearLayout times = new android.widget.LinearLayout(a);
        times.addView(from, new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        times.addView(to, new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(times);
        final android.widget.CheckBox wifi = new android.widget.CheckBox(a);
        wifi.setText("Only on Wi-Fi");
        wifi.setChecked(was.wifiOnly);
        box.addView(wifi);
        final android.widget.CheckBox charging = new android.widget.CheckBox(a);
        charging.setText("Only while charging");
        charging.setChecked(was.chargingOnly);
        box.addView(charging);
        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Learning schedule")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    ai.ibiko.ibiko1.LearningSchedule.Plan p = new ai.ibiko.ibiko1.LearningSchedule.Plan(on.isChecked(), start[0], end[0],
                            wifi.isChecked(), charging.isChecked(), was.most);
                    ai.ibiko.ibiko1.LearningJob.setPlan(a, p);
                    a.note(p.on ? "Learning is scheduled: " + p.words() + "." : "Scheduled learning is off.");
                })
                .setNeutralButton("Learn now", (d, w) -> {
                    ai.ibiko.ibiko1.LearningJob.learnNow(a);
                    a.note(a.IBIKO_OWN + " is learning now. The Workload sheet shows what it's on.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * The model under the title, tapped: every brain to pick from, the one in use clearly marked (a filled radio with a
     * check, a tinted row, "In use"): Ibiko 1.0 (Ibiko's own), the API models set up, the offline models on the device.
     */
    void showModelSwitch() {
        final ai.ibiko.llm.ProviderStore providers = new ai.ibiko.llm.ProviderStore(a);
        final String pick = a.brainPick();
        final android.widget.LinearLayout list = new android.widget.LinearLayout(a);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = a.dp(16);
        list.setPadding(pad, 0, pad, pad);
        final android.app.Dialog[] sheet = new android.app.Dialog[1];
        list.addView(brainHeader("Ibiko"));
        list.addView(brainRow(a.IBIKO_OWN, "Ibiko's own intelligence: built in, no download, instant. Exact sums, reading what"
                + " you mean, and everything it learned from the models you used.", a.ibikoOwn(), () -> {
                    a.prefs.edit().putString("brain_pick", "ibiko").apply();
                    a.applyProvider();
                    if (a.model != null) {
                        final ai.ibiko.llm.LocalModel leaving = a.model;
                        a.hub.worker.execute(leaving::releaseIfIdle); // no model in memory: Ibiko thinks on its own
                    }
                    sheet[0].dismiss();
                }));
        java.util.List<ai.ibiko.llm.Providers> apis = providers.usable();
        if (!apis.isEmpty()) {
            list.addView(brainHeader("API models"));
            for (int i = 0; i < apis.size(); i++) {
                final ai.ibiko.llm.Providers p = apis.get(i);
                final String now = providers.model(p);
                // every model kept for this provider: one tap switches, the one in use has Edit beside it
                for (final String m : providers.models(p)) {
                    boolean on = "api".equals(pick) && i == 0 && m.equals(now);
                    String about = p.title + (m.equals(now) ? (i == 0 ? " \u00B7 primary" : " \u00B7 backup") : "");
                    android.view.View row = brainRow(m, about, on, () -> {
                        providers.useModel(p, m);
                        providers.setActive(p);
                        a.prefs.edit().putString("brain_pick", "api").apply();
                        a.applyProvider();
                        sheet[0].dismiss();
                    }, m.equals(now) ? () -> {
                        sheet[0].dismiss();
                        a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.ProvidersActivity.class)
                                .putExtra(ai.ibiko.chat.ui.ProvidersActivity.EXTRA_PROVIDER, p.name()));
                    } : null);
                    if (!m.equals(now)) {
                        row.setOnLongClickListener(v -> {
                            new androidx.appcompat.app.AlertDialog.Builder(a)
                                    .setMessage("Take " + m + " off the list? You can add it again any time.")
                                    .setPositiveButton("Take off", (d, w) -> {
                                        providers.removeModel(p, m);
                                        sheet[0].dismiss();
                                        showModelSwitch();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            return true;
                        });
                    }
                    list.addView(row);
                }
                list.addView(brainLink("Add a " + p.title + " model", "Another model with the same key; switch to it here any time",
                        () -> addModel(providers, p, sheet[0])));
            }
        }
        boolean anyHere = false;
        for (final ai.ibiko.llm.ModelSpec m : ai.ibiko.llm.ModelSpec.ALL) {
            if (a.model == null || !a.model.has(m)) {
                continue;
            }
            if (!anyHere) {
                list.addView(brainHeader("Offline models on this device"));
                anyHere = true;
            }
            boolean on = "device".equals(pick) && m.file.equals(a.hub.chosen().file)
                    || "api".equals(pick) && apis.isEmpty() && m.file.equals(a.hub.chosen().file);
            list.addView(brainRow(m.weights, "Offline, on this device. " + m.about + " Needs " + m.ramGb + " GB of memory.", on, () -> {
                a.prefs.edit().putString("brain_pick", "device").putBoolean("llm_on", true).apply();
                a.applyProvider();
                if (m.file.equals(a.hub.chosen().file)) {
                    if (!a.model.localReady()) {
                        a.loadModel(true);
                    }
                } else {
                    switchOfflineModel(m);
                }
                sheet[0].dismiss();
            }));
        }
        list.addView(brainHeader("More"));
        ai.ibiko.ibiko1.LearningSchedule.Plan night = ai.ibiko.ibiko1.LearningJob.plan(a);
        list.addView(brainLink("Learning schedule", night.on ? "On, " + night.words() : "Off. Let " + a.IBIKO_OWN + " learn at the hours you choose",
                () -> {
                    sheet[0].dismiss();
                    showNightLearning();
                }));
        list.addView(brainLink("All offline models", "Download another family or size, or delete one",
                () -> a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.SettingsActivity.class)
                        .putExtra(ai.ibiko.chat.ui.SettingsActivity.EXTRA_SECTION, "Models"))));
        list.addView(brainLink("AI providers", "API models and keys", a::openProviders));
        sheet[0] = ai.ibiko.chat.ui.BottomSheet.showView(a, "Choose the brain", list);
    }

    android.widget.TextView brainHeader(String name) {
        android.widget.TextView t = new android.widget.TextView(a);
        t.setText(name);
        t.setTextSize(13f);
        t.setTextColor(a.getColor(R.color.text_secondary));
        t.setPadding(a.dp(4), a.dp(14), 0, a.dp(6));
        return t;
    }

    /**
     * One brain to pick: a radio circle (filled with the accent and a check when it is the one in use), the name and
     * what it is; the row in use is tinted with an accent edge and says "In use", so it can't be missed.
     */
    android.view.View brainRow(String name, String about, boolean selected, final Runnable pick) {
        return brainRow(name, about, selected, pick, null);
    }

    /** Asks for a model name (or one of the provider's suggestions), keeps it on the list and switches to it. */
    void addModel(final ai.ibiko.llm.ProviderStore providers, final ai.ibiko.llm.Providers p, final android.app.Dialog sheet) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(a.dp(20), a.dp(8), a.dp(20), 0);
        final android.widget.EditText name = new android.widget.EditText(a);
        name.setHint("Model name, as " + p.title + " writes it");
        name.setSingleLine(true);
        box.addView(name);
        final java.util.List<String> have = providers.models(p);
        // the provider's own list, fetched on a tap; until then its usual models
        final android.widget.TextView fetch = new android.widget.TextView(a);
        fetch.setText("Fetch the model list from " + p.title);
        fetch.setTextSize(15f);
        fetch.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        fetch.setTextColor(a.getColor(R.color.accent));
        fetch.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_refresh_list, 0, 0, 0);
        fetch.setCompoundDrawablePadding(a.dp(8));
        fetch.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(a.getColor(R.color.accent)));
        fetch.setPadding(a.dp(4), a.dp(12), a.dp(4), a.dp(8));
        box.addView(fetch);
        final android.widget.TextView status = new android.widget.TextView(a);
        status.setTextSize(13f);
        status.setTextColor(a.getColor(R.color.text_secondary));
        status.setVisibility(android.view.View.GONE);
        box.addView(status);
        final android.widget.LinearLayout picks = new android.widget.LinearLayout(a);
        picks.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        scroll.addView(picks);
        box.addView(scroll, new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        final java.util.function.Consumer<java.util.List<String>> show = names -> {
            picks.removeAllViews();
            for (final String m : names) {
                if (have.contains(m)) {
                    continue;
                }
                android.widget.TextView chip = new android.widget.TextView(a);
                chip.setText(m);
                chip.setTextSize(14f);
                chip.setTextColor(a.getColor(R.color.text_primary));
                chip.setPadding(a.dp(4), a.dp(10), a.dp(4), a.dp(10));
                chip.setOnClickListener(v -> name.setText(m));
                picks.addView(chip);
            }
            // a long list stays inside the dialog: about seven rows show, the rest scroll
            scroll.getLayoutParams().height = names.size() > 7 ? a.dp(300) : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            scroll.requestLayout();
        };
        show.accept(java.util.Arrays.asList(p.suggestions()));
        fetch.setOnClickListener(v -> {
            fetch.setEnabled(false);
            status.setVisibility(android.view.View.VISIBLE);
            status.setText("Asking " + p.title + " for its models\u2026");
            new Thread(() -> {
                java.util.List<String> got = null;
                String why = null;
                try {
                    got = providers.fetchModels(p);
                } catch (java.io.IOException e) {
                    why = e.getMessage();
                }
                final java.util.List<String> list = got;
                final String problem = why;
                a.runOnUiThread(() -> {
                    fetch.setEnabled(true);
                    if (list == null) {
                        status.setText("I couldn't get the list: " + problem + ".");
                        return;
                    }
                    status.setText(list.size() + " models from " + p.title + ". Tap one, then Add and use.");
                    show.accept(list);
                });
            }, "ibiko-model-list").start();
        });
        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Add a " + p.title + " model")
                .setView(box)
                .setPositiveButton("Add and use", (d, w) -> {
                    String m = name.getText().toString().trim();
                    if (m.isEmpty()) {
                        return;
                    }
                    providers.addModel(p, m);
                    providers.useModel(p, m);
                    providers.setActive(p);
                    a.prefs.edit().putString("brain_pick", "api").apply();
                    a.applyProvider();
                    sheet.dismiss();
                    a.note("Now answering with " + m + ". Switch back from the model menu any time.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** As above; {@code edit} (when not null) puts an Edit button beside "In use", to change that model's settings. */
    android.view.View brainRow(String name, String about, boolean selected, final Runnable pick, final Runnable edit) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(a);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12));
        row.setMinimumHeight(a.dp(64));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(a.dp(16));
        int accent = a.getColor(R.color.accent);
        if (selected) {
            bg.setColor((accent & 0x00FFFFFF) | 0x24000000);
            bg.setStroke(a.dp(2), accent);
        } else {
            bg.setColor(0x00000000);
        }
        row.setBackground(bg);
        // the radio: an empty ring, or a filled accent circle with a white check
        android.widget.TextView radio = new android.widget.TextView(a);
        radio.setGravity(android.view.Gravity.CENTER);
        radio.setTextSize(13f);
        radio.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        if (selected) {
            dot.setColor(accent);
            radio.setText("\u2713");
            radio.setTextColor(a.getColor(R.color.on_accent));
        } else {
            dot.setColor(0x00000000);
            dot.setStroke(a.dp(2), a.getColor(R.color.text_secondary));
        }
        radio.setBackground(dot);
        android.widget.LinearLayout.LayoutParams rp = new android.widget.LinearLayout.LayoutParams(a.dp(24), a.dp(24));
        rp.rightMargin = a.dp(14);
        row.addView(radio, rp);
        android.widget.LinearLayout words = new android.widget.LinearLayout(a);
        words.setOrientation(android.widget.LinearLayout.VERTICAL);
        android.widget.TextView title = new android.widget.TextView(a);
        title.setText(name);
        title.setTextSize(16f);
        title.setTextColor(a.getColor(selected ? R.color.accent : R.color.text_primary));
        title.setTypeface(selected ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        words.addView(title);
        android.widget.TextView sub = new android.widget.TextView(a);
        sub.setText(about);
        sub.setTextSize(13.5f);
        sub.setLineSpacing(a.dp(3), 1f); // airy: small explanations read easily
        sub.setPadding(0, a.dp(3), 0, 0);
        sub.setTextColor(a.getColor(R.color.text_secondary));
        words.addView(sub);
        row.addView(words, new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (selected) {
            android.widget.TextView inUse = new android.widget.TextView(a);
            inUse.setText("In use");
            inUse.setTextSize(12f);
            inUse.setTextColor(a.getColor(R.color.on_accent));
            android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
            pill.setColor(accent);
            pill.setCornerRadius(a.dp(10));
            inUse.setBackground(pill);
            inUse.setPadding(a.dp(8), a.dp(3), a.dp(8), a.dp(3));
            android.widget.LinearLayout.LayoutParams ip = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            ip.leftMargin = a.dp(8);
            row.addView(inUse, ip);
        }
        if (edit != null) {
            android.widget.TextView change = new android.widget.TextView(a);
            change.setText("Edit");
            change.setTextSize(14f);
            change.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            change.setTextColor(accent);
            android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
            ring.setCornerRadius(a.dp(10));
            ring.setStroke(a.dp(1), accent);
            change.setBackground(ring);
            change.setPadding(a.dp(10), a.dp(4), a.dp(10), a.dp(4));
            change.setContentDescription("Edit " + name);
            change.setOnClickListener(v -> edit.run());
            android.widget.LinearLayout.LayoutParams ep = new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            ep.leftMargin = a.dp(8);
            row.addView(change, ep);
        }
        row.setContentDescription(name + (selected ? ", in use" : ""));
        row.setOnClickListener(v -> pick.run());
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = a.dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    android.view.View brainLink(String name, String about, Runnable open) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(a);
        row.setOrientation(android.widget.LinearLayout.VERTICAL);
        row.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10));
        android.widget.TextView t = new android.widget.TextView(a);
        t.setText(name);
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_right, 0);
        t.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(a.getColor(R.color.accent)));
        t.setCompoundDrawablePadding(a.dp(4));
        t.setTextSize(15f);
        t.setTextColor(a.getColor(R.color.accent));
        row.addView(t);
        android.widget.TextView sub = new android.widget.TextView(a);
        sub.setText(about);
        sub.setTextSize(13.5f);
        sub.setLineSpacing(a.dp(3), 1f); // airy: small explanations read easily
        sub.setPadding(0, a.dp(3), 0, 0);
        sub.setTextColor(a.getColor(R.color.text_secondary));
        row.addView(sub);
        row.setOnClickListener(v -> open.run());
        return row;
    }

    /** Switches to an offline model that is already on the phone, keeping the other file, and loads it. */
    void switchOfflineModel(ai.ibiko.llm.ModelSpec next) {
        if (next.file.equals(a.hub.chosen().file)) {
            return;
        }
        if (a.hub.downloading()) {
            a.note("Pause the model download first, then switch.");
            return;
        }
        new ai.ibiko.llm.ModelGuard(a.prefs).clear(next.file); // chosen on purpose: tried again
        a.hub.choose(a, next, false);
        a.modelGeneration = a.hub.generation;
        a.reportModel();
        a.note("Switching to " + next.weights + "\u2026");
        a.applyProvider(); // the header names the model now in use
        if (a.prefs.getBoolean("llm_on", true)) {
            a.loadModel(false);
        }
    }

}
