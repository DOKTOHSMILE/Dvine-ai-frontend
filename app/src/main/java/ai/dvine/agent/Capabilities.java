package ai.dvine.agent;

/**
 * What DVINE can do on this phone right now, so it checks before it acts: whether the brain is ready, whether it's
 * online and allowed to look things up, whether a designed body is on show to move. Each task either can be done
 * (null) or comes with the honest reason it can't. Pure Java.
 */
public final class Capabilities {
    public boolean brainReady;
    public boolean online;
    public boolean lookupsAllowed = true;
    public boolean bodyShown;

    /** Null when the task can be done now, else why not, in words to say. */
    public String missing(Interpretation.Task task) {
        switch (task) {
            case SEARCH:
                if (!online) {
                    return "I need the internet for that, and this phone is offline right now.";
                }
                return lookupsAllowed ? null : "You asked me not to look things up online. Say you can look things up online, and I will.";
            case DRAW:
            case BUILD:
                return brainReady || online ? null : "To draw that I need my brain files or the internet.";
            case DEBATE:
                return brainReady ? null : "For a debate, both of us need my brain files on this phone. Say download your brain.";
            case MOVE:
                return bodyShown ? null : "There's no body to move yet. Ask me to build one in 3D first, like build me a robot.";
            case OTHER:
                return "I can't do that on this phone yet.";
            default:
                return null;
        }
    }
}
