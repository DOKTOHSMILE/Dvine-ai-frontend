package ai.ibiko.suit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Exact arithmetic for the Cortex: language models guess at sums, so Ibiko works them out and hands the model the
 * result. Reads symbols ("12*(3+4)", "2^10", "15% of 2400") and words ("twelve times seven", "15 percent of 2,400",
 * "square root of 81", "what is 7 divided by 2"). Decimal, not floating point: 0.1 + 0.2 is 0.3. Pure Java.
 */
public final class Arithmetic {
    private static final MathContext MC = new MathContext(34, RoundingMode.HALF_EVEN);
    private static final String[] SMALL = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};

    private final String s;
    private int at;

    private Arithmetic(String s) {
        this.s = s;
    }

    /** The result of an expression in symbols or words, or null when it isn't one (or divides by zero). */
    public static BigDecimal eval(String text) {
        String e = normalise(text);
        if (e == null || e.isEmpty() || !e.matches(".*\\d.*") || !e.matches("[0-9.+\\-*/^()%r ]+")) {
            return null;
        }
        try {
            Arithmetic p = new Arithmetic(e.replace(" ", ""));
            BigDecimal v = p.sum();
            return p.at == p.s.length() ? v : null;
        } catch (ArithmeticException | NumberFormatException | IndexOutOfBoundsException ex) {
            return null;
        }
    }

    /** The expression a message asks for, if the message is only arithmetic ("what's 12 x 7?"); else null. */
    public static String asked(String message) {
        String m = message == null ? "" : message.trim().toLowerCase(Locale.US)
                .replaceAll("^(hey|please|pls|so)[,\\s]+", "")
                .replaceAll("^(what('?s| is)|calculate|compute|work out|how much is|solve|evaluate)\\s+", "")
                .replaceAll("[?!.=\\s]+$", "").trim();
        return eval(m) == null ? null : m;
    }

    /** The result as a person writes it: no trailing zeros, no exponent, thousands kept plain. */
    public static String show(BigDecimal v) {
        if (v == null) {
            return "";
        }
        BigDecimal r = v.round(new MathContext(16, RoundingMode.HALF_EVEN)).stripTrailingZeros();
        return r.scale() < 0 ? r.setScale(0).toPlainString() : r.toPlainString();
    }

    /** Words and symbols into one form: "fifteen percent of 2,400" becomes "15%*2400". */
    static String normalise(String text) {
        if (text == null) {
            return null;
        }
        String t = " " + text.toLowerCase(Locale.US).replace('×', '*').replace('÷', '/').replace('−', '-') + " ";
        t = t.replaceAll("(\\d),(\\d{3})", "$1$2").replaceAll("(\\d),(\\d{3})", "$1$2");
        t = numberWords(t);
        t = t.replaceAll("\\bsquare root of\\b", " r ").replaceAll("\\bsqrt\\b", " r ").replaceAll("√", " r ");
        t = t.replaceAll("\\b(\\d+(?:\\.\\d+)?)\\s*(percent|per cent|%)\\s*of\\b", " $1%* ");
        t = t.replaceAll("\\b(percent|per cent)\\b", "%");
        t = t.replaceAll("\\bsquared\\b", "^2").replaceAll("\\bcubed\\b", "^3");
        t = t.replaceAll("\\bto the power of\\b|\\braised to\\b|\\bpower\\b", "^");
        t = t.replaceAll("\\bmultiplied by\\b|\\btimes\\b|\\bx\\b|\\bof\\b", "*");
        t = t.replaceAll("\\bdivided by\\b|\\bover\\b", "/");
        t = t.replaceAll("\\bplus\\b|\\badd\\b|\\band\\b", "+");
        t = t.replaceAll("\\bminus\\b|\\bless\\b|\\btake away\\b", "-");
        t = t.replaceAll("\\bmodulo\\b|\\bmod\\b", "%"); // "7 mod 3" is 7%3: a remainder (a number follows the %)
        return t.trim().replaceAll("\\s+", " ");
    }

    /** "twenty one" becomes "21" (up to thousands). */
    private static String numberWords(String t) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\\b((?:(?:zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen"
                        + "|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand)[\\s-]*)+)\\b")
                .matcher(t);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            long total = 0, part = 0;
            for (String w : m.group(1).trim().split("[\\s-]+")) {
                int small = java.util.Arrays.asList(SMALL).indexOf(w), ten = java.util.Arrays.asList(TENS).indexOf(w);
                if (small >= 0) {
                    part += small;
                } else if (ten >= 2) {
                    part += ten * 10L;
                } else if (w.equals("hundred")) {
                    part = Math.max(1, part) * 100;
                } else if (w.equals("thousand")) {
                    total += Math.max(1, part) * 1000;
                    part = 0;
                }
            }
            m.appendReplacement(out, " " + (total + part) + " ");
        }
        m.appendTail(out);
        return out.toString();
    }

    // sum := product (('+'|'-') product)*
    private BigDecimal sum() {
        BigDecimal v = product();
        while (at < s.length() && (s.charAt(at) == '+' || s.charAt(at) == '-')) {
            char op = s.charAt(at++);
            BigDecimal r = product();
            v = op == '+' ? v.add(r, MC) : v.subtract(r, MC);
        }
        return v;
    }

    // product := power (('*'|'/'|'%') power)*   ("a % b" is the remainder; "15%" alone is a percent, see unary)
    private BigDecimal product() {
        BigDecimal v = power();
        while (at < s.length() && (s.charAt(at) == '*' || s.charAt(at) == '/' || s.charAt(at) == '%')) {
            char op = s.charAt(at++);
            BigDecimal r = power();
            if (op == '*') {
                v = v.multiply(r, MC);
            } else if (op == '/') {
                v = v.divide(r, MC);
            } else {
                v = v.remainder(r, MC);
            }
        }
        return v;
    }

    // power := unary ('^' power)?   (right to left: 2^3^2 is 2^9)
    private BigDecimal power() {
        BigDecimal base = unary();
        if (at < s.length() && s.charAt(at) == '^') {
            at++;
            BigDecimal exp = power();
            if (exp.stripTrailingZeros().scale() > 0 || exp.abs().compareTo(BigDecimal.valueOf(999)) > 0) {
                return BigDecimal.valueOf(Math.pow(base.doubleValue(), exp.doubleValue()));
            }
            int n = exp.intValueExact();
            return n >= 0 ? base.pow(n, MC) : BigDecimal.ONE.divide(base.pow(-n, MC), MC);
        }
        return base;
    }

    // unary := ('-'|'+'|'r') unary | atom '%'?
    private BigDecimal unary() {
        char c = s.charAt(at);
        if (c == '-') {
            at++;
            return unary().negate();
        }
        if (c == '+') {
            at++;
            return unary();
        }
        if (c == 'r') {
            at++;
            BigDecimal v = unary();
            if (v.signum() < 0) {
                throw new ArithmeticException("square root of a negative number");
            }
            return v.sqrt(MC);
        }
        BigDecimal v = atom();
        // "15%" followed by "*" (from "of") or at the end is a percent; "7 % 3" with a number after is a remainder
        if (at < s.length() && s.charAt(at) == '%' && (at + 1 == s.length() || s.charAt(at + 1) == '*' || s.charAt(at + 1) == ')')) {
            at++;
            v = v.divide(BigDecimal.valueOf(100), MC);
        }
        return v;
    }

    private BigDecimal atom() {
        if (s.charAt(at) == '(') {
            at++;
            BigDecimal v = sum();
            if (at >= s.length() || s.charAt(at) != ')') {
                throw new NumberFormatException("an open bracket");
            }
            at++;
            return v;
        }
        int from = at;
        while (at < s.length() && (Character.isDigit(s.charAt(at)) || s.charAt(at) == '.')) {
            at++;
        }
        return new BigDecimal(s.substring(from, at));
    }
}
