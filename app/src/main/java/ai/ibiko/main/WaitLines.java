package ai.ibiko.main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Something to read while the app files download: what Ibiko can do, a short quote, an interesting fact. Shown one
 * at a time, in a shuffled order that doesn't repeat a line until all have been shown. Pure Java.
 */
public final class WaitLines {
    /** One line and what kind it is ("Ibiko can", "Quote", "Did you know"). */
    public static final class Line {
        public final String kind, text;

        Line(String kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    static final String CAN = "Ibiko can", QUOTE = "Quote", FACT = "Did you know";

    static final String[][] ALL = {
            {CAN, "Clone your GitHub repository, fix a bug and open a pull request while you wait."},
            {CAN, "Tell you what any English word means, even offline: about 152,000 words."},
            {CAN, "Run a real Linux shell on your device: git, Python, Node and more."},
            {CAN, "Run the code you paste: say “run this” with Python, JavaScript, C or shell."},
            {CAN, "Read your whole project and remember where it stopped, even after the device closes."},
            {CAN, "Speak in a natural human voice, a man's or a woman's, chosen in Settings."},
            {CAN, "Make a PDF, a Word file or a spreadsheet of any answer."},
            {CAN, "Model things in 3D from the sizes you give it."},
            {CAN, "Answer the capital, currency and languages of every country with no internet."},
            {CAN, "Search the web and show you real photographs."},
            {CAN, "Split a big job between helpers that work at the same time."},
            {CAN, "Learn from every reply: tell it Correct or Wrong and it remembers."},
            {CAN, "Ask before it runs a command, pushes code or posts for you."},
            {CAN, "Keep working in the background with the screen off."},
            {QUOTE, "“The journey of a thousand miles begins with one step.” Lao Tzu"},
            {QUOTE, "“If you want to go fast, go alone. If you want to go far, go together.” African proverb"},
            {QUOTE, "“However long the night, the dawn will break.” African proverb"},
            {QUOTE, "“Until the lion learns to write, tales of the hunt will glorify the hunter.” African proverb"},
            {QUOTE, "“It always seems impossible until it's done.” Nelson Mandela"},
            {QUOTE, "“Education is the most powerful weapon which you can use to change the world.” Nelson Mandela"},
            {QUOTE, "“Talk is cheap. Show me the code.” Linus Torvalds"},
            {QUOTE, "“The best way to predict the future is to invent it.” Alan Kay"},
            {QUOTE, "“Knowledge is power.” Francis Bacon"},
            {QUOTE, "“Imagination is more important than knowledge.” Albert Einstein"},
            {FACT, "Octopuses have three hearts."},
            {FACT, "A day on Venus is longer than its year."},
            {FACT, "Light from the Sun takes about 8 minutes to reach Earth."},
            {FACT, "Bananas are berries, but strawberries are not."},
            {FACT, "Your brain uses about a fifth of your body's energy."},
            {FACT, "Sharks lived on Earth before there were trees."},
            {FACT, "The first computer “bug” was a real moth, found in a machine in 1947."},
            {FACT, "Nigeria has more than 500 living languages."},
            {FACT, "Lightning is about five times hotter than the surface of the Sun."},
            {FACT, "Sound travels about four times faster in water than in air."},
            {FACT, "Mount Kilimanjaro is the highest mountain in Africa."},
            {FACT, "Honey found in ancient Egyptian tombs was still good to eat."},
    };

    private final List<Line> order = new ArrayList<Line>();
    private final Random random;
    private int at;

    public WaitLines(Random random) {
        this.random = random;
        for (String[] l : ALL) {
            order.add(new Line(l[0], l[1]));
        }
        Collections.shuffle(order, random);
    }

    /** The next line; after the last, a new shuffle (never the same line twice in a row). */
    public Line next() {
        if (at >= order.size()) {
            Line last = order.get(order.size() - 1);
            Collections.shuffle(order, random);
            if (order.get(0) == last) {
                Collections.swap(order, 0, order.size() - 1);
            }
            at = 0;
        }
        return order.get(at++);
    }

    public int size() {
        return order.size();
    }
}
