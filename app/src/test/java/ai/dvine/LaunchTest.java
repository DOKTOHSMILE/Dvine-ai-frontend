package ai.dvine;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Boots the real Activity on a simulated Android 14 so start-up crashes show up in CI. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LaunchTest {
    @Test(timeout = 120000)
    public void startsAndDrawsWithoutCrashing() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume();
        MainActivity a = c.get();
        assertNotNull(a);
        android.view.View root = a.getWindow().getDecorView();
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2340);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(1080, 2340, android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bmp));
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        root.draw(new android.graphics.Canvas(bmp));
        c.pause().stop().destroy();
    }

    /** The AI providers sheet and the Usage screen open, draw, and take a tap on a provider card. */
    @Test(timeout = 120000)
    public void providersAndUsageOpen() {
        ActivityController<ai.dvine.chat.ui.ProvidersActivity> p = Robolectric.buildActivity(ai.dvine.chat.ui.ProvidersActivity.class)
                .create().start().resume();
        android.view.View root = p.get().getWindow().getDecorView();
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2340);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(1080, 2340, android.graphics.Bitmap.Config.ARGB_8888);
        root.draw(new android.graphics.Canvas(bmp));
        assertNotNull(find(root, "Anthropic (Claude)"));
        find(root, "Custom gateway").performClick(); // opens its card: key, address, model, certificate
        assertNotNull(find(root, "Add backup key"));
        root.draw(new android.graphics.Canvas(bmp));
        // the keyboard opens: the sheet must move up above it (Android 15 no longer does this by itself)
        android.view.View content = ((android.view.ViewGroup) p.get().findViewById(android.R.id.content)).getChildAt(0);
        androidx.core.view.WindowInsetsCompat withKeyboard = new androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(), androidx.core.graphics.Insets.of(0, 0, 0, 900))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), true)
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(), androidx.core.graphics.Insets.of(0, 80, 0, 40))
                .build();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(content, withKeyboard);
        org.junit.Assert.assertEquals(900, content.getPaddingBottom());
        org.junit.Assert.assertEquals(80, content.getPaddingTop());
        p.pause().stop().destroy();

        ActivityController<ai.dvine.chat.ui.UsageActivity> u = Robolectric.buildActivity(ai.dvine.chat.ui.UsageActivity.class)
                .create().start().resume();
        android.view.View ur = u.get().getWindow().getDecorView();
        ur.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(2340, android.view.View.MeasureSpec.EXACTLY));
        ur.layout(0, 0, 1080, 2340);
        ur.draw(new android.graphics.Canvas(bmp));
        assertNotNull(find(ur, "Context window"));
        u.pause().stop().destroy();
    }

    /** The view showing exactly this text (its card, for a title in a card row), or null. */
    @Test(timeout = 120000)
    public void yourOwnMessageCanBeCopied() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        java.lang.reflect.Field cf = MainActivity.class.getDeclaredField("chats");
        cf.setAccessible(true);
        ai.dvine.chat.ChatBook chats = (ai.dvine.chat.ChatBook) cf.get(c.get());
        chats.add("user", "text", "RE clone the repo and audit my codebase", chats.current().persona);
        java.lang.reflect.Field pf = MainActivity.class.getDeclaredField("chatPane");
        pf.setAccessible(true);
        ((ai.dvine.stage.ChatPane) pf.get(c.get())).show();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        android.view.View copy = described(c.get().getWindow().getDecorView(), "Copy your message");
        assertNotNull(copy);
        copy.performClick();
        android.content.ClipboardManager clips = (android.content.ClipboardManager) c.get().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        org.junit.Assert.assertEquals("RE clone the repo and audit my codebase", clips.getPrimaryClip().getItemAt(0).getText().toString());
        c.pause().stop().destroy();
    }

    private static android.view.View described(android.view.View v, String what) {
        if (what.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())) {
            return v;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View f = described(g.getChildAt(i), what);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static android.view.View find(android.view.View v, String text) {
        if (v instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView) v).getText())) {
            android.view.View up = v;
            while (up.getParent() instanceof android.view.View && !up.isClickable()) {
                up = (android.view.View) up.getParent();
            }
            return up.isClickable() ? up : v;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View f = find(g.getChildAt(i), text);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }
}
