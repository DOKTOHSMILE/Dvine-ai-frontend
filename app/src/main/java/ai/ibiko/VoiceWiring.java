package ai.ibiko;

/**
 * Wires the voice to the chat screen at start: each voice's tuning, the words and level for the stage and the pad,
 * the read-aloud notification, the floating player (play or pause, 15 s back and forward, speed, close) with its
 * loading ring, and the one notice when the voice can't start. Moved out of MainActivity.
 */
final class VoiceWiring {
    private final MainActivity a;
    private ai.ibiko.stage.PlayerBar playerBar;

    VoiceWiring(MainActivity a) {
        this.a = a;
    }

    void wire() {
        a.talk.setTuning(false,
                ai.ibiko.voice.VoiceTuning.parse(a.prefs.getString("voice_obi", null), false));
        a.talk.setTuning(true,
                ai.ibiko.voice.VoiceTuning.parse(a.prefs.getString("voice_ivie", null), true));
        a.talk.setWordListener(a.stage::speechWord);
        a.talk.setListener((ivie, speaking) -> {
            a.ambient.setPersona(ivie);
            a.ambient.setLevel(speaking ? 1f : 0f);
        });
        // reading aloud shows in the notification panel, with Pause and Stop, so it can be controlled outside the app
        a.talk.setStateListener((reading, paused, text, ivie) -> {
            String who = "Ibiko";
            ai.ibiko.voice.ReadAloudNotice.show(a, reading && !a.isDestroyed(), paused, text, who, MainActivity.class);
        });
        // the floating player over the chat while Ibiko reads: play or pause, back and forward 15 s, speed, time, close
        playerBar = new ai.ibiko.stage.PlayerBar(a, new ai.ibiko.stage.PlayerBar.Control() {
            @Override
            public void playPause() {
                if (a.talk.paused()) {
                    a.talk.resume();
                } else {
                    a.talk.pause();
                }
            }

            @Override
            public boolean skip(int seconds) {
                return a.readAloudHelper.skip(seconds, a.talk.playerSpeed());
            }

            @Override
            public void speed(float s) {
                a.talk.setPlayerSpeed(s);
            }

            @Override
            public void close() {
                a.readAloudHelper.stop();
                a.talk.stop();
            }
        });
        android.widget.FrameLayout.LayoutParams pp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
        ((android.widget.FrameLayout) a.findViewById(android.R.id.content)).addView(playerBar, pp);
        a.talk.addStateListener((reading, paused, text, ivie) -> {
            placePlayer();
            playerBar.state(reading, paused, a.readAloudHelper.reading());
        });
        // while the voice gets ready (loading or making the first sentence) a ring turns where play or pause is
        a.talk.addPreparingListener(now -> {
            placePlayer();
            playerBar.loading(now);
        });
        // one voice only: when it can't start, nothing reads in another voice; the pilot is told once, and why
        a.talk.setProblemListener(why -> android.widget.Toast.makeText(a, "The voice couldn't start on this device ("
                + why + "). Settings \u203a Voice has more.", android.widget.Toast.LENGTH_LONG).show());
    }

    /** Puts the floating player just above the quick actions (Tools, Code, Media...) over the message box. */
    private void placePlayer() {
        android.view.View content = a.findViewById(android.R.id.content);
        android.view.View row = a.findViewById(ai.ibiko.R.id.quick_scroll);
        if (row == null || row.getVisibility() != android.view.View.VISIBLE || row.getHeight() == 0) {
            row = a.findViewById(ai.ibiko.R.id.composerBar);
        }
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) playerBar.getLayoutParams();
        int above = 0;
        if (row != null && content != null) {
            int[] c = new int[2];
            int[] r = new int[2];
            content.getLocationInWindow(c);
            row.getLocationInWindow(r);
            above = Math.max(0, c[1] + content.getHeight() - r[1]);
        }
        lp.bottomMargin = above + (int) (6 * a.getResources().getDisplayMetrics().density); // close over the Tools row
        playerBar.setLayoutParams(lp);
    }

    /** The floating player over the chat while a reply is read aloud. */
}
