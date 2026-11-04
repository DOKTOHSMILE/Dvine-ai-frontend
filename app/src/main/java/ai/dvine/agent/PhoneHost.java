package ai.dvine.agent;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Handler;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import ai.dvine.connect.ConnectorStore;
import ai.dvine.connect.Connectors;
import ai.dvine.net.WebGet;

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
                    .setPositiveButton("Run", (d, w) -> {
                        ok[0] = true;
                        done.countDown();
                    })
                    .setNeutralButton("Always run", (d, w) -> {
                        prefs.edit().putBoolean("auto_accept", true).apply();
                        if (onAutoAccept != null) {
                            onAutoAccept.run();
                        }
                        ok[0] = true;
                        done.countDown();
                    })
                    .setNegativeButton("Don't run", (d, w) -> done.countDown())
                    .setOnCancelListener(d -> done.countDown())
                    .show();
        });
        try {
            done.await(3, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            return false;
        }
        return ok[0];
    }

    /** The real file, PDF included (drawn with Android's PdfDocument). */
    @Override
    public String makeFile(File f, String markdown) throws java.io.IOException {
        ai.dvine.act.FileMaker.Format fmt = ai.dvine.act.FileMaker.of(f.getName());
        byte[] bytes = ai.dvine.act.FileMaker.any(fmt, f.getName().replaceAll("\\.[^.]+$", ""), markdown);
        java.nio.file.Files.write(f.toPath(), bytes);
        return fmt.name() + ", " + (bytes.length < 1024 ? bytes.length + " bytes" : bytes.length / 1024 + " KB");
    }

    @Override
    public String run(String command, File dir) {
        return SandboxShell.run(command, dir, 30_000, 8000);
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

    /** Where the model's notes between the steps go (the chat); without it they are dropped. */
    public PhoneHost onNote(Consumer<String> to) {
        onNote = to;
        return this;
    }

    @Override
    public void note(String text) {
        main.post(() -> {
            if (!stopped() && onNote != null) {
                onNote.accept(text);
            }
        });
    }

    @Override
    public boolean stopped() {
        return id != current.getAsInt();
    }
}
