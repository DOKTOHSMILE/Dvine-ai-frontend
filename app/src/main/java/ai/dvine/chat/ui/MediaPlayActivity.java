package ai.dvine.chat.ui;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;

import ai.dvine.R;

/** Plays one local video or audio file. The player is released when the screen closes. */
public final class MediaPlayActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_VIDEO = "video";

    private VideoView video;
    private MediaPlayer audio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_play);
        TextView back = findViewById(R.id.media_back);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }
        String path = getIntent().getStringExtra(EXTRA_PATH);
        File file = new File(path == null ? "" : path);
        TextView title = findViewById(R.id.media_title);
        if (title != null) {
            title.setText(file.getName());
        }
        if (!file.isFile()) {
            if (title != null) {
                title.setText("That file is not on this phone.");
            }
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, "ai.dvine.files", file);
        if (getIntent().getBooleanExtra(EXTRA_VIDEO, true)) {
            video = findViewById(R.id.media_video);
            if (video != null) {
                video.setVisibility(android.view.View.VISIBLE);
                video.setVideoURI(uri);
                video.setOnPreparedListener(mp -> {
                    mp.setLooping(false);
                    video.start();
                });
            }
        } else {
            TextView play = findViewById(R.id.media_play);
            if (play != null) {
                play.setVisibility(android.view.View.VISIBLE);
                play.setOnClickListener(v -> toggleAudio(uri, play));
            }
        }
    }

    private void toggleAudio(Uri uri, TextView play) {
        try {
            if (audio != null && audio.isPlaying()) {
                audio.pause();
                play.setText("Play");
                return;
            }
            if (audio == null) {
                audio = new MediaPlayer();
                audio.setDataSource(this, uri);
                audio.prepare();
            }
            audio.start();
            play.setText("Pause");
        } catch (Exception failed) {
            play.setText("This audio file could not be played.");
        }
    }

    @Override
    protected void onDestroy() {
        if (video != null) {
            video.stopPlayback();
        }
        if (audio != null) {
            audio.release();
            audio = null;
        }
        super.onDestroy();
    }
}
