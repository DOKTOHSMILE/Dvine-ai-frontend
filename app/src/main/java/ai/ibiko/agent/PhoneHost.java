package ai.ibiko.agent;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import ai.ibiko.connect.ConnectorStore;
import ai.ibiko.connect.Connectors;
import ai.ibiko.net.WebGet;

/**
 * What the phone does for Ibiko's agent: asks before a command, a push or a pull request (Run / Always run / Don't
 * run, unless Auto-accept is on), runs commands in the workspace, fetches pages, runs connector tools, and reports
 * each step. One per task; it stops answering once the user has moved on ({@code current} no longer matches).
 */
public final class PhoneHost implements CodeAgent.Host {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Handler main;
    private final ConnectorStore connectors;
    private final int id;
    private final IntSupplier current;
    private final Consumer<String> onStep;
    private final Runnable onAutoAccept;

    public PhoneHost(Activity activity, SharedPreferences prefs, Handler main, ConnectorStore connectors, int id, IntSupplier current,
            Consumer<String> onStep, Runnable onAutoAccept) {
        this.activity = activity;
        this.prefs = prefs;
        this.main = main;
        this.connectors = connectors;
        this.id = id;
        this.current = current;
        this.onStep = onStep;
        this.onAutoAccept = onAutoAccept;
    }

    @Override
    public boolean allowRun(String command) {
        if (prefs.getBoolean("auto_accept", false)) {
            if (!toldAuto) {
                toldAuto = true; // said once a task: nothing is asked because the pilot turned asking off
                if (onStep != null) {
                    onStep.accept("Going ahead without asking: Run commands without asking is on (Settings \u203a Coding)");
                }
            }
            return true;
        }
        if (allowThisTask) {
            return true;
        }
        synchronized (ASKING) {
            // one question at a time: helpers working at once never stack dialogs on each other
            if (allowThisTask) {
                return true;
            }
            final CountDownLatch done = new CountDownLatch(1);
            final boolean[] ok = {false};
            main.post(() -> {
                if (activity.isFinishing()) {
                    done.countDown();
                    return;
                }
                new androidx.appcompat.app.AlertDialog.Builder(activity)
                        .setTitle("Ibiko wants to go ahead")
                        .setMessage(command + "\n\nIt runs in Ibiko's project folder on this device.")
                        .setPositiveButton("Allow", (d, w) -> {
                            ok[0] = true;
                            done.countDown();
                        })
                        .setNeutralButton("Allow for this task", (d, w) -> {
                            allowThisTask = true; // until this task ends; the next one asks again
                            ok[0] = true;
                            done.countDown();
                        })
                        .setNegativeButton("Don't allow", (d, w) -> done.countDown())
                        .setOnCancelListener(d -> done.countDown())
                        .show();
            });
            try {
                if (!done.await(3, TimeUnit.MINUTES)) {
                    return false; // no answer: not done
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            return ok[0];
        }
    }

    /** One approval dialog at a time across every task and helper. */
    private static final Object ASKING = new Object();
    /** "Allow for this task" was tapped: the rest of this task goes ahead without asking. */
    private volatile boolean allowThisTask;
    private volatile boolean toldAuto;

    /** The real file, PDF included (drawn with Android's PdfDocument). */
    @Override
    public String makeFile(File f, String markdown) throws java.io.IOException {
        ai.ibiko.act.FileMaker.Format fmt = ai.ibiko.act.FileMaker.of(f.getName());
        byte[] bytes = ai.ibiko.act.FileMaker.any(fmt, f.getName().replaceAll("\\.[^.]+$", ""), markdown);
        java.nio.file.Files.write(f.toPath(), bytes);
        return fmt.name() + ", " + (bytes.length < 1024 ? bytes.length + " bytes" : bytes.length / 1024 + " KB");
    }

    @Override
    public String run(String command, File dir) {
        try {
            onCommand(command);
            File where = dir != null ? dir : activity instanceof ai.ibiko.MainActivity
                    ? ((ai.ibiko.MainActivity) activity).chatSpace() : new File(activity.getFilesDir(), "ibiko");
            where.mkdirs();
            return ai.ibiko.linux.LinuxHub.run(activity, command, where); // the one Alpine, the same as the Shell screen
        } catch (Throwable e) {
            return "The shell stopped before it could run that.";
        }
    }

    @Override
    public boolean termuxReady() {
        return ai.ibiko.linux.AlpineRun.ready(activity);
    }

    @Override
    public String termux(String command, File folder) {
        return run(command, folder); // there is one Linux: "termux" commands run in it too
    }

    @Override
    public String fetch(String url) {
        WebGet.Page p = WebGet.page(url, 15_000, 1_000_000);
        if (p.body == null) {
            return "The page couldn't be fetched: " + p.why + ".";
        }
        String b = p.body;
        return b.trim().startsWith("<") ? SandboxShell.text(b) : b; // a script or JSON stays as it is
    }

    /** The raw text (an index file, a page's HTML with its anchors), or null when it couldn't be fetched. */
    @Override
    public String fetchRaw(String url, int maxBytes) {
        WebGet.Page p = WebGet.page(url, 30_000, maxBytes);
        return p.body == null || p.status >= 400 ? null : p.body;
    }

    @Override
    public String connector(String call) {
        String[] c = Connectors.parseCall(call);
        return c == null ? "No tool named." : connectors.run(c[0], c[1], null);
    }

    @Override
    public void step(String line) {
        main.post(() -> {
            if (!stopped() && onStep != null) {
                onStep.accept(line);
            }
        });
    }

    /**
     * A step with something to open: the detail is kept in a small file on the phone, and the step line carries
     * its name ("Edited app.py ⟦…⟧"), so it can be opened from the chat now and later.
     */
    @Override
    public void step(String line, String title, java.util.List<String> detail, int added, int removed) {
        String keep = StepDetail.save(activity.getFilesDir(), title, detail, added, removed);
        step(keep == null ? line : line + StepDetail.mark(keep));
    }

    private Consumer<String> onNote;
    private Consumer<String> onCommand;
    private java.util.function.Supplier<String> steerFrom;

    /** Where the model's notes between the steps go (the chat); without it they are dropped. */
    public PhoneHost onNote(Consumer<String> to) {
        onNote = to;
        return this;
    }

    /** Every command line as the agent dispatches it, so a session can show what ran in it. */
    public PhoneHost onCommand(Consumer<String> to) {
        onCommand = to;
        return this;
    }

    public void onCommand(String command) {
        main.post(() -> {
            if (!stopped() && onCommand != null) {
                onCommand.accept(command);
            }
        });
    }

    /** Messages the user sends while this task runs. Taken once, before the next step. */
    public PhoneHost onSteer(java.util.function.Supplier<String> from) {
        steerFrom = from;
        return this;
    }

    @Override
    public String steer() {
        if (steerFrom == null) {
            return null;
        }
        try {
            return steerFrom.get();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Override
    public void note(String text) {
        main.post(() -> {
            if (!stopped() && onNote != null) {
                onNote.accept(text);
            }
        });
    }

    /** What a step looked for and found, in the chat between the steps (Settings › Ibiko › Say what each step found). */
    @Override
    public void found(String line) {
        if (prefs != null && !prefs.getBoolean("narrate_steps", true)) {
            return;
        }
        step(line); // stays with the steps. A chat bubble here looked like the reply had finished.
    }

    @Override
    public boolean stopped() {
        return id != current.getAsInt();
    }
}
