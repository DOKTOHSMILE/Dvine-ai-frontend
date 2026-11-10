package ai.ibiko.voice;

public interface ListenCallback {
    void onPartial(String text);

    void onResult(String text);

    void onError(String message);

    /** How loud the voice is right now, 0 (silence) to 1 (loud), many times a second, for the recording effect. */
    default void onLevel(float level) {
    }
}
