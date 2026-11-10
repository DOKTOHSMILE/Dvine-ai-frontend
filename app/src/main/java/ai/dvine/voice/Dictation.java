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

    /**
     * A finished stretch: the words go in and are kept, and the session carries on (the button stays lit). The next
     * stretch, after a pause, is written after them: before this, a pause wiped what was said before it.
     */
    @Override
    public void onResult(String text) {
        put(text, false);
        before = field.getText() == null ? "" : field.getText().toString(); // kept: the next words go after these
    }

    /** Listening itself failed: end the session, because nothing more will be heard. */
    @Override
    public void onError(String message) {
        put("", true);
        if (note != null) {
            note.accept(message);
        }
    }

    /** The recording effect: the red disc swells with the voice, smoothly, and rests when it is quiet. */
    @Override
    public void onLevel(float level) {
        if (mic == null || !active) {
            return;
        }
        float target = 1f + 0.28f * level;
        mic.animate().scaleX(target).scaleY(target).setDuration(90).start();
    }

    private void show(boolean on) {
        if (mic != null) {
            mic.setSelected(on);
            // recording: the mic in white on a red disc, as a recorder shows; otherwise the plain icon
            mic.setBackgroundResource(on ? ai.dvine.R.drawable.bg_mic_recording : 0);
            if (!on) {
                android.util.TypedValue ripple = new android.util.TypedValue();
                mic.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
                mic.setBackgroundResource(ripple.resourceId);
                mic.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            mic.setImageTintList(on ? ColorStateList.valueOf(0xFFFFFFFF) : null);
            mic.setContentDescription(on ? "Stop dictating" : "Speak");
        }
        field.setHint(on ? "Listening…" : "Ask anything");
    }

    /** What is in the box plus the words of this stretch, with one space between. */
    static String join(String before, String words) {
        String kept = before == null ? "" : before.replaceAll("\\s+$", "");
        String w = words == null ? "" : words.trim();
        return kept.isEmpty() ? w : w.isEmpty() ? (before == null ? "" : before) : kept + " " + w;
    }

    private void put(String words, boolean done) {
        String w = words == null ? "" : words.trim();
        if (done && !w.isEmpty()) {
            w = w.substring(0, 1).toUpperCase(Locale.getDefault()) + w.substring(1);
        }
        String text = join(before, w);
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