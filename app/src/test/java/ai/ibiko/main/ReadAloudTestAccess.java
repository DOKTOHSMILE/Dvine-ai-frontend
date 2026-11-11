package ai.ibiko.main;

/** Lets tests in other packages reach ReadAloud's skip arithmetic. */
public final class ReadAloudTestAccess {
    private ReadAloudTestAccess() {
    }

    public static int skipTo(java.util.List<String> sentences, int at, int seconds, float speed) {
        return ReadAloud.skipTo(sentences, at, seconds, speed);
    }
}
