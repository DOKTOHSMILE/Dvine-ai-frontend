package ai.dvine.stage;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

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
    }

    private final ChatBook book;
    private final Host host;
    private final LinearLayout thread;
    private final ScrollView scroll;
    private final float density;

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
        View empty = ((Activity) getContext()).findViewById(R.id.emptyChatHint);
        if (empty != null) {
            empty.setVisibility(cur.messages.isEmpty() ? VISIBLE : GONE);
        }
        setVisibility(cur.messages.isEmpty() ? GONE : VISIBLE);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void fillSections() {
        View found = ((Activity) getContext()).findViewById(R.id.sectionChips);
        if (!(found instanceof LinearLayout)) {
            return;
        }
        LinearLayout chips = (LinearLayout) found;
        chips.removeAllViews();
        for (ChatBook.Section s : book.sections()) {
            TextView chip = plain(s.title);
            boolean on = s.id.equals(book.current().id);
            chip.setTextColor(on ? 0xFF071018 : 0xFFD5E8F2);
            chip.setBackgroundResource(on ? R.drawable.dvine_persona_tab_selected : R.drawable.dvine_persona_tab);
            chip.setOnClickListener(v -> {
                book.use(s.id);
                host.onSection(book.current());
                show();
            });
            chips.addView(chip);
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
            return image;
        }
        if ("video".equals(m.kind)) {
            TextView tap = plain("Video on this phone. Tap to play.");
            tap.setTextColor(0xFFE8F6FF);
            tap.setOnClickListener(v -> playVideo(m.body, tap));
            return tap;
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
        return row;
    }

    private static String labelOf(String persona) {
        if (persona == null || persona.isEmpty()) {
            return "Obi";
        }
        return persona.substring(0, 1).toUpperCase() + persona.substring(1);
    }

    private void playVideo(String path, View label) {
        java.io.File f = new java.io.File(path == null ? "" : path);
        if (!f.isFile() || !(label.getParent() instanceof LinearLayout)) {
            return;
        }
        LinearLayout parent = (LinearLayout) label.getParent();
        int at = parent.indexOfChild(label);
        try {
            VideoView video = new VideoView(getContext());
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(180));
            lp.topMargin = dp(6);
            video.setLayoutParams(lp);
            Uri uri = androidx.core.content.FileProvider.getUriForFile(getContext(), "ai.dvine.files", f);
            video.setVideoURI(uri);
            parent.removeView(label);
            parent.addView(video, at);
            video.setOnPreparedListener(mp -> video.start());
        } catch (RuntimeException failed) {
            label.setOnClickListener(null);
            if (label instanceof TextView) {
                ((TextView) label).setText("That video file is here, but the player could not open it.");
            }
        }
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
