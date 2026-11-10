package ai.dvine.dvine1;

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
 * Runs DVINE 1's learning sessions ({@link LearningSchedule}) in the pilot's hours (or now): the job scheduler starts it when the hours
 * opens and the network (Wi-Fi if asked) and charging (if asked) allow; it learns one topic at a time, stops when the
 * hours end or Android asks, writes a report for afterwards, and plans the next session.
 */
public final class LearningJob extends JobService {
    static final int JOB = 7020;
    public static final String PREFS = "dvine";
    public static final String KEY = "night_learn";
    static final String CHANNEL = "night_learning";

    private volatile boolean stopped;
    private volatile String boarded;
    private Thread worker;

    // ------------------------------------------------------------ the pilot's plan

    public static LearningSchedule.Plan plan(Context c) {
        String line = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        return line == null ? LearningSchedule.Plan.defaults() : LearningSchedule.Plan.read(line);
    }

    /** Keeps the plan and schedules (or cancels) the next night. */
    public static void setPlan(Context c, LearningSchedule.Plan p) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, p.write()).apply();
        schedule(c);
    }

    static final int JOB_NOW = 7021;

    /** Learn now, whatever the hours: on the network the pilot allowed, without waiting to charge. */
    public static void learnNow(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) {
            return;
        }
        LearningSchedule.Plan p = plan(c);
        android.os.PersistableBundle now = new android.os.PersistableBundle();
        now.putBoolean("now", true);
        try {
            js.schedule(new JobInfo.Builder(JOB_NOW, new ComponentName(c, LearningJob.class))
                    .setRequiredNetworkType(p.wifiOnly ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
                    .setExtras(now)
                    .build());
        } catch (RuntimeException e) {
            // the scheduler refused (rare)
        }
    }

    /** One job: no earlier than the window's start, on the network and charge the pilot allowed. */
    public static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) {
            return;
        }
        LearningSchedule.Plan p = plan(c);
        if (!p.on) {
            js.cancel(JOB);
            return;
        }
        long wait = Math.max(0, p.untilStart(System.currentTimeMillis(), TimeZone.getDefault()));
        JobInfo.Builder b = new JobInfo.Builder(JOB, new ComponentName(c, LearningJob.class))
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
    public static ResearchAnswer research(Context c, String question) {
        ResearchAnswer r = null;
        String subject = ResearchAnswer.subject(question);
        if (subject != null) {
            r = ResearchAnswer.fromSummary(Plug.web.get(ResearchAnswer.summaryUrl(subject)));
        }
        if (r == null) {
            String found;
            try {
                found = Plug.web.search(question);
            } catch (RuntimeException e) {
                found = null;
            }
            r = ResearchAnswer.from(question, found);
        }
        return r;
    }

    // ------------------------------------------------------------ the night's work

    @Override
    public boolean onStartJob(final JobParameters params) {
        final boolean now = params.getExtras() != null && params.getExtras().getBoolean("now", false);
        final LearningSchedule.Plan p = now ? new LearningSchedule.Plan(true, 0, 0, false, false, plan(this).most) : plan(this);
        if (!p.on) {
            return false;
        }
        if (!now && !p.inWindow(System.currentTimeMillis(), TimeZone.getDefault())) {
            schedule(this); // woke outside the window: wait for tonight's
            return false;
        }
        stopped = false;
        worker = new Thread(() -> {
            String board = Plug.workers.start("Learner", "learning from your questions: facts and words", Dvine1.NAME);
            boarded = board;
            try {
                learnSession(p);
            } finally {
                Plug.workers.end(board, !stopped);
                jobFinished(params, false);
                schedule(LearningJob.this); // the next night
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

    private void learnSession(LearningSchedule.Plan p) {
        TimeZone zone = TimeZone.getDefault();
        final Knowledge k = Store.knowledge(dir(this));
        final Facts facts = Store.facts(dir(this));
        final Lexicon words = Store.lexicon(dir(this));
        final ai.dvine.dvine1.learner.Learner learner = Store.learner(dir(this));
        List<String> unanswered = read(new File(dir(this), "to-learn.txt"));
        List<String> interests = new ArrayList<String>(Plug.interests.get()); // what the pilot is into, from the app
        // only what came from using DVINE: no outside lists of topics
        List<String> topics = LearningSchedule.topics(unanswered, interests,
                t -> k.recall(unanswered.contains(t) ? t : LearningSchedule.question(t)) != null, p.most);
        List<String> learned = new ArrayList<String>();
        List<String> stillToLearn = new ArrayList<String>(unanswered);
        for (String topic : topics) {
            if (stopped || !(p.start == p.end || p.inWindow(System.currentTimeMillis(), zone))) {
                break;
            }
            boolean asked = unanswered.contains(topic);
            if (boarded != null) {
                Plug.workers.step(boarded, "Learning: " + topic);
            }
            String q = asked ? topic : LearningSchedule.question(topic);
            ResearchAnswer r = research(this, q);
            if (r == null || k.rejected(q, r.shown())) {
                continue;
            }
            facts.learn(r.text, ResearchAnswer.subject(q) == null ? topic : ResearchAnswer.subject(q), 2, System.currentTimeMillis());
            learner.read(r.text, ai.dvine.dvine1.learner.Learner.Source.RESEARCH, System.currentTimeMillis());
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
        // words from the pilot's questions DVINE doesn't know yet: their meanings, from Wiktionary
        int newWords = 0;
        for (String w : unknownWords(unanswered, words, 20)) {
            if (stopped || !(p.start == p.end || p.inWindow(System.currentTimeMillis(), zone))) {
                break;
            }
            if (boarded != null) {
                Plug.workers.step(boarded, "Learning the word \u201c" + w + "\u201d");
            }
            String page = Plug.web.get(Lexicon.url(w));
            if (page != null && words.learn(w, page)) {
                newWords++;
                learned.add("the word \u201c" + w + "\u201d");
            }
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                break;
            }
        }
        // the review pass: facts fade and merge, shapes promoted or pruned, one journal paragraph
        if (boarded != null) {
            Plug.workers.step(boarded, "Reviewing what I learned");
        }
        learner.review(System.currentTimeMillis(), 2000);
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

    /** Words from the pilot's questions that DVINE's dictionary doesn't have yet (no small or common words). */
    static List<String> unknownWords(List<String> questions, Lexicon known, int most) {
        java.util.Set<String> common = new java.util.HashSet<String>(java.util.Arrays.asList("what", "which", "when", "where", "who",
                "whom", "whose", "why", "how", "does", "did", "the", "this", "that", "these", "those", "with", "from", "about", "into",
                "your", "have", "been", "were", "they", "there", "their", "them", "then", "than", "will", "would", "could", "should",
                "mean", "means", "tell", "know", "many", "much", "some", "any", "made", "make", "first", "most", "more", "very", "also"));
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
        for (String q : questions) {
            for (String w : q.toLowerCase(java.util.Locale.US).split("[^a-z]+")) {
                if (w.length() >= 5 && !common.contains(w) && !known.knows(w)) {
                    out.add(w);
                }
                if (out.size() >= most) {
                    return new ArrayList<String>(out);
                }
            }
        }
        return new ArrayList<String>(out);
    }

    /** A quiet notification: nothing rings while the pilot may be asleep; it is there afterwards. */
    private void notifyLearned(int n) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Learning", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("What Ibiko learned in a learning session");
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (open == null) {
            open = new Intent();
        }
        PendingIntent tap = PendingIntent.getActivity(this, 4, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification note = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(Plug.icon)
                .setContentTitle("Ibiko learned " + n + (n == 1 ? " new thing" : " new things"))
                .setContentText("Open Ibiko to see what it learned.")
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
