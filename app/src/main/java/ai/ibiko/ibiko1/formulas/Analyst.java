package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data, handled with surgical precision: a tactical procedure for processing and analysing numbers that Ibiko 1 runs
 * itself on data in a message (a list of numbers, a table, CSV) and hands to every other brain as its method. Exact
 * arithmetic, every step named, every figure checked a second way, and no conclusion wider than the data.
 *
 * <pre>
 * A1  objective      what is to be learned from the data (average, trend, the odd one out, a comparison); with no
 *                    question, the full profile
 * A2  inventory      what the data is: how many values, which columns, which are numbers; nothing assumed
 * A3  clean          blanks, non-numbers, thousands separators and currency or percent signs are dealt with and said;
 *                    nothing is silently dropped or invented
 * A4  exact          count, sum, mean, median, minimum, maximum, range and standard deviation (sample, n-1), worked out
 *                    exactly and rounded only when shown
 * A5  shape          outliers by Tukey's fences (below Q1 - 1.5 IQR or above Q3 + 1.5 IQR): flagged, never deleted
 * A6  movement       for values in order: the change first to last (absolute and percent) and the direction of the
 *                    least-squares line; a trend is called only with at least 4 values
 * A7  cross-check    every total is checked a second way (the sum against mean x count; the median against the sorted
 *                    middle); a mismatch is reported, not hidden
 * A8  limits         what the data cannot say is said: a sample is not the whole, a correlation is not a cause, a few
 *                    points are not a trend
 * A9  report         the figures first, then what they mean, then what would sharpen it (more data, a missing column)
 * A10 units          a unit or sign seen in the data (%, $, NGN, kg) stays with every figure said from it
 * </pre>
 */
public final class Analyst {
    private Analyst() {
    }

    /** A10, A3: a number in text, with its unit and thousands separators. */
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])([$₦£€]|NGN|USD)?\\s?(-?\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|-?\\d+(?:\\.\\d+)?)\\s?(%|kg|km|m|cm|g|h|s|NGN|USD)?(?![\\w.])");

    /** The profile of one series of numbers (A4–A7). */
    public static final class Profile {
        public String name = "the values";
        public String unit = "";
        public double[] values = new double[0];
        public int skipped;
        public double sum, mean, median, min, max, sd, q1, q3, slope;
        public final List<Double> outliers = new ArrayList<Double>();
        public boolean checked = true;
        public String checkNote = "";
    }

    /** A1–A3: whether a message carries data worth analysing: three or more numbers, in a list or a table. */
    public static boolean hasData(String message) {
        if (message == null) {
            return false;
        }
        if (message.matches("(?is).*\\b(capital|population of|atomic|who|when was|born)\\b.*") && !message.matches("(?is).*\\b(analy[sz]e|average|mean|median|stats|statistics|data|trend|outlier)\\b.*")) {
            return false; // a question that mentions numbers, not data to analyse
        }
        int n = 0;
        Matcher m = NUMBER.matcher(message);
        while (m.find()) {
            n++;
        }
        return n >= 3 && (message.matches("(?is).*\\b(analy[sz]e|average|mean|median|sum|total|stats|statistics|data|trend|outlier|"
                + "spread|range|deviation|summari[sz]e|profile|growth|change)\\b.*") || message.contains("\n") && n >= 5);
    }

    /** A2, A3: the numeric columns of a table (CSV, tabs, pipes), or the one series of numbers in the text. */
    public static List<Profile> read(String message) {
        List<Profile> out = new ArrayList<Profile>();
        String[] lines = message.trim().split("\\r?\\n");
        // a table: at least 3 rows with the same number (2+) of cells
        List<String[]> rows = new ArrayList<String[]>();
        for (String l : lines) {
            String[] cells = l.contains("\t") ? l.split("\t") : l.contains("|") ? l.replaceAll("^\\s*\\||\\|\\s*$", "").split("\\|")
                    : l.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            if (cells.length >= 2 && !l.matches("^\\s*\\|?\\s*-{3,}.*")) {
                rows.add(cells);
            }
        }
        if (rows.size() >= 3 && sameWidth(rows)) {
            String[] head = rows.get(0);
            boolean header = !isNumber(head[head.length - 1]);
            int cols = head.length;
            for (int c = 0; c < cols; c++) {
                List<Double> v = new ArrayList<Double>();
                int skipped = 0;
                String unit = "";
                for (int r = header ? 1 : 0; r < rows.size(); r++) {
                    String cell = rows.get(r)[c].trim();
                    Double d = parse(cell);
                    if (d == null) {
                        skipped++;
                    } else {
                        v.add(d);
                        unit = unit.isEmpty() ? unitOf(cell) : unit;
                    }
                }
                if (v.size() >= 3 && v.size() >= skipped) { // a mostly numeric column
                    Profile p = profile(v, unit);
                    p.name = header ? head[c].trim() : "column " + (c + 1);
                    p.skipped = skipped;
                    out.add(p);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        List<Double> v = new ArrayList<Double>();
        String unit = "";
        Matcher m = NUMBER.matcher(message);
        while (m.find()) {
            Double d = parse(m.group());
            if (d != null) {
                v.add(d);
                unit = unit.isEmpty() ? unitOf(m.group()) : unit;
            }
        }
        if (v.size() >= 3) {
            out.add(profile(v, unit));
        }
        return out;
    }

    private static boolean sameWidth(List<String[]> rows) {
        int w = rows.get(0).length;
        for (String[] r : rows) {
            if (r.length != w) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNumber(String s) {
        return parse(s.trim()) != null;
    }

    /** A3: "1,250", "$40", "12.5%", "-3" as numbers; null for anything else. */
    static Double parse(String cell) {
        String s = cell == null ? "" : cell.trim().replaceAll("^(?:[$₦£€]|NGN|USD)\\s?", "")
                .replaceAll("\\s?(%|kg|km|m|cm|g|h|s|NGN|USD)$", "").replace(",", "");
        if (!s.matches("-?\\d+(\\.\\d+)?")) {
            return null;
        }
        return Double.parseDouble(s);
    }

    private static String unitOf(String cell) {
        Matcher m = Pattern.compile("([$₦£€%]|NGN|USD|kg|km|cm)").matcher(cell);
        return m.find() ? m.group(1) : "";
    }

    /** A4–A7 for one series, in the order given. */
    public static Profile profile(List<Double> list, String unit) {
        Profile p = new Profile();
        p.unit = unit == null ? "" : unit;
        double[] v = new double[list.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = list.get(i);
        }
        p.values = v;
        int n = v.length;
        double sum = 0;
        for (double x : v) {
            sum += x;
        }
        p.sum = sum;
        p.mean = sum / n;
        double[] s = v.clone();
        Arrays.sort(s);
        p.min = s[0];
        p.max = s[n - 1];
        p.median = quantile(s, 0.5);
        p.q1 = quantile(s, 0.25);
        p.q3 = quantile(s, 0.75);
        double ss = 0;
        for (double x : v) {
            ss += (x - p.mean) * (x - p.mean);
        }
        p.sd = n > 1 ? Math.sqrt(ss / (n - 1)) : 0;
        double iqr = p.q3 - p.q1, lo = p.q1 - 1.5 * iqr, hi = p.q3 + 1.5 * iqr;
        for (double x : v) {
            if (x < lo || x > hi) {
                p.outliers.add(x);
            }
        }
        // A6: the least-squares slope over the order given
        double mx = (n - 1) / 2.0, sxy = 0, sxx = 0;
        for (int i = 0; i < n; i++) {
            sxy += (i - mx) * (v[i] - p.mean);
            sxx += (i - mx) * (i - mx);
        }
        p.slope = sxx == 0 ? 0 : sxy / sxx;
        // A7: the sum a second way (Kahan, in reverse) and the median from the sorted middle
        double again = 0, comp = 0;
        for (int i = n - 1; i >= 0; i--) {
            double y = v[i] - comp, t = again + y;
            comp = (t - again) - y;
            again = t;
        }
        double tolerance = 1e-9 * Math.max(1, Math.abs(sum));
        String note = "";
        if (Math.abs(again - sum) > tolerance) {
            p.checked = false;
            note = "the two sums differ by " + fmt(Math.abs(again - sum));
        }
        double middle = n % 2 == 1 ? s[n / 2] : (s[n / 2 - 1] + s[n / 2]) / 2;
        if (Math.abs(middle - p.median) > tolerance) {
            p.checked = false;
            note += (note.isEmpty() ? "" : "; ") + "the median checks differ";
        }
        p.checkNote = note;
        return p;
    }

    /** Linear interpolation between the sorted values (the common "type 7" quantile). */
    static double quantile(double[] sorted, double q) {
        double pos = (sorted.length - 1) * q;
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
    }

    /** A9, A10, A8: the report for people: figures, then meaning, then limits. */
    public static String report(List<Profile> profiles) {
        StringBuilder b = new StringBuilder();
        for (Profile p : profiles) {
            String u = p.unit;
            b.append(b.length() == 0 ? "" : "\n\n").append("**").append(cap(p.name)).append("** (").append(p.values.length)
                    .append(" values").append(p.skipped > 0 ? ", " + p.skipped + " non-numbers left out" : "").append(")\n");
            b.append("- Sum ").append(with(p.sum, u)).append(", mean ").append(with(p.mean, u)).append(", median ")
                    .append(with(p.median, u)).append('\n');
            b.append("- Lowest ").append(with(p.min, u)).append(", highest ").append(with(p.max, u)).append(", range ")
                    .append(with(p.max - p.min, u)).append(", standard deviation ").append(with(p.sd, u)).append('\n');
            if (!p.outliers.isEmpty()) {
                List<String> o = new ArrayList<String>();
                for (double x : p.outliers) {
                    o.add(with(x, u));
                }
                b.append("- Unusual: ").append(String.join(", ", o)).append(" (outside the normal spread; kept in the figures)\n");
            } else {
                b.append("- No outliers by the usual rule (Tukey's fences)\n");
            }
            int n = p.values.length;
            if (n >= 4) {
                double first = p.values[0], last = p.values[n - 1];
                String pct = first != 0 ? " (" + (last >= first ? "+" : "") + fmt((last - first) / Math.abs(first) * 100) + "%)" : "";
                String dir = Math.abs(p.slope) < 1e-9 * Math.max(1, Math.abs(p.mean)) ? "flat"
                        : p.slope > 0 ? "rising, about " + with(p.slope, u) + " a step" : "falling, about " + with(-p.slope, u) + " a step";
                b.append("- In the order given: from ").append(with(first, u)).append(" to ").append(with(last, u)).append(pct)
                        .append("; the trend line is ").append(dir).append('\n');
            } else {
                b.append("- Too few values to call a trend\n");
            }
            if (p.mean != 0 && Math.abs(p.median - p.mean) > 0.2 * Math.abs(p.mean)) {
                b.append("- The mean and median differ a lot: a few values pull the average; the median is the fairer middle\n");
            }
            b.append(p.checked ? "- Checked: the sum and the median were worked out a second way and agree" : "- Check failed: " + p.checkNote);
        }
        b.append("\n\nWhat this can't tell you: why the numbers are what they are, or whether they hold beyond these values."
                + " Tell me what you want to find out (a comparison, a forecast, the cause of the change) and I'll work from that.");
        return b.toString();
    }

    /** For any brain with data in the request: the same procedure, as its method. */
    public static final String BRIEF = " This request has data in it. Handle it with surgical precision: state the objective;"
            + " list what the data is (count, columns, units); say how you cleaned it (blanks, non-numbers, separators) and"
            + " drop nothing silently; compute exactly (count, sum, mean, median, min, max, range, sample standard"
            + " deviation) and check each total a second way; flag outliers by Tukey's fences without deleting them; call"
            + " a trend only from 4 or more ordered values; keep the units with every figure; give the figures first, then"
            + " what they mean, then what the data cannot show (sample is not the whole, correlation is not cause).";

    private static String with(double v, String unit) {
        String n = fmt(v);
        if (unit.isEmpty()) {
            return n;
        }
        return unit.matches("[$₦£€]") ? unit + n : unit.equals("%") ? n + "%" : n + " " + unit;
    }

    static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.format(Locale.US, "%,d", (long) Math.rint(v));
        }
        String s = String.format(Locale.US, "%,.2f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
