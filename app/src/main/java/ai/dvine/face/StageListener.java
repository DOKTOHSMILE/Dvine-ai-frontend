package ai.dvine.face;

public interface StageListener {
    void onHeadTap();

    void onMic();

    void onType();

    void onDraft(boolean send);
}
