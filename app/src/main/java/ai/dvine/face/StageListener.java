package ai.dvine.face;

/** The only inputs the stage has. There are no buttons. */
public interface StageListener {
    /** Tap on the face: the persona on screen speaks. */
    void onHeadTap();

    /** Swipe anywhere: microphone on or off. */
    void onMic();

    /** Long-press anywhere (accessibility): silent typing. */
    void onType();

    /** Tap on the full body: which part was touched ("left hand", "chest", "face"…). */
    default void onBodyTap(String part) {
        onHeadTap();
    }
}
