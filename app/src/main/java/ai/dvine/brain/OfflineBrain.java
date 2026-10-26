package ai.dvine.brain;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.dvine.turn.LocalTurn;
import ai.dvine.turn.TurnLine;

/**
 * Answers without the internet, from the knowledge pack plus what the user
 * taught on this phone. Never performs side effects. When it does not know,
 * it says so and asks for a connection instead of guessing.
 */
public final class OfflineBrain {
    public interface Clock {
        long now();
    }

    private static final Pattern TEACH = Pattern.compile(
            "^(?:please )?(?:remember|note|know)(?: that)? (my|our) (.+?) (?:is|are|was) (.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATE = Pattern.compile("^(my|our) (.+?) (?:is|are) (.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECALL = Pattern.compile(
            "^(?:do you (?:know|remember) )?(?:what|who|where|when)(?:'s| is| are| was)? (my|our) (.+?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CORRECT = Pattern.compile("^no[, ]+(?:it'?s|it is|they are|the answer is) (.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNIT = Pattern.compile(
            "(-?\\d+(?:\\.\\d+)?) ?([a-z°]+(?: [a-z]+)?) (?:to|in|into) ([a-z°]+)");
    private static final Pattern DEFINE = Pattern.compile(
            "^(?:what does (.+?) mean|define (.+)|meaning of (.+)|what is the meaning of (.+)|what is an? (.+)|what are (.+))$");
    private static final Set<String> STOP = new HashSet<String>();

    static {
        String[] w = {"a", "an", "the", "is", "are", "was", "were", "of", "in", "on", "to", "what", "who", "which",
                "where", "when", "how", "do", "does", "did", "tell", "me", "about", "please", "can", "you", "i",
                "and", "for", "it", "that", "this", "with", "by", "be", "s", "whats", "many", "much"};
        for (String s : w) {
            STOP.add(s);
        }
    }

    private KnowledgePack pack;
    private final LearningJournal journal;
    private final Clock clock;
    private final TimeZone zone;
    private int turn;
    private String lastKey;
    private final ActionPlanner planner = new ActionPlanner();
    private ai.dvine.face.Look obiLook = ai.dvine.face.Look.defaults(false);
    private ai.dvine.face.Look ivieLook = ai.dvine.face.Look.defaults(true);
    private String userName;
    private String lastSaid;

    public OfflineBrain(KnowledgePack pack, LearningJournal journal, Clock clock, TimeZone zone) {
        this.pack = pack;
        this.journal = journal;
        this.clock = clock;
        this.zone = zone;
    }

    public synchronized void replacePack(KnowledgePack next) {
        if (next != null && next.version >= pack.version) {
            pack = next;
        }
    }

    public synchronized KnowledgePack pack() {
        return pack;
    }

    public synchronized void setUserName(String name) {
        userName = name;
    }

    public synchronized void setLooks(ai.dvine.face.Look obi, ai.dvine.face.Look ivie) {
        obiLook = obi.copy();
        ivieLook = ivie.copy();
    }

    public synchronized String userName() {
        return userName;
    }

    public synchronized BrainAnswer respond(String raw, String onScreen) {
        BrainAnswer a = respondInner(raw, onScreen);
        if (a.gesture == null) {
            a.gesture = gestureFor(a);
        }
        if (a.action == null && !a.listenAfter && planner.pending(clock.now()) == null) {
            for (int i = a.lines.size() - 1; i >= 0; i--) {
                if ("SAY".equals(a.lines.get(i).cue) && a.lines.get(i).text != null) {
                    lastSaid = a.lines.get(i).text;
                    break;
                }
            }
        }
        return a;
    }

    private BrainAnswer respondInner(String raw, String onScreen) {
        turn++;
        String speaker = "ivie".equals(onScreen) ? "ivie" : "obi";
        String text = normalize(raw);
        if (text.length() == 0) {
            return one(speaker, pick(speaker, "I'm here. Give me the goal and the constraint.",
                    "I'm here. Tell me what you want, and I'll stay with you."), "PACK");
        }
        BrainAnswer pre = beforeGate(raw, text, speaker);
        if (pre != null) {
            return pre;
        }
        if (LocalTurn.consequential(text)) {
            return new BrainAnswer(LocalTurn.script(raw, speaker), true, false, "GATE");
        }

        BrainAnswer a = memoryCommands(text, cased(raw), speaker);
        if (a == null) a = feelings(text);
        if (a == null) a = timeAndDate(text, speaker);
        if (a == null) a = units(text, speaker);
        if (a == null) a = maths(text, speaker);
        if (a == null) a = intents(text, speaker);
        if (a == null) a = definitions(text, speaker);
        if (a == null) a = facts(text, speaker);
        if (a == null) a = wisdomAsk(text, speaker);
        if (a != null) {
            return a;
        }
        return unknown(raw, text, speaker);
    }

    // ------------------------------------------------------------ handlers

    /** Confirmations, actions, settings, gestures and drawings: handled before the safety gate. */
    private BrainAnswer beforeGate(String raw, String text, String speaker) {
        long now = clock.now();
        ActionPlanner.Reply reply = planner.reply(text, now);
        if (reply == ActionPlanner.Reply.CONFIRMED) {
            PendingAction act = planner.take();
            BrainAnswer a = act.kind == PendingAction.Kind.COPY
                    ? one(speaker, "Copied. It's on your clipboard.", "ACT")
                    : one(speaker, "Opening " + act.appName() + " now. The post is ready; press send when you're happy with it.", "ACT");
            a.action = act;
            a.gesture = "👍";
            return a;
        }
        if (reply == ActionPlanner.Reply.CANCELLED) {
            BrainAnswer a = one(speaker, "Cancelled. Nothing was shared.", "ACT");
            a.gesture = "✋";
            return a;
        }
        String cased = cased(raw);
        PendingAction planned = planner.plan(cased, lastSaid, now);
        if (planned != null) {
            BrainAnswer a = one(speaker, ActionPlanner.question(planned), "ACT");
            a.listenAfter = true;
            a.gesture = "🫴";
            return a;
        }
        String name = Onboarding.rename(cased);
        if (name != null) {
            userName = name;
            journal.add(LearningJournal.TAUGHT_FACT, "my name", name);
            BrainAnswer a = one(speaker, "Alright, " + name + ". I'll call you that.", "LEARN");
            a.newName = name;
            a.gesture = "🤝";
            return a;
        }
        if (text.matches("^(turn |switch )?(the )?music (off|stop)$|^(stop|mute) (the )?music$|^no music$")) {
            BrainAnswer a = one(speaker, "Music off.", "SET");
            a.command = "MUSIC_OFF";
            return a;
        }
        if (text.matches("^(turn |switch )?(the )?music on$|^play (the )?music$")) {
            BrainAnswer a = one(speaker, "Music on, softly.", "SET");
            a.command = "MUSIC_ON";
            return a;
        }
        BrainAnswer styled = restyle(raw, text, speaker);
        if (styled != null) {
            return styled;
        }
        if (text.matches("^(hide|turn off) (the )?(subtitles|captions)$")) {
            BrainAnswer a = one(speaker, "Subtitles hidden.", "SET");
            a.command = "SUBS_OFF";
            return a;
        }
        if (text.matches("^(show|turn on) (the )?(subtitles|captions)$")) {
            BrainAnswer a = one(speaker, "Subtitles on.", "SET");
            a.command = "SUBS_ON";
            return a;
        }
        if (text.matches("^what('s| is) my name$") && userName != null) {
            return one(speaker, "Your name is " + userName + ".", "MEMORY");
        }
        ai.dvine.face.Sketch sketch = ai.dvine.face.Sketch.forRequest(text);
        if (sketch != null) {
            BrainAnswer a = one(speaker, sketchLine(sketch), "DRAW");
            a.sketch = sketch;
            a.gesture = "👉";
            return a;
        }
        String g = ai.dvine.face.Gestures.find(raw);
        if (g != null) {
            BrainAnswer a = one(speaker, pick(speaker, "Here.", "Here you go."), "GESTURE");
            a.gesture = g;
            return a;
        }
        return null;
    }

    /** "Make your skin darker", "dot mode", "make Ivie's hair longer": redraws a persona. */
    private BrainAnswer restyle(String raw, String text, String speaker) {
        String lower = raw == null ? "" : raw.toLowerCase(Locale.US);
        boolean forIvie = lower.contains("ivie") ? !lower.contains("obi") || "ivie".equals(speaker)
                : lower.contains("obi") ? false : "ivie".equals(speaker);
        ai.dvine.face.Look target = (forIvie ? ivieLook : obiLook).copy();
        String said;
        if (text.matches(".*(reset|restore|original|default) (your |the )?(look|face|style).*|.*go back to (your )?normal( look)?.*")) {
            target = ai.dvine.face.Look.defaults(forIvie);
            said = "Back to how I first looked.";
        } else {
            said = target.apply(text.replaceAll("\\b(obi|ivie)'?s?\\b", "your"));
        }
        if (said == null) {
            return null;
        }
        if (forIvie) {
            ivieLook = target;
        } else {
            obiLook = target;
        }
        String who = forIvie ? "ivie" : "obi";
        BrainAnswer a = one(who, said, "LOOK");
        a.look = target;
        a.lookIvie = forIvie;
        a.gesture = "💅";
        return a;
    }

    private static String sketchLine(ai.dvine.face.Sketch s) {
        String t = s.title;
        if (t.contains(" plus ")) {
            String[] p = t.split(" plus ");
            int a = Integer.parseInt(p[0]);
            int b = Integer.parseInt(p[1]);
            return "Here are " + a + " dots, and here are " + b + " more. Put them together and count: " + (a + b) + ".";
        }
        switch (t) {
            case "one half": return "Cut a circle into two equal parts. Each part is one half.";
            case "one quarter": return "Cut it into four equal parts. Each one is a quarter.";
            case "one third": return "Three equal parts. Each one is a third.";
            case "solar system": return "The Sun sits in the middle. Mercury, Venus, Earth and Mars circle closest to it.";
            case "steps": return "Every plan moves the same way: name the goal, make the plan, check it, then do it.";
            case "water cycle": return "Water rises from the sea as vapour, gathers into clouds, and falls back as rain.";
            case "house": return "A house: four walls, a roof that sheds the rain, a door to come in, and a window for light.";
            case "tree": return "Roots hold it and drink, the trunk carries water up, and the leaves turn sunlight into food.";
            case "sun": return "The Sun: a star, with its light reaching us in about eight minutes.";
            case "flower": return "A flower: petals to invite bees, a centre that holds the seeds, and a stem with a leaf.";
            case "person": return "A simple person: head, body, arms and legs.";
            case "car": return "A car: the body, two wheels on this side, and the windows.";
            case "fish": return "A fish: a smooth body for swimming, a tail to push, and an eye.";
            case "mountains": return "Two mountains, snow on the tall one, and the sun behind them.";
            case "boat": return "A small sailing boat on the water: hull, mast and sail.";
            case "moon": return "A crescent moon, with two little stars.";
            case "eye": return "An eye: the lids protect it, the iris gives the colour, and the pupil lets light in.";
            default: return "Let me draw a " + t + " for you.";
        }
    }

    private String gestureFor(BrainAnswer a) {
        if (a.source == null) {
            return null;
        }
        switch (a.source) {
            case "CARE":
                return a.spoken().contains("sorry") ? "🤲" : (turn % 2 == 0 ? "🙏" : "🤝");
            case "CALC":
            case "DICT":
                return "☝";
            case "WISDOM":
                return "🙌";
            case "OFFLINE":
                return "🫴";
            case "GATE":
                return "✋";
            case "LEARN":
                return "👌";
            case "PACK": {
                String s = a.spoken().toLowerCase(Locale.US);
                if (s.startsWith("hello") || s.startsWith("hi") || s.startsWith("good to see") || s.startsWith("goodbye")
                        || s.startsWith("until next") || s.startsWith("sleep well")) {
                    return "👋";
                }
                if (s.contains("joke") || s.contains("cache") || s.contains("checked a sunrise") || s.contains("quick plan")) {
                    return "🤙";
                }
                return null;
            }
            default:
                return null;
        }
    }

    private BrainAnswer memoryCommands(String text, String cased, String speaker) {
        if (text.matches("^(share|upload|send) (what you('ve)? learn(ed|t)?|your learning)$")
                || text.equals("you can share what you learn")) {
            journal.setConsent(true);
            return one(speaker, "Done. When we're online I'll share what I learned so the main database can review it. Nothing private like passwords is kept.", "LEARN");
        }
        if (text.matches("^(stop|don'?t) shar(e|ing).*")) {
            journal.setConsent(false);
            return one(speaker, "Understood. What I learn stays on this phone.", "LEARN");
        }
        if (text.matches("^forget (everything|all|it all)( you learned)?$")) {
            journal.forgetAll();
            return one(speaker, "Cleared. Everything you taught me on this phone is gone.", "LEARN");
        }
        if (text.matches("^forget (that|it|the last thing)$")) {
            boolean ok = journal.forgetLast();
            return one(speaker, ok ? "Forgotten." : "There was nothing to forget.", "LEARN");
        }
        if (text.matches("^what (have|did) you learn(ed|t)?( from me)?$")) {
            int n = journal.taughtCount();
            return one(speaker, n == 0 ? "Nothing yet. Tell me something with \"remember that my\", and I'll keep it."
                    : "You've taught me " + n + (n == 1 ? " thing" : " things") + " on this phone."
                    + (journal.consent() ? " I'll share them when we're online." : " They stay here unless you say share what you learned."), "LEARN");
        }
        Matcher m = CORRECT.matcher(cased);
        if (m.matches() && lastKey != null) {
            String value = m.group(1);
            if (journal.add(LearningJournal.CORRECTION, lastKey, value) == null) {
                return one(speaker, "I won't keep that. It looks like private information.", "LEARN");
            }
            return pair("Corrected. " + capital(lastKey) + " is " + value + ".",
                    "Thank you for setting me straight.", speaker, "LEARN");
        }
        m = TEACH.matcher(cased);
        if (!m.matches() && !text.endsWith("?")) {
            m = STATE.matcher(cased);
        }
        if (m.matches()) {
            String key = (m.group(1) + " " + m.group(2)).toLowerCase(Locale.US);
            String value = m.group(3);
            if (journal.add(LearningJournal.TAUGHT_FACT, key, value) == null) {
                return one(speaker, "I won't remember that. It looks like a password, a code or a card number, and those stay with you.", "LEARN");
            }
            lastKey = key;
            return one(speaker, pick(speaker, "Noted. " + capital(key) + " is " + value + ".",
                    "I'll remember that " + key + " is " + value + "."), "LEARN");
        }
        m = RECALL.matcher(stripQuestion(text));
        if (m.matches()) {
            String key = m.group(1) + " " + m.group(2);
            String value = journal.recall(key);
            lastKey = key;
            if (value != null) {
                return one(speaker, capital(key) + " is " + value + ".", "MEMORY");
            }
            return one(speaker, "You haven't told me " + key.replaceFirst("^my ", "your ").replaceFirst("^our ", "your ")
                    + " yet. Say remember that " + key + " is, and the answer.", "MEMORY");
        }
        return null;
    }

    private BrainAnswer feelings(String text) {
        if (anyWord(text, "sad", "grieving", "grief", "depressed", "lonely", "heartbroken", "hopeless", "cry", "crying")
                || text.contains("passed away") || text.contains("lost my") || text.contains("bad news")
                || text.contains("feel low") || text.contains("not okay")) {
            KnowledgePack.Wisdom w = wisdomFor("sad");
            List<TurnLine> lines = new ArrayList<TurnLine>();
            lines.add(new TurnLine("ivie", "THINK", null));
            lines.add(new TurnLine("ivie", "SAY", "I'm so sorry. You don't have to carry this alone. Take your time; I'm here."));
            if (w != null) {
                lines.add(new TurnLine("ivie", "SAY", w.text + (w.source.length() > 0 ? " " + w.source + "." : "")));
            }
            lines.add(new TurnLine("obi", "SAY", "No rush, and nothing to solve right now. We stay with you."));
            return new BrainAnswer(lines, true, false, "CARE");
        }
        if (text.matches("^(thanks|thank you|thank you so much|that worked|it worked|it works|solved|we did it|great job|well done)( obi| ivie| both| guys)?$")) {
            List<TurnLine> lines = new ArrayList<TurnLine>();
            if (turn % 2 == 0) {
                lines.add(new TurnLine("obi", "SAY", "Thank you for your patience. It held."));
                lines.add(new TurnLine("ivie", "SAY", "And Obi only checked it three times. A personal best."));
            } else {
                lines.add(new TurnLine("ivie", "SAY", "Thank you. That was a good one to solve together."));
                lines.add(new TurnLine("obi", "SAY", "Agreed. I'll write it down before Ivie tells it as a legend."));
            }
            return new BrainAnswer(lines, true, false, "CARE");
        }
        return null;
    }

    private BrainAnswer timeAndDate(String text, String speaker) {
        String t = stripQuestion(text);
        Date now = new Date(clock.now());
        if (t.matches("^(what('s| is)? the )?time( is it)?( now)?$") || t.equals("what time is it")) {
            return one(speaker, "It's " + fmt("h:mm a", now) + ".", "CLOCK");
        }
        if (t.matches("^(what('s| is)? )?(today'?s date|the date( today)?|today)$") || t.matches("^what day is (it|today)( today)?$")) {
            return one(speaker, "Today is " + fmt("EEEE, d MMMM yyyy", now) + ".", "CLOCK");
        }
        if (t.matches("^what year is it( now)?$")) {
            return one(speaker, "It's " + fmt("yyyy", now) + ".", "CLOCK");
        }
        return null;
    }

    private BrainAnswer units(String text, String speaker) {
        Matcher m = UNIT.matcher(text);
        if (!m.find()) {
            return null;
        }
        String from = unitName(m.group(2));
        String to = unitName(m.group(3));
        if (from == null || to == null) {
            return null;
        }
        double v = Double.parseDouble(m.group(1));
        for (KnowledgePack.Unit u : pack.units) {
            if (u.from.equals(from) && u.to.equals(to)) {
                return converted(speaker, v, v * u.factor + u.offset, from, to);
            }
            if (u.from.equals(to) && u.to.equals(from)) {
                return converted(speaker, v, (v - u.offset) / u.factor, from, to);
            }
        }
        return null;
    }

    private BrainAnswer converted(String speaker, double v, double r, String from, String to) {
        String rs = Calculator.format(Math.round(r * 100.0) / 100.0);
        return one(speaker, Calculator.format(v) + " " + plural(from, v) + " is about " + rs + " " + plural(to, r) + ".", "CALC");
    }

    private BrainAnswer maths(String text, String speaker) {
        String expr = Calculator.normalize(text);
        if (expr == null) {
            return null;
        }
        try {
            String result = Calculator.format(Calculator.eval(expr));
            return one(speaker, pick(speaker, "That's " + result + ".", "It comes to " + result + "."), "CALC");
        } catch (IllegalArgumentException e) {
            if ("divide by zero".equals(e.getMessage())) {
                return one(speaker, "That divides by zero, so there's no answer.", "CALC");
            }
            return null;
        }
    }

    private BrainAnswer intents(String text, String speaker) {
        String t = stripQuestion(text);
        KnowledgePack.Intent best = null;
        int bestLen = 0;
        for (KnowledgePack.Intent in : pack.intents) {
            for (String p : in.patterns) {
                boolean hit = t.equals(p) || t.startsWith(p + " ") && words(t) <= words(p) + 3
                        || (p.indexOf(' ') > 0 && (" " + t + " ").contains(" " + p + " "));
                if (hit && p.length() > bestLen) {
                    best = in;
                    bestLen = p.length();
                }
            }
        }
        if (best == null) {
            return null;
        }
        List<String> options = "ivie".equals(speaker) ? best.ivie : best.obi;
        String line = options.get(turn % options.size());
        if (userName != null && ("greet".equals(best.id) || "bye".equals(best.id))) {
            line = line.replaceFirst("^(Hello|Hi|Goodbye|Good to see you|Sleep well|Until next time)", "$1, " + userName);
        }
        return one(speaker, line, "PACK");
    }

    private BrainAnswer definitions(String text, String speaker) {
        Matcher m = DEFINE.matcher(stripQuestion(text));
        if (!m.matches()) {
            return null;
        }
        String word = null;
        for (int g = 1; g <= m.groupCount(); g++) {
            if (m.group(g) != null) {
                word = m.group(g).trim().replaceAll("^(a|an|the) ", "");
                break;
            }
        }
        if (word == null) {
            return null;
        }
        for (KnowledgePack.Word w : pack.words) {
            if (w.word.equals(word) || (w.word + "s").equals(word)) {
                return one(speaker, capital(w.word) + " means " + w.meaning, "DICT");
            }
        }
        return null;
    }

    private BrainAnswer facts(String text, String speaker) {
        Set<String> tokens = tokens(text);
        if (tokens.isEmpty()) {
            return null;
        }
        KnowledgePack.Fact best = null;
        double bestScore = 0;
        for (KnowledgePack.Fact f : pack.facts) {
            int hit = 0;
            for (String k : f.keys) {
                if (k.indexOf(' ') > 0 ? text.contains(k) : tokens.contains(k)) {
                    hit++;
                }
            }
            double score = hit / (double) f.keys.size();
            double coverage = hit / (double) tokens.size();
            double total = score + coverage * 0.25;
            if (score >= 0.99 && total > bestScore) {
                best = f;
                bestScore = total;
            }
        }
        if (best == null) {
            return null;
        }
        return one(speaker, best.answer, "PACK");
    }

    private BrainAnswer wisdomAsk(String text, String speaker) {
        String tag = null;
        if (anyWord(text, "purpose", "potential", "calling")) tag = "purpose";
        else if (anyWord(text, "afraid", "fear", "anxious", "worried", "worry", "stress", "stressed")) tag = "fear";
        else if (anyWord(text, "wisdom", "wise", "decision", "decide")) tag = "wisdom";
        else if (anyWord(text, "encourage", "encouragement", "motivate", "motivation", "verse", "scripture", "bible")) tag = "encourage";
        else if (anyWord(text, "money", "wealth", "business", "work")) tag = "work";
        if (tag == null) {
            return null;
        }
        KnowledgePack.Wisdom w = wisdomFor(tag);
        if (w == null) {
            return null;
        }
        String line = w.text + (w.source.length() > 0 ? " " + w.source + "." : "");
        if ("fear".equals(tag)) {
            return pair("Let's slow it down. " + line, "One step at a time. Tell me the first thing that worries you.", "ivie", "WISDOM");
        }
        return one(speaker, line, "WISDOM");
    }

    private BrainAnswer unknown(String raw, String text, String speaker) {
        journal.add(LearningJournal.UNANSWERED, raw == null ? "" : raw.trim(), "");
        String line = pack.offlineLine;
        boolean complex = words(text) > 12 || anyWord(text, "should", "plan", "compare", "which", "why", "strategy");
        if (complex) {
            List<TurnLine> lines = new ArrayList<TurnLine>();
            lines.add(new TurnLine("obi", "THINK", null));
            lines.add(new TurnLine("obi", "SAY", line));
            lines.add(new TurnLine("ivie", "SAY", "I've saved your question, so we can look at it properly together once you're connected."));
            return new BrainAnswer(lines, false, true, "OFFLINE");
        }
        List<TurnLine> lines = new ArrayList<TurnLine>();
        lines.add(new TurnLine(speaker, "THINK", null));
        lines.add(new TurnLine(speaker, "SAY", line));
        return new BrainAnswer(lines, false, true, "OFFLINE");
    }

    // ------------------------------------------------------------ helpers

    private KnowledgePack.Wisdom wisdomFor(String tag) {
        List<KnowledgePack.Wisdom> found = new ArrayList<KnowledgePack.Wisdom>();
        for (KnowledgePack.Wisdom w : pack.wisdom) {
            if (w.tags.contains(tag)) {
                found.add(w);
            }
        }
        return found.isEmpty() ? null : found.get(turn % found.size());
    }

    private BrainAnswer one(String speaker, String text, String source) {
        List<TurnLine> lines = new ArrayList<TurnLine>();
        lines.add(new TurnLine(speaker, "THINK", null));
        lines.add(new TurnLine(speaker, "SAY", text));
        return new BrainAnswer(lines, true, false, source);
    }

    private BrainAnswer pair(String first, String second, String firstSpeaker, String source) {
        String other = "ivie".equals(firstSpeaker) ? "obi" : "ivie";
        List<TurnLine> lines = new ArrayList<TurnLine>();
        lines.add(new TurnLine(firstSpeaker, "SAY", first));
        lines.add(new TurnLine(other, "SAY", second));
        return new BrainAnswer(lines, true, false, source);
    }

    private String pick(String speaker, String obi, String ivie) {
        return "ivie".equals(speaker) ? ivie : obi;
    }

    private String fmt(String pattern, Date d) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(zone);
        return f.format(d);
    }

    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.toLowerCase(Locale.US).replace('’', '\'').replace('\u0000', ' ');
        t = t.replaceAll("\\s+", " ").trim();
        t = t.replaceAll("^(hey |ok |okay |so )?(obi|ivie|eva|dvine)[, ]+", "");
        t = t.replaceAll("[.!]+$", "").trim();
        return t;
    }

    /** Same clean-up as {@link #normalize} but keeps the user's capital letters. */
    static String cased(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.replace('’', '\'').replace('\u0000', ' ').replaceAll("\\s+", " ").trim();
        t = t.replaceAll("(?i)^(hey |ok |okay |so )?(obi|ivie|eva|dvine)[, ]+", "");
        return t.replaceAll("[.!]+$", "").trim();
    }

    static String stripQuestion(String t) {
        return t.replaceAll("\\?+$", "").trim();
    }

    private static Set<String> tokens(String text) {
        Set<String> out = new HashSet<String>();
        for (String w : text.replaceAll("[^a-z0-9' ]", " ").split(" ")) {
            String s = w.replaceAll("'s$", "");
            if (s.length() > 0 && !STOP.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    private static boolean anyWord(String text, String... words) {
        Set<String> t = new HashSet<String>();
        for (String w : text.replaceAll("[^a-z' ]", " ").split(" ")) {
            t.add(w);
        }
        for (String w : words) {
            if (t.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static int words(String t) {
        return t.length() == 0 ? 0 : t.split(" ").length;
    }

    private static String capital(String s) {
        return s.length() == 0 ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String plural(String unit, double v) {
        if (Math.abs(v - 1.0) < 1e-9 || unit.startsWith("degree")) {
            return unit;
        }
        if (unit.equals("foot")) {
            return "feet";
        }
        if (unit.equals("inch")) {
            return "inches";
        }
        return unit + "s";
    }

    static String unitName(String raw) {
        String u = raw.trim();
        switch (u) {
            case "km": case "kilometer": case "kilometers": case "kilometre": case "kilometres": return "kilometre";
            case "mi": case "mile": case "miles": return "mile";
            case "kg": case "kilo": case "kilos": case "kilogram": case "kilograms": return "kilogram";
            case "lb": case "lbs": case "pound": case "pounds": return "pound";
            case "c": case "°c": case "celsius": case "degrees celsius": case "degree celsius": return "degree celsius";
            case "f": case "°f": case "fahrenheit": case "degrees fahrenheit": case "degree fahrenheit": return "degree fahrenheit";
            case "m": case "meter": case "meters": case "metre": case "metres": return "metre";
            case "ft": case "foot": case "feet": return "foot";
            case "cm": case "centimeter": case "centimeters": case "centimetre": case "centimetres": return "centimetre";
            case "in": case "inch": case "inches": return "inch";
            case "l": case "liter": case "liters": case "litre": case "litres": return "litre";
            case "gal": case "gallon": case "gallons": return "gallon";
            case "g": case "gram": case "grams": return "gram";
            case "oz": case "ounce": case "ounces": return "ounce";
            default:
                if (u.startsWith("degrees ") || u.startsWith("degree ")) {
                    return unitName(u.substring(u.indexOf(' ') + 1));
                }
                return null;
        }
    }
}
