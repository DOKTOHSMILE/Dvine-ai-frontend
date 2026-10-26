package ai.dvine.brain;

import java.util.Locale;

/**
 * Safe arithmetic for spoken or typed maths. A recursive-descent parser;
 * nothing is ever evaluated as code.
 */
public final class Calculator {
    private final String s;
    private int pos;

    private Calculator(String s) {
        this.s = s;
    }

    /** Turns "what is 12 times 7" into "12*7". Returns null when no maths is present. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String t = " " + raw.toLowerCase(Locale.US).replace(',', ' ') + " ";
        t = t.replace("×", "*").replace("÷", "/").replace("−", "-");
        t = t.replaceAll("(\\d+(?:\\.\\d+)?)\\s*(?:percent|%)\\s+of\\s+", "($1/100)*");
        t = t.replaceAll("square root of\\s*", " sqrt ");
        t = t.replaceAll("\\bsquared\\b", "^2").replaceAll("\\bcubed\\b", "^3");
        t = t.replaceAll("to the power of|raised to|\\bpower\\b", "^");
        t = t.replaceAll("multiplied by|\\btimes\\b|(?<=\\d)\\s*x\\s*(?=\\d)", "*");
        t = t.replaceAll("divided by|\\bover\\b", "/");
        t = t.replaceAll("\\bplus\\b|\\badd\\b", "+");
        t = t.replaceAll("\\bminus\\b|\\bsubtract\\b", "-");
        t = t.replaceAll("\\bmod\\b|\\bmodulo\\b", "%");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c >= '0' && c <= '9') || "+-*/^%().".indexOf(c) >= 0) {
                out.append(c);
            } else if (t.startsWith("sqrt", i)) {
                out.append("sqrt");
                i += 3;
            }
        }
        String expr = out.toString();
        boolean digit = false;
        boolean op = false;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c >= '0' && c <= '9') {
                digit = true;
            } else if ("+-*/^%".indexOf(c) >= 0 || expr.startsWith("sqrt", i)) {
                op = true;
            }
        }
        return digit && op ? expr : null;
    }

    /** Evaluates a normalized expression. Throws IllegalArgumentException on bad input. */
    public static double eval(String expr) {
        Calculator c = new Calculator(expr.replace(" ", ""));
        double v = c.sum();
        if (c.pos != c.s.length()) {
            throw new IllegalArgumentException("unexpected " + c.s.charAt(c.pos));
        }
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new IllegalArgumentException("undefined");
        }
        return v;
    }

    public static String format(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 1e15) {
            return Long.toString(Math.round(v));
        }
        String out = String.format(Locale.US, "%.6f", v);
        out = out.replaceAll("0+$", "").replaceAll("\\.$", "");
        return out;
    }

    private double sum() {
        double v = product();
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '+') {
                pos++;
                v += product();
            } else if (c == '-') {
                pos++;
                v -= product();
            } else {
                break;
            }
        }
        return v;
    }

    private double product() {
        double v = power();
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '*') {
                pos++;
                v *= power();
            } else if (c == '/') {
                pos++;
                double d = power();
                if (d == 0) {
                    throw new IllegalArgumentException("divide by zero");
                }
                v /= d;
            } else if (c == '%') {
                pos++;
                v %= power();
            } else {
                break;
            }
        }
        return v;
    }

    private double power() {
        double base = unary();
        if (pos < s.length() && s.charAt(pos) == '^') {
            pos++;
            return Math.pow(base, power());
        }
        return base;
    }

    private double unary() {
        if (pos < s.length() && s.charAt(pos) == '-') {
            pos++;
            return -unary();
        }
        if (pos < s.length() && s.charAt(pos) == '+') {
            pos++;
            return unary();
        }
        if (s.startsWith("sqrt", pos)) {
            pos += 4;
            double v = unary();
            if (v < 0) {
                throw new IllegalArgumentException("negative root");
            }
            return Math.sqrt(v);
        }
        return atom();
    }

    private double atom() {
        if (pos < s.length() && s.charAt(pos) == '(') {
            pos++;
            double v = sum();
            if (pos >= s.length() || s.charAt(pos) != ')') {
                throw new IllegalArgumentException("missing )");
            }
            pos++;
            return v;
        }
        int start = pos;
        while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException("expected number");
        }
        return Double.parseDouble(s.substring(start, pos));
    }
}
