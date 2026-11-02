package ai.dvine.chat.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import ai.dvine.chat.Attachments;

/**
 * What is attached to the message being written, shown above the words as in Claude: a photo as a thumbnail, a file
 * or a long paste as a small card ("Pasted text · 120 lines"), each with ✕. Nothing is sent until Send: the user adds
 * the instructions first.
 */
public final class ComposerAttachments {
    /** One attached thing. */
    public static final class Item {
        /** "image", "text" (its words are sent) or "file" (only its name and what it is). */
        public final String kind, name, path, text;

        public Item(String kind, String name, String path, String text) {
            this.kind = kind;
            this.name = name;
            this.path = path;
            this.text = text;
        }
    }

    private final Context context;
    private final HorizontalScrollView scroll;
    private final LinearLayout row;
    private final List<Item> items = new ArrayList<Item>();
    private final Runnable changed;

    public ComposerAttachments(Context context, HorizontalScrollView scroll, LinearLayout row, Runnable changed) {
        this.context = context;
        this.scroll = scroll;
        this.row = row;
        this.changed = changed;
        Ui.init(context);
    }

    public void add(Item item) {
        items.add(item);
        render();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public List<Item> items() {
        return new ArrayList<Item>(items);
    }

    public void clear() {
        items.clear();
        render();
    }

    /** The message for the model: the words, then each attachment (images as markers, texts as data blocks). */
    public String forModel(String words) {
        StringBuilder b = new StringBuilder(words == null || words.trim().isEmpty()
                ? (items.size() == 1 && "image".equals(items.get(0).kind) ? "What do you make of this image?" : "Look at the attachment.")
                : words.trim());
        for (Item it : items) {
            b.append("\n\n");
            if ("image".equals(it.kind)) {
                b.append(Attachments.image(it.path));
            } else {
                b.append(Attachments.text(it.name, it.text));
            }
        }
        return b.toString();
    }

    private void render() {
        row.removeAllViews();
        for (final Item it : items) {
            FrameLayout box = new FrameLayout(context);
            View face;
            if ("image".equals(it.kind)) {
                ImageView img = new ImageView(context);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 4;
                Bitmap bmp = BitmapFactory.decodeFile(it.path, o);
                if (bmp != null) {
                    img.setImageBitmap(bmp);
                }
                img.setBackground(Ui.round(context, Ui.FIELD, 0, 12));
                img.setClipToOutline(true);
                face = img;
                box.addView(face, new FrameLayout.LayoutParams(Ui.dp(context, 64), Ui.dp(context, 64)));
            } else {
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(Ui.round(context, Ui.FIELD, Ui.LINE, 12));
                card.setPadding(Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 22), Ui.dp(context, 8));
                TextView n = Ui.text(context, it.name, 13, Ui.TEXT, true);
                n.setSingleLine(true);
                n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                card.addView(n);
                card.addView(Ui.text(context, "text".equals(it.kind) ? Attachments.lines(it.text) : "file", 12, Ui.SUB, false));
                face = card;
                box.addView(face, new FrameLayout.LayoutParams(Ui.dp(context, 150), Ui.dp(context, 64)));
            }
            TextView x = Ui.text(context, "✕", 11, Ui.TEXT, true);
            x.setGravity(Gravity.CENTER);
            x.setBackground(Ui.round(context, Ui.SURFACE, Ui.LINE, 10));
            x.setContentDescription("Remove " + it.name);
            x.setOnClickListener(v -> {
                items.remove(it);
                render();
            });
            FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20), Gravity.END | Gravity.TOP);
            xp.setMargins(0, Ui.dp(context, 3), Ui.dp(context, 3), 0);
            box.addView(x, xp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(context, 8);
            row.addView(box, lp);
        }
        scroll.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        if (changed != null) {
            changed.run();
        }
    }
}
