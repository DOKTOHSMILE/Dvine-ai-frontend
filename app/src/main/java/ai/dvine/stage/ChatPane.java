package ai.dvine.stage;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.R;
import ai.dvine.chat.ChatBook;

/**
 * The transcript only. The screen around it (header, composer, empty state) is XML.
 * A picture is decoded in the app. A video player is created only when the row is tapped.
 */
public final class ChatPane extends LinearLayout {
    public interface Host {
        void onSend(String text);

        void onSection(ChatBook.Section section);

        void onReadAloud(String text);

        /**
         * Listen tapped: reads the reply aloud, or pauses it when it is being read (a second tap carries on from the
         * sentence it stopped at). True when it is now reading.
         */
        default boolean onReadAloudToggle(String text) {
            onReadAloud(text);
            return false;
        }

        /**
         * The user edited their message at {@code index} of the open chat: it and everything after it are already
         * removed; the edited text is to be sent as a new message.
         */
        default void onEdit(String before, String edited) {
            onSend(edited);
        }
    }

    private final ChatBook book;
    private final Host host;
    private final LinearLayout thread;
    private final ScrollView scroll;
    private final float density;
    private String thinkingText;
    private View thinkingRow;
    /** The reply as the model writes it, shown before it is finished; null when nothing is being written. */
    private String liveText;
    private View liveRow;

    public ChatPane(Context context, ChatBook book, Host host) {
        super(context);
        this.book = book;
        this.host = host;
        density = getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        thread = new LinearLayout(context);
        thread.setOrientation(VERTICAL);
        scroll = new ScrollView(context);
        // the transcript never takes the focus (or the keyboard) from the message box
        scroll.setFocusable(false);
        scroll.setFocusableInTouchMode(false);
        scroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        scroll.addView(thread, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        scroll.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN || action == android.view.MotionEvent.ACTION_MOVE) {
                userMoved = true;
                stickToBottom = false;
            } else if (action == android.view.MotionEvent.ACTION_UP
                    || action == android.view.MotionEvent.ACTION_CANCEL) {
                userMoved = false;
                stickToBottom = !canScrollDown();
            }
            return false;
        });
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        show();
    }

    public void show() {
        fillSections();
        thread.removeAllViews();
        openActions = null;
        ChatBook.Section cur = book.current();
        StringBuilder reply = new StringBuilder(); // the whole of the assistant's turn, for its Copy and Listen
        String work = null; // this reply's Diff, Pull requests and Artifacts, shown under it
        for (int i = 0; i < cur.messages.size(); i++) {
            ChatBook.Message m = cur.messages.get(i);
            View shown = null;
            if ("work".equals(m.kind)) {
                work = m.body;
            } else {
                try {
                    if (!"user".equals(m.role) && "text".equals(m.kind) && m.body != null && m.body.length() > CHUNK) {
                        // a long report as several text blocks (paragraphs together), not one huge block that is
                        // slow to lay out and scroll
                        for (String piece : chunks(m.body, CHUNK)) {
                            shown = bubble(new ChatBook.Message(m.role, m.kind, piece, m.persona, m.language));
                            thread.addView(shown);
                        }
                    } else {
                        shown = bubble(m);
                        thread.addView(shown);
                    }
                } catch (RuntimeException failed) {
                    thread.addView(plain("That message could not be shown."));
                }
            }
            if ("user".equals(m.role)) {
                reply.setLength(0);
                if (shown != null && "text".equals(m.kind) && m.body != null && !m.body.trim().isEmpty()) {
                    // the user's own message: its copy icon shows on a tap and hides on the next one (or when the
                    // chat moves on: every redraw starts with it hidden)
                    View actions = userActions(m.body, i);
                    actions.setVisibility(GONE);
                    thread.addView(actions);
                    tapToggles(shown, actions);
                }
                continue;
            }
            if (("text".equals(m.kind) || "code".equals(m.kind)) && m.body != null) {
                reply.append(reply.length() == 0 ? "" : "\n\n").append("code".equals(m.kind) ? "```" + (m.language == null ? "" : m.language)
                        + "\n" + m.body + "\n```" : m.body);
            }
            boolean lastOfTurn = i == cur.messages.size() - 1 || "user".equals(cur.messages.get(i + 1).role);
            if (lastOfTurn && work != null) {
                workPills(work);
                work = null;
            }
            if (lastOfTurn && reply.length() > 0) {
                thread.addView(actions(reply.toString(), stripCode(reply.toString())));
                reply.setLength(0);
            }
        }
        title(cur);
        thinkingRow = null;
        liveRow = null;
        if (working()) {
            thinkingRow = activityBlock();
            thread.addView(thinkingRow);
        }
        if (liveText != null) {
            liveRow = liveView(liveText.substring(0, Math.max(0, Math.min(liveShown, liveText.length()))));
            thread.addView(liveRow);
        }
        boolean noMessages = cur.messages.isEmpty() && !working() && liveText == null;
        View empty = ((Activity) getContext()).findViewById(R.id.emptyChatHint);
        if (empty != null) {
            empty.setVisibility(noMessages ? VISIBLE : GONE);
            TextView hello = ((Activity) getContext()).findViewById(R.id.empty_title);
            if (hello != null && noMessages) {
                hello.setText(greeting());
            }
        }
        setVisibility(noMessages ? GONE : VISIBLE);
        followBottom();
    }

    // ------------------------------------------------------------ what DVINE is doing, step by step

    /** The steps of the current turn ("Searching the web for …", "Asking OpenAI (gpt-5-mini)"), oldest first. */
    private final List<String> steps = new ArrayList<String>();
    /** When each step started (milliseconds), beside {@link #steps}: each step's time in the sheet. */
    private final List<Long> stepAt = new ArrayList<Long>();
    private long workStarted;

    private boolean working() {
        return thinkingText != null || !steps.isEmpty();
    }

    /**
     * The live status line (what is happening this second, with a timer, e.g. "Writing · 12s"); null when the turn
     * is over. Shown at the top of the activity panel, with the steps so far under it.
     */
    public void setThinking(String line) {
        String t = line == null || line.trim().isEmpty() ? null : line.trim();
        if (t != null && workStarted == 0) {
            workStarted = System.currentTimeMillis();
        }
        thinkingText = t;
        refreshActivity();
    }

    /** A new step of the turn, shown as it happens (repeats of the last step are ignored). */
    public void activityStep(String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }
        String l = line.trim();
        if (!steps.isEmpty() && steps.get(steps.size() - 1).equals(l)) {
            return;
        }
        if (workStarted == 0) {
            workStarted = System.currentTimeMillis();
        }
        steps.add(l);
        stepAt.add(System.currentTimeMillis());
        refreshActivity();
    }

    /**
     * The turn is over: its steps are kept in the chat as a folded "Worked for 6s · 4 steps" line above the reply,
     * which opens to show them, as Claude and Grok do.
     */
    /** Drops the work in progress without keeping it in the chat (a new chat starts clean). */
    public void activityDrop() {
        steps.clear();
        stepAt.clear();
        workStarted = 0;
        thinkingText = null;
        liveText = null;
        removeCallbacks(typeNext);
        typing = "";
        typed = 0;
        show();
    }

    public void activityEnd() {
        if (!steps.isEmpty()) {
            long secs = Math.max(1, (System.currentTimeMillis() - workStarted + 500) / 1000);
            StringBuilder body = new StringBuilder(secs + "\n");
            for (String st : timedSteps(System.currentTimeMillis())) {
                body.append(st.replace('\n', ' ')).append('\n'); // each with its time after a tab
            }
            book.add("assistant", "activity", body.toString().trim(), book.current().persona);
        }
        if (stepsSheet != null && stepsSheet.isShowing() && stepsLive) {
            fillSteps(timedSteps(System.currentTimeMillis()), true); // an open sheet stays, every step now ticked
            stepsLive = false;
        }
        removeCallbacks(stepsTick);
        steps.clear();
        stepAt.clear();
        workStarted = 0;
        thinkingText = null;
        removeCallbacks(typeNext);
        typing = "";
        typed = 0;
        show();
    }

    private void refreshActivity() {
        int at = thinkingRow != null && thinkingRow.getParent() == thread ? thread.indexOfChild(thinkingRow) : -1;
        if (at >= 0) {
            thread.removeViewAt(at);
        }
        thinkingRow = null;
        if (working()) {
            thinkingRow = activityBlock();
            int liveAt = liveRow != null && liveRow.getParent() == thread ? thread.indexOfChild(liveRow) : -1;
            thread.addView(thinkingRow, at >= 0 ? at : liveAt >= 0 ? liveAt : thread.getChildCount());
            setVisibility(VISIBLE);
            View emptyView = ((Activity) getContext()).findViewById(R.id.emptyChatHint);
            if (emptyView != null) {
                emptyView.setVisibility(GONE);
            }
            followBottom();
        }
    }

    /**
     * The live line: a pulsing dot, what is happening now typed out like a typewriter ("Reading app/Main.java"), and
     * the time and step count. The steps themselves are in a bottom sheet a tap away (live while it is open), so the
     * chat keeps only this line and the reply as plain text.
     */
    private View activityBlock() {
        if (liveBlock == null) {
            liveBlock = new LinearLayout(getContext());
            liveBlock.setOrientation(HORIZONTAL);
            liveBlock.setGravity(Gravity.CENTER_VERTICAL);
            liveBlock.setPadding(0, dp(8), 0, dp(8));
            liveBlock.setMinimumHeight(dp(40));
            // the live mark: a turning, breathing spark while the work goes on
            ThinkingMark mark = new ThinkingMark(getContext(), color(R.color.accent));
            LayoutParams mp = new LayoutParams(dp(18), dp(18));
            mp.setMarginEnd(dp(10));
            liveBlock.addView(mark, mp);
            liveNow = plain("");
            liveNow.setTextColor(color(R.color.text_primary));
            liveNow.setTextSize(14f);
            liveNow.setPadding(0, 0, 0, 0);
            liveNow.setMaxLines(2);
            liveNow.setEllipsize(android.text.TextUtils.TruncateAt.END);
            liveBlock.addView(liveNow, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            liveMeta = plain("");
            liveMeta.setTextColor(color(R.color.text_secondary));
            liveMeta.setTextSize(13f);
            liveMeta.setPadding(dp(8), 0, 0, 0);
            liveBlock.addView(liveMeta);
            liveBlock.setOnClickListener(v -> openSteps("Working", timedSteps(System.currentTimeMillis()), true));
            liveBlock.setContentDescription("What is happening now; tap for every step");
        }
        if (liveBlock.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) liveBlock.getParent()).removeView(liveBlock);
        }
        String now = !steps.isEmpty() ? ai.dvine.agent.StepDetail.words(steps.get(steps.size() - 1))
                : thinkingText != null ? stripTimer(thinkingText) : "Working";
        type(now);
        long secs = workStarted == 0 ? 0 : (System.currentTimeMillis() - workStarted) / 1000;
        liveMeta.setText((secs > 0 ? duration(secs) : "") + (steps.isEmpty() ? "" : (secs > 0 ? " \u00B7 " : "") + steps.size()
                + " step" + (steps.size() == 1 ? "" : "s")) + "  \u203A");
        if (stepsSheet != null && stepsSheet.isShowing() && stepsLive) {
            fillSteps(timedSteps(System.currentTimeMillis()), false);
        }
        return liveBlock;
    }

    private LinearLayout liveBlock;
    private TextView liveNow, liveMeta;
    /** The words being typed and how many are shown so far. */
    private String typing = "";
    private int typed;
    private final Runnable typeNext = new Runnable() {
        @Override
        public void run() {
            if (liveNow == null || typed >= typing.length()) {
                return;
            }
            typed = Math.min(typing.length(), typed + Math.max(1, typing.length() / 60)); // long lines type faster
            liveNow.setText(typing.substring(0, typed) + (typed < typing.length() ? "\u258F" : ""));
            if (typed < typing.length()) {
                postDelayed(this, TYPE_MS);
            }
        }
    };
    /** A character every this many milliseconds: the typewriter's pace. */
    static final long TYPE_MS = 18;

    /** Types {@code words} into the live line, a character at a time; the same words again are left as they are. */
    private void type(String words) {
        if (words.equals(typing)) {
            return;
        }
        typing = words;
        typed = 0;
        removeCallbacks(typeNext);
        liveNow.setText("");
        post(typeNext);
    }

    private android.app.Dialog stepsSheet;
    private LinearLayout stepsList;
    private boolean stepsLive;

    /** The steps in a bottom sheet; {@code live}: the current work, which keeps adding to it while it is open. */
    private void openSteps(String title, List<String> list, boolean live) {
        stepsLive = live;
        stepsList = new LinearLayout(getContext());
        stepsList.setOrientation(VERTICAL);
        stepsList.setPadding(dp(20), 0, dp(20), dp(8));
        fillSteps(list, !live);
        stepsSheet = ai.dvine.chat.ui.BottomSheet.showView((Activity) getContext(), title, stepsList);
        if (live) {
            removeCallbacks(stepsTick);
            postDelayed(stepsTick, 1000); // the step running now counts up, second by second
        }
    }

    /** While the live steps sheet is open: every second, the running step's time moves on. */
    private final Runnable stepsTick = new Runnable() {
        @Override
        public void run() {
            if (stepsSheet == null || !stepsSheet.isShowing() || !stepsLive) {
                return;
            }
            fillSteps(timedSteps(System.currentTimeMillis()), false);
            postDelayed(this, 1000);
        }
    };

    /**
     * The steps, each followed by a tab and its seconds: from its start to the next step's (the last one: to
     * {@code now}, still running).
     */
    public List<String> timedSteps(long now) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < steps.size(); i++) {
            long from = i < stepAt.size() ? stepAt.get(i) : now;
            long to = i + 1 < stepAt.size() ? stepAt.get(i + 1) : now;
            out.add(steps.get(i) + "\t" + Math.max(0, (to - from) / 1000));
        }
        return out;
    }

    /** 45 → "45s", 185 → "3m 05s", 3720 → "1h 02m". */
    static String duration(long secs) {
        if (secs < 60) {
            return secs + "s";
        }
        if (secs < 3600) {
            return secs / 60 + "m " + String.format(java.util.Locale.US, "%02ds", secs % 60);
        }
        return secs / 3600 + "h " + String.format(java.util.Locale.US, "%02dm", secs % 3600 / 60);
    }

    /** "Asking Custom gateway (gpt-5-mini)": which API was asked, kept in older chats' steps; not shown. */
    static boolean providerLine(String step) {
        return step != null && ai.dvine.agent.StepDetail.words(step.split("\t")[0]).matches("^Asking .+\\(.+\\).*");
    }

    private void fillSteps(List<String> all, boolean allDone) {
        if (stepsList == null) {
            return;
        }
        List<String> list = new java.util.ArrayList<String>();
        for (String step : all) {
            if (!providerLine(step)) {
                list.add(step);
            }
        }
        stepsList.removeAllViews();
        if (list.isEmpty()) {
            TextView none = plain(thinkingText != null ? stripTimer(thinkingText) : "Nothing yet.");
            none.setTextColor(color(R.color.text_secondary));
            stepsList.addView(none);
        }
        for (int i = 0; i < list.size(); i++) {
            boolean last = i == list.size() - 1;
            if (i > 0) {
                // a thin line between steps, so each process reads on its own
                View line = new View(getContext());
                line.setBackgroundColor(color(R.color.line));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
                lp.topMargin = dp(8);
                lp.bottomMargin = dp(8);
                lp.setMarginStart(dp(30)); // under the text, not the check mark
                stepsList.addView(line, lp);
            }
            String[] parts = list.get(i).split("\t");
            long secs = -1;
            if (parts.length > 1) {
                try {
                    secs = Long.parseLong(parts[1].trim());
                } catch (NumberFormatException ignored) {
                    // a step kept before steps had times
                }
            }
            stepsList.addView(timedRow(stepLine(parts[0], allDone || !last, !allDone && last), secs, !allDone && last));
        }
    }

    /** A step with its time at the right ("12s"; the running step's in the accent colour); no time for old steps. */
    private View timedRow(TextView step, long secs, boolean running) {
        if (secs < 0) {
            return step;
        }
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(step, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView time = plain(duration(secs));
        time.setTextSize(12f);
        time.setTextColor(color(running ? R.color.accent : R.color.text_secondary));
        time.setPadding(dp(10), 0, 0, 0);
        time.setContentDescription((running ? "running for " : "took ") + duration(secs));
        row.addView(time);
        return row;
    }

    /**
     * One step. A step with a detail (a file written or edited, a command run, a file read) shows its +added −removed
     * and opens with a tap: the changes in green and red, or what the command printed.
     */
    private TextView stepLine(String text, boolean done, boolean current) {
        final String words = ai.dvine.agent.StepDetail.words(text);
        final String id = ai.dvine.agent.StepDetail.id(text);
        final ai.dvine.agent.StepDetail.Detail d = id == null ? null : ai.dvine.agent.StepDetail.load(getContext().getFilesDir(), id);
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(words);
        if (d != null && d.diff()) {
            appendColoured(b, "  +" + d.added, ai.dvine.chat.ui.DetailSheet.GREEN);
            appendColoured(b, " \u2212" + d.removed, ai.dvine.chat.ui.DetailSheet.RED);
        }
        if (d != null) {
            b.append("  \u203A");
        }
        TextView t = plain("");
        t.setText(b);
        t.setTextColor(color(current ? R.color.text_primary : R.color.text_secondary));
        t.setTextSize(13f);
        // a standard check icon for a step that is done, a chevron for the one happening now
        android.graphics.drawable.Drawable mark = done || current
                ? androidx.core.content.ContextCompat.getDrawable(getContext(), done ? R.drawable.ic_check : R.drawable.ic_chevron_right).mutate()
                : new android.graphics.drawable.ColorDrawable(0);
        mark.setBounds(0, 0, dp(16), dp(16));
        mark.setTint(color(done ? R.color.text_secondary : R.color.accent));
        t.setCompoundDrawablesRelative(mark, null, null, null);
        t.setCompoundDrawablePadding(dp(10));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(4), dp(1), 0, dp(1));
        if (d != null) {
            t.setPaintFlags(t.getPaintFlags());
            t.setContentDescription(words + ", open");
            t.setOnClickListener(v -> ai.dvine.chat.ui.DetailSheet.show((Activity) getContext(), words, d));
            t.setPadding(dp(4), dp(4), 0, dp(4)); // a bigger target
        }
        return t;
    }

    private static void appendColoured(android.text.SpannableStringBuilder b, String s, int colour) {
        int at = b.length();
        b.append(s);
        b.setSpan(new android.text.style.ForegroundColorSpan(colour), at, b.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** A kept activity line: folded to "Worked for 6s · 4 steps ›", a tap opens the steps. */
    private View activityRecord(ChatBook.Message m) {
        String[] lines = (m.body == null ? "" : m.body).split("\n");
        String secs = lines.length > 0 ? lines[0].trim() : "";
        final List<String> kept = new ArrayList<String>();
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                kept.add(lines[i].trim());
            }
        }
        String took = secs;
        try {
            took = duration(Long.parseLong(secs));
        } catch (NumberFormatException ignored) {
            took = secs + "s";
        }
        final String label = "Worked for " + took + " \u00B7 " + kept.size() + " step" + (kept.size() == 1 ? "" : "s");
        final TextView head = plain(label + "  \u203A");
        head.setTextColor(color(R.color.text_secondary));
        head.setTextSize(13f);
        head.setPadding(0, dp(8), 0, dp(6));
        head.setContentDescription(label + ", open the steps");
        head.setOnClickListener(v -> openSteps(label, kept, false));
        return head;
    }

    private static String stripTimer(String line) {
        return line.replaceAll("\\s*[\\u00B7(]?\\s*\\d+\\s*s\\)?\\s*$", "").replaceAll("\\u2026$", "").trim();
    }

    /**
     * Shows the reply while it is being written, under the activity panel; null removes it (the finished reply
     * then takes its place).
     */
    public void setLive(String text) {
        String t = text == null || text.trim().isEmpty() ? null : text.trim();
        if (t == null) {
            removeCallbacks(typeLive);
            liveText = null;
            liveShown = 0;
            renderLive(null);
            return;
        }
        if (liveText == null || !t.startsWith(liveText.substring(0, Math.min(liveShown, liveText.length())))) {
            liveShown = 0; // another reply: typed from the start
        }
        liveText = t;
        removeCallbacks(typeLive);
        post(typeLive);
    }

    /** How much of the live reply is typed out so far. */
    private int liveShown;

    /**
     * The fast typewriter: the live reply is typed out a few characters at a time, catching up quicker the further
     * behind it is, so it reads as writing without ever lagging behind the model.
     */
    private final Runnable typeLive = new Runnable() {
        @Override
        public void run() {
            String t = liveText;
            if (t == null) {
                return;
            }
            if (t.length() > LONG_LIVE) {
                // a long report: no typing, and redrawn at most a few times a second, only its end (re-reading and
                // re-formatting the whole of it every frame froze the phone after a long audit)
                long now = android.os.SystemClock.uptimeMillis();
                if (now - liveDrawnAt < LONG_LIVE_MS) {
                    postDelayed(this, LONG_LIVE_MS - (now - liveDrawnAt));
                    return;
                }
                liveDrawnAt = now;
                liveShown = t.length();
                renderLive(liveTail(t));
                return;
            }
            int behind = t.length() - liveShown;
            if (behind > 0) {
                liveShown = nextStop(t, liveShown);
                renderLive(t.substring(0, liveShown));
            }
            if (liveShown < t.length()) {
                postDelayed(this, LIVE_TICK_MS);
            }
        }
    };
    /** A text block holds about this many characters at most; a longer one is split at paragraphs. */
    static final int CHUNK = 2500;

    /** Splits long text at blank lines (or, for one very long paragraph, at line ends) into pieces of about {@code most}. */
    static List<String> chunks(String text, int most) {
        List<String> out = new java.util.ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (String para : text.split("\n\n")) {
            if (cur.length() > 0 && cur.length() + para.length() + 2 > most) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (para.length() > most) {
                for (String line : para.split("\n")) {
                    if (cur.length() > 0 && cur.length() + line.length() + 1 > most) {
                        out.add(cur.toString());
                        cur.setLength(0);
                    }
                    cur.append(cur.length() == 0 ? "" : "\n").append(line);
                }
                continue;
            }
            cur.append(cur.length() == 0 ? "" : "\n\n").append(para);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    /** Above this many characters the live reply is not typed out but shown as it comes, a few times a second. */
    static final int LONG_LIVE = 4000;
    static final long LONG_LIVE_MS = 400;
    /** While writing, a long reply shows only its last part (all of it when it is finished). */
    static final int LIVE_TAIL = 3000;
    private long liveDrawnAt;

    /** The end of a long reply while it is being written, from a paragraph start, with a note that more is above. */
    static String liveTail(String t) {
        if (t.length() <= LIVE_TAIL) {
            return t;
        }
        int from = t.length() - LIVE_TAIL;
        int para = t.indexOf("\n\n", from);
        if (para > 0 && para < t.length() - 200) {
            from = para + 2;
        }
        String tail = t.substring(from);
        // don't start inside a code block: an odd number of fences before the cut means one is open
        int fences = 0;
        for (int i = t.indexOf("```"); i >= 0 && i < from; i = t.indexOf("```", i + 3)) {
            fences++;
        }
        return "\u2026 (" + (from / 1000) + "k characters above; all of it shows when the reply is finished)\n\n"
                + (fences % 2 == 1 ? "```\n" : "") + tail;
    }

    /** At least this many characters a frame (about 500 a second at 60 frames), in whole words, as ChatGPT streams. */
    static final int LIVE_CHARS_PER_TICK = 8;
    static final long LIVE_TICK_MS = 16;

    /**
     * Where the typewriter stops next: a few characters on, more the further behind it is (a third of the gap a
     * frame), then on to the end of the word, so whole words appear and it never trails the model.
     */
    static int nextStop(String t, int shown) {
        int behind = t.length() - shown;
        int at = Math.min(t.length(), shown + Math.max(LIVE_CHARS_PER_TICK, behind / 3));
        int limit = Math.min(t.length(), at + 16);
        while (at < limit && !Character.isWhitespace(t.charAt(at))) {
            at++;
        }
        return at;
    }

    /** The kinds of the live reply's parts as shown ("text", "code"…), to redraw only the part being written. */
    private final java.util.List<String> liveKinds = new java.util.ArrayList<String>();

    /**
     * Redraws only the last part of the live reply when the parts before it are unchanged (the usual case: text is
     * added at the end), instead of the whole reply every frame; false when it must be built again.
     */
    private boolean growLive(String shown) {
        if (!(liveRow instanceof LinearLayout) || liveRow.getParent() != thread || liveKinds.isEmpty()) {
            return false;
        }
        java.util.List<ai.dvine.chat.MessageParts.Part> parts;
        try {
            parts = ai.dvine.chat.MessageParts.split(ai.dvine.chat.ReplyText.prepare(shown));
        } catch (RuntimeException e) {
            return false;
        }
        LinearLayout box = (LinearLayout) liveRow;
        if (parts.size() != liveKinds.size() || box.getChildCount() != parts.size()) {
            return false;
        }
        for (int i = 0; i < parts.size(); i++) {
            if (!parts.get(i).kind.equals(liveKinds.get(i))) {
                return false;
            }
        }
        ai.dvine.chat.MessageParts.Part last = parts.get(parts.size() - 1);
        box.removeViewAt(box.getChildCount() - 1);
        box.addView(bubble(new ChatBook.Message("assistant", last.kind, last.body, book.current().persona, last.language)));
        followBottom();
        return true;
    }

    private void renderLive(String shown) {
        if (shown != null && growLive(shown)) {
            return;
        }
        int at = liveRow != null && liveRow.getParent() == thread ? thread.indexOfChild(liveRow) : -1;
        if (at >= 0) {
            thread.removeViewAt(at);
        }
        liveRow = null;
        if (shown != null) {
            liveRow = liveView(shown);
            if (at >= 0) {
                thread.addView(liveRow, at);
            } else {
                thread.addView(liveRow);
            }
            setVisibility(VISIBLE);
            View empty = ((Activity) getContext()).findViewById(R.id.emptyChatHint);
            if (empty != null) {
                empty.setVisibility(GONE);
            }
            followBottom();
        }
    }

    /**
     * The reply as it is written, laid out as it will be kept: prose as text, code (fenced, badly fenced, or not
     * fenced at all) in the code viewer with its language named.
     */
    private View liveView(String text) {
        liveKinds.clear();
        try {
            LinearLayout box = new LinearLayout(getContext());
            box.setOrientation(VERTICAL);
            String persona = book.current().persona;
            for (ai.dvine.chat.MessageParts.Part part : ai.dvine.chat.MessageParts.split(ai.dvine.chat.ReplyText.prepare(text))) {
                box.addView(bubble(new ChatBook.Message("assistant", part.kind, part.body, persona, part.language)));
                liveKinds.add(part.kind);
            }
            return box;
        } catch (RuntimeException e) {
            liveKinds.clear();
            return plain(text);
        }
    }

    private boolean stickToBottom = true;
    private boolean userMoved;

    /** Stays where the user put the list. The thinking line must not pull it back down. */
    private void followBottom() {
        if (userMoved || !stickToBottom) {
            return;
        }
        scroll.post(() -> {
            if (!userMoved && stickToBottom) {
                // scrollTo, not fullScroll: fullScroll moves the focus onto the list, which took the keyboard away
                // from the message box after Send and on every "Thinking" update
                View child = scroll.getChildAt(0);
                if (child != null) {
                    scroll.scrollTo(0, Math.max(0, child.getBottom() - scroll.getHeight()));
                }
            }
        });
    }

    private boolean canScrollDown() {
        View child = scroll.getChildCount() == 0 ? null : scroll.getChildAt(0);
        if (child == null) {
            return false;
        }
        return child.getBottom() - (scroll.getScrollY() + scroll.getHeight()) > dp(32);
    }

    private void fillSections() {
        View found = ((Activity) getContext()).findViewById(R.id.historyList);
        if (!(found instanceof LinearLayout)) {
            return;
        }
        LinearLayout list = (LinearLayout) found;
        list.removeAllViews();
        // as in Claude: Projects, then Pinned, then Recents
        List<String> projects = book.projects();
        if (!projects.isEmpty()) {
            list.addView(drawerHeader("Projects"));
            for (final String p : projects) {
                int n = book.inProject(p).size();
                TextView row = drawerRow(p, R.drawable.ic_project, false);
                row.setContentDescription("Project " + p + ", " + n + " chat" + (n == 1 ? "" : "s"));
                row.setOnClickListener(v -> openProject(p));
                list.addView(row);
            }
        }
        List<ChatBook.Section> all = book.pinnedFirst();
        boolean pinned = false, recents = false;
        for (ChatBook.Section s : all) {
            if (s.pinned && !pinned) {
                list.addView(drawerHeader("Pinned"));
                pinned = true;
            }
            if (!s.pinned && !recents) {
                list.addView(drawerHeader("Recents"));
                recents = true;
            }
            list.addView(chatRow(s));
        }
    }

    private TextView drawerHeader(String name) {
        TextView t = plain(name);
        t.setTextColor(color(R.color.text_secondary));
        t.setTextSize(13f);
        t.setPadding(dp(10), dp(14), dp(10), dp(6));
        return t;
    }

    private TextView drawerRow(String text, int icon, boolean on) {
        TextView row = new TextView(getContext());
        row.setText(text);
        row.setTextColor(color(on ? R.color.text_primary : R.color.icon));
        row.setTextSize(15f);
        row.setSingleLine(true);
        row.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinHeight(dp(44));
        row.setPadding(dp(10), 0, dp(10), 0);
        row.setBackgroundResource(R.drawable.bg_drawer_row);
        row.setSelected(on);
        if (icon != 0) {
            android.graphics.drawable.Drawable d = getContext().getDrawable(icon);
            if (d != null) {
                d = d.mutate();
                d.setTint(color(R.color.icon));
                d.setBounds(0, 0, dp(18), dp(18));
                row.setCompoundDrawablesRelative(d, null, null, null);
                row.setCompoundDrawablePadding(dp(12));
            }
        }
        return row;
    }

    /** One line per chat: its title (or its last words), the open one highlighted; hold for its menu. */
    private TextView chatRow(final ChatBook.Section s) {
        boolean on = s.id.equals(book.current().id);
        String preview = "";
        for (int i = s.messages.size() - 1; i >= 0; i--) {
            ChatBook.Message m = s.messages.get(i);
            if ("text".equals(m.kind) && m.body != null && !m.body.isEmpty()) {
                preview = m.body.length() > 48 ? m.body.substring(0, 48) + "…" : m.body;
                break;
            }
        }
        String named = s.title == null || s.title.trim().isEmpty() || s.title.matches("(?i)^(chat|new chat)$")
                ? (preview.isEmpty() ? "New chat" : preview) : s.title;
        TextView row = drawerRow(named, s.pinned ? R.drawable.ic_pin : 0, on);
        row.setContentDescription(named + (s.pinned ? ", pinned" : "") + (s.project != null ? ", in " + s.project : "")
                + ". Hold for rename, pin, project and delete.");
        row.setOnLongClickListener(v -> { // hold a chat: rename, pin, move to a project, delete
            chatMenu(s.id);
            return true;
        });
        row.setOnClickListener(v -> openChat(s.id));
        return row;
    }

    private void openChat(String id) {
        book.use(id);
        host.onSection(book.current());
        show();
        closeDrawer();
    }

    private void closeDrawer() {
        android.view.View drawer = ((Activity) getContext()).findViewById(R.id.historyDrawer);
        android.view.View scrim = ((Activity) getContext()).findViewById(R.id.drawerScrim);
        if (drawer != null) {
            drawer.setVisibility(GONE);
        }
        if (scrim != null) {
            scrim.setVisibility(GONE);
        }
    }

    /** The projects: each opens its chats; a new one is made here. */
    public void openProjects() {
        final List<String> projects = book.projects();
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(VERTICAL);
        box.setPadding(dp(16), 0, dp(16), dp(8));
        final android.app.Dialog[] sheet = {null};
        TextView add = drawerRow("New project", R.drawable.ic_plus, false);
        add.setOnClickListener(v -> {
            if (sheet[0] != null) {
                sheet[0].dismiss();
            }
            newProject(null);
        });
        box.addView(add);
        if (projects.isEmpty()) {
            TextView none = plain("No projects yet. A project keeps related chats together: make one, then hold a chat to move it in.");
            none.setTextColor(color(R.color.text_secondary));
            none.setPadding(dp(10), dp(10), dp(10), dp(10));
            box.addView(none);
        }
        for (final String p : projects) {
            int n = book.inProject(p).size();
            TextView row = drawerRow(p + "  \u00B7  " + n + " chat" + (n == 1 ? "" : "s"), R.drawable.ic_project, false);
            row.setOnClickListener(v -> {
                if (sheet[0] != null) {
                    sheet[0].dismiss();
                }
                openProject(p);
            });
            box.addView(row);
        }
        sheet[0] = ai.dvine.chat.ui.BottomSheet.showView((Activity) getContext(), "Projects", box);
    }

    /** One project: its chats, a new chat in it, and deleting the project (its chats stay). */
    public void openProject(final String name) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(VERTICAL);
        box.setPadding(dp(16), 0, dp(16), dp(8));
        final android.app.Dialog[] sheet = {null};
        TextView fresh = drawerRow("New chat in " + name, R.drawable.ic_new_chat, false);
        fresh.setOnClickListener(v -> {
            ChatBook.Section s = book.fresh(book.current().persona);
            book.moveTo(s.id, name);
            if (sheet[0] != null) {
                sheet[0].dismiss();
            }
            openChat(s.id);
        });
        box.addView(fresh);
        List<ChatBook.Section> chats = book.inProject(name);
        if (chats.isEmpty()) {
            TextView none = plain("No chats here yet. Hold a chat in the menu and choose Move to project.");
            none.setTextColor(color(R.color.text_secondary));
            none.setPadding(dp(10), dp(10), dp(10), dp(10));
            box.addView(none);
        }
        for (int i = chats.size() - 1; i >= 0; i--) {
            final ChatBook.Section s = chats.get(i);
            TextView row = chatRow(s);
            row.setOnClickListener(v -> {
                if (sheet[0] != null) {
                    sheet[0].dismiss();
                }
                openChat(s.id);
            });
            box.addView(row);
        }
        TextView remove = drawerRow("Delete project", 0, false);
        remove.setTextColor(color(R.color.text_secondary));
        remove.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setMessage("Delete the project \u201c" + name + "\u201d? Its chats stay, outside any project.")
                .setPositiveButton("Delete", (d, w) -> {
                    book.removeProject(name);
                    if (sheet[0] != null) {
                        sheet[0].dismiss();
                    }
                    fillSections();
                })
                .setNegativeButton("Cancel", null)
                .show());
        box.addView(remove);
        sheet[0] = ai.dvine.chat.ui.BottomSheet.showView((Activity) getContext(), name, box);
    }

    /** Asks for a project's name; with a chat id, that chat moves into the new project. */
    public void newProject(final String chatId) {
        final android.widget.EditText field = new android.widget.EditText(getContext());
        field.setSingleLine(true);
        field.setHint("Project name");
        android.widget.FrameLayout pad = new android.widget.FrameLayout(getContext());
        pad.setPadding(dp(20), dp(8), dp(20), 0);
        pad.addView(field);
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("New project")
                .setView(pad)
                .setPositiveButton("Create", (d, w) -> {
                    String n = field.getText().toString().trim();
                    if (n.isEmpty()) {
                        return;
                    }
                    book.addProject(n);
                    if (chatId != null) {
                        book.moveTo(chatId, n);
                    }
                    fillSections();
                })
                .setNegativeButton("Cancel", null)
                .show();
        field.requestFocus();
    }

    /** Moves a chat into a project, a new one, or out of its project. */
    public void moveToProject(final String id) {
        ChatBook.Section s = find(id);
        if (s == null) {
            return;
        }
        final List<String> projects = book.projects();
        final List<String> items = new ArrayList<String>(projects);
        items.add("New project\u2026");
        if (s.project != null) {
            items.add("Out of \u201c" + s.project + "\u201d");
        }
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("Move to project")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which < projects.size()) {
                        book.moveTo(id, projects.get(which));
                    } else if (which == projects.size()) {
                        newProject(id);
                        return;
                    } else {
                        book.moveTo(id, null);
                    }
                    fillSections();
                })
                .show();
    }

    public void assistant(String persona, String text) {
        int before = book.current().messages.size();
        book.reply(persona, text);
        if (book.current().messages.size() != before) {
            show();
        }
    }

    public void image(String path) {
        book.add("assistant", "image", path, book.current().persona);
        show();
    }

    /** Keeps a reply's work (its id in files/work) with the chat; the reply shows it as pills. */
    public void work(String id) {
        book.add("assistant", "work", id, book.current().persona);
        show();
    }

    /**
     * The pills of a reply's work, inside the reply's last text row when it ends with one, else in a strip of their
     * own, so they sit under the reply that produced them and scroll with it.
     */
    private void workPills(String id) {
        ai.dvine.chat.ui.WorkChips.Run run = ai.dvine.chat.ui.WorkChips.load(getContext(), id);
        if (run.isEmpty()) {
            return;
        }
        View last = thread.getChildCount() == 0 ? null : thread.getChildAt(thread.getChildCount() - 1);
        android.widget.HorizontalScrollView sv = last == null ? null : last.findViewById(R.id.reply_work_scroll);
        LinearLayout row = last == null ? null : last.findViewById(R.id.reply_work_row);
        if (sv == null || row == null) {
            View strip = LayoutInflater.from(getContext()).inflate(R.layout.item_message_assistant, thread, false);
            strip.findViewById(R.id.tv_message).setVisibility(GONE);
            thread.addView(strip);
            sv = strip.findViewById(R.id.reply_work_scroll);
            row = strip.findViewById(R.id.reply_work_row);
        }
        new ai.dvine.chat.ui.WorkChips((Activity) getContext(), sv, row).show(run);
    }

    public void video(String path) {
        book.add("assistant", "video", path, book.current().persona);
        show();
    }

    private View bubble(ChatBook.Message m) {
        boolean user = "user".equals(m.role);
        if ("image".equals(m.kind)) {
            // the picture at its own shape on the sender's side (the user's on the right, as their messages), never
            // stretched across the chat; decoded small for the chat, whole in the viewer
            ImageView image = new ImageView(getContext());
            android.graphics.Bitmap bitmap = ai.dvine.chat.ui.ImageViewActivity.decode(m.body == null ? "" : m.body, dp(360));
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
            }
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setMaxHeight(dp(240));
            image.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.7f));
            image.setClipToOutline(true);
            image.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
                }
            });
            image.setContentDescription(user ? "Your picture. Tap to open." : "Picture. Tap to open.");
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            lp.gravity = user ? Gravity.END : Gravity.START;
            image.setLayoutParams(lp);
            image.setOnClickListener(v -> open(ai.dvine.chat.ui.ImageViewActivity.class, "path", m.body));
            return image;
        }
        if ("video".equals(m.kind) || "audio".equals(m.kind)) {
            TextView tap = plain("audio".equals(m.kind) ? "Audio. Tap to play." : "Video. Tap to play.");
            tap.setTextColor(color(R.color.text_primary));
            tap.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(getContext(),
                        ai.dvine.chat.ui.MediaPlayActivity.class);
                intent.putExtra(ai.dvine.chat.ui.MediaPlayActivity.EXTRA_PATH, m.body);
                intent.putExtra(ai.dvine.chat.ui.MediaPlayActivity.EXTRA_VIDEO, "video".equals(m.kind));
                getContext().startActivity(intent);
            });
            return tap;
        }
        if ("activity".equals(m.kind)) {
            return activityRecord(m);
        }
        if ("file".equals(m.kind)) {
            // an attached file or long paste: a small card on the user's side
            String[] parts = (m.body == null ? "file" : m.body).split("\n", 2);
            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(VERTICAL);
            card.setBackgroundResource(R.drawable.bg_user_bubble);
            card.setPadding(dp(14), dp(10), dp(14), dp(10));
            TextView n = plain("\uD83D\uDCC4  " + parts[0]);
            n.setTextSize(14f);
            n.setPadding(0, 0, 0, 0);
            card.addView(n);
            if (parts.length > 1) {
                TextView sub = plain(parts[1]);
                sub.setTextColor(color(R.color.text_secondary));
                sub.setTextSize(12f);
                sub.setPadding(0, dp(2), 0, 0);
                card.addView(sub);
            }
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.END;
            lp.topMargin = dp(6);
            card.setLayoutParams(lp);
            return card;
        }
        if ("step".equals(m.kind)) {
            // one step of Ibiko's work: a small grey line, no actions
            TextView step = plain("\u2022 " + (m.body == null ? "" : m.body));
            step.setTextColor(color(R.color.text_secondary));
            step.setTypeface(Typeface.MONOSPACE);
            step.setTextSize(12.5f);
            step.setPadding(0, dp(2), 0, dp(2));
            return step;
        }
        if ("code".equals(m.kind) || "html".equals(m.kind)) {
            View row = LayoutInflater.from(getContext()).inflate(R.layout.item_code_message, thread, false);
            TextView lang = row.findViewById(R.id.code_lang);
            TextView body = row.findViewById(R.id.code_body);
            if (lang != null) {
                lang.setText("html".equals(m.kind) ? "HTML preview" : (m.language == null || m.language.isEmpty() ? "code" : m.language));
            }
            if (body != null) {
                body.setText(m.body == null ? "" : m.body);
            }
            TextView copy = row.findViewById(R.id.code_copy);
            if (copy != null) {
                copy.setOnClickListener(v -> {
                    copyText(m.body);
                    android.widget.Toast.makeText(getContext(), "Code copied", android.widget.Toast.LENGTH_SHORT).show();
                });
            }
            TextView open = row.findViewById(R.id.code_open);
            if (open != null) {
                open.setContentDescription("html".equals(m.kind) ? "Preview" : "Open full screen");
                open.setOnClickListener(v -> {
                    if ("html".equals(m.kind)) {
                        open(ai.dvine.chat.ui.HtmlViewActivity.class, ai.dvine.chat.ui.HtmlViewActivity.EXTRA_HTML, m.body);
                    } else {
                        android.content.Intent intent = new android.content.Intent(getContext(),
                                ai.dvine.chat.ui.CodeViewActivity.class);
                        intent.putExtra(ai.dvine.chat.ui.CodeViewActivity.EXTRA_CODE, m.body);
                        intent.putExtra(ai.dvine.chat.ui.CodeViewActivity.EXTRA_LANG, m.language);
                        getContext().startActivity(intent);
                    }
                });
            }
            return row;
        }
        int layout = user ? R.layout.item_message_user : R.layout.item_message_assistant;
        View row = LayoutInflater.from(getContext()).inflate(layout, thread, false);
        TextView body = row.findViewById(R.id.tv_message);
        body.setText(user ? (m.body == null ? "" : m.body) : styled(m.body));
        body.setTextIsSelectable(true); // hold to select any part and copy it
        if (!user) {
            body.setLineSpacing(dp(3), 1.05f);
        }
        if ("code".equals(m.kind)) {
            body.setTypeface(Typeface.MONOSPACE);
            body.setTextSize(13f);
        }
        if (!user) {
            String name = labelOf(m.persona);
            TextView sender = row.findViewById(R.id.tv_sender);
            TextView mark = row.findViewById(R.id.tv_avatar);
            if (sender != null) {
                sender.setText(name);
            }
            if (mark != null) {
                mark.setText(name.substring(0, 1));
            }
        }
        return row;
    }

    /** Copy and Listen, once, under the whole of a reply: small icons with 40 dp targets. */
    private View actions(final String all, final String spoken) {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, dp(2), 0, dp(10));
        bar.addView(icon(R.drawable.ic_copy, "Copy", v -> {
            copyText(all);
            android.widget.Toast.makeText(getContext(), "Copied", android.widget.Toast.LENGTH_SHORT).show();
        }));
        final android.widget.ImageButton listen = (android.widget.ImageButton) icon(R.drawable.ic_speaker, "Read aloud", null);
        listen.setOnClickListener(v -> {
            if (readingButton != null && readingButton != listen) {
                readingStopped(); // another reply's button goes back to Listen
            }
            boolean on = host.onReadAloudToggle(spoken);
            listen.setImageResource(on ? R.drawable.ic_pause : R.drawable.ic_speaker);
            listen.setContentDescription(on ? "Pause reading" : "Read aloud");
            readingButton = on ? listen : null;
        });
        bar.addView(listen);
        return bar;
    }

    /** The Listen button of the reply being read aloud (showing pause); null when nothing is being read. */
    private android.widget.ImageButton readingButton;

    /** Reading aloud ended or was paused elsewhere: its button shows Listen again. */
    public void readingStopped() {
        if (readingButton != null) {
            readingButton.setImageResource(R.drawable.ic_speaker);
            readingButton.setContentDescription("Read aloud");
            readingButton = null;
        }
    }

    /** Copy under the user's own message, on its side (right), as under a reply. */
    /** The message box's actions, open after a tap on it; null when none is open. */
    private View openActions;

    /**
     * A tap on the message shows its actions, another tap hides them; opening one closes the one open before. A
     * long press still selects text.
     */
    private void tapToggles(View message, final View actions) {
        final Runnable toggle = () -> {
            boolean show = actions.getVisibility() != VISIBLE;
            if (openActions != null && openActions != actions) {
                openActions.setVisibility(GONE);
            }
            actions.setVisibility(show ? VISIBLE : GONE);
            openActions = show ? actions : null;
        };
        View text = message.findViewById(R.id.tv_message);
        (text != null ? text : message).setOnClickListener(v -> toggle.run()); // a long press still selects text
    }

    private View userActions(final String text, final int index) {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.END);
        bar.setPadding(0, 0, 0, dp(4));
        bar.addView(icon(R.drawable.ic_edit, "Edit your message", v -> edit(text, index)));
        bar.addView(icon(R.drawable.ic_copy, "Copy your message", v -> {
            copyText(text);
            android.widget.Toast.makeText(getContext(), "Copied", android.widget.Toast.LENGTH_SHORT).show();
        }));
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bar.setLayoutParams(lp);
        return bar;
    }

    /**
     * Edits one of the user's messages, as in ChatGPT: the text opens ready to change; Send removes that message and
     * everything after it in this chat (the old reply too) and sends the edited message in its place.
     */
    public void edit(final String before, final int index) {
        final android.widget.EditText field = new android.widget.EditText(getContext());
        field.setText(before);
        field.setSelection(before.length());
        field.setMinLines(2);
        field.setMaxLines(8);
        field.setHint("Your message");
        android.widget.FrameLayout pad = new android.widget.FrameLayout(getContext());
        pad.setPadding(dp(20), dp(8), dp(20), 0);
        pad.addView(field);
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("Edit message")
                .setView(pad)
                .setPositiveButton("Send", (d, w) -> {
                    String now = field.getText().toString().trim();
                    if (now.isEmpty() || !book.cutFrom(book.current().id, index)) {
                        return;
                    }
                    openActions = null;
                    show();
                    host.onEdit(before, now);
                })
                .setNegativeButton("Cancel", null)
                .show();
        field.requestFocus();
    }

    private View icon(int drawable, String label, View.OnClickListener click) {
        android.widget.ImageButton b = new android.widget.ImageButton(getContext());
        b.setImageResource(drawable);
        b.setContentDescription(label);
        android.util.TypedValue ripple = new android.util.TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        b.setBackgroundResource(ripple.resourceId);
        b.setOnClickListener(click);
        // small, like ChatGPT's: a 16 dp icon in a 36 by 32 dp target
        b.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        b.setPadding(dp(10), dp(8), dp(10), dp(8));
        LayoutParams lp = new LayoutParams(dp(36), dp(32));
        b.setLayoutParams(lp);
        return b;
    }

    /** What to read aloud: the words, not the code. */
    private static String stripCode(String text) {
        return text.replaceAll("(?s)```.*?```", " (code) ").replaceAll("\\s+", " ").trim();
    }

    /** The header shows the open chat's name. */
    private void title(ChatBook.Section cur) {
        TextView t = ((Activity) getContext()).findViewById(R.id.tv_header_title);
        if (t == null) {
            return;
        }
        String name = cur.title == null || cur.title.trim().isEmpty() || cur.title.matches("(?i)^chat$") ? "New chat" : cur.title;
        if (cur.messages.isEmpty() && (cur.title == null || cur.title.matches("(?i)^(chat|new chat|obi|ivie|ibiko|ibiko code)$"))) {
            name = "DVINE";
        }
        t.setText(name);
        t.setContentDescription("Chat name: " + name + ". Tap to rename.");
        t.setOnClickListener(v -> rename(cur.id));
    }

    /** What can be done to a chat: rename it, pin or unpin it, delete it. */
    public void chatMenu(final String id) {
        ChatBook.Section s = find(id);
        if (s == null) {
            return;
        }
        final boolean pinned = s.pinned;
        String[] items = {"Rename", pinned ? "Unpin" : "Pin to top", "Move to project", "Delete"};
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        rename(id);
                    } else if (which == 1) {
                        book.pin(id, !pinned);
                        fillSections();
                    } else if (which == 2) {
                        moveToProject(id);
                    } else {
                        delete(id);
                    }
                })
                .show();
    }

    /** Asks first, then deletes the chat; what DVINE learned from it stays ({@link ChatBook#delete}). */
    public void delete(final String id) {
        if (find(id) == null) {
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("Delete this chat?")
                .setMessage("Its messages are removed. What DVINE learned from it is kept.")
                .setPositiveButton("Delete", (d, w) -> {
                    boolean wasOpen = id.equals(book.current().id);
                    if (book.delete(id)) {
                        if (wasOpen) {
                            host.onSection(book.current());
                        }
                        show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private ChatBook.Section find(String id) {
        for (ChatBook.Section x : book.sections()) {
            if (x.id.equals(id)) {
                return x;
            }
        }
        return null;
    }

    /** Asks for a new name for a chat, with the current one ready to edit. */
    public void rename(final String id) {
        ChatBook.Section s = null;
        for (ChatBook.Section x : book.sections()) {
            if (x.id.equals(id)) {
                s = x;
            }
        }
        if (s == null) {
            return;
        }
        final android.widget.EditText field = new android.widget.EditText(getContext());
        field.setSingleLine(true);
        field.setText(s.title == null || s.title.matches("(?i)^chat$") ? "" : s.title);
        field.setSelectAllOnFocus(true);
        field.setHint("Chat name");
        android.widget.FrameLayout pad = new android.widget.FrameLayout(getContext());
        pad.setPadding(dp(20), dp(8), dp(20), 0);
        pad.addView(field);
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("Rename chat")
                .setView(pad)
                .setPositiveButton("Rename", (d, w) -> {
                    if (book.rename(id, field.getText().toString())) {
                        show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        field.requestFocus();
    }

    /** "Good evening, Valentine" by the phone's clock, with the name DVINE was told. */
    private String greeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String part = hour < 5 ? "Hello" : hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        String name = getContext().getSharedPreferences("dvine", Context.MODE_PRIVATE).getString("user_name", null);
        return name == null || name.trim().isEmpty() ? part : part + ", " + name.trim();
    }

    private String thoughtLabel(String line) {
        if (line == null) {
            return "Thinking";
        }
        java.util.regex.Matcher match = java.util.regex.Pattern.compile("(\\d+)s").matcher(line);
        if (match.find()) {
            return "Thought for " + match.group(1) + "s";
        }
        return "Thinking";
    }

    private void open(Class<?> screen, String extra, String value) {
        android.content.Intent intent = new android.content.Intent(getContext(), screen);
        intent.putExtra(extra, value);
        getContext().startActivity(intent);
    }

    private void copyText(String text) {
        android.content.ClipboardManager clips = (android.content.ClipboardManager) getContext()
                .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clips != null) {
            clips.setPrimaryClip(android.content.ClipData.newPlainText("dvine", text == null ? "" : text));
        }
    }

    /** Bold, inline code and headings, so a reply is not a wall of asterisks. */
    private CharSequence styled(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                b.append('\n');
            }
            String line = lines[i];
            int heading = 0;
            while (heading < line.length() && line.charAt(heading) == '#') {
                heading++;
            }
            if (heading > 0 && heading <= 3 && heading < line.length() && line.charAt(heading) == ' ') {
                line = line.substring(heading + 1);
            }
            int from = b.length();
            appendInline(b, line);
            if (heading > 0) {
                b.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), from, b.length(),
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return b;
    }

    private void appendInline(android.text.SpannableStringBuilder b, String line) {
        int i = 0;
        while (i < line.length()) {
            if (line.startsWith("**", i) || line.startsWith("__", i)) {
                int end = line.indexOf(line.substring(i, i + 2), i + 2);
                if (end > i + 2) {
                    int from = b.length();
                    appendCode(b, line.substring(i + 2, end));
                    b.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), from, b.length(),
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }
            if (line.charAt(i) == '`') {
                int end = line.indexOf('`', i + 1);
                if (end > i) {
                    int from = b.length();
                    b.append(line, i + 1, end);
                    b.setSpan(new android.text.style.TypefaceSpan("monospace"), from, b.length(),
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }
            int next = line.length();
            int star = line.indexOf("**", i);
            int under = line.indexOf("__", i);
            int tick = line.indexOf('`', i);
            if (star >= 0) {
                next = Math.min(next, star);
            }
            if (under >= 0) {
                next = Math.min(next, under);
            }
            if (tick >= 0) {
                next = Math.min(next, tick);
            }
            if (next == i) {
                b.append(line.charAt(i));
                i++;
            } else {
                b.append(line, i, next);
                i = next;
            }
        }
    }

    private void appendCode(android.text.SpannableStringBuilder b, String line) {
        int i = 0;
        while (i < line.length()) {
            if (line.charAt(i) == '`') {
                int end = line.indexOf('`', i + 1);
                if (end > i) {
                    int from = b.length();
                    b.append(line, i + 1, end);
                    b.setSpan(new android.text.style.TypefaceSpan("monospace"), from, b.length(),
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }
            int tick = line.indexOf('`', i);
            if (tick < 0) {
                b.append(line.substring(i));
                return;
            }
            b.append(line, i, tick);
            i = tick;
        }
    }

    private static String labelOf(String persona) {
        if (persona == null || persona.isEmpty()) {
            return "Obi";
        }
        return persona.substring(0, 1).toUpperCase() + persona.substring(1);
    }

    private TextView plain(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(color(R.color.text_primary));
        tv.setTextSize(13f);
        int pad = dp(10);
        tv.setPadding(pad, dp(6), pad, dp(6));
        return tv;
    }

    private int color(int id) {
        return getContext().getColor(id);
    }

    private int dp(int v) {
        return (int) (v * density);
    }
}
