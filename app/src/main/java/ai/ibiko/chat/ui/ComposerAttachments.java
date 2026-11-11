package ai.ibiko.chat.ui;

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

import ai.ibiko.chat.Attachments;

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
        String said = words == null ? "" : words.trim();
        if (said.isEmpty()) {
            // No words yet: say what there is, singular or plural, instead of one fixed line.
            said = items.isEmpty() ? "Look at the attachment."
                    : items.size() == 1 && "image".equals(items.get(0).kind)
                    ? "What do you make of this image?" : "Look at the attachments.";
        }
        StringBuilder b = new StringBuilder(said);
        for (Item it : items) {
            b.append("\n\n");
            if ("image".equals(it.kind)) {
                b.append(Attachments.image(it.path));
            } else if ("doc".equals(it.kind)) {
                b.append(Attachments.file(it.path, mime(it), it.name, pageList(it)));
            } else {
                b.append(Attachments.text(it.name, it.text == null ? "" : it.text));
            }
        }
        return b.toString();
    }

    /** What kind of file it is ("PDF · 12 pages", "Spreadsheet", "Video"). */
    public static String pages(Item it) {
        String[] lines = meta(it);
        return lines.length > 1 ? lines[1].trim() : "File";
    }

    /** The doc's three metadata lines: page list, human label, mime (missing lines read as empty). */
    private static String[] meta(Item it) {
        return it == null || it.text == null ? new String[0] : it.text.split("\n");
    }

    private static String mime(Item it) {
        String[] lines = meta(it);
        return lines.length > 2 && !lines[2].isEmpty() ? lines[2] : "application/octet-stream";
    }

    /** The page list the reader recorded ("1;2;5"), as individual entries. */
    private static List<String> pageList(Item it) {
        List<String> pages = new ArrayList<String>();
        String[] lines = meta(it);
        if (lines.length > 0) {
            for (String p : lines[0].split(";")) {
                if (!p.isEmpty()) {
                    pages.add(p);
                }
            }
        }
        return pages;
    }

    /** The thumbnail, decoded at about the size it is shown rather than a fixed quarter scale. */
    private Bitmap thumb(Item it) {
        if (it.path == null || it.path.isEmpty()) {
            return null;
        }
        int target = Math.max(Ui.dp(context, 64), 1);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(it.path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null; // not an image the decoder understands
        }
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
            sample *= 2;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(it.path, o);
    }

    private void render() {
        row.removeAllViews();
        for (final Item it : items) {
            FrameLayout box = new FrameLayout(context);
            View face;
            if ("image".equals(it.kind)) {
                ImageView img = new ImageView(context);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Bitmap bmp = thumb(it);
                if (bmp != null) {
                    img.setImageBitmap(bmp);
                }
                img.setContentDescription("Attached " + (it.name == null ? "image" : it.name));
                img.setBackground(Ui.round(context, Ui.FIELD, 0, 12));
                img.setClipToOutline(true);
                face = img;
                box.addView(face, new FrameLayout.LayoutParams(Ui.dp(context, 64), Ui.dp(context, 64)));
            } else {
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(Ui.round(context, Ui.FIELD, Ui.LINE, 12));
                card.setPadding(Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 22), Ui.dp(context, 8));
                TextView n = Ui.text(context, it.name == null ? "Attachment" : it.name, 13, Ui.TEXT, true);
                n.setSingleLine(true);
                n.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                card.addView(n);
                card.addView(Ui.text(context, "text".equals(it.kind)
                                ? Attachments.lines(it.text)
                                : "doc".equals(it.kind) ? pages(it) : "File",
                        12, Ui.SUB, false));
                face = card;
                box.addView(face, new FrameLayout.LayoutParams(Ui.dp(context, 150), Ui.dp(context, 64)));
            }
            android.widget.ImageView x = Ui.glyph(context, ai.ibiko.R.drawable.ic_close, Ui.TEXT, 14);
            x.setPadding(Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3));
            x.setBackground(Ui.round(context, Ui.SURFACE, Ui.LINE, 10));
            x.setContentDescription("Remove " + (it.name == null ? "attachment" : it.name));
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