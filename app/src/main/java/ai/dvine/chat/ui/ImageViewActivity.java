package ai.dvine.chat.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

import ai.dvine.R;

/**
 * A picture from this chat, full screen: it opens fitted to the screen; pinch to zoom, drag to move, double-tap to
 * zoom in or back. A big photo is decoded at about the screen's size, so it opens quickly and never runs out of memory.
 */
public final class ImageViewActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "path";

    ZoomImageView image;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        image = new ZoomImageView(this);
        image.setContentDescription("Picture. Pinch to zoom, double-tap to zoom in or out.");
        root.addView(image, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        ImageButton close = new ImageButton(this);
        close.setImageResource(R.drawable.ic_close);
        close.setColorFilter(0xFFFFFFFF);
        close.setBackgroundResource(android.R.drawable.screen_background_dark_transparent);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> finish());
        int s = Ui.dp(this, 48), m = Ui.dp(this, 12);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(s, s, Gravity.TOP | Gravity.START);
        cp.setMargins(m, m + Ui.dp(this, 24), m, m);
        root.addView(close, cp);
        setContentView(root);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        Bitmap bitmap = decode(path == null ? "" : path, Math.max(getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels) * 2);
        if (bitmap != null) {
            image.setImageBitmap(bitmap);
        } else {
            TextView gone = Ui.text(this, "This picture isn't on the phone any more.", 16, 0xFFFFFFFF, false);
            gone.setGravity(Gravity.CENTER);
            root.addView(gone, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
    }

    /** The picture at no more than about {@code most} pixels on its long side (null when it can't be read). */
    public static Bitmap decode(String path, int most) {
        File f = new File(path);
        if (!f.isFile()) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
        int side = Math.max(bounds.outWidth, bounds.outHeight), sample = 1;
        while (side / (sample * 2) >= Math.max(1, most)) {
            sample *= 2;
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (OutOfMemoryError e) {
            o.inSampleSize = sample * 2;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        }
    }
}
