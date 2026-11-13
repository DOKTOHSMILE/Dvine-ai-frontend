package ai.ibiko.agent;

/**
 * How Ibiko works with a repository through its git tool (clone, pull, fetch, merge, sync, push, pr): which action fits
 * which moment, and what to do when one fails, in the words the tool's own messages use ({@code connect/GitHubGit}).
 * Given to the coding agent whenever the git tool is on. Pure Java.
 */
public final class GitPlaybook {
    private GitPlaybook() {
    }

    public static final String RULES = "\nGIT (how to use the git tool well):\n"
            + "G1 Get the code once: clone a repository the first time, into repos/<name>. If repos/<name> is already there,"
            + " never clone it again: pull it (its own branch) or sync it with main to get the latest.\n"
            + "G2 Start fresh: before changing code in a clone, pull (or sync) so you build on the latest, and read git status.\n"
            + "G3 Before every push: git diff to read exactly what will go, run the project's check, then sync with the base"
            + " branch (usually main) so the push isn't refused. Push to a new branch named for the work unless the user"
            + " asked for main; then open a pr (title says what changed, the description says why and how it was checked).\n"
            + "G4 'has newer commits on GitHub; pull first': someone pushed meanwhile. Sync (or pull), fix any conflicts, check"
            + " again, push again. Never try to overwrite their work.\n"
            + "G5 Conflicts: a conflicted file holds both versions, yours under <<<<<<< yours and theirs under >>>>>>>, with a"
            + " .theirs copy beside it. Read both, write the file so both changes are kept as they were meant (not just one"
            + " side), remove every marker line, delete the .theirs file, run the check, then push. Pushing with markers or"
            + " .theirs files left is refused.\n"
            + "G6 'GitHub said 401/403' or 'the token may lack access': the GitHub token can't reach that repository. Stop"
            + " and tell the user to check the GitHub connector's token and its Contents permission; retrying won't help.\n"
            + "G7 'Nothing to commit': no file changed, so there is nothing to push; say so instead of pushing again.\n"
            + "G8 Commit messages: one line saying what changed and why, in the imperative ('Fix the login crash on rotate').\n"
            + "G9 Only say a push landed or a pull request exists when the tool's result says so, with its commit or link.\n";

    /** The same for a small model's short memory. */
    public static final String SHORT = "\nGIT: clone once into repos/<name>, then pull or sync instead; sync with main before"
            + " pushing; 'pull first' means sync and push again; in a conflict keep both changes, remove the <<<<<<< >>>>>>> markers"
            + " and the .theirs file; a 401/403 is the token, tell the user; never claim a push without the tool's result.\n";
}
