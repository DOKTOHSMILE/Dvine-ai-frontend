package ai.dvine.voice;

public interface ListenCallback {
    void onPartial(String text);

    void onResult(String text);

    void onError(String message);
}
