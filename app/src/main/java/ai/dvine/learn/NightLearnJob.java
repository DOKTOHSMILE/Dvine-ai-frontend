package ai.dvine.learn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Runs night learning ({@link NightLearn}) in the pilot's window: the device's job scheduler starts it when the window
 * opens and the network (Wi-Fi if asked) and charging (if asked) allow; it learns one topic at a time, stops when the
 * window closes or Android asks, writes a morning report, and plans the next night.
 */
public final class NightLearnJob extends JobService {
    static final int JOB = 7020;
    public static final String PREFS = "dvine";
    public static final String KEY = "night_learn";
    static final String CHANNEL = "night_learning";

    private volatile boolean stopped;
    private volatile String boarded;
    private Thread worker;

    // ------------------------------------------------------------ the pilot's plan

    public static NightLearn.Plan plan(Context c) {
        String line = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        return line == null ? NightLearn.Plan.defaults() : NightLearn.Plan.read(line);
    }

    /** Keeps the plan and schedules (or cancels) the next night. */
    public static void setPlan(Context c, NightLearn.Plan p) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, p.write()).apply();
        schedule(c);
    }

    /** One job: no earlier than the window's start, on the network and charge the pilot allowed. */
    public static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) {
            return;
        }
        NightLearn.Plan p = plan(c);
        if (!p.on) {
            js.cancel(JOB);
            return;
        }
        long wait = Math.max(0, p.untilStart(System.currentTimeMillis(), TimeZone.getDefault()));
        JobInfo.Builder b = new JobInfo.Builder(JOB, new ComponentName(c, NightLearnJob.class))
                .setMinimumLatency(wait)
                .setRequiredNetworkType(p.wifiOnly ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
                .setRequiresCharging(p.chargingOnly)
                .setPersisted(true);
        try {
            js.schedule(b.build());
        } catch (RuntimeException e) {
            // the scheduler refused (rare): the next app start schedules again
        }
    }

    // ------------------------------------------------------------ what to learn

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "brain");
        d.mkdirs();
        return d;
    }

    /** A question DVINE 1 couldn't answer (or got wrong) today: it is learned first tonight. */
    public static synchronized void remember(Context c, String question) {
        String q = question == null ? "" : question.trim().replace('\n', ' ');
        if (q.length() < 3 || q.length() > 200) {
            return;
        }
        List<String> all = read(new File(dir(c), "to-learn.txt"));
        all.remove(q);
        all.add(q);
        while (all.size() > 200) {
            all.remove(0);
        }
        write(new File(dir(c), "to-learn.txt"), all);
    }

    static List<String> read(File f) {
        try {
            return f.isFile() ? new ArrayList<String>(Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) : new ArrayList<String>();
        } catch (IOException e) {
            return new ArrayList<String>();
        }
    }

    static void write(File f, List<String> lines) {
        try {
            Files.write(f.toPath(), (String.join("\n", lines) + (lines.isEmpty() ? "" : "\n")).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // tried again tomorrow
        }
    }

    /** What the last night learned ("topic" lines), and whether the pilot has seen it. */
    public static List<String> report(Context c) {
        return read(new File(dir(c), "night-report.txt"));
    }

    public static boolean reportSeen(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("night_report_seen", true);
    }

    public static void markReportSeen(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("night_report_seen", true).apply();
    }

    /**
     * DVINE 1's research for one question: the subject's own encyclopedia summary for "what/who is X", else the web
     * search's sentences that answer it. Blocking; off the main thread.
     */
    public static ai.dvine.suit.ResearchAnswer research(Context c, String question) {
        ai.dvine.suit.ResearchAnswer r = null;
        String subject = ai.dvine.suit.ResearchAnswer.subject(question);
        if (subject != null) {
            ai.dvine.net.WebGet.Page page = ai.dvine.net.WebGet.page(ai.dvine.suit.ResearchAnswer.summaryUrl(subject), 12_000, 200_000);
            r = page.status == 200 ? ai.dvine.suit.ResearchAnswer.fromSummary(page.body) : null;
        }
        if (r == null) {
            String found;
            try {
                found = new ai.dvine.connect.ConnectorStore(c).research(question, 2, 8000);
            } catch (RuntimeException e) {
                found = null;
            }
            r = ai.dvine.suit.ResearchAnswer.from(question, found);
        }
        return r;
    }

    // ------------------------------------------------------------ the night's work

    @Override
    public boolean onStartJob(final JobParameters params) {
        final NightLearn.Plan p = plan(this);
        if (!p.on) {
            return false;
        }
        if (!p.inWindow(System.currentTimeMillis(), TimeZone.getDefault())) {
            schedule(this); // woke outside the window: wait for tonight's
            return false;
        }
        stopped = false;
        worker = new Thread(() -> {
            String board = ai.dvine.agent.AgentBoard.start("Night learner", "learning from today's questions while you sleep", "DVINE 1 \u00B7 0.1");
            boarded = board;
            try {
                learnTonight(p);
            } finally {
                ai.dvine.agent.AgentBoard.end(board, stopped ? ai.dvine.agent.AgentBoard.Outcome.STOPPED : ai.dvine.agent.AgentBoard.Outcome.DONE);
                jobFinished(params, false);
                schedule(NightLearnJob.this); // the next night
            }
        }, "dvine-night-learning");
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stopped = true; // Android needs the device back (or Wi-Fi/charging went): what was learned is kept
        return true; // try again while the window is open
    }

    private void learnTonight(NightLearn.Plan p) {
        TimeZone zone = TimeZone.getDefault();
        final Knowledge k = new Knowledge(new File(dir(this), "knowledge.tsv"));
        List<String> unanswered = read(new File(dir(this), "to-learn.txt"));
        PilotProfile profile = new PilotProfile(new File(dir(this), "pilot.tsv"));
        List<String> interests = new ArrayList<String>();
        for (String key : new String[] { "building", "likes", "works as", "works at", "home", "from" }) {
            String v = profile.get(key);
            if (v != null) {
                interests.add(v);
            }
        }
        // only what came from using DVINE: no outside lists of topics
        List<String> topics = NightLearn.topics(unanswered, interests,
                t -> k.recall(unanswered.contains(t) ? t : NightLearn.question(t)) != null, p.most);
        List<String> learned = new ArrayList<String>();
        List<String> stillToLearn = new ArrayList<String>(unanswered);
        for (String topic : topics) {
            if (stopped || !p.inWindow(System.currentTimeMillis(), zone)) {
                break;
            }
            boolean asked = unanswered.contains(topic);
            if (boarded != null) {
                ai.dvine.agent.AgentBoard.step(boarded, "Learning: " + topic);
            }
            String q = asked ? topic : NightLearn.question(topic);
            ai.dvine.suit.ResearchAnswer r = research(this, q);
            if (r == null || k.rejected(q, r.shown())) {
                continue;
            }
            if (k.learnUnchecked(q, r.shown())) {
                if (!asked && topic.matches("[A-Z][\\p{L}'-]+( [A-Z][\\p{L}'-]+)+")) {
                    k.learnUnchecked("Who is " + topic + "?", r.shown()); // a name: asked about with "who" too
                }
                learned.add(topic);
            }
            stillToLearn.remove(topic);
            try {
                Thread.sleep(1500); // gently: one page at a time, a pause between
            } catch (InterruptedException e) {
                break;
            }
        }
        write(new File(dir(this), "to-learn.txt"), stillToLearn);
        if (!learned.isEmpty()) {
            List<String> report = new ArrayList<String>(report(this));
            if (reportSeen(this)) {
                report.clear(); // a new night's report
            }
            report.addAll(learned);
            write(new File(dir(this), "night-report.txt"), report);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("night_report_seen", false).apply();
            notifyLearned(report.size());
        }
    }

    /** A quiet notification: nothing rings at night; it is there in the morning. */
    private void notifyLearned(int n) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Night learning", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("What DVINE learned while you slept");
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (open == null) {
            open = new Intent();
        }
        PendingIntent tap = PendingIntent.getActivity(this, 4, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification note = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(ai.dvine.R.drawable.ic_send)
                .setContentTitle("DVINE learned " + n + (n == 1 ? " new thing" : " new things") + " overnight")
                .setContentText("Open DVINE to see what it learned.")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(JOB, note);
        } catch (SecurityException noPermission) {
            // shown in the chat in the morning all the same
        }
    }
}
