package ai.dvine.main;

import android.view.MotionEvent;
import android.view.View;

/**
 * The chat drawer, the standard Android way: a finger dragging left to right (from anywhere but over something that
 * scrolls sideways) pulls it out with it, the chat darkening as it comes; on letting go it settles open or shut by how
 * far it came and how fast the finger moved. Dragging the open drawer to the left pushes it back; the menu button
 * slides it in and out.
 */
public final class DrawerController {
    /** The screen around the drawer. */
    public interface Host {
        View drawer();

        View scrim();

        /** True while the drawer must stay shut (a full-screen design). */
        boolean blocked();

        /** Before it opens: the keyboard goes, the chat list is fresh. */
        void beforeOpen();

        /** The views under a drag that became the drawer's are told it was cancelled. */
        void cancel(MotionEvent e);

        View root();

        float density();

        int touchSlop();
    }

    private final Host host;
    private float swipeX = -1, swipeY, dragFrom;
    private boolean swipeOpen, dragging;
    private android.view.VelocityTracker velocity;

    public DrawerController(Host host) {
        this.host = host;
    }

    public boolean isOpen() {
        View d = host.drawer();
        return d != null && d.getVisibility() == View.VISIBLE;
    }

    public void toggle() {
        if (isOpen()) {
            close();
        } else {
            open();
        }
    }

    public void open() {
        View drawer = host.drawer();
        if (drawer == null || drawer.getVisibility() == View.VISIBLE) {
            return;
        }
        reveal(drawer);
        place(drawer, -width(drawer)); // slides out from the left, as on any Android app
        settle(drawer, true);
    }

    public void close() {
        View drawer = host.drawer();
        if (drawer == null || drawer.getVisibility() != View.VISIBLE) {
            View scrim = host.scrim();
            if (scrim != null) {
                scrim.setVisibility(View.GONE);
            }
            return;
        }
        settle(drawer, false); // slides away, as it came
    }

    private void reveal(View drawer) {
        host.beforeOpen();
        drawer.setVisibility(View.VISIBLE);
        View scrim = host.scrim();
        if (scrim != null) {
            scrim.setVisibility(View.VISIBLE);
        }
        drawer.bringToFront();
    }

    private float width(View drawer) {
        return drawer.getWidth() > 0 ? drawer.getWidth() : 304 * host.density();
    }

    /** The drawer at {@code offset} (−width shut, 0 open), the scrim darkening with it. */
    private void place(View drawer, float offset) {
        float w = width(drawer);
        float x = Math.max(-w, Math.min(0f, offset));
        drawer.setTranslationX(x);
        View scrim = host.scrim();
        if (scrim != null) {
            scrim.setAlpha(1f + x / w);
        }
    }

    /** True when (x, y) is over something that scrolls sideways itself (code, pills, a 3D view): its swipe is its own. */
    static boolean sideways(View v, float x, float y) {
        int[] at = new int[2];
        v.getLocationOnScreen(at);
        if (v.getVisibility() != View.VISIBLE || x < at[0] || y < at[1] || x > at[0] + v.getWidth() || y > at[1] + v.getHeight()) {
            return false;
        }
        if (v instanceof android.widget.HorizontalScrollView || v instanceof android.view.SurfaceView || v instanceof android.view.TextureView
                || v.canScrollHorizontally(1) || v.canScrollHorizontally(-1)) {
            return true;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (sideways(g.getChildAt(i), x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A touch on the screen: true when the drawer took it (the screen then does nothing more with it). */
    public boolean onTouch(MotionEvent e) {
        float edge = 24 * host.density();
        View drawer = host.drawer();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                swipeOpen = !isOpen();
                swipeX = !host.blocked() && drawer != null && (isOpen() || e.getX() <= edge
                        || !sideways(host.root(), e.getRawX(), e.getRawY())) ? e.getX() : -1;
                swipeY = e.getY();
                if (velocity != null) {
                    velocity.recycle();
                }
                velocity = android.view.VelocityTracker.obtain();
                velocity.addMovement(e);
                return false;
            case MotionEvent.ACTION_MOVE:
                if (velocity != null) {
                    velocity.addMovement(e);
                }
                if (swipeX >= 0 && drawer != null) {
                    float dx = e.getX() - swipeX, dy = Math.abs(e.getY() - swipeY);
                    if (!dragging && Math.abs(dx) > host.touchSlop() && Math.abs(dx) > dy * 1.5f && (swipeOpen ? dx > 0 : dx < 0)) {
                        dragging = true; // the drawer is the finger's now
                        host.cancel(e);
                        drawer.animate().cancel();
                        if (swipeOpen) {
                            reveal(drawer);
                            dragFrom = -width(drawer);
                        } else {
                            dragFrom = 0f;
                        }
                        swipeX = e.getX();
                        dx = 0;
                    }
                    if (dragging) {
                        place(drawer, dragFrom + dx);
                        return true;
                    }
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging && drawer != null) {
                    dragging = false;
                    float vx = 0;
                    if (velocity != null) {
                        velocity.addMovement(e);
                        velocity.computeCurrentVelocity(1000);
                        vx = velocity.getXVelocity();
                    }
                    float fling = 800 * host.density();
                    boolean open = vx > fling || vx > -fling && drawer.getTranslationX() > -width(drawer) / 2;
                    swipeX = -1;
                    settle(drawer, open);
                    return true;
                }
                swipeX = -1;
                return false;
            default:
                return false;
        }
    }

    /** The drawer glides the rest of the way, open or shut. */
    private void settle(final View drawer, final boolean open) {
        final float w = width(drawer);
        final View scrim = host.scrim();
        long ms = (long) (220 * Math.abs((open ? 0 : -w) - drawer.getTranslationX()) / w) + 60;
        drawer.animate().cancel();
        drawer.animate().translationX(open ? 0 : -w).setDuration(ms)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setUpdateListener(a -> {
                    if (scrim != null) {
                        scrim.setAlpha(1f + drawer.getTranslationX() / w);
                    }
                })
                .withEndAction(() -> {
                    if (!open) {
                        drawer.setVisibility(View.GONE);
                        if (scrim != null) {
                            scrim.setVisibility(View.GONE);
                        }
                    }
                    drawer.setTranslationX(0); // shut: hidden in place, so the next open starts clean
                    if (scrim != null) {
                        scrim.setAlpha(1f);
                    }
                }).start();
    }
}
