package ai.dvine.brain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small JSON reader and writer. Objects become LinkedHashMap, arrays ArrayList,
 * numbers Double. Kept local so the brain runs in plain JVM tests.
 */
public final class MiniJson {
    private final String src;
    private int pos;

    private MiniJson(String src) {
        this.src = src;
    }

    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("null json");
        }
        MiniJson p = new MiniJson(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.pos != p.src.length()) {
            throw p.error("trailing data");
        }
        return v;
    }

    private Object value() {
        if (pos >= src.length()) {
            throw error("unexpected end");
        }
        char c = src.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return word("true", Boolean.TRUE);
            case 'f':
                return word("false", Boolean.FALSE);
            case 'n':
                return word("null", null);
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        pos++;
        ws();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            ws();
            if (peek() != '"') {
                throw error("expected key");
            }
            String key = string();
            ws();
            expect(':');
            ws();
            map.put(key, value());
            ws();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw error("expected , or }");
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<Object>();
        pos++;
        ws();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            ws();
            list.add(value());
            ws();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw error("expected , or ]");
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (pos + 4 > src.length()) {
                            throw error("bad unicode");
                        }
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Double number() {
        int start = pos;
        while (pos < src.length() && "+-0123456789.eE".indexOf(src.charAt(pos)) >= 0) {
            pos++;
        }
        if (start == pos) {
            throw error("unexpected character");
        }
        try {
            return Double.valueOf(src.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("bad number");
        }
    }

    private Object word(String w, Object v) {
        if (!src.startsWith(w, pos)) {
            throw error("bad literal");
        }
        pos += w.length();
        return v;
    }

    private void ws() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private char next() {
        if (pos >= src.length()) {
            throw error("unexpected end");
        }
        return src.charAt(pos++);
    }

    private void expect(char c) {
        if (next() != c) {
            throw error("expected " + c);
        }
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("json " + msg + " at " + pos);
    }

    // ------------------------------------------------------------ writing

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        write(v, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            quote((String) v, sb);
        } else if (v instanceof Number || v instanceof Boolean) {
            if (v instanceof Double && ((Double) v) == Math.rint((Double) v) && !((Double) v).isInfinite()) {
                sb.append(((Double) v).longValue());
            } else {
                sb.append(v);
            }
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(e.getKey(), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object o : (List<Object>) v) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(o, sb);
            }
            sb.append(']');
        } else {
            quote(v.toString(), sb);
        }
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------ typed access

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object v) {
        return v instanceof List ? (List<Object>) v : new ArrayList<Object>();
    }

    public static String str(Object v, String fallback) {
        return v instanceof String ? (String) v : fallback;
    }

    public static double num(Object v, double fallback) {
        return v instanceof Number ? ((Number) v).doubleValue() : fallback;
    }

    public static List<String> strings(Object v) {
        List<String> out = new ArrayList<String>();
        for (Object o : list(v)) {
            if (o instanceof String) {
                out.add((String) o);
            }
        }
        return out;
    }
}
