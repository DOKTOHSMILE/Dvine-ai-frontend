package ai.ibiko.linux;

/**
 * What each key sends to a program in the terminal, as xterm does. Pure Java, so the screen and the tests share it.
 */
public final class TermKeys {
    private TermKeys() {
    }

    public static final int UP = 1, DOWN = 2, RIGHT = 3, LEFT = 4, HOME = 5, END = 6, PAGE_UP = 7, PAGE_DOWN = 8,
            INSERT = 9, DELETE = 10, ENTER = 11, BACKSPACE = 12, TAB = 13, ESCAPE = 14, F1 = 21;

    /** The bytes for a named key. {@code appCursor}: the program asked for the other arrow-key form. */
    public static String key(int key, boolean ctrl, boolean alt, boolean appCursor) {
        String s;
        switch (key) {
            case UP:
                s = arrow('A', ctrl, appCursor);
                break;
            case DOWN:
                s = arrow('B', ctrl, appCursor);
                break;
            case RIGHT:
                s = arrow('C', ctrl, appCursor);
                break;
            case LEFT:
                s = arrow('D', ctrl, appCursor);
                break;
            case HOME:
                s = arrow('H', ctrl, appCursor);
                break;
            case END:
                s = arrow('F', ctrl, appCursor);
                break;
            case PAGE_UP:
                s = "\u001b[5~";
                break;
            case PAGE_DOWN:
                s = "\u001b[6~";
                break;
            case INSERT:
                s = "\u001b[2~";
                break;
            case DELETE:
                s = "\u001b[3~";
                break;
            case ENTER:
                s = "\r";
                break;
            case BACKSPACE:
                s = ctrl ? "\u0008" : "\u007f";
                break;
            case TAB:
                s = "\t";
                break;
            case ESCAPE:
                s = "\u001b";
                break;
            default:
                if (key >= F1 && key < F1 + 12) {
                    String[] f = {"\u001bOP", "\u001bOQ", "\u001bOR", "\u001bOS", "\u001b[15~", "\u001b[17~", "\u001b[18~",
                            "\u001b[19~", "\u001b[20~", "\u001b[21~", "\u001b[23~", "\u001b[24~"};
                    s = f[key - F1];
                } else {
                    return "";
                }
        }
        return alt ? "\u001b" + s : s;
    }

    private static String arrow(char letter, boolean ctrl, boolean appCursor) {
        if (ctrl) {
            return "\u001b[1;5" + letter;
        }
        return (appCursor ? "\u001bO" : "\u001b[") + letter;
    }

    /** Typed text with CTRL or ALT held: CTRL-C is byte 3, ALT-x is ESC then x. */
    public static String typed(String text, boolean ctrl, boolean alt) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                c = '\r';
            }
            if (ctrl) {
                if (c >= 'a' && c <= 'z') {
                    c = (char) (c - 'a' + 1);
                } else if (c >= '@' && c <= '_') {
                    c = (char) (c - '@');
                } else if (c == ' ' || c == '2') {
                    c = 0;
                } else if (c == '?') {
                    c = 0x7f;
                } else if (c >= '3' && c <= '7') {
                    c = (char) (c - '3' + 27);
                }
            }
            if (alt) {
                b.append('\u001b');
            }
            b.append(c);
        }
        return b.toString();
    }

    /** Pasted text. A program that asked for it is told where the paste starts and ends. */
    public static String paste(String text, boolean bracketed) {
        String t = text == null ? "" : text.replace("\r\n", "\r").replace('\n', '\r').replace("\u001b", "");
        return bracketed ? "\u001b[200~" + t + "\u001b[201~" : t;
    }
}
