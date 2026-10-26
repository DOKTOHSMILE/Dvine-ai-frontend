package ai.dvine;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import ai.dvine.brain.BrainAnswer;
import ai.dvine.brain.LearningJournal;
import ai.dvine.brain.OfflineBrain;
import ai.dvine.face.FaceMode;
import ai.dvine.face.MatrixStageView;
import ai.dvine.face.StageListener;
import ai.dvine.net.Connectivity;
import ai.dvine.net.KnowledgeStore;
import ai.dvine.net.SyncWorker;
import ai.dvine.voice.ListenCallback;
import ai.dvine.turn.LocalTurn;
import ai.dvine.turn.TurnLine;
import ai.dvine.turn.TurnRoute;
import ai.dvine.voice.ListenBridge;
import ai.dvine.voice.TalkBridge;

import java.io.File;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

/**
 * One face on screen. Lines from {@link LocalTurn} switch the face
 * when Obi or Ivie has something to say, or when the user calls one of them.
 */
public final class MainActivity extends Activity implements StageListener, ListenCallback {
    private static final int MIC = 21;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MatrixStageView stage;
    private TalkBridge talk;
    private OfflineBrain brain;
    private Connectivity connectivity;
    private SyncWorker sync;
    private ListenBridge listen;
    private List<TurnLine> lines = java.util.Collections.emptyList();
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
        FrameLayout root = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        stage.requestApplyInsets();
        talk = new TalkBridge(this);
        listen = new ListenBridge(this);

        KnowledgeStore store = new KnowledgeStore(this);
        LearningJournal journal = new LearningJournal(new File(getFilesDir(), "learning"));
        brain = new OfflineBrain(store.load(), journal, System::currentTimeMillis, TimeZone.getDefault());
        sync = new SyncWorker(getString(R.string.api_base), deviceId(), journal, store,
                () -> null, pack -> handler.post(() -> brain.replacePack(pack)));
        connectivity = new Connectivity(this, online -> {
            stage.setOnline(online);
            if (online) {
                sync.kick(brain.pack().version);
            }
        });
    }

    private String deviceId() {
        android.content.SharedPreferences prefs = getSharedPreferences("dvine", MODE_PRIVATE);
        String id = prefs.getString("device_id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString("device_id", id).apply();
        }
        return id;
    }

    /** One place decides who answers: a persona switch, or the brain. */
    private void answer(String text) {
        TurnRoute route = LocalTurn.route(text, stage.agent());
        if (route.switchOnly) {
            stage.setStaged(false, "");
            play(route.lines);
            return;
        }
        BrainAnswer reply = brain.respond(text, stage.agent());
        stage.setStaged(false, "");
        play(reply.lines);
        stage.pushActivity(reply.source + "  ·  " + shortLine(text));
    }

    @Override
    public void onHeadTap() {
        stopPlay();
        stage.setStaged(false, "");
        play(LocalTurn.call(stage.isIvie()));
    }

    @Override
    public void onDraft(boolean send) {
        stopPlay();
        String line = send
                ? "Nothing was sent. It stays staged until you confirm it out loud."
                : "Shorter: we stop here, and nothing goes out.";
        play(LocalTurn.speakAs(stage.isIvie(), line));
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
        listen.start(this);
    }

    @Override
    public void onPartial(String text) {
        stage.setNotice(text);
    }

    @Override
    public void onResult(String text) {
        stage.setListening(false);
        stage.setNotice("");
        answer(text);
    }

    @Override
    public void onError(String message) {
        stage.setListening(false);
        stage.setNotice(message);
        stage.setMode(FaceMode.ERROR);
    }

    @Override
    public void onType() {
        final EditText input = new EditText(this);
        input.setTextColor(0xFFEDEDED);
        input.setHint("Ask anything, or say Obi switch / Ivie switch");
        input.setHintTextColor(0xFF8A8A8A);
        input.setBackgroundColor(0xFF000000);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(stage.isIvie() ? "Talk to Ivie" : "Talk to Obi")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Ask", (d, w) -> answer(input.getText().toString()))
                .show();
    }

    private void play(List<TurnLine> next) {
        stopPlay();
        stage.clearActivity();
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
        final TurnLine line = lines.get(index);
        index++;
        final boolean ivie = "ivie".equals(line.agent);
        if ("SAY".equals(line.cue)) {
            stage.setCaption(line.text == null ? "" : line.text);
            stage.pushActivity("TTS  ·  " + shortLine(line.text));
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
                stage.pushActivity("GATE  ·  Checking the request");
                handler.postDelayed(() -> step(token), 460);
            });
        }
    }

    private static String shortLine(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replace('\n', ' ').trim();
        if (clean.length() <= 42) {
            return clean;
        }
        return clean.substring(0, 41) + "...";
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
    protected void onStart() {
        super.onStart();
        connectivity.start();
    }

    @Override
    protected void onStop() {
        connectivity.stop();
        super.onStop();
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
        if (sync != null) {
            sync.shutdown();
        }
        if (listen != null) {
            listen.stop();
        }
        super.onDestroy();
    }
}
