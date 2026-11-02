package ai.dvine.ui;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import ai.dvine.MainActivity;

/**
 * Renders the chat screens with real pixels (a phone 1080 × 2340 at 440 dpi) into build/screens, so the layout can
 * be looked at without a phone: the empty chat, a conversation, the drawer, the providers sheet and the usage screen.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotTest {
    private static final File OUT = new File(System.getProperty("user.dir"), "build/screens");

    @Test(timeout = 180000)
    public void rendersTheChatScreens() throws Exception {
        OUT.mkdirs();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        MainActivity a = c.get();
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        shoot(a, "1_chat_empty");
        java.lang.reflect.Field chatsF = MainActivity.class.getDeclaredField("chats");
        chatsF.setAccessible(true);
        ai.dvine.chat.ChatBook book = (ai.dvine.chat.ChatBook) chatsF.get(a);
        if (book != null) {
            book.add("user", "text", "Write a Java function that reverses a string", "ibiko");
            book.reply("ibiko", "Here's a simple one:\n\n```java\nstatic String reverse(String s) {\n    return new StringBuilder(s).reverse().toString();\n}\n```\n\nIt works for any string, including an empty one.");
            book.add("user", "text", "What's the capital of Nigeria?", "ivie");
            book.reply("ivie", "Abuja. It became the capital in 1991, taking over from Lagos.");
            java.lang.reflect.Field paneF = MainActivity.class.getDeclaredField("chatPane");
            paneF.setAccessible(true);
            Object pane = paneF.get(a);
            if (pane != null) {
                pane.getClass().getMethod("show").invoke(pane);
            }
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
            shoot(a, "2_chat_conversation");
        }
        java.lang.reflect.Method toggle = MainActivity.class.getDeclaredMethod("toggleDrawer");
        toggle.setAccessible(true);
        toggle.invoke(a);
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        shoot(a, "3_drawer");
        c.pause().stop().destroy();

        ActivityController<ai.dvine.chat.ui.ProvidersActivity> p = Robolectric.buildActivity(ai.dvine.chat.ui.ProvidersActivity.class)
                .create().start().resume().visible();
        shoot(p.get(), "4_providers");
        p.pause().stop().destroy();
        ActivityController<ai.dvine.chat.ui.SetupActivity> st = Robolectric.buildActivity(ai.dvine.chat.ui.SetupActivity.class)
                .create().start().resume().visible();
        shoot(st.get(), "0_setup");
        st.pause().stop().destroy();
        ActivityController<ai.dvine.chat.ui.UsageActivity> us = Robolectric.buildActivity(ai.dvine.chat.ui.UsageActivity.class)
                .create().start().resume().visible();
        shoot(us.get(), "5_usage");
        us.pause().stop().destroy();
        assertTrue(new File(OUT, "1_chat_empty.png").length() > 0);
    }

    private static void shoot(android.app.Activity a, String name) throws Exception {
        android.view.View root = a.getWindow().getDecorView();
        int w = 1080, h = 2340;
        root.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        bmp.eraseColor(0xFF000000);
        root.draw(new android.graphics.Canvas(bmp));
        try (FileOutputStream o = new FileOutputStream(new File(OUT, name + ".png"))) {
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o);
        }
    }
}
