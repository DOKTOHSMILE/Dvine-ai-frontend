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
            thinkingRow = plain(thinkingText);
            thread.addView(thinkingRow);
        }
        boolean noMessages = cur.messages.isEmpty() && thinkingText == null;
        View empty = ((Activity) getContext()).findViewById(R.id.emptyChatHint);
        if (empty != null) {
            empty.setVisibility(noMessages ? VISIBLE : GONE);
        }
        setVisibility(noMessages ? GONE : VISIBLE);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    /** The live line sits in the transcript, under the last message, not under the composer. */
    public void setThinking(String line) {
        thinkingText = line == null || line.trim().isEmpty() ? null : line.trim();
        if (thinkingRow != null && thinkingRow.getParent() == thread) {
            if (thinkingText == null) {
                thread.removeView(thinkingRow);
                thinkingRow = null;
            } else {
                thinkingRow.setText(thinkingText);
            }
        } else if (thinkingText != null) {
            thinkingRow = plain(thinkingText);
            thread.addView(thinkingRow);
        }
        boolean empty = book.current().messages.isEmpty() && thinkingText == null;
        View emptyView = ((Activity) getContext()).findViewById(R.id.emptyChatHint);
        if (emptyView != null) {
            emptyView.setVisibility(empty ? VISIBLE : GONE);
        }
        setVisibility(empty ? GONE : VISIBLE);
        if (thinkingText != null) {
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
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
            TextView row = new TextView(getContext());
            String preview = "";
            for (int i = s.messages.size() - 1; i >= 0; i--) {
                ChatBook.Message m = s.messages.get(i);
                if ("text".equals(m.kind) && m.body != null && !m.body.isEmpty()) {
                    preview = m.body.length() > 48 ? m.body.substring(0, 48) + "…" : m.body;
                    break;
                }
            }
            row.setText(preview.isEmpty() ? s.title : s.title + "\n" + preview);
            row.setTextColor(on ? 0xFF071018 : 0xFFE8F6FF);
            row.setTextSize(14f);
            int pad = dp(12);
            row.setPadding(pad, pad, pad, pad);
            row.setBackgroundResource(on ? R.drawable.dvine_persona_tab_selected : R.drawable.bg_input_container);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            row.setLayoutParams(lp);
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
        if (!user && "text".equals(m.kind) && row instanceof LinearLayout) {
            TextView read = new TextView(getContext());
            read.setText("Read");
            read.setTextColor(0xFF7DD3E0);
            read.setPadding(0, dp(4), 0, 0);
            read.setOnClickListener(v -> host.onReadAloud(m.body));
            ((LinearLayout) row).addView(read);
            body.setOnLongClickListener(v -> {
                copyText(m.body);
                return true;
            });
        }
        return row;
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
