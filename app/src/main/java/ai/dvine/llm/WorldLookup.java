package ai.dvine.llm;

/** When a world question can be checked online without the language model. */
public final class WorldLookup {
    private WorldLookup() {
    }

    public static boolean shouldSearch(boolean online, boolean modelReady, boolean alreadyAnswered, boolean worthLooking) {
        return online && !modelReady && !alreadyAnswered && worthLooking;
    }
}
