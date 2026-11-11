package ai.ibiko.chat.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A sheet that slides up from the bottom of the screen with a row per choice (an icon, a name and a line under it),
 * the standard Android way of offering a short menu. Tapping outside it, or Back, closes it.
 */
public final class BottomSheet {
    /** One choice: its icon, its name and an optional line of explanation. */
    public static final class Item {
        final int icon;
        final String name, about;

        public Item(int icon, String name, String about) {
            this.icon = icon;
            this.name = name;
            this.about = about;
        }
    }

    public interface Pick {
        void picked(int which);
    }

    private BottomSheet() {
    }

    /**
     * A sheet holding any view under its title (the steps of a piece of work, say). It opens at half the screen;
     * dragging its grip or title up takes it to nearly full screen, down closes it, and a tap on the grip switches
     * between half and full. What it holds scrolls inside.
     */
    public static Dialog showView(Activity a, String title, View content) {
        Ui.init(a);
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = frame(a, d, title);
        final DragScroll sv = new DragScroll(a);
        sv.addView(content);
        sheet.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        place(a, d, sheet);
        final Window w = d.getWindow();
        if (w != null) {
            final int screen = a.getResources().getDisplayMetrics().heightPixels;
            final int half = screen / 2, full = (int) (screen * 0.94f);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, half);
            final Resizer resize = new Resizer() {
                float y0;
                int h0;
                boolean moved;

                int height() {
                    return w.getAttributes().height > 0 ? w.getAttributes().height : half;
                }

                @Override
                public boolean canGrow() {
                    return height() < full - Ui.dp(a, 8);
                }

                @Override
                public void down(float y) {
                    y0 = y;
                    h0 = height();
                    moved = false;
                }

                @Override
                public void move(float y) {
                    int to = Math.max(screen / 5, Math.min(full, h0 - (int) (y - y0)));
                    if (Math.abs(y - y0) > Ui.dp(a, 6)) {
                        moved = true;
                    }
                    w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, to);
                }

                @Override
                public void up(boolean tapAllowed) {
                    int h = height();
                    if (!moved) {
                        if (tapAllowed) {
                            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h >= full - Ui.dp(a, 8) ? half : full); // a tap
                        }
                    } else if (h < screen * 0.33f) {
                        d.dismiss(); // pulled down: closed
                    } else {
                        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h > screen * 0.7f ? full : half);
                    }
                }
            };
            View.OnTouchListener drag = (v, e) -> {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        resize.down(e.getRawY());
                        return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        resize.move(e.getRawY());
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        resize.up(true);
                        return true;
                    default:
                        return false;
                }
            };
            for (int i = 0; i < Math.min(2, sheet.getChildCount() - 1); i++) {
                sheet.getChildAt(i).setOnTouchListener(drag); // the grip and the title
            }
            sv.resizer = resize; // and the body: a pull down at the top, or up while it isn't full, moves the sheet
            sheet.getChildAt(0).setContentDescription("Drag up for full screen, down to close");
        }
        return d;
    }

    /** Moves the sheet: where a drag started, where it is, and where it let go. */
    interface Resizer {
        boolean canGrow();

        void down(float rawY);

        void move(float rawY);

        void up(boolean tapAllowed);
    }

    /**
     * The sheet's body: it scrolls what it holds, and a vertical drag that the list can't use moves the sheet instead
     * (pulling down while the list is at its top, or up while the sheet isn't full), so the whole sheet can be dragged,
     * not only its grip. Taps still reach the rows.
     */
    static final class DragScroll extends android.widget.ScrollView {
        Resizer resizer;
        private float downY;
        private boolean sheetDrag;
        private final int slop;

        DragScroll(android.content.Context c) {
            super(c);
            slop = android.view.ViewConfiguration.get(c).getScaledTouchSlop();
        }

        @Override
        public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            if (resizer != null) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        sheetDrag = false;
                        resizer.down(downY);
                        break;
                    case android.view.MotionEvent.ACTION_MOVE:
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dy) > slop && ((dy > 0 && getScrollY() == 0) || (dy < 0 && resizer.canGrow()))) {
                            sheetDrag = true;
                            return true;
                        }
                        break;
                    default:
                        break;
                }
            }
            return super.onInterceptTouchEvent(e);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            if (resizer != null && sheetDrag) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_MOVE:
                        resizer.move(e.getRawY());
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        sheetDrag = false;
                        resizer.up(false);
                        return true;
                    default:
                        return true;
                }
            }
            if (resizer != null && e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                downY = e.getRawY();
                resizer.down(downY);
            }
            float dy = e.getRawY() - downY;
            if (resizer != null && e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE
                    && ((dy > slop && !canScrollVertically(-1)) || (dy < -slop && resizer.canGrow()))) {
                sheetDrag = true; // a short list that can't scroll: the drag is the sheet's
                resizer.move(e.getRawY());
                return true;
            }
            return super.onTouchEvent(e);
        }
    }

    /** The sheet's height now (pixels), for tests. */
    static int heightOf(Dialog d) {
        return d.getWindow() == null ? 0 : d.getWindow().getAttributes().height;
    }

    /** The sheet's rounded body with its grip and title. */
    private static LinearLayout frame(Activity a, Dialog d, String title) {
        LinearLayout sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SHEET);
        float r = Ui.dp(a, 24);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 16));
        // the grip: a 4 dp bar in a 24 dp strip the full width, easy to take hold of
        android.widget.FrameLayout strip = new android.widget.FrameLayout(a);
        View grip = new View(a);
        grip.setBackground(Ui.round(a, Ui.LINE, 0, 3));
        android.widget.FrameLayout.LayoutParams gp = new android.widget.FrameLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 4));
        gp.gravity = Gravity.CENTER;
        strip.addView(grip, gp);
        sheet.addView(strip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 24)));
        if (title != null) {
            TextView t = Ui.text(a, title, 17, Ui.TEXT, true);
            t.setPadding(Ui.dp(a, 24), Ui.dp(a, 4), Ui.dp(a, 24), Ui.dp(a, 8));
            sheet.addView(t);
        }
        return sheet;
    }

    /** Shows the sheet at the bottom of the screen, dimming what is behind, clear of the navigation bar. */
    private static void place(Activity a, Dialog d, LinearLayout sheet) {
        d.setContentView(sheet);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
            w.getAttributes().windowAnimations = android.R.style.Animation_InputMethod; // slides up from the bottom
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sheet, (v, insets) -> {
                int nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
                v.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 16) + nav);
                return insets;
            });
        }
        d.setCanceledOnTouchOutside(true);
        d.show();
    }

    public static Dialog show(Activity a, String title, Item[] items, final Pick pick) {
        return show(a, title, null, items, -1, pick);
    }

    /**
     * A choice of one: a line under the title, and the chosen row ({@code selected}) in the accent colour with a
     * check mark ({@code -1}: none).
     */
    public static Dialog show(Activity a, String title, String subtitle, Item[] items, int selected, final Pick pick) {
        Ui.init(a);
        final Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = new LinearLayout(a);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.SHEET);
        float r = Ui.dp(a, 24);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 16));

        View grip = new View(a);
        grip.setBackground(Ui.round(a, Ui.LINE, 0, 3));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 4));
        gp.gravity = Gravity.CENTER_HORIZONTAL;
        gp.bottomMargin = Ui.dp(a, 10);
        sheet.addView(grip, gp);
        if (title != null) {
            TextView t = Ui.text(a, title, 17, Ui.TEXT, true);
            t.setPadding(Ui.dp(a, 24), Ui.dp(a, 4), Ui.dp(a, 24), Ui.dp(a, 8));
            sheet.addView(t);
        }
        if (subtitle != null) {
            TextView st = Ui.text(a, subtitle, 13, Ui.SUB, false);
            st.setPadding(Ui.dp(a, 24), 0, Ui.dp(a, 24), Ui.dp(a, 8));
            sheet.addView(st);
        }
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            Item it = items[i];
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(a, 56));
            row.setPadding(Ui.dp(a, 24), Ui.dp(a, 12), Ui.dp(a, 24), Ui.dp(a, 12));
            android.util.TypedValue ripple = new android.util.TypedValue();
            a.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
            row.setBackgroundResource(ripple.resourceId);
            ImageView icon = new ImageView(a);
            icon.setImageResource(it.icon);
            row.addView(icon, new LinearLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)));
            LinearLayout words = new LinearLayout(a);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(Ui.dp(a, 20), 0, 0, 0);
            boolean on = i == selected;
            words.addView(Ui.text(a, it.name, 16, on ? Ui.ACCENT : Ui.TEXT, false));
            if (it.about != null && !it.about.isEmpty()) {
                words.addView(Ui.text(a, it.about, 13, on ? Ui.ACCENT : Ui.SUB, false));
            }
            row.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (on) {
                ImageView check = new ImageView(a);
                check.setImageResource(ai.ibiko.R.drawable.ic_check);
                check.setColorFilter(Ui.ACCENT);
                row.addView(check, new LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22)));
                row.setSelected(true);
            }
            row.setContentDescription(it.name + (on ? ", chosen" : "") + (it.about == null ? "" : ". " + it.about));
            row.setOnClickListener(v -> {
                d.dismiss();
                pick.picked(which);
            });
            sheet.addView(row);
        }
        d.setContentView(sheet);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
            w.getAttributes().windowAnimations = android.R.style.Animation_InputMethod; // slides up from the bottom
            // keep clear of the navigation bar
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sheet, (v, insets) -> {
                int nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
                v.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 16) + nav);
                return insets;
            });
        }
        d.setCanceledOnTouchOutside(true);
        d.show();
        return d;
    }
}
