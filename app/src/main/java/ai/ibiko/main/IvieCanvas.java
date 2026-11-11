package ai.ibiko.main;

/**
 * Ivie's 3D canvas: the card above her chat, and the full-screen mode that covers the screen with the
 * message box floating over the scene. Built on first use. The activity still decides when to show it.
 */
public final class IvieCanvas {

    /** The screen this canvas is drawn on. */
    public interface Host {
        <T extends android.view.View> T find(int id);

        android.content.res.Resources getResources();

        int color(int id);

        android.view.Window getWindow();

        ai.ibiko.stage.StageView stage();

        ai.ibiko.render.PodView podView();
    }

    private final Host host;
    private boolean full;
    private final java.util.List<android.view.View> hidden = new java.util.ArrayList<android.view.View>();
    private android.graphics.drawable.Drawable canvasBackground, composerBackground;
    private int composerAt = -1;
    private android.view.ViewGroup.LayoutParams composerParams;

    public IvieCanvas(Host host) {
        this.host = host;
    }

    /** True while the design space covers the whole screen. */
    public boolean isFull() {
        return full;
    }

    /**
     * The card above Ivie's chat where what she draws turns, and can be dragged and pinched.
     * Shown on her tab while there is something to see, hidden on the others.
     */
    public void show(boolean on) {
        android.widget.FrameLayout canvas = host.find(ai.ibiko.R.id.ivieCanvas);
        ai.ibiko.stage.StageView stage = host.stage();
        if (canvas == null || stage == null) {
            return;
        }
        if (on && canvas.getChildCount() == 0) {
            canvas.setClipToOutline(true);
            ai.ibiko.render.PodView podView = host.podView();
            if (podView != null && podView.getParent() == null) {
                canvas.addView(podView, new android.widget.FrameLayout.LayoutParams(-1, -1));
            }
            if (stage.getParent() == null) {
                canvas.addView(stage, new android.widget.FrameLayout.LayoutParams(-1, -1));
            }
            android.widget.TextView close = new android.widget.TextView(canvas.getContext());
            close.setText("\u2715");
            close.setTextSize(16f);
            close.setTextColor(host.color(ai.ibiko.R.color.text_primary));
            close.setGravity(android.view.Gravity.CENTER);
            close.setContentDescription("Hide the 3D view");
            close.setOnClickListener(v -> show(false));
            int s = Math.round(36 * host.getResources().getDisplayMetrics().density);
            canvas.addView(close, new android.widget.FrameLayout.LayoutParams(s, s, android.view.Gravity.END | android.view.Gravity.TOP));
            android.widget.ImageView expand = new android.widget.ImageView(canvas.getContext());
            expand.setImageResource(ai.ibiko.R.drawable.ic_fullscreen);
            expand.setColorFilter(host.color(ai.ibiko.R.color.icon_brand));
            int pad = Math.round(ai.ibiko.IconSize.pad(36, ai.ibiko.IconSize.STANDARD) * host.getResources().getDisplayMetrics().density);
            expand.setPadding(pad, pad, pad, pad);
            expand.setContentDescription("Full screen");
            expand.setOnClickListener(v -> fullScreen(!full));
            android.widget.FrameLayout.LayoutParams fl = new android.widget.FrameLayout.LayoutParams(s, s, android.view.Gravity.END | android.view.Gravity.TOP);
            fl.rightMargin = s;
            canvas.addView(expand, fl);
        }
        if (!on && full) {
            fullScreen(false);
        }
        canvas.setVisibility(on ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    /**
     * Full screen: the 3D space covers the whole screen, edge to edge, with the device's bars hidden (a swipe from
     * the edge shows them for a moment), and the message box floats inside it at the bottom. The title bar and the
     * chat are hidden until it shrinks again. The message box moves, not the 3D view: moving a GL view would rebuild
     * its surface.
     */
    public void fullScreen(boolean on) {
        android.widget.FrameLayout canvas = host.find(ai.ibiko.R.id.ivieCanvas);
        android.view.View chat = host.find(ai.ibiko.R.id.chatPaneHost);
        android.view.View composer = host.find(ai.ibiko.R.id.composerBar);
        android.widget.LinearLayout root = host.find(ai.ibiko.R.id.chatScreenRoot);
        if (canvas == null || chat == null || composer == null || root == null || on == full) {
            return;
        }
        full = on;
        int[] hide = {ai.ibiko.R.id.chatHeader, ai.ibiko.R.id.banner_crash};
        android.widget.LinearLayout.LayoutParams lp = (android.widget.LinearLayout.LayoutParams) canvas.getLayoutParams();
        int side = Math.round(16 * host.getResources().getDisplayMetrics().density);
        if (on) {
            canvasBackground = canvas.getBackground();
            hidden.clear();
            for (int id : hide) {
                android.view.View v = host.find(id);
                if (v != null && v.getVisibility() == android.view.View.VISIBLE) {
                    hidden.add(v);
                    v.setVisibility(android.view.View.GONE);
                }
            }
            chat.setVisibility(android.view.View.GONE);
            lp.height = 0;
            lp.weight = 1f;
            lp.setMargins(0, 0, 0, 0);
            canvas.setBackground(null);
            canvas.setClipToOutline(false);
            composerAt = root.indexOfChild(composer);
            composerParams = composer.getLayoutParams();
            root.removeView(composer);
            composerBackground = composer.getBackground();
            composer.setBackground(null);
            canvas.addView(composer, new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.BOTTOM));
        } else {
            for (android.view.View v : hidden) {
                v.setVisibility(android.view.View.VISIBLE);
            }
            hidden.clear();
            chat.setVisibility(android.view.View.VISIBLE);
            lp.height = Math.round(300 * host.getResources().getDisplayMetrics().density);
            lp.weight = 0f;
            lp.setMargins(side, 0, side, Math.round(8 * host.getResources().getDisplayMetrics().density));
            canvas.setBackground(canvasBackground);
            canvas.setClipToOutline(true);
            canvas.removeView(composer);
            composer.setBackground(composerBackground);
            root.addView(composer, Math.min(Math.max(0, composerAt), root.getChildCount()), composerParams);
        }
        canvas.setLayoutParams(lp);
        androidx.core.view.WindowInsetsControllerCompat bars = androidx.core.view.WindowCompat.getInsetsController(host.getWindow(), root);
        if (bars != null) {
            if (on) {
                bars.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            } else {
                bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            }
        }
        for (int i = 0; i < canvas.getChildCount(); i++) {
            android.view.View c = canvas.getChildAt(i);
            if ("Full screen".contentEquals(String.valueOf(c.getContentDescription())) || "Shrink".contentEquals(String.valueOf(c.getContentDescription()))) {
                ((android.widget.ImageView) c).setImageResource(on ? ai.ibiko.R.drawable.ic_fullscreen_exit : ai.ibiko.R.drawable.ic_fullscreen);
                c.setContentDescription(on ? "Shrink" : "Full screen");
            }
        }
    }
}
