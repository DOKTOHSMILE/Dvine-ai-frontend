package ai.ibiko;

/**
 * Ibiko's icon sizes: two, and only two, so every icon in the app matches.
 * <ul>
 * <li>{@link #STANDARD} 24 dp: an icon on its own (a button, a row's leading icon, a trailing chevron, a close).</li>
 * <li>{@link #INLINE} 18 dp: an icon beside words (chips, the steps sheet, the reply's actions, a status mark).</li>
 * </ul>
 * Every tappable icon sits in a target of at least {@link #TARGET} dp. The drawables carry the same sizes
 * (IconSetTest checks), so an icon shown at its own size is one of these two as well.
 */
public final class IconSize {
    public static final int STANDARD = 24;
    public static final int INLINE = 18;
    public static final int TARGET = 48;

    private IconSize() {
    }

    /** The padding that puts an icon of {@code icon} dp in the middle of a box of {@code box} dp. */
    public static int pad(int box, int icon) {
        return Math.max(0, (box - icon) / 2);
    }
}
