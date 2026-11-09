package ai.dvine.main;

/**
 * The three persona tabs in the chat header — Ivie, Obi and Ibiko — and what happens when one is chosen.
 *
 * <p>
 * Selecting a persona puts it on stage (its face, its instructions), writes the choice into the drawer prefs, re-points the drawer of earlier chats at that persona's conversations, opens the pane, and repaints the header: a hint under the title, the mark on the empty chat, the tab that is chosen showing its name as well as its icon. Ivie's 3D canvas is only shown when she has something on it, and Ibiko's mode only when Ibiko is chosen.
 *
 * <p>
 * Tapping Ibiko also offers the Termux tools once (or says they are already here), and any change of persona warms the model so the new instructions are read ahead instead of at the first question.
 *
 * <p>
 * The screen side lives in {@link ai.dvine.MainActivity}: it implements {@link Host} and hands over the views, the stage, the chats, the prefs, and the few screen actions this class needs.
 */
public final class PersonaTabs {

    /** What choosing a persona needs from the screen it serves. */
    public interface Host {
        /** The screen itself, as a context (the Termux tools ask the system whether they are installed). */
        android.content.Context context();

        /** The drawer prefs, where the chosen persona ("face") is remembered, or null. */
        android.content.SharedPreferences prefs();

        /** Whether the stage is there to put a persona on. */
        boolean hasStage();

        /** Put this persona's face on the stage. */
        void ensureFace(String persona);

        /** Whether the stage is showing an object (Ivie's canvas is only shown when she has something on it). */
        boolean objectShown();

        /** Re-point the drawer of earlier chats at this persona's conversations. */
        void chatsForPersona(String persona);

        /** Open the pane of the current chat. */
        void showChatPane();

        /** Find a view of the screen by id, or null. */
        <T extends android.view.View> T find(int id);

        /** The screen's resources, for densities. */
        android.content.res.Resources resources();

        /** A colour of the screen's theme. */
        int color(int resId);

        /** The brain now in use, named for the header. */
        String brainLabel();

        /** Show or hide the 3D canvas (Ivie's stage). */
        void showIvieCanvas(boolean on);

        /** Show or hide Ibiko's mode row. */
        void showIbikoMode(boolean visible);

        /** Read the new persona's instructions now, ahead of the first question. */
        void warmModel();

        /** Put lines on screen as a spoken turn. */
        void play(java.util.List<ai.dvine.turn.TurnLine> next, Runnable done);

        /** True when this persona's page of the chat has something new the pilot hasn't seen. */
        default boolean unread(String persona) {
            return false;
        }
    }

    private final Host host;

    public PersonaTabs(Host host) {
        this.host = host;
    }

    /** A tab was tapped: choose that persona. */
    public void select(String persona) {
        select(persona, false);
    }

    /** Choose this persona: its face on stage, its chats in the drawer, its name in the header, its tab lit. */
    public void select(String persona, boolean fromTap) {
        if (persona == null || persona.isEmpty() || !host.hasStage())
            return;
        android.content.SharedPreferences prefs = host.prefs();
        if (prefs != null)
            prefs.edit().putString("face", persona).apply();
        host.ensureFace(persona);
        update(persona);
        host.showIvieCanvas("ivie".equals(persona) && host.objectShown()); // the 3D canvas is Ivie's
        host.chatsForPersona(persona);
        host.showChatPane();
        android.widget.EditText field = host.find(ai.dvine.R.id.et_message);
        if (field != null) {
            field.setHint("Ask anything");
        }
        android.widget.TextView sub = host.find(ai.dvine.R.id.tv_header_sub);
        if (sub != null) {
            String name = persona.substring(0, 1).toUpperCase() + persona.substring(1);
            sub.setText(name + " · " + host.brainLabel());
        }
        android.widget.TextView mark = host.find(ai.dvine.R.id.empty_mark);
        if (mark != null) {
            mark.setText(persona.substring(0, 1).toUpperCase());
        }
        host.showIbikoMode("ibiko".equals(persona));
        if (fromTap) {
            if ("ibiko".equals(persona)) {
                offerTermux();
            }
            host.warmModel(); // the new persona's instructions read ahead, beside the others
        }
    }

    /** The coding chat was opened: say once what the file tools here really do. */
    public void offerTermux() {
        android.content.SharedPreferences prefs = host.prefs();
        if (prefs == null) {
            return;
        }
        boolean installed = ai.dvine.ops.TermuxAndroid.installed(host.context());
        String key = installed ? "ibiko_ready_said" : "ibiko_install_offered";
        if (prefs.getBoolean(key, false)) {
            return;
        }
        prefs.edit().putBoolean(key, true).apply();
        // only when the coding chat is opened, and only once; the wording matches what the tools really do here
        host.play(ai.dvine.turn.LocalTurn.speakAs("ibiko", installed
                ? "This is the coding chat. My file tools live here: ask me to find a zip, unzip it, or run a command in this chat. If a task needs another app I'll ask you first."
                : "This is the coding chat. My file tools live here: ask me to find a zip, unzip it, or run a command in this chat. If a task needs another app I'll ask you first."),
                null);
    }

    /** Light the chosen tab and rest the other two. */
    public void update(String persona) {
        set(host.find(ai.dvine.R.id.tabIvie), "ivie".equalsIgnoreCase(persona), host.unread("ivie"));
        set(host.find(ai.dvine.R.id.tabObi), "obi".equalsIgnoreCase(persona), host.unread("obi"));
        set(host.find(ai.dvine.R.id.tabIbiko), "ibiko".equalsIgnoreCase(persona), host.unread("ibiko"));
    }

    /**
     * One page of the chat: its icon and name always (Obi, Ivie, Ibiko are three pages of one conversation), the open
     * one lit, and a dot in the accent colour on a page with something new.
     */
    private void set(android.widget.TextView tab, boolean selected, boolean unread) {
        if (tab == null)
            return;
        CharSequence name = tab.getContentDescription();
        String n = name == null ? "" : name.toString().replace(", new", "");
        if (unread && !selected) {
            android.text.SpannableString t = new android.text.SpannableString(n + " \u25CF");
            t.setSpan(new android.text.style.ForegroundColorSpan(host.color(ai.dvine.R.color.accent)), n.length() + 1, t.length(), 0);
            t.setSpan(new android.text.style.RelativeSizeSpan(0.7f), n.length() + 1, t.length(), 0);
            tab.setText(t);
            tab.setContentDescription(n + ", new");
        } else {
            tab.setText(n);
            tab.setContentDescription(n);
        }
        tab.setCompoundDrawablePadding(Math.round(4 * host.resources().getDisplayMetrics().density));
        tab.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(host.color(
                selected ? ai.dvine.R.color.text_primary : ai.dvine.R.color.text_secondary)));
        tab.setTextColor(host
                .color(selected ? ai.dvine.R.color.text_primary : ai.dvine.R.color.text_secondary));
        tab.setTypeface(null,
                selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        if (selected) {
            tab.setBackgroundResource(ai.dvine.R.drawable.bg_segment_selected);
        } else {
            tab.setBackgroundColor(0x00000000);
        }
        tab.setSelected(selected);
    }
}
