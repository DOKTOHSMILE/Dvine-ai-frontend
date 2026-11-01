package ai.dvine.chat.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

import ai.dvine.R;

/** A photograph from this chat. Pinch to zoom. The file is not fetched again. */
public final class ImageViewActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "path";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_view);
        TextView back = findViewById(R.id.image_back);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }
        ImageView image = findViewById(R.id.image_full);
        if (image == null) {
            return;
        }
        File file = new File(getIntent().getStringExtra(EXTRA_PATH) == null ? "" : getIntent().getStringExtra(EXTRA_PATH));
        Bitmap bitmap = file.isFile() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null;
        if (bitmap != null) {
            image.setImageBitmap(bitmap);
        }
        image.setScaleType(ImageView.ScaleType.MATRIX);
        final Matrix matrix = new Matrix();
        image.setImageMatrix(matrix);
        ScaleGestureDetector scale = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        matrix.postScale(detector.getScaleFactor(), detector.getScaleFactor(),
                                detector.getFocusX(), detector.getFocusY());
                        image.setImageMatrix(matrix);
                        return true;
                    }
                });
        image.setOnTouchListener((v, event) -> {
            scale.onTouchEvent(event);
            return true;
        });
    }
}
