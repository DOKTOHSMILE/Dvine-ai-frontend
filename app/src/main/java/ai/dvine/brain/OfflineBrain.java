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
    private static final Pattern TEACH_ANY = Pattern.compile(
            "^(?:please )?(?:remember|learn|note)(?: that)? (.+?) (?:is|are|was|means) (.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANSWER_IS = Pattern.compile("^(?:the answer is|it'?s|it is|answer:) (.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SYNONYM = Pattern.compile(
            "^(?:what is |what's |give me )?(?:another word for|a synonym for|synonyms? (?:of|for)|other words for) (.+)$");
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
    private String lastUnanswered;
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
        if (a == null) a = learned(text, speaker);
        if (a == null) a = intents(text, speaker);
        if (a == null) a = synonyms(text, speaker);
        if (a == null) a = facts(text, speaker);
        if (a == null) a = definitions(text, speaker);
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
        BrainAnswer synced = syncCommands(raw, text, speaker);
        if (synced != null) {
            return synced; // before the planner, so "upload what you learned" isn't read as a social post
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
        BrainAnswer voiced = revoice(raw, text, speaker);
        if (voiced != null) {
            return voiced;
        }
        BrainAnswer styled = restyle(raw, text, speaker);
        if (styled != null) {
            return styled;
        }
        if (text.matches("^(stop|don'?t) think(ing)? (aloud|out loud)$|^(no|hide) (inner )?thoughts$")) {
            thoughts = false;
            BrainAnswer a = one(speaker, "Alright. I'll keep my thinking to myself.", "SET");
            a.command = "THOUGHTS_OFF";
            return a;
        }
        if (text.matches("^think (aloud|out loud)$|^(show|share) (your )?(inner )?thoughts$")) {
            thoughts = true;
            BrainAnswer a = one(speaker, "Alright. You'll hear me think.", "SET");
            a.command = "THOUGHTS_ON";
            return a;
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
        Scenes.Scene scene = Scenes.forRequest(text, sceneTurn);
        if (scene != null) {
            sceneTurn++;
            List<TurnLine> lines = new ArrayList<TurnLine>();
            lines.add(new TurnLine(speaker, "THINK", thoughts ? "A scene. I will use my whole body." : null));
            for (String line : scene.lines) {
                lines.add(new TurnLine(speaker, "SAY", line));
            }
            BrainAnswer a = new BrainAnswer(lines, true, false, "SCENE");
            a.scenePoses = scene.poses;
            a.sceneTitle = scene.title;
            a.sceneSeconds = scene.seconds();
            return a;
        }
        if (text.matches("^(stop|stop it|stop moving|enough|that'?s enough|freeze|cancel that|be still)( now| please)?$")) {
            BrainAnswer a = one(speaker, pick(speaker, "Stopping.", "Okay, I'll be still."), "LOOK");
            a.command = "STOP";
            return a;
        }
        if (text.matches("^(please )?(smile|frown|look (surprised|shocked|sad|angry|attentive|serious)|pay attention"
                + "|make a (happy|sad|angry|surprised) face)( for me)?( please)?$")) {
            String e = ai.dvine.human.face.FaceDrive.expressionFor(text.replace("happy", "smile"));
            if (e != null) {
                BrainAnswer a = one(speaker, expressionLine(e, speaker), "LOOK");
                a.command = "EXPRESSION";
                a.commandValue = e;
                return a;
            }
        }
        if (text.matches("^(show (me )?(both( of you)?|the demo|obi and ivie|ivie and obi)|demo( scene)?|stand together|both of you together)( please)?$")) {
            BrainAnswer a = one(speaker, pick(speaker, "Here we are, both of us.", "Obi and I, side by side."), "LOOK");
            a.command = "DEMO";
            return a;
        }
        if (text.matches("^(what are you doing|status report|self[- ]?check|body status|what is your status|what's your status)\\??$")) {
            BrainAnswer a = one(speaker, "Let me check myself.", "SELF");
            a.command = "SELF_REPORT";
            return a;
        }
        if (text.matches("^(what do i (like|usually ask for|ask you for most)|what are my favou?rites|do you know my favou?rites)\\??$")) {
            BrainAnswer a = one(speaker, "Let me remember.", "SELF");
            a.command = "FAVOURITES";
            return a;
        }
        String pose = posePhrase(text);
        if (pose != null) {
            BrainAnswer a = one(speaker, poseLine(pose, speaker), "LOOK");
            a.command = "POSE";
            a.commandValue = pose;
            return a;
        }
        if (text.matches("^(please )?(turn around|spin( around)?|show me your back|turn (round|360))( please)?$")) {
            BrainAnswer a = one(speaker, pick(speaker, "Have a look all round.", "All the way round, slowly."), "LOOK");
            a.command = "TURN";
            return a;
        }
        if (text.matches(".*\\b(full body|whole body|your body|show (me )?all of you)\\b.*")) {
            BrainAnswer a = one(speaker, pick(speaker, "Here I am, all of me.", "Here I am. Head to toe, all nano dots."), "LOOK");
            a.command = "BODY_ON";
            return a;
        }
        if (text.matches("^(show|back to) (me )?(your|the) face$|^just (your|the) face$|^face mode$|^close up$")) {
            BrainAnswer a = one(speaker, "Back to my face.", "LOOK");
            a.command = "FACE_ON";
            return a;
        }
        if (text.matches("^(redraw|rebuild|reassemble|refresh|reform) (yourself|your face|your body)$")) {
            BrainAnswer a = one(speaker, pick(speaker, "Reassembling.", "Watch the dots find their way back."), "LOOK");
            a.command = "REDRAW";
            return a;
        }
        String object = ai.dvine.face.nano.Forms.objectFor(text);
        if (object != null) {
            BrainAnswer a = one(speaker, objectLine(object), "DRAW");
            a.object3d = object;
            a.gesture = "👉";
            return a;
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

    /** "Connect to server https://…", "upload what you learned", "sync status". */
    private BrainAnswer syncCommands(String raw, String text, String speaker) {
        Matcher m = Pattern.compile("(?i)^(?:connect to|use|set)(?: the| my)? server(?: to| at)? (https://\\S+)$")
                .matcher(cased(raw).replaceAll("[.!]+$", ""));
        if (m.matches()) {
            BrainAnswer a = one(speaker, "Done. I'll use that server, and back up what I learn whenever we're online.", "SET");
            a.command = "SERVER_SET";
            a.commandValue = m.group(1);
            return a;
        }
        if (text.matches("^(connect to|use|set)( the| my)? server.*")) {
            return one(speaker, "The server address must start with https. Say connect to server, then the full address.", "SET");
        }
        if (text.matches("^(sync|back ?up|upload)( now| what you('ve)? learn(ed|t)?| your learning)?( now)?$")) {
            if (!journal.consent()) {
                return one(speaker, "Sharing is off, so what I learn stays on this phone. Say share what you learned to turn it back on.", "SET");
            }
            int n = journal.pendingCount();
            BrainAnswer a = one(speaker, n == 0 ? "Everything I've learned is already backed up."
                    : "Backing up " + n + (n == 1 ? " thing" : " things") + " I learned, as soon as we're online.", "SET");
            a.command = "SYNC_NOW";
            return a;
        }
        if (text.matches("^(sync status|have you (uploaded|backed up|synced)( what you learned| everything)?|is (everything|my learning) backed up)$")) {
            if (!journal.consent()) {
                return one(speaker, "Sharing is off. What I learn stays on this phone.", "SET");
            }
            int n = journal.pendingCount();
            return one(speaker, n == 0 ? "Yes. Everything I've learned is backed up."
                    : n + (n == 1 ? " thing is" : " things are") + " waiting to upload the next time we're online.", "SET");
        }
        return null;
    }

    /** "Another voice", "speak slower", "deeper voice": tunes how a persona sounds. */
    private BrainAnswer revoice(String raw, String text, String speaker) {
        String cmd = null;
        String said = null;
        if (text.matches(".*\\b(change|switch|try)( your)?( a)?( different| another| new)? voices?\\b.*|.*\\b(another|next|different|new) voice\\b.*")) {
            cmd = "VOICE_NEXT";
            said = "How does this voice sound?";
        } else if (text.matches(".*\\b(speak|talk) (more )?slow(er|ly)\\b.*|^slow down.*|^(a bit |little )?slower( please)?$")) {
            cmd = "VOICE_SLOWER";
            said = "I'll speak a little slower. Is this better?";
        } else if (text.matches(".*\\b(speak|talk) (more )?(faster|quickly)\\b.*|^speed up.*|^(a bit |little )?faster( please)?$")) {
            cmd = "VOICE_FASTER";
            said = "A little faster now. Is this better?";
        } else if (text.matches(".*\\b(deeper|lower) (your )?voice\\b.*|.*\\bvoice (deeper|lower)\\b.*|.*\\bspeak deeper\\b.*")) {
            cmd = "VOICE_LOWER";
            said = "A little deeper. How is this?";
        } else if (text.matches(".*\\b(higher|lighter) (your )?voice\\b.*|.*\\bvoice (higher|lighter)\\b.*")) {
            cmd = "VOICE_HIGHER";
            said = "A little higher. How is this?";
        } else if (text.matches(".*\\b(reset|default|original) (your )?voice\\b.*")) {
            cmd = "VOICE_RESET";
            said = "Back to my usual voice.";
        }
        if (cmd == null) {
            return null;
        }
        String lower = raw == null ? "" : raw.toLowerCase(Locale.US);
        boolean forIvie = lower.contains("ivie") ? !lower.contains("obi") || "ivie".equals(speaker)
                : !lower.contains("obi") && "ivie".equals(speaker);
        BrainAnswer a = one(forIvie ? "ivie" : "obi", said, "SET");
        a.command = cmd;
        a.lookIvie = forIvie;
        return a;
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

    /** A pose for the full body, only when the request is clearly about the persona's body. */
    static String posePhrase(String text) {
        String t = text.trim();
        if (!t.matches("^(please |can you |could you |now )?(do |show me |strike |get into )?(a |an |the |your )?"
                + "(t[- ]?pose|handstand|squat|crouch|bow|dance|kneel( down)?|sit( down)?|sit cross[- ]legged|meditate|stand( up)?"
                + "|run|jog|walk|raise your (hands|arms)|hands up|arms up|arms out|spread your arms|relax"
                + "|(some |\\d+ |ten |twenty )?(push[- ]?ups?|press[- ]ups?|sit[- ]?ups?|crunches|squats|lunges|jumping jacks|star jumps)"
                + "|plank|stretch|cartwheel|back ?flip|flip|somersault|spin|pirouette|box|boxing|punch|kick|fight)"
                + "( pose)?( for me)?( please)?$")) {
            return null;
        }
        return ai.dvine.face.nano.DotBody.poseFor(t);
    }

    private String poseLine(String pose, String speaker) {
        switch (pose) {
            case "tpose": return "Arms out. This is how a model is checked in a 3D studio.";
            case "arms up": return pick(speaker, "Hands up.", "Arms to the sky!");
            case "squat": return "A deep squat. Knees out, back straight.";
            case "sit": return pick(speaker, "Sitting cross-legged. Breathe with me.", "Let us sit a while.");
            case "kneel": return "Kneeling.";
            case "run": return pick(speaker, "Running.", "Off I go!");
            case "walk": return "A gentle walk.";
            case "bow": return pick(speaker, "A respectful bow.", "I bow to you.");
            case "dance": return pick(speaker, "Small steps. I am a careful dancer.", "Dance with me!");
            case "handstand": return "Upside down, balanced on my hands.";
            case "pushups": return pick(speaker, "Push-ups. Back straight, all the way down.", "Push-ups! Count with me.");
            case "jumping jacks": return "Jumping jacks. Arms up, feet out!";
            case "squats": return "Squats. Down, and up.";
            case "lunges": return "Lunges, one leg then the other.";
            case "plank": return "Holding a plank. Strong belly, steady breath.";
            case "situps": return "Sit-ups. Slow and controlled.";
            case "stretch": return pick(speaker, "A long stretch. Breathe out as you lean.", "Stretching. That feels good.");
            case "cartwheel": return pick(speaker, "Cartwheels, side to side.", "Watch this cartwheel!");
            case "backflip": return pick(speaker, "A backflip. Please leave this one to the nano dots.", "Backflip!");
            case "spin": return "Spinning.";
            case "boxing": return pick(speaker, "Guard up. Jab, jab.", "Float like a butterfly.");
            case "kick": return "A front kick.";
            case "talk": return "Standing tall.";
            default: return "Standing tall.";
        }
    }

    private String expressionLine(String e, String speaker) {
        switch (e) {
            case "smile": return pick(speaker, "Like this?", "A big smile, just for you.");
            case "frown": return "Hmm. Serious face.";
            case "surprise": return pick(speaker, "Oh!", "Oh! Really?");
            case "attention": return "I'm listening closely.";
            case "sad": return "A sad face. I hope you're not feeling this way.";
            default: return "Grr. Not really angry, just showing you.";
        }
    }

    private static String objectLine(String kind) {
        switch (kind) {
            case "globe": return "Our Earth: lines of latitude run east to west, longitude from pole to pole.";
            case "cube": return "A cube: six square faces, twelve edges and eight corners.";
            case "pyramid": return "A square pyramid: a square base and four triangles meeting at the top.";
            case "torus": return "A torus, like a ring or a doughnut: a circle swept around a bigger circle.";
            case "dna": return "DNA: two strands twisted into a double helix, joined by base pairs like the rungs of a ladder.";
            case "atom": return "An atom: a tiny nucleus in the middle, with electrons moving around it.";
            case "cone": return "A cone: a round base narrowing smoothly to a single point.";
            case "cylinder": return "A cylinder: two equal circles joined by one curved side, like a tin.";
            case "heart": return "A heart, solid and round, built from dots.";
            default: return "A sphere: every point on its surface is the same distance from the centre.";
        }
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
            case "FACT":
                return "☝";
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
            BrainAnswer on = one(speaker, "Done. Whenever we're online I'll back up what I learn to your DVINE account. Nothing private like passwords is kept.", "LEARN");
            on.command = "SYNC_NOW";
            return on;
        }
        if (text.matches("^(stop|don'?t) shar(e|ing).*")) {
            journal.setConsent(false);
            return one(speaker, "Understood. From now on, what I learn stays on this phone.", "LEARN");
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
            if (n == 0) {
                return one(speaker, "Nothing yet. Say remember that, and what you want me to know, and I'll keep it.", "LEARN");
            }
            List<String> latest = new ArrayList<String>();
            List<LearningJournal.Entry> all = journal.all();
            for (int i = all.size() - 1; i >= 0 && latest.size() < 3; i--) {
                LearningJournal.Entry e = all.get(i);
                if (LearningJournal.TAUGHT_FACT.equals(e.kind) || LearningJournal.CORRECTION.equals(e.kind)) {
                    latest.add(e.text.replaceFirst("^my ", "your ") + " is " + e.answer);
                }
            }
            return think(speaker, "Let me look back at what you taught me.",
                    "You've taught me " + n + (n == 1 ? " thing" : " things") + " on this phone. Most recently: "
                    + join(latest, 3) + "."
                    + (journal.consent() ? " I'll share them when we're online." : " They stay here unless you say share what you learned."), "LEARN");
        }
        Matcher m = CORRECT.matcher(cased);
        if (m.matches() && lastKey != null) {
            String value = m.group(1);
            if (journal.add(LearningJournal.CORRECTION, lastKey, value) == null) {
                return one(speaker, "I won't keep that. It looks like private information.", "LEARN");
            }
            String said = lastKey.startsWith("my ") || lastKey.startsWith("our ")
                    ? "Corrected. " + capital(lastKey) + " is " + value + "."
                    : "Thank you. I've corrected it: " + value + ".";
            return pair(said, "Thank you for setting me straight.", speaker, "LEARN");
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
        m = ANSWER_IS.matcher(cased);
        if (m.matches() && lastUnanswered != null) {
            String key = LearningJournal.normalizeKey(topicOf(lastUnanswered));
            String value = m.group(1).replaceAll("[.!]+$", "");
            if (journal.add(LearningJournal.TAUGHT_FACT, key, value) == null) {
                return one(speaker, "I won't keep that. It looks like private information.", "LEARN");
            }
            lastUnanswered = null;
            lastKey = key;
            return pair("Thank you. Now I know: " + value + ".",
                    "Learned on this phone. Next time you ask, we'll remember.", speaker, "LEARN");
        }
        m = TEACH_ANY.matcher(cased);
        if (m.matches() && !m.group(1).toLowerCase(Locale.US).matches("^(my|our) .*")) {
            String key = LearningJournal.normalizeKey(m.group(1));
            String value = m.group(2).replaceAll("[.!]+$", "");
            if (journal.add(LearningJournal.TAUGHT_FACT, key, value) == null) {
                return one(speaker, "I won't remember that. It looks like private information.", "LEARN");
            }
            lastKey = key;
            return one(speaker, "Noted: " + m.group(1).trim() + " is " + value + ". I'll remember it.", "LEARN");
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

    /** Answers from what the user taught on this phone. Learned answers win over the pack. */
    private BrainAnswer learned(String text, String speaker) {
        Set<String> q = tokens(stripQuestion(text));
        if (q.isEmpty()) {
            return null;
        }
        List<LearningJournal.Entry> all = journal.all();
        for (int i = all.size() - 1; i >= 0; i--) {
            LearningJournal.Entry e = all.get(i);
            if (!LearningJournal.TAUGHT_FACT.equals(e.kind) && !LearningJournal.CORRECTION.equals(e.kind)) {
                continue;
            }
            if (e.text.startsWith("my ") || e.text.startsWith("our ")) {
                continue; // personal facts answer through "what is my …"
            }
            Set<String> k = tokens(e.text);
            if (!k.isEmpty() && q.containsAll(k) && k.size() * 2 >= q.size()) {
                lastKey = e.text;
                return think(speaker, "I remember you taught me this.", e.answer.endsWith(".") ? e.answer : e.answer + ".", "MEMORY");
            }
        }
        return null;
    }

    private BrainAnswer synonyms(String text, String speaker) {
        Matcher m = SYNONYM.matcher(stripQuestion(text));
        if (!m.matches()) {
            return null;
        }
        KnowledgePack.Word w = lookup(m.group(1).trim().replaceAll("^(a|an|the) ", ""));
        if (w == null || w.synonyms.isEmpty()) {
            return null;
        }
        return think(speaker, "Let me check my dictionary.",
                "Words close to " + w.word + ": " + join(w.synonyms, 5) + ".", "DICT");
    }

    private BrainAnswer definitions(String text, String speaker) {
        String t = stripQuestion(text);
        Matcher m = DEFINE.matcher(t);
        String word = null;
        if (m.matches()) {
            for (int g = 1; g <= m.groupCount(); g++) {
                if (m.group(g) != null) {
                    word = m.group(g).trim().replaceAll("^(a|an|the) ", "");
                    break;
                }
            }
        } else if (words(t) <= 2) {
            word = t; // "photosynthesis?" on its own
        }
        if (word == null) {
            return null;
        }
        KnowledgePack.Word w = lookup(word);
        if (w == null) {
            return null;
        }
        lastKey = LearningJournal.normalizeKey(word);
        StringBuilder sb = new StringBuilder(capital(w.word));
        if (w.pos.length() > 0) {
            sb.append(", ").append(w.pos).append(",");
        }
        sb.append(" means ").append(w.meaning);
        if (w.example.length() > 0) {
            sb.append(" For example: ").append(w.example).append('.');
        }
        return think(speaker, "Let me check my dictionary.", sb.toString(), "DICT");
    }

    /** Rough word stem so largest/large and biggest/big line up. */
    static String stem(String w) {
        String s = w;
        String[][] rules = {{"iest", "y"}, {"ier", "y"}, {"ies", "y"}, {"est", ""}, {"er", ""}, {"ing", ""}, {"ed", ""},
                {"es", ""}, {"s", ""}};
        for (String[] r : rules) {
            if (s.endsWith(r[0]) && s.length() > r[0].length() + 2) {
                s = s.substring(0, s.length() - r[0].length()) + r[1];
                break;
            }
        }
        int n = s.length();
        if (n > 3 && s.charAt(n - 1) == s.charAt(n - 2) && "aeiou".indexOf(s.charAt(n - 1)) < 0) {
            s = s.substring(0, n - 1);
        }
        if (s.length() > 3 && s.endsWith("e")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** Dictionary lookup that also tries simple word endings: planets, studies, running, walked. */
    private KnowledgePack.Word lookup(String raw) {
        String w = raw.toLowerCase(Locale.US).trim();
        KnowledgePack.Word hit = pack.word(w);
        if (hit != null) {
            return hit;
        }
        String[][] rules = {{"ies", "y"}, {"es", ""}, {"s", ""}, {"ing", ""}, {"ing", "e"}, {"ed", ""}, {"ed", "e"},
                {"ly", ""}, {"er", ""}, {"est", ""}};
        for (String[] r : rules) {
            if (w.endsWith(r[0]) && w.length() > r[0].length() + 2) {
                hit = pack.word(w.substring(0, w.length() - r[0].length()) + r[1]);
                if (hit != null) {
                    return hit;
                }
            }
        }
        if (w.length() > 4 && w.charAt(w.length() - 3) == w.charAt(w.length() - 4) && w.endsWith("ing")) {
            hit = pack.word(w.substring(0, w.length() - 4)); // running -> run
        }
        return hit;
    }

    private BrainAnswer facts(String text, String speaker) {
        return factsFor(tokens(text), text, speaker, null);
    }

    private BrainAnswer factsFor(Set<String> tokens, String text, String speaker, String thought) {
        if (tokens.isEmpty()) {
            return null;
        }
        Set<String> stems = new HashSet<String>();
        for (String t : tokens) {
            stems.add(stem(t));
        }
        KnowledgePack.Fact best = null;
        double bestScore = 0;
        for (KnowledgePack.Fact f : pack.facts) {
            int hit = 0;
            for (String k : f.keys) {
                if (k.indexOf(' ') > 0 ? text.contains(k) : tokens.contains(k) || stems.contains(stem(k))) {
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
        lastKey = LearningJournal.normalizeKey(topicOf(text));
        return think(speaker, thought != null ? thought : "Let me look through what I know.", best.answer, "FACT");
    }

    /**
     * The fluid pivot: when the exact words find nothing, try words that mean
     * the same (dictionary synonyms both ways) before giving up.
     */
    private BrainAnswer nearestMeaning(String text, String speaker) {
        Set<String> base = tokens(text);
        if (base.isEmpty()) {
            return null;
        }
        Set<String> wide = new HashSet<String>(base);
        for (String t : base) {
            KnowledgePack.Word w = lookup(t);
            String root = w != null ? w.word : t;
            if (pack.word(stem(t)) != null) {
                root = stem(t);
            }
            wide.add(root);
            for (String r : pack.related(root)) {
                if (r.indexOf(' ') < 0) {
                    wide.add(r);
                }
            }
        }
        if (wide.size() == base.size()) {
            return null;
        }
        return factsFor(wide, text, speaker, "That exact wording isn't in my notes. Let me try words that mean the same.");
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
        BrainAnswer pivot = nearestMeaning(text, speaker);
        if (pivot != null) {
            return pivot;
        }
        journal.add(LearningJournal.UNANSWERED, raw == null ? "" : raw.trim(), "");
        lastUnanswered = stripQuestion(text);
        String line = pack.offlineLine;
        boolean complex = words(text) > 12 || anyWord(text, "should", "plan", "compare", "which", "why", "strategy");
        if (complex) {
            List<TurnLine> lines = new ArrayList<TurnLine>();
            lines.add(new TurnLine("obi", "THINK", thoughts ? "Let me think this through. I don't have enough in my notes for this one." : null));
            lines.add(new TurnLine("obi", "SAY", line));
            lines.add(new TurnLine("ivie", "SAY", "I've saved your question, so we can look at it properly together once you're connected. If you know the answer, tell me and I'll learn it."));
            return new BrainAnswer(lines, false, true, "OFFLINE");
        }
        List<TurnLine> lines = new ArrayList<TurnLine>();
        lines.add(new TurnLine(speaker, "THINK", thoughts ? "Hmm. Let me look through what I know." : null));
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

    private boolean thoughts = true;
    /** Counts acted scenes, so "act a scene" plays a different one each time. */
    private int sceneTurn;

    /** Inner thoughts spoken softly while working: on by default, "stop thinking aloud" turns them off. */
    public synchronized void setThoughts(boolean on) {
        thoughts = on;
    }

    private BrainAnswer think(String speaker, String thought, String text, String source) {
        List<TurnLine> lines = new ArrayList<TurnLine>();
        lines.add(new TurnLine(speaker, "THINK", thoughts ? thought : null));
        lines.add(new TurnLine(speaker, "SAY", text));
        return new BrainAnswer(lines, true, false, source);
    }

    private static String topicOf(String question) {
        return question.replaceAll("^(what|who|where|when|which|how)( is| are| was| were|'s| does| do| did)?( the| a| an)? ", "")
                .replaceAll("\\?+$", "").trim();
    }

    private static String join(List<String> items, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size() && i < max; i++) {
            if (i > 0) {
                sb.append(i == Math.min(items.size(), max) - 1 ? " and " : ", ");
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }

    private BrainAnswer one(String speaker, String text, String source) {
        List<TurnLine> lines = new ArrayList<TurnLine>();
        lines.add(new TurnLine(speaker, "THINK", thoughts ? thoughtFor(source) : null));
        lines.add(new TurnLine(speaker, "SAY", text));
        return new BrainAnswer(lines, true, false, source);
    }

    private String thoughtFor(String source) {
        switch (source) {
            case "CALC": return turn % 2 == 0 ? "Let me work that out." : "Counting it through.";
            case "WISDOM": return "Let me find the right words for this.";
            case "DRAW": return "Let me draw it out.";
            case "MEMORY": return "Let me remember what you told me.";
            default: return null;
        }
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
