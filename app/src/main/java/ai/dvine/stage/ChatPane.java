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
    private TextView thinkingRow;

    public ChatPane(Context context, ChatBook book, Host host) {
        super(context);
        this.book = book;
        this.host = host;
        density = getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        thread = new LinearLayout(context);
        thread.setOrientation(VERTICAL);
        scroll = new ScrollView(context);
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
        ChatBook.Section cur = book.current();
        for (ChatBook.Message m : cur.messages) {
            try {
                thread.addView(bubble(m));
            } catch (RuntimeException failed) {
                thread.addView(plain("That message could not be shown."));
            }
        }
        thinkingRow = null;
        if (thinkingText != null) {
            thinkingRow = plain(thoughtLabel(thinkingText));
            thinkingRow.setTextColor(0xFF8E8E93);
            thinkingRow.setTextSize(14f);
            thread.addView(thinkingRow);
        }
        boolean noMessages = cur.messages.isEmpty() && thinkingText == null;
        View empty = ((Activity) getContext()).findViewById(R.id.emptyChatHint);
        if (empty != null) {
            empty.setVisibility(noMessages ? VISIBLE : GONE);
        }
        setVisibility(noMessages ? GONE : VISIBLE);
        followBottom();
    }

    /** The live line sits in the transcript, under the last message, not under the composer. */
    public void setThinking(String line) {
        thinkingText = line == null || line.trim().isEmpty() ? null : line.trim();
        if (thinkingRow != null && thinkingRow.getParent() == thread) {
            if (thinkingText == null) {
                thread.removeView(thinkingRow);
                thinkingRow = null;
            } else {
                thinkingRow.setText(thoughtLabel(thinkingText));
                thinkingRow.setTextColor(0xFF8E8E93);
            }
        } else if (thinkingText != null) {
            thinkingRow = plain(thoughtLabel(thinkingText));
            thinkingRow.setTextColor(0xFF8E8E93);
            thinkingRow.setTextSize(14f);
            thread.addView(thinkingRow);
        }
        boolean empty = book.current().messages.isEmpty() && thinkingText == null;
        View emptyView = ((Activity) getContext()).findViewById(R.id.emptyChatHint);
        if (emptyView != null) {
            emptyView.setVisibility(empty ? VISIBLE : GONE);
        }
        setVisibility(empty ? GONE : VISIBLE);
        if (thinkingText != null) {
            followBottom();
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
                scroll.fullScroll(View.FOCUS_DOWN);
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
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(12);
            row.setPadding(0, pad, 0, pad);
            TextView title = new TextView(getContext());
            title.setText(s.title);
            title.setTextColor(0xFFF2F2F7);
            title.setTextSize(16f);
            title.setMaxLines(1);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            TextView sub = new TextView(getContext());
            sub.setText(preview.isEmpty() ? labelOf(s.persona) : preview);
            sub.setTextColor(on ? 0xFFF2F2F7 : 0xFF8E8E93);
            sub.setTextSize(13f);
            sub.setMaxLines(1);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(title);
            row.addView(sub);
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
            tap.setTextColor(0xFFE8F6FF);
            tap.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(getContext(),
                        ai.dvine.chat.ui.MediaPlayActivity.class);
                intent.putExtra(ai.dvine.chat.ui.MediaPlayActivity.EXTRA_PATH, m.body);
                intent.putExtra(ai.dvine.chat.ui.MediaPlayActivity.EXTRA_VIDEO, "video".equals(m.kind));
                getContext().startActivity(intent);
            });
            return tap;
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
                copy.setOnClickListener(v -> copyText(m.body));
            }
            TextView open = row.findViewById(R.id.code_open);
            if (open != null) {
                open.setText("html".equals(m.kind) ? "Preview" : "Open");
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
        body.setText(m.body == null ? "" : m.body);
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
        if (!user && "text".equals(m.kind)) {
            LinearLayout actions = new LinearLayout(getContext());
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(6), 0, 0);
            TextView copy = action("Copy");
            copy.setOnClickListener(v -> copyText(m.body));
            TextView listen = action("Listen");
            listen.setPadding(dp(16), 0, 0, 0);
            listen.setOnClickListener(v -> host.onReadAloud(m.body));
            actions.addView(copy);
            actions.addView(listen);
            View message = row.findViewById(R.id.tv_message);
            if (message != null && message.getParent() instanceof LinearLayout) {
                ((LinearLayout) message.getParent()).addView(actions);
            }
        }
        return row;
    }

    private TextView action(String label) {
        TextView tv = new TextView(getContext());
        tv.setText(label);
        tv.setTextColor(0xFF8E8E93);
        tv.setTextSize(13f);
        return tv;
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

    private static String labelOf(String persona) {
        if (persona == null || persona.isEmpty()) {
            return "Obi";
        }
        return persona.substring(0, 1).toUpperCase() + persona.substring(1);
    }

    private TextView plain(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextColor(0xFFD5E8F2);
        tv.setTextSize(13f);
        int pad = dp(10);
        tv.setPadding(pad, dp(6), pad, dp(6));
        return tv;
    }

    private int dp(int v) {
        return (int) (v * density);
    }
}
