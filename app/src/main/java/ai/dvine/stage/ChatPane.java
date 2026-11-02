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
                    shown = bubble(m);
                    thread.addView(shown);
                } catch (RuntimeException failed) {
                    thread.addView(plain("That message could not be shown."));
                }
            }
            if ("user".equals(m.role)) {
                reply.setLength(0);
                if (shown != null && "text".equals(m.kind) && m.body != null && !m.body.trim().isEmpty()) {
                    // the user's own message: its copy icon shows on a tap and hides on the next one (or when the
                    // chat moves on: every redraw starts with it hidden)
                    View actions = userActions(m.body);
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
            liveRow = liveView(liveText);
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
        refreshActivity();
    }

    /**
     * The turn is over: its steps are kept in the chat as a folded "Worked for 6s · 4 steps" line above the reply,
     * which opens to show them, as Claude and Grok do.
     */
    public void activityEnd() {
        if (!steps.isEmpty()) {
            long secs = Math.max(1, (System.currentTimeMillis() - workStarted + 500) / 1000);
            StringBuilder body = new StringBuilder(secs + "\n");
            for (String st : steps) {
                body.append(st.replace('\n', ' ')).append('\n');
            }
            book.add("assistant", "activity", body.toString().trim(), book.current().persona);
        }
        if (stepsSheet != null && stepsSheet.isShowing() && stepsLive) {
            fillSteps(steps, true); // an open sheet stays, every step now ticked
            stepsLive = false;
        }
        steps.clear();
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
            TextView dot = plain("\u25CF");
            dot.setTextColor(color(R.color.accent));
            dot.setTextSize(12f);
            dot.setPadding(0, 0, dp(8), 0);
            android.view.animation.AlphaAnimation pulse = new android.view.animation.AlphaAnimation(1f, 0.35f);
            pulse.setDuration(700);
            pulse.setRepeatMode(android.view.animation.Animation.REVERSE);
            pulse.setRepeatCount(android.view.animation.Animation.INFINITE);
            dot.startAnimation(pulse);
            liveBlock.addView(dot);
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
            liveBlock.setOnClickListener(v -> openSteps("Working", steps, true));
            liveBlock.setContentDescription("What is happening now; tap for every step");
        }
        if (liveBlock.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) liveBlock.getParent()).removeView(liveBlock);
        }
        String now = !steps.isEmpty() ? ai.dvine.agent.StepDetail.words(steps.get(steps.size() - 1))
                : thinkingText != null ? stripTimer(thinkingText) : "Working";
        type(now);
        long secs = workStarted == 0 ? 0 : (System.currentTimeMillis() - workStarted) / 1000;
        liveMeta.setText((secs > 0 ? secs + "s" : "") + (steps.isEmpty() ? "" : (secs > 0 ? " \u00B7 " : "") + steps.size()
                + " step" + (steps.size() == 1 ? "" : "s")) + "  \u203A");
        if (stepsSheet != null && stepsSheet.isShowing() && stepsLive) {
            fillSteps(steps, false);
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
    }

    private void fillSteps(List<String> list, boolean allDone) {
        if (stepsList == null) {
            return;
        }
        stepsList.removeAllViews();
        if (list.isEmpty()) {
            TextView none = plain(thinkingText != null ? stripTimer(thinkingText) : "Nothing yet.");
            none.setTextColor(color(R.color.text_secondary));
            stepsList.addView(none);
        }
        for (int i = 0; i < list.size(); i++) {
            boolean last = i == list.size() - 1;
            stepsList.addView(stepLine(list.get(i), allDone || !last, !allDone && last));
        }
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
        final String label = "Worked for " + secs + "s \u00B7 " + kept.size() + " step" + (kept.size() == 1 ? "" : "s");
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
        liveText = t;
        int at = liveRow != null && liveRow.getParent() == thread ? thread.indexOfChild(liveRow) : -1;
        if (at >= 0) {
            thread.removeViewAt(at);
        }
        liveRow = null;
        if (t != null) {
            liveRow = liveView(t);
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

    private View liveView(String text) {
        try {
            return bubble(new ChatBook.Message("assistant", "text", text, book.current().persona));
        } catch (RuntimeException e) {
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
        for (ChatBook.Section s : book.sections()) {
            boolean on = s.id.equals(book.current().id);
            String preview = "";
            for (int i = s.messages.size() - 1; i >= 0; i--) {
                ChatBook.Message m = s.messages.get(i);
                if ("text".equals(m.kind) && m.body != null && !m.body.isEmpty()) {
                    preview = m.body.length() > 48 ? m.body.substring(0, 48) + "…" : m.body;
                    break;
                }
            }
            // one line per chat, as in Claude: its title (or its last words), the open one highlighted
            TextView row = new TextView(getContext());
            String named = s.title == null || s.title.trim().isEmpty() || s.title.matches("(?i)^(chat|new chat)$")
                    ? (preview.isEmpty() ? "New chat" : preview) : s.title;
            row.setText(named);
            row.setTextColor(color(on ? R.color.text_primary : R.color.icon));
            row.setTextSize(15f);
            row.setSingleLine(true);
            row.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(44));
            row.setPadding(dp(10), 0, dp(10), 0);
            row.setBackgroundResource(R.drawable.bg_drawer_row);
            row.setSelected(on);
            row.setOnClickListener(v -> {
                book.use(s.id);
                host.onSection(book.current());
                show();
                android.view.View drawer = ((Activity) getContext()).findViewById(R.id.historyDrawer);
                android.view.View scrim = ((Activity) getContext()).findViewById(R.id.drawerScrim);
                if (drawer != null) {
                    drawer.setVisibility(GONE);
                }
                if (scrim != null) {
                    scrim.setVisibility(GONE);
                }
            });
            list.addView(row);
        }
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
            ImageView image = new ImageView(getContext());
            java.io.File f = new java.io.File(m.body == null ? "" : m.body);
            if (f.isFile()) {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath());
                if (bitmap != null) {
                    image.setImageBitmap(bitmap);
                }
            }
            image.setAdjustViewBounds(true);
            image.setMaxHeight(dp(220));
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
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
        bar.addView(icon(R.drawable.ic_speaker, "Read aloud", v -> host.onReadAloud(spoken)));
        return bar;
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

    private View userActions(final String text) {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.END);
        bar.setPadding(0, 0, 0, dp(4));
        bar.addView(icon(R.drawable.ic_copy, "Copy your message", v -> {
            copyText(text);
            android.widget.Toast.makeText(getContext(), "Copied", android.widget.Toast.LENGTH_SHORT).show();
        }));
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bar.setLayoutParams(lp);
        return bar;
    }

    private View icon(int drawable, String label, View.OnClickListener click) {
        android.widget.ImageButton b = new android.widget.ImageButton(getContext());
        b.setImageResource(drawable);
        b.setContentDescription(label);
        android.util.TypedValue ripple = new android.util.TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        b.setBackgroundResource(ripple.resourceId);
        b.setOnClickListener(click);
        LayoutParams lp = new LayoutParams(dp(40), dp(36));
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
        if (cur.messages.isEmpty()) {
            name = "DVINE";
        }
        t.setText(name);
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
