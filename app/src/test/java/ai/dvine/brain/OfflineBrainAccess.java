package ai.dvine.brain;

/** Test access to the offline brain's package-private helpers. */
public final class OfflineBrainAccess {
    private OfflineBrainAccess() {
    }

    public static String pose(String said) {
        return OfflineBrain.posePhrase(said);
    }
}
