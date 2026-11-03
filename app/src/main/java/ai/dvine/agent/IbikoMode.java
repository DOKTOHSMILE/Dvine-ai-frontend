package ai.dvine.agent;

import android.content.SharedPreferences;

/**
 * How Ibiko works, as in Claude Code: {@link #AUTO} runs everything without asking; {@link #EDITS} changes files
 * without asking but asks before commands, pushes and pull requests; {@link #PLAN} only reads and writes a plan, then
 * asks before carrying it out. Kept as two settings so the older "auto_accept" switch still means the same thing.
 */
public enum IbikoMode {
    AUTO("Auto", "Runs commands, pushes and pull requests without asking"),
    EDITS("Accept edits", "Changes files without asking; asks before commands and pushes"),
    PLAN("Plan", "Reads and makes a plan before making changes");

    static final String AUTO_ACCEPT = "auto_accept", PLANNING = "ibiko_plan";

    public final String label, about;

    IbikoMode(String label, String about) {
        this.label = label;
        this.about = about;
    }

    public static IbikoMode of(SharedPreferences prefs) {
        if (prefs == null) {
            return EDITS;
        }
        if (prefs.getBoolean(PLANNING, false)) {
            return PLAN;
        }
        return prefs.getBoolean(AUTO_ACCEPT, false) ? AUTO : EDITS;
    }

    /** Sets the mode; Plan keeps the auto-accept choice for when the plan is carried out. */
    public static void set(SharedPreferences prefs, IbikoMode m) {
        SharedPreferences.Editor e = prefs.edit().putBoolean(PLANNING, m == PLAN);
        if (m != PLAN) {
            e.putBoolean(AUTO_ACCEPT, m == AUTO);
        }
        e.apply();
    }

    /** What Ibiko is told in plan mode, ahead of its usual instructions. */
    public static final String PLAN_RULES = "Plan mode: you change nothing yet. Read the files you need, search and fetch,"
            + " then give the plan inside <final>: what you found, numbered steps, the files you will create or change and"
            + " how, the commands you will run, and how you will check it works. The user approves it before any change.";
}
