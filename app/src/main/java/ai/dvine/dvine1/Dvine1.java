package ai.dvine.dvine1;

import ai.dvine.dvine1.formulas.Commands;
import ai.dvine.dvine1.formulas.Grammar;
import ai.dvine.dvine1.learner.Learner;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DVINE 1 · 0.1, DVINE's own model, in one class the app plugs in like any other model: {@link #answer} takes a
 * message and answers through the {@link Host}. Everything it is lives in this folder: its memory of confirmed answers
 * ({@link Knowledge}), the facts it read ({@link Facts}), its dictionary ({@link Lexicon}), its language formulas
 * ({@link Grammar}), its research ({@link ResearchAnswer}) and its learning sessions ({@link LearningJob}). Remove the
 * folder and the few lines that plug it in, and the app runs on its other models.
 *
 * <p>Why Java and not a native engine: DVINE 1 is rules and lookups that run in microseconds, so it lives in the app's
 * own language with no bridge to cross. A trained network is another matter: {@link Training} writes what DVINE 1
 * learned as a training file, and a model trained from it runs on the device through the app's llama.cpp engine
 * (C++, the fastest way to run a language model on a phone).
 */
public final class Dvine1 {
    public static final String NAME = "DVINE 1 · 0.1";

    /** How an answer came: from memory, from research, or neither (a greeting, a sum). */
    public enum How { MEMORY, RESEARCH, OTHER }

    /** What DVINE 1 needs from the screen it answers on. */
    public interface Host {
        /** The persona answering ("obi"). */
        String persona();

        /** A step shown while it thinks, and the end of the steps. */
        void step(String step);

        void stepsDone();

        /** Its answer in the chat. */
        void say(String persona, String text);

        /** Check and cancel under {@code answer}; none under a set reply; and the state of the buttons. */
        void askRating(String question, String answer);

        void noRating(String text);

        boolean ratingWaiting();

        void clearRating();

        boolean online();

        /** An exact sum for the message ("12 × 7 = 84"), or null. */
        String sum(String message);

        /** What the brain box learned from the other models: an answer, learning one, forgetting one, its sizes. */
        String recallTalk(String question);

        void learnTalk(String question, String answer);

        void forgetTalk(String question);

        int talkSize();

        int shapes();

        /** What the pilot has said about themselves, as lines. */
        List<String> aboutPilot();

        /** One answer, counted in today's use; then the workload ring. */
        void answered(How how);

        void note(String text);

        void later(Runnable r, long ms);

        void onScreen(Runnable r);

        android.content.Context context();

        /** Whether this chat's tools allow a function code ("SR"). */
        default boolean allows(String code) {
            return false;
        }

        /** Carries out a function code line ("§SR weather in Lagos") through the app's gate for every model. */
        default void act(String message, String codeLine) {
        }
    }

    private final Endpoint endpoint = new Endpoint(this);

    /** The door the app talks to DVINE 1 through. */
    public Endpoint endpoint() {
        return endpoint;
    }

    private final Host host;
    private final File dir;
    private Knowledge knowledge;
    private Facts facts;
    private Lexicon lexicon;
    private Learner learner;
    /** Answers are numbered: a newer message makes an older one's steps stop. */
    private int token;
    private String worker;
    private How how = How.OTHER;
    /** The question it last asked "accurate?" about, for a correction typed instead of tapped. */
    private String ratingQuestion;

    public Dvine1(Host host, File filesDir) {
        this.host = host;
        this.dir = new File(filesDir, "brain");
    }

    public synchronized Knowledge knowledge() {
        if (knowledge == null) {
            knowledge = Store.knowledge(dir);
        }
        return knowledge;
    }

    public synchronized Facts facts() {
        if (facts == null) {
            facts = Store.facts(dir);
        }
        return facts;
    }

    public synchronized Lexicon lexicon() {
        if (lexicon == null) {
            lexicon = Store.lexicon(dir);
        }
        return lexicon;
    }

    /** The independent learner: grammar, sentence shapes, the pilot's word on answers, open questions, the score. */
    public synchronized Learner learner() {
        if (learner == null) {
            learner = Store.learner(dir);
        }
        return learner;
    }

    /** The learning score line, read from the files (for screens that don't hold DVINE 1). */
    public static String scoreLine(File filesDir) {
        File d = new File(filesDir, "brain");
        return Store.learner(d).scoreLine();
    }

    /** One of the pilot's messages, to any brain: it may judge DVINE 1's last answer, and it is read for language. */
    public void hear(String said) {
        learner().heard(said, System.currentTimeMillis());
    }

    /** Any model's answer: read for language only (a model's claim is not a fact). */
    public void readReply(String reply) {
        learner().read(reply, Learner.Source.MODEL, System.currentTimeMillis());
    }

    /** People's posts and pages: read for language only. */
    public void readSocial(String text) {
        learner().read(text, Learner.Source.SOCIAL, System.currentTimeMillis());
    }

    private static String cap(String persona) {
        return persona == null || persona.isEmpty() ? "DVINE" : Character.toUpperCase(persona.charAt(0)) + persona.substring(1);
    }

    /**
     * Answers a message: exact sums, a greeting, questions about itself, a statement learned, what the pilot confirmed
     * (asked any way), a word from its dictionary, a fact it read (said by its sentence formula), what an earlier model
     * answered; otherwise it researches, answers in the sources' words and asks whether that was right. Nothing is made
     * up.
     */
    public void answer(final String t) {
        final String who = host.persona();
        if (worker != null) {
            Plug.workers.end(worker, false); // a newer message took over
        }
        worker = Plug.workers.start(cap(who) + " · " + NAME, t, NAME);
        how = How.OTHER;
        Grammar.Parse parse = Grammar.parse(t);
        final String sum = host.sum(t);
        if (sum != null) {
            think(Arrays.asList("Reading your question", "Working it out exactly"), () -> says(who, sum));
            return;
        }
        if (parse.intent == Grammar.Intent.GREETING) {
            think(Arrays.asList("Reading your message"), () -> saysPlain(who, "Hello, I'm " + NAME
                    + ". Ask me anything: I answer what I know, research what I don't, and learn when you tell me Correct or Wrong."));
            return;
        }
        // a clear command: DVINE 1 operates the app like any model, by writing one of its function codes
        final String code = Commands.code(t, host::allows);
        if (code != null) {
            think(Arrays.asList("Reading your message", "That's something the app does: " + code), () -> {
                host.stepsDone();
                if (worker != null) {
                    Plug.workers.end(worker, true);
                    worker = null;
                }
                host.answered(How.OTHER);
                host.act(t, code);
            });
            return;
        }
        final String self = aboutMe(t);
        if (self != null) {
            think(Arrays.asList("Reading your question", "Looking through my memory"), () -> saysPlain(who, self));
            return;
        }
        if (parse.intent == Grammar.Intent.THANKS) {
            think(Arrays.asList("Reading your message"), () -> saysPlain(who, "You're welcome."));
            return;
        }
        if (parse.intent == Grammar.Intent.STATEMENT) {
            // a statement teaches; it isn't researched
            think(Arrays.asList("Reading what you said", "Saving it to memory"), () -> saysPlain(who, learnStatement(t)));
            return;
        }
        final Knowledge.Fact known = knowledge().recall(t);
        if (known != null) {
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Checking what I already know",
                    known.sure ? "Found it: you confirmed this before" : "Found it: I learned this in a learning session"), () -> {
                        if (known.sure) {
                            says(who, known.answer); // confirmed before: no need to ask again
                        } else {
                            says(who, known.answer + "\n(Learned in a learning session; not checked yet.)");
                            rateable(t, known.answer);
                        }
                    });
            return;
        }
        // a word's meaning: from its own dictionary, or learned from Wiktionary now
        final String word = Lexicon.asked(t);
        if (word != null) {
            final Lexicon.Word w = lexicon().get(word);
            if (w != null) {
                how = How.MEMORY;
                think(Arrays.asList("Reading your question", "Looking up “" + word + "” in my dictionary"), () -> {
                    says(who, w.said());
                    rateable(t, w.said());
                });
                return;
            }
        }
        // the language formulas: the question parsed, matched to a fact it read, the answer said as a sentence
        final String formed = facts().answer(t);
        if (formed != null && !knowledge().rejected(t, formed)) {
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Parsing what it asks", "Found it in what I've read",
                    "Putting it into a sentence"), () -> {
                        says(who, formed);
                        rateable(t, formed);
                    });
            return;
        }
        final String learned = host.recallTalk(t);
        if (learned != null && !knowledge().rejected(t, learned)) {
            how = How.MEMORY;
            think(Arrays.asList("Reading your question", "Checking what I already know", "Found an answer an earlier model gave"), () -> {
                says(who, learned + "\n(Learned from an earlier model.)");
                rateable(t, learned);
            });
            return;
        }
        if (!host.online()) {
            LearningJob.remember(host.context(), t);
            learner().unanswered(t, System.currentTimeMillis());
            think(Arrays.asList("Reading your question", "Checking what I already know"), () -> saysPlain(who,
                    "I don't know that yet, and there's no internet connection to research it. I've kept the question:"
                            + " ask me again when you're online" + (LearningJob.plan(host.context()).on
                                    ? ", or I'll learn it in my next learning session." : ".")));
            return;
        }
        final String subject = ResearchAnswer.subject(t);
        how = How.RESEARCH;
        think(Arrays.asList("Reading your question", "Checking what I already know",
                subject != null ? "Not in my memory: looking up “" + subject + "”" : "Not in my memory: searching the web"), () -> {
                    final int mine = token;
                    new Thread(() -> {
                        ResearchAnswer r = null;
                        if (word != null) {
                            // a word it doesn't know yet: its meaning from Wiktionary, kept in the dictionary
                            String page = Plug.web.get(Lexicon.url(word));
                            if (page != null && lexicon().learn(word, page) && lexicon().get(word) != null) {
                                r = ResearchAnswer.of(lexicon().get(word).said(), "en.wiktionary.org");
                            }
                        }
                        if (r == null) {
                            r = LearningJob.research(host.context(), t);
                        }
                        if (r != null) {
                            long now = System.currentTimeMillis();
                            facts().learn(r.text, ResearchAnswer.subject(t), 2, now); // learning while being used
                            learner().read(r.text, Learner.Source.RESEARCH, now);
                        }
                        final ResearchAnswer found = r != null && knowledge().rejected(t, r.shown()) ? null : r;
                        host.onScreen(() -> {
                            if (mine != token) {
                                return; // a newer message took over
                            }
                            if (found == null) {
                                LearningJob.remember(host.context(), t);
                                learner().unanswered(t, System.currentTimeMillis());
                                think(Arrays.asList("Nothing I found answers it"), () -> saysPlain(who,
                                        "I don't know that yet, and what I found didn't answer it. Try asking it another way, or pick an API"
                                                + " or offline model for this one: I learn from their answers too."
                                                + (LearningJob.plan(host.context()).on ? " I've also kept it for my next learning session." : "")),
                                        false);
                                return;
                            }
                            final String shown = found.shown();
                            List<String> more = new ArrayList<String>();
                            more.add("Reading " + (found.sources.isEmpty() ? "what I found" : String.join(", ", found.sources)));
                            more.add("Writing the answer");
                            think(more, () -> {
                                says(who, shown); // one block; the check and cancel ask underneath
                                rateable(t, shown);
                            }, false);
                        });
                    }, "dvine-research").start();
                });
    }

    private void rateable(String question, String answer) {
        ratingQuestion = question;
        learner().answered(question, answer, System.currentTimeMillis());
        host.askRating(question, answer);
    }

    /** It thinks visibly, like the models: its steps show one by one, then it answers. */
    private void think(List<String> steps, Runnable then) {
        think(steps, then, true);
    }

    private void think(final List<String> steps, final Runnable then, boolean fresh) {
        final int mine = fresh ? ++token : token;
        for (int i = 0; i < steps.size(); i++) {
            final String step = steps.get(i);
            host.later(() -> {
                if (mine == token) {
                    host.step(step);
                    if (worker != null) {
                        Plug.workers.step(worker, step);
                    }
                }
            }, 120 + i * 650L);
        }
        host.later(() -> {
            if (mine == token) {
                then.run();
            }
        }, 120 + steps.size() * 650L + 250);
    }

    /** A set reply (a greeting, its memory list, "noted", "I don't know"): no check and cancel, nothing to learn. */
    private void saysPlain(String who, String text) {
        host.noRating(text);
        says(who, text);
    }

    private void says(String who, String text) {
        host.stepsDone();
        if (worker != null) {
            Plug.workers.end(worker, true);
            worker = null;
        }
        host.say(who, text);
        host.answered(how);
    }

    /** Questions about DVINE 1 itself: what it learned, what it knows, who it is. Null for anything else. */
    public String aboutMe(String t) {
        String q = t.toLowerCase(Locale.US);
        // "what do you know about shoes" is a question about shoes, not about its memory
        if (q.matches("(?s).*\\bwhat (have|did) you (learn|learned|learnt)\\b(?!.*\\babout\\b).*"
                + "|.*\\bwhat do you (know|remember)\\b(?!.*\\babout\\b).*|.*\\bwhat have you (remembered|saved|kept)\\b(?!.*\\babout\\b).*")) {
            List<Knowledge.Fact> sure = knowledge().confirmed();
            List<String> me = host.aboutPilot();
            StringBuilder b = new StringBuilder();
            b.append(sure.isEmpty() ? "No answers confirmed yet: tap the check under an answer that's right and I keep it."
                    : "Answers you confirmed (" + sure.size() + "):");
            for (int i = sure.size() - 1, n = 0; i >= 0 && n < 8; i--, n++) {
                Knowledge.Fact f = sure.get(i);
                String a = f.answer.replaceAll("\\s+", " ");
                b.append("\n- ").append(f.question).append(" → ").append(a.length() > 90 ? a.substring(0, 90) + "…" : a);
            }
            if (!me.isEmpty()) {
                b.append("\nAbout you: ").append(String.join("; ", me)).append('.');
            }
            b.append("\nRead and understood: ").append(facts().size()).append(facts().size() == 1 ? " fact, " : " facts, ")
                    .append(lexicon().size()).append(lexicon().size() == 1 ? " word." : " words.");
            b.append("\n").append(learner().scoreLine()).append('.');
            int talk = host.talkSize(), shapes = host.shapes();
            if (talk + shapes > 0) {
                b.append("\nFrom the models you used: ").append(talk).append(talk == 1 ? " answer" : " answers").append(" and ")
                        .append(shapes).append(shapes == 1 ? " action shape." : " action shapes.");
            }
            return b.toString();
        }
        if (q.matches("(?s)\\s*(who|what) are you\\??\\s*|.*\\bwhat can you do\\b.*")) {
            return "I'm " + NAME + ", DVINE's own brain, running on this device with no model loaded. I work out sums exactly,"
                    + " answer what you've confirmed before however it's asked, research what I don't know and learn from your"
                    + " check and cancel. For open-ended writing or reasoning, pick an API or offline model: I learn from them too.";
        }
        return null;
    }

    /**
     * A statement: right after an answer it asked about, it is taken as the right answer to that question; "a pig is
     * an animal" on its own is kept as what a pig is. Nothing is searched.
     */
    public String learnStatement(String t) {
        String said = t.trim();
        if (ratingQuestion != null && host.ratingWaiting()) {
            String q = ratingQuestion;
            ratingQuestion = null;
            host.clearRating();
            knowledge().confirm(q, said);
            host.learnTalk(q, said);
            return "Got it. When you ask “" + q + "”, I'll answer: " + said;
        }
        Matcher m = Pattern.compile("(?i)^(?:(the|a|an) )?(.{2,60}?) (is|are|was|were) (.{2,300}?)[.!]*$").matcher(said);
        if (m.matches()) {
            String subject = (m.group(1) == null ? "" : m.group(1) + " ") + m.group(2);
            String verb = m.group(3).toLowerCase(Locale.US);
            knowledge().confirm("What " + verb + " " + subject + "?", said);
            facts().learn(said, null, 2, System.currentTimeMillis()); // the pilot's own words: as good as a source
            if (Character.isUpperCase(m.group(2).charAt(0)) && m.group(1) == null) {
                knowledge().confirm("Who " + verb + " " + subject + "?", said); // a name: kept for "who" too
            }
            return "Noted. Ask me what " + verb + " " + subject + " and I'll remember.";
        }
        return "Noted.";
    }

    /**
     * The pilot marked an answer (from any model): right keeps it for its intent, wrong marks it so it is never given
     * again and keeps the question for the next learning session.
     */
    public void rate(String question, String answer, boolean right) {
        if (question == null || answer == null) {
            return;
        }
        learner().judged(right, System.currentTimeMillis());
        if (right) {
            knowledge().confirm(question, answer);
            host.learnTalk(question, answer);
            host.note("Saved to DVINE's memory: it will answer this, however it's asked.");
        } else {
            knowledge().reject(question, answer);
            host.forgetTalk(question);
            LearningJob.remember(host.context(), question);
            host.note("Got it: DVINE won't give that answer again.");
        }
    }

    /** What the last learning session learned, said once afterwards; null when there is nothing new. */
    public String report() {
        if (LearningJob.reportSeen(host.context())) {
            return null;
        }
        List<String> learned = LearningJob.report(host.context());
        LearningJob.markReportSeen(host.context());
        if (learned.isEmpty()) {
            return null;
        }
        StringBuilder b = new StringBuilder("In my last learning session I learned " + learned.size()
                + (learned.size() == 1 ? " new thing" : " new things") + ":");
        for (int i = 0; i < learned.size() && i < 12; i++) {
            b.append("\n- ").append(learned.get(i));
        }
        if (learned.size() > 12) {
            b.append("\n…and ").append(learned.size() - 12).append(" more.");
        }
        b.append("\nAsk me about any of them; tap the check when I get one right.");
        return b.toString();
    }

    /** Something the pilot asked any model about: learned in the next session if DVINE 1 doesn't know it yet. */
    public void noticeQuestion(String said) {
        if (ResearchAnswer.subject(said) != null && knowledge().recall(said) == null) {
            LearningJob.remember(host.context(), said);
        }
    }
}
