package ai.dvine.brain;

import java.util.Locale;

/**
 * Acted scenes: the persona zooms out to the full body and plays a short
 * story, workout or show, one pose per narrated line. Pure Java.
 */
public final class Scenes {
    private Scenes() {
    }

    /** One scene: a line to say and a pose to hold while saying it, beat by beat. */
    public static final class Scene {
        public final String title;
        public final String[] lines;
        public final String[] poses;

        Scene(String title, String... beats) {
            this.title = title;
            lines = new String[beats.length / 2];
            poses = new String[beats.length / 2];
            for (int i = 0; i < lines.length; i++) {
                poses[i] = beats[i * 2];
                lines[i] = beats[i * 2 + 1];
            }
        }

        /** Seconds each pose holds: about as long as its line takes to say slowly. */
        public float[] seconds() {
            float[] s = new float[lines.length];
            for (int i = 0; i < lines.length; i++) {
                int words = lines[i].trim().split("\\s+").length;
                s[i] = Math.max(2.6f, words * 0.43f + 0.7f);
            }
            return s;
        }
    }

    static final Scene HARE = new Scene("the hare and the tortoise",
            "stand", "Let me act out the story of the hare and the tortoise.",
            "run", "The hare was fast, and very proud of it. He laughed at the slow tortoise.",
            "walk", "The tortoise only smiled and said: let us race.",
            "run", "Off went the hare, far, far ahead.",
            "sit", "So far ahead that he sat down to rest, and fell fast asleep.",
            "walk", "Step by step, the tortoise kept walking. He never stopped.",
            "arms up", "When the hare woke up, the tortoise had already crossed the line!",
            "bow", "Slow and steady wins the race.");

    static final Scene WORKOUT = new Scene("morning workout",
            "stand", "Morning workout. Follow me, at your own pace.",
            "jumping jacks", "Jumping jacks to warm up. One, two, three, four.",
            "squats", "Now squats. Sit back, chest up, and stand.",
            "pushups", "Push-ups. Keep your back straight like a plank of wood.",
            "plank", "Hold the plank. Breathe slowly. You are stronger than you think.",
            "situps", "Sit-ups. Gently, use your belly, not your neck.",
            "stretch", "And stretch. Reach up, lean to each side.",
            "arms up", "Well done! Drink some water.");

    static final Scene WARRIOR = new Scene("the warrior",
            "stand", "A warrior stood at the edge of his village.",
            "point", "One morning he saw danger coming over the hills.",
            "boxing", "He raised his fists to defend his people.",
            "kick", "He fought bravely, and he did not run.",
            "kneel", "But he knew that strength alone was not enough.",
            "bow", "So he bowed before the elders, and asked them for wisdom.",
            "arms up", "Together, with courage and wisdom, the village stood strong.");

    static final Scene SHOW = new Scene("the stunt show",
            "dance", "Welcome to the show! Music is playing.",
            "spin", "First, a spin.",
            "cartwheel", "Now, a cartwheel across the floor.",
            "backflip", "And a backflip! Do not try this without a coach.",
            "handstand", "Steady now, a handstand.",
            "bow", "Thank you, thank you!");

    static final Scene[] ALL = {HARE, WORKOUT, WARRIOR, SHOW};

    /**
     * The scene a request asks for, or null. A general "act a scene" gets
     * the next scene in turn ({@code turn} counts up).
     */
    public static Scene forRequest(String text, int turn) {
        String t = text.toLowerCase(Locale.US).trim();
        boolean acting = t.matches(".*\\b(act|acting|perform|play|show|story|scene|drama|skit)\\b.*");
        if ((t.contains("tortoise") && t.contains("hare")) && acting) return HARE;
        if (t.matches(".*\\b(work ?out|exercise|exercises|keep fit|get fit|training)\\b.*")
                && t.matches(".*\\b(do|let'?s|show|start|lead|with me|morning|teach)\\b.*")) return WORKOUT;
        if (t.contains("warrior") && acting || t.contains("fight scene") || t.contains("action scene")) return WARRIOR;
        if (t.matches(".*\\b(stunts?|tricks?|acrobatics?|gymnastics)\\b.*")
                && t.matches(".*\\b(do|show|perform|some|a)\\b.*")) return SHOW;
        if (t.matches(".*\\b(act|perform|play|show me|do)\\b.*\\b(scene|drama|play|story|skit|show)\\b.*")
                || t.matches(".*\\btell (me )?a story with your body\\b.*")) {
            return ALL[Math.floorMod(turn, ALL.length)];
        }
        return null;
    }
}
