package ai.dvine.voice;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * The mic button writes what you say into the message box, after anything already typed, so you can read and edit
 * it before sending: the words appear as you speak, the box gets the keyboard back at the end. The session runs
 * until you tap the mic again — a stretch of silence only ends that stretch, and the next one begins straight
 * away, so it never stops by itself in the middle of a conversation. Nothing is sent.
 */
public final class Dictation implements ListenCallback {
    private final EditText field;
    private final ImageButton mic;
    private final ListenBridge listen;
    private final int accent;
    private final Consumer<String> note;
    private boolean active, draft;
    private String before = "";

    public Dictation(EditText field, ImageButton mic, ListenBridge listen, int accent, Consumer<String> note) {
        this.field = field;
        this.mic = mic;
        this.listen = listen;
        this.accent = accent;
        this.note = note;
    }

    public boolean active() {
        return active;
    }

    /** Whether the message now in the box was spoken (and forgets it): its reply may be read aloud. */
    public boolean takeDraft() {
        boolean d = draft;
        draft = false;
        return d;
    }

    /** Starts dictating, or ends it. Only this call stops a hold-to-continue session; silence does not. */
    public void toggle() {
        if (active) {
            active = false;
            show(false);
            listen.finish(); // the last words still arrive through onResult; the mic is already off
            return;
        }
        before = field.getText() == null ? "" : field.getText().toString();
        active = true;
        show(true);
        // continuous: the session keeps restarting until finish() — silence never ends it by itself
        listen.start(this, false, true);
    }

    @Override
    public void onPartial(String text) {
        put(text, false);
    }

    /** A finished stretch: the words go in, but the session carries on — the button stays lit. */
    @Override
    public void onResult(String text) {
        put(text, false);
    }

    /** Listening itself failed: end the session, because nothing more will be heard. */
    @Override
    public void onError(String message) {
        put("", true);
        if (note != null) {
            note.accept(message);
        }
    }

    private void show(boolean on) {
        if (mic != null) {
            mic.setSelected(on);
            mic.setImageTintList(on ? ColorStateList.valueOf(accent) : null);
            mic.setContentDescription(on ? "Stop dictating" : "Speak");
        }
        field.setHint(on ? "Listening…" : "Ask anything");
    }

    private void put(String words, boolean done) {
        String w = words == null ? "" : words.trim();
        if (done && !w.isEmpty()) {
            w = w.substring(0, 1).toUpperCase(Locale.getDefault()) + w.substring(1);
        }
        String kept = before.replaceAll("\\s+$", "");
        String text = kept.isEmpty() ? w : w.isEmpty() ? before : kept + " " + w;
        field.setText(text);
        field.setSelection(field.getText().length());
        if (done) {
            active = false;
            show(false);
            if (!w.isEmpty()) {
                draft = true;
            }
            field.requestFocus();
            InputMethodManager ime = (InputMethodManager) field.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (ime != null) {
                ime.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }
}