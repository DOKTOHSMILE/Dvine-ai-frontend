package ai.ibiko.agent;

import java.util.Locale;

/**
 * Shipping work to a repository batch by batch: split the job into small batches that each build on their own, and
 * for each one change, check, commit and push, so the repository grows step by step and never holds broken work. One
 * pull request carries the branch; every batch updates it. Given to the coding agent when the task is to build, ship,
 * push or release to a repository (S1–S9). Pure Java.
 */
public final class Shipping {
    private Shipping() {
    }

    /** Work that ends in a repository: build and push, ship, release, commit, open a pull request, in batches. */
    public static boolean applies(String task) {
        String t = task == null ? "" : task.toLowerCase(Locale.US);
        return t.matches("(?s).*\\b(ship|push|commit|pull request|\\bpr\\b|release|deploy|batch(es)?|merge"
                + "|build (it|this|the app|the project|and push)|to (the |my )?(git ?hub|repo|repository))\\b.*");
    }

    public static final String RULES = "\nSHIP IN BATCHES (this task ends in a repository; S1–S9):\n"
            + "S1 Plan the batches first: split the work into small batches, each one change that builds and works on its"
            + " own (a fix, a feature slice, its tests, its docs). Write them as a checklist in the project's plan file"
            + " (PLAN.md, or the .blueprint plan) before the first change.\n"
            + "S2 Branch: never push to main unless the user said so. The first push makes a branch for this work"
            + " (git push with branch=\"ibiko/<short-name>\"); every later batch goes to the same branch.\n"
            + "S3 Each batch, in this order: make the change → run the project's build and tests → read the diff (git"
            + " diff) → git push with a clear message → tick the batch in the plan. Then the next batch.\n"
            + "S4 Never push red: if the build or a test fails, fix it in the same batch and check again. If it can't be"
            + " fixed, stop, say what fails and why, and push nothing broken.\n"
            + "S5 Commit messages: one line saying what changed and why, then short lines for the details. One batch, one"
            + " commit.\n"
            + "S6 After the first push, open one pull request for the branch (git action=\"pr\") with what it does and how"
            + " it was checked. Later pushes update the same pull request; don't open another.\n"
            + "S7 Keep the user's version and docs rules when the project has them (version numbers, changelog, the plan"
            + " file) in the batch that needs them.\n"
            + "S8 A long job carries on in rounds: what is shipped stays shipped; the plan shows the next batch, so"
            + " \"continue\" picks up there.\n"
            + "S9 At the end: each batch shipped with its commit (quote the push result), the pull request link, what was"
            + " checked, and what is left.\n";
}
