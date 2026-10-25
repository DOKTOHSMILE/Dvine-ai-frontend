package ai.dvine;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import ai.dvine.face.FaceMode;
import ai.dvine.face.MatrixStageView;
import ai.dvine.turn.LocalTurn;
import ai.dvine.voice.ListenBridge;
import ai.dvine.voice.TalkBridge;

import java.util.List;

/**
 * One face on screen. Lines from {@link LocalTurn} switch the face
 * when Obi or Ivie has something to say, or when the user calls one of them.
 */
public final class MainActivity extends Activity implements MatrixStageView.Listener {
    private static final int MIC = 21;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MatrixStageView stage;
    private TalkBridge talk;
    private ListenBridge listen;
    private List<LocalTurn.Line> lines = java.util.Collections.emptyList();
    private int index;
    private int playId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.setStatusBarColor(0xFF000000);
        window.setNavigationBarColor(0xFF000000);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }
        stage = new MatrixStageView(this);
        stage.setListener(this);
        setContentView(stage);
        stage.requestApplyInsets();
        talk = new TalkBridge(this);
        listen = new ListenBridge(this);
    }

    @Override
    public void onHeadTap() {
        stopPlay();
        play(LocalTurn.call(stage.isIvie()));
    }

    @Override
    public void onSwipeTo(boolean ivie) {
        stopPlay();
        stage.setCaption("");
        stage.setNotice("");
        stage.ensureFace(ivie, () -> stage.setMode(FaceMode.CONTENT));
    }

    @Override
    public void onMic() {
        if (listen.isListening()) {
            listen.stop();
            stage.setListening(false);
            return;
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, MIC);
            return;
        }
        stopPlay();
        stage.setListening(true);
        stage.setNotice("");
        stage.setMode(FaceMode.LISTENING);
        listen.start(new ListenBridge.Callback() {
            @Override
            public void onPartial(String text) {
                stage.setNotice(text);
            }

            @Override
            public void onResult(String text) {
                stage.setListening(false);
                stage.setNotice("");
                play(LocalTurn.script(text, stage.agent()));
            }

            @Override
            public void onError(String message) {
                stage.setListening(false);
                stage.setNotice(message);
                stage.setMode(FaceMode.ERROR);
            }
        });
    }

    @Override
    public void onType() {
        final EditText input = new EditText(this);
        input.setTextColor(0xFFEDEDED);
        input.setHint("Say it, or call Obi or Ivie");
        input.setHintTextColor(0xFF8A8A8A);
        input.setBackgroundColor(0xFF000000);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Talk")
                .setView(input)
                .setNegativeButton("Obi", (d, w) -> play(LocalTurn.speakAs(false, input.getText().toString())))
                .setPositiveButton("Ivie", (d, w) -> play(LocalTurn.speakAs(true, input.getText().toString())))
                .setNeutralButton("Ask both", (d, w) -> play(LocalTurn.script(input.getText().toString(), stage.agent())))
                .show();
    }

    private void play(List<LocalTurn.Line> next) {
        stopPlay();
        lines = next;
        index = 0;
        final int token = playId;
        step(token);
    }

    private void step(final int token) {
        if (token != playId) {
            return;
        }
        if (index >= lines.size()) {
            stage.setMode(FaceMode.DONE);
            return;
        }
        final LocalTurn.Line line = lines.get(index);
        index++;
        final boolean ivie = "ivie".equals(line.agent);
        if ("SAY".equals(line.cue)) {
            stage.setCaption(line.text == null ? "" : line.text);
            stage.ensureFace(ivie, () -> {
                if (token != playId) {
                    return;
                }
                stage.setMode(FaceMode.SPEAKING);
                talk.speak(ivie, line.text, () -> step(token));
            });
        } else {
            stage.ensureFace(ivie, () -> {
                if (token != playId) {
                    return;
                }
                stage.setMode(FaceMode.THINKING);
                stage.setCaption(ivie ? "Ivie is thinking" : "Obi is thinking");
                handler.postDelayed(() -> step(token), 460);
            });
        }
    }

    private void stopPlay() {
        playId++;
        handler.removeCallbacksAndMessages(null);
        if (talk != null) {
            talk.stop();
        }
        if (listen != null && listen.isListening()) {
            listen.stop();
            stage.setListening(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == MIC && results.length > 0 && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            onMic();
        } else if (requestCode == MIC) {
            stage.setNotice("Microphone is off. Type instead.");
        }
    }

    @Override
    protected void onPause() {
        stopPlay();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (talk != null) {
            talk.shutdown();
        }
        if (listen != null) {
            listen.stop();
        }
        super.onDestroy();
    }
}
