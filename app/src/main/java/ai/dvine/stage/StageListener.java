package ai.dvine.stage;

/** The only inputs the stage has. There are no buttons. */
public interface StageListener {
    /** A tap on the pod: the persona speaking says hello. */
    void onHeadTap();

    /** Touch and hold anywhere: the microphone opens while the finger stays down. */
    void onMic();

    /** The finger lifts after holding: what was said is sent. */
    default void onMicRelease() {
    }

    /** Double-tap anywhere: the text field, to type instead. */
    void onType();

    /** A row in the part tree: "undo" or "redo". */
    default void onHistory(String action) {
    }

    /** A part row was tapped. The part is selected, not moved. */
    default void onPart(String label) {
    }
}
