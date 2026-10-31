package ai.dvine.stage;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import ai.dvine.chat.ChatBook;

/**
 * The chat sheet over the 3D stage: sections, a transcript, code, a picture, a video file, and a text field.
 * It only shows a picture or a video when that file is on the phone.
 */
public final class ChatPane extends LinearLayout {
    public interface Host {
        void onSend(String text);

        void onSection(ChatBook.Section section);
    }

    private final ChatBook book;
    private final Host host;
    private final LinearLayout chips;
    private final LinearLayout thread;
    private final ScrollView scroll;
    private final TextView persona;
    private final EditText input;
    private final float density;

    public ChatPane(Context context, ChatBook book, Host host) {
        super(context);
        this.book = book;
        this.host = host;
        density = getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE605070C);
        bg.setCornerRadius(18f * density);
        setBackground(bg);

        chips = new LinearLayout(context);
        chips.setOrientation(HORIZONTAL);
        HorizontalScrollView chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.addView(chips);
        addView(chipScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        persona = new TextView(context);
        persona.setTextColor(0xFF7FE3FF);
        persona.setTextSize(13f);
        persona.setPadding(dp(4), dp(6), dp(8), dp(4));
        persona.setOnClickListener(v -> cyclePersona());
        head.addView(persona, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView neu = label(context, "New chat");
        neu.setOnClickListener(v -> {
            ChatBook.Section s = book.open("New chat", book.current().persona);
            host.onSection(s);
            show();
        });
        head.addView(neu);
        addView(head, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        thread = new LinearLayout(context);
        thread.setOrientation(VERTICAL);
        scroll = new ScrollView(context);
        scroll.addView(thread);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        input = new EditText(context);
        input.setHint("Message");
        input.setHintTextColor(0x8890C8E0);
        input.setTextColor(0xFFE8F6FF);
        input.setTextSize(16f);
        input.setBackground(null);
        input.setMaxLines(4);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                submit();
                return true;
            }
            return false;
        });
        row.addView(input, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView send = label(context, "Send");
        send.setOnClickListener(v -> submit());
        row.addView(send);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        show();
    }

    public void show() {
        chips.removeAllViews();
        for (ChatBook.Section s : book.sections()) {
            TextView chip = label(getContext(), s.title);
            boolean on = s.id.equals(book.current().id);
            chip.setTextColor(on ? 0xFF041018 : 0xFFD5E8F2);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(12f * density);
            g.setColor(on ? 0xFF7FE3FF : 0xFF1A2833);
            chip.setBackground(g);
            chip.setOnClickListener(v -> {
                book.use(s.id);
                host.onSection(book.current());
                show();
            });
            chips.addView(chip);
        }
        ChatBook.Section cur = book.current();
        persona.setText(cur.persona.substring(0, 1).toUpperCase() + cur.persona.substring(1) + "  ·  tap to switch");
        thread.removeAllViews();
        for (ChatBook.Message m : cur.messages) {
            thread.addView(bubble(m));
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void submit() {
        String text = input.getText() == null ? "" : input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        input.setText("");
        if (text.toLowerCase(java.util.Locale.US).startsWith("remember ")) {
            book.remember(text.substring(9).trim());
        }
        book.add("user", "text", text, book.current().persona);
        show();
        host.onSend(text);
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

    public void video(String path) {
        book.add("assistant", "video", path, book.current().persona);
        show();
    }

    private void cyclePersona() {
        String now = book.current().persona;
        String next = "ivie".equals(now) ? "obi" : "obi".equals(now) ? "ibiko" : "ivie";
        book.persona(next);
        host.onSection(book.current());
        show();
    }

    private View bubble(ChatBook.Message m) {
        boolean user = "user".equals(m.role);
        if ("image".equals(m.kind)) {
            ImageView image = new ImageView(getContext());
            java.io.File f = new java.io.File(m.body);
            if (f.isFile()) {
                image.setImageURI(Uri.fromFile(f));
            }
            image.setAdjustViewBounds(true);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            image.setLayoutParams(lp);
            return image;
        }
        if ("video".equals(m.kind)) {
            VideoView video = new VideoView(getContext());
            java.io.File f = new java.io.File(m.body);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(180));
            lp.topMargin = dp(6);
            video.setLayoutParams(lp);
            if (f.isFile()) {
                video.setVideoURI(Uri.fromFile(f));
                video.seekTo(1);
                video.setOnClickListener(v -> video.start());
            }
            return video;
        }
        TextView tv = new TextView(getContext());
        tv.setText(m.body);
        tv.setTextSize("code".equals(m.kind) ? 13f : 15f);
        tv.setTextColor(user ? 0xFF041018 : 0xFFE8F6FF);
        if ("code".equals(m.kind)) {
            tv.setTypeface(Typeface.MONOSPACE);
        }
        int pad = dp(10);
        tv.setPadding(pad, pad, pad, pad);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(14f * density);
        g.setColor(user ? 0xFFB8F2FF : "code".equals(m.kind) ? 0xFF101820 : 0xFF18242E);
        tv.setBackground(g);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        lp.gravity = user ? Gravity.END : Gravity.START;
        tv.setLayoutParams(lp);
        if ("code".equals(m.kind)) {
            HorizontalScrollView wrap = new HorizontalScrollView(getContext());
            wrap.addView(tv);
            wrap.setLayoutParams(lp);
            return wrap;
        }
        return tv;
    }

    private TextView label(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(0xFFD5E8F2);
        tv.setTextSize(13f);
        tv.setPadding(dp(10), dp(6), dp(10), dp(6));
        return tv;
    }

    private int dp(int v) {
        return (int) (v * density);
    }
}
