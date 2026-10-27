package ai.dvine.engine;

import ai.dvine.motion.Motions;
import ai.dvine.render.forms.Forms;

/**
 * The skill registry: every action the stage knows (drawings, and the movement engine's moves), its channel, whether it
 * can be interrupted and how long it holds its channel. Requests for anything
 * else, or with a target the skill does not know, are rejected here.
 */
public final class Skills {
    private Skills() {
    }

    /** Holds the channel until another command replaces it. */
    static final long UNTIL_REPLACED = -1L;

    static Command.Channel channel(String action) {
        switch (action) {
            case "body.pose":
            case "body.scene":
            case "body.turn":
            case "body.redraw":
            case "body.stop":
                return Command.Channel.BODY;
            case "draw.sketch":
            case "draw.object":
            case "draw.clear":
                return Command.Channel.DRAW;
            default:
                return null;
        }
    }

    /** Null when the command is valid, else why not. */
    static String invalid(Command c) {
        if (channel(c.action) == null) {
            return "unknown skill";
        }
        switch (c.action) {
            case "body.pose":
                return known(Motions.POSES, c.value) ? null : "unknown pose";
            case "body.scene":
                if (c.poses == null || c.seconds == null || c.poses.length == 0 || c.poses.length != c.seconds.length) {
                    return "bad scene";
                }
                for (String p : c.poses) {
                    if (!known(Motions.POSES, p)) {
                        return "unknown pose in scene";
                    }
                }
                return null;
            case "draw.object":
                return known(Forms.OBJECTS, c.value) ? null : "unknown object";
            case "draw.sketch":
                return c.payload instanceof ai.dvine.stage.Sketch ? null : "no sketch";
            default:
                return null;
        }
    }

    /** Whether something of lower priority may cut this command short. */
    static boolean interruptible(Command c) {
        // scenes and stunts in mid-air finish unless the user (or a stop) asks otherwise
        return !c.action.equals("body.scene");
    }

    /** How long the command holds its channel, in ms. */
    static long duration(Command c) {
        switch (c.action) {
            case "body.pose":
                return UNTIL_REPLACED;
            case "body.scene": {
                float s = 0f;
                for (float x : c.seconds) {
                    s += Math.max(0.5f, x);
                }
                return (long) (s * 1000f);
            }
            case "draw.object":
                return 22000L;
            case "draw.sketch":
                return (long) ((((ai.dvine.stage.Sketch) c.payload).length + 18f) * 1000f);
            default:
                return 0L;
        }
    }

    private static boolean known(String[] list, String value) {
        if (value == null) {
            return false;
        }
        for (String s : list) {
            if (s.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
