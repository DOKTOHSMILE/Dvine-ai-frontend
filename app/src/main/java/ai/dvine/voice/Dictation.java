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
 * it before sending: the words appear as you speak, the box gets the keyboard back at the end. Tap the mic again to
 * stop; it also stops by itself after a pause. Nothing is sent.
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

    /** Starts dictating, or ends it (the final words are kept). */
    public void toggle() {
        if (active) {
            listen.finish();
            return;
        }
        before = field.getText() == null ? "" : field.getText().toString();
        active = true;
        show(true);
        listen.start(this, true);
    }

    @Override
    public void onPartial(String text) {
        put(text, false);
    }

    @Override
    public void onResult(String text) {
        put(text, true);
    }

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
