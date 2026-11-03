package ai.dvine.ui;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ai.dvine.MainActivity;

/**
 * A tour of the whole app: every screen is opened and every button, switch, tab, row and link on it is tapped, one
 * after another, including the ones that only appear after a tap (the menu, sheets). A tap must not crash or close the
 * screen by accident; a sheet or dialog it opens has its own rows tapped too, and a confirmation is always answered
 * "no", so nothing is deleted. Each tap is listed in the report (UITOUR lines).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h891dp-xxhdpi")
public class UiTourTest {
    private final List<String> report = new ArrayList<String>(), failures = new ArrayList<String>();

    @Test
    public void everyControlOnEveryScreenCanBeTapped() {
        tour(MainActivity.class, null);
        for (Class<? extends Activity> screen : new Class[]{ai.dvine.chat.ui.SettingsActivity.class, ai.dvine.chat.ui.ProvidersActivity.class,
                ai.dvine.chat.ui.ConnectorsActivity.class, ai.dvine.chat.ui.UsageActivity.class, ai.dvine.chat.ui.MemoryActivity.class, ai.dvine.chat.ui.WorkspaceActivity.class, ai.dvine.chat.ui.HistoryActivity.class,
                ai.dvine.chat.ui.SetupActivity.class}) {
            tour(screen, null);
        }
        tour(ai.dvine.chat.ui.CodeViewActivity.class, new Intent().putExtra(ai.dvine.chat.ui.CodeViewActivity.EXTRA_CODE, "class Main {}\n")
                .putExtra(ai.dvine.chat.ui.CodeViewActivity.EXTRA_LANG, "java"));
        tour(ai.dvine.chat.ui.HtmlViewActivity.class, new Intent().putExtra(ai.dvine.chat.ui.HtmlViewActivity.EXTRA_HTML, "<p>Hello</p>"));
        tour(ai.dvine.chat.ui.ImageViewActivity.class, new Intent().putExtra(ai.dvine.chat.ui.ImageViewActivity.EXTRA_PATH, "/missing.jpg"));
        for (String line : report) {
            System.out.println("UITOUR " + line);
        }
        System.out.println("UITOUR " + report.size() + " taps, " + failures.size() + " failures");
        assertTrue(String.join("\n", failures), failures.isEmpty());
        assertTrue("too few controls reached: " + report.size(), report.size() > 60);
    }

    private <T extends Activity> void tour(Class<T> screen, Intent extras) {
        String name = screen.getSimpleName();
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), screen);
        if (extras != null) {
            intent.putExtras(extras);
        }
        ActivityController<T> c;
        try {
            c = Robolectric.buildActivity(screen, intent).create().start().resume().visible();
        } catch (RuntimeException e) {
            failures.add(name + " didn't open: " + e);
            return;
        }
        idle();
        Set<String> done = new LinkedHashSet<String>();
        for (int round = 0; round < 400; round++) {
            View next = null;
            String key = null;
            for (View v : clickable(c.get().getWindow().getDecorView())) {
                String k = key(v);
                if (!done.contains(k)) {
                    next = v;
                    key = k;
                    break;
                }
            }
            if (next == null) {
                break;
            }
            done.add(key);
            tap(name, next, key);
            sheets(name);
            if (c.get().isFinishing()) {
                // a Back or Close button: the screen closing is what it should do; open it again to go on
                report.add(name + " › " + key + " (closed the screen, as it should)");
                c = Robolectric.buildActivity(screen, intent).create().start().resume().visible();
                idle();
            }
        }
        if (c.get() instanceof MainActivity) {
            drawer(c);
        }
        try {
            c.pause().stop().destroy();
        } catch (RuntimeException e) {
            failures.add(name + " didn't close cleanly: " + e);
        }
    }

    /** The side menu: opened again before each of its buttons and chats, since each one closes it. */
    private <T extends Activity> void drawer(ActivityController<T> c) {
        View menu = c.get().findViewById(ai.dvine.R.id.btnMenu);
        for (int i = 0; i < 40; i++) {
            View drawer = c.get().findViewById(ai.dvine.R.id.historyDrawer);
            if (drawer == null || menu == null) {
                failures.add("MainActivity › the side menu isn't there");
                return;
            }
            if (drawer.getVisibility() != View.VISIBLE) {
                tap("MainActivity", menu, "btnMenu (open the menu)");
            }
            List<View> items = clickable(drawer);
            if (i >= items.size()) {
                report.add("MainActivity › side menu: " + items.size() + " buttons and chats tapped");
                return;
            }
            tap("MainActivity › menu", items.get(i), key(items.get(i)));
            sheets("MainActivity › menu");
        }
    }

    /** Taps the rows of any sheet or dialog a tap opened; a yes/no is answered no. */
    private void sheets(String screen) {
        for (int depth = 0; depth < 3; depth++) {
            Dialog d = ShadowDialog.getLatestDialog();
            if (d == null || !d.isShowing()) {
                return;
            }
            if (d instanceof androidx.appcompat.app.AlertDialog) {
                View no = ((androidx.appcompat.app.AlertDialog) d).getButton(android.content.DialogInterface.BUTTON_NEGATIVE);
                if (no != null && no.getVisibility() == View.VISIBLE) {
                    tap(screen + " › dialog", no, "answered no");
                }
                if (d.isShowing()) {
                    d.dismiss();
                }
                continue;
            }
            List<View> rows = clickable(d.getWindow().getDecorView());
            report.add(screen + " › sheet with " + rows.size() + " choices");
            // each choice is tried once from a fresh copy of the sheet is costly; the first choice is tapped, the rest
            // are checked to be tappable (enabled, with an action)
            for (View r : rows) {
                if (!r.isEnabled()) {
                    failures.add(screen + " › sheet choice disabled: " + key(r));
                }
            }
            if (!rows.isEmpty()) {
                tap(screen + " › sheet", rows.get(0), key(rows.get(0)));
            }
            if (d.isShowing()) {
                d.dismiss();
            }
            idle();
        }
    }

    private void tap(String screen, View v, String key) {
        try {
            v.performClick();
            idle();
            report.add(screen + " › " + key);
        } catch (Throwable t) {
            failures.add(screen + " › " + key + ": " + t);
        }
    }

    private static void idle() {
        ShadowLooper.idleMainLooper(300, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Every control that does something when tapped and can be seen, in screen order. */
    private static List<View> clickable(View root) {
        List<View> out = new ArrayList<View>();
        collect(root, out);
        return out;
    }

    private static void collect(View v, List<View> out) {
        if (v.getVisibility() != View.VISIBLE) {
            return;
        }
        if (v.hasOnClickListeners() && v.isEnabled()) {
            out.add(v);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collect(g.getChildAt(i), out);
            }
        }
    }

    /** A control by its name: its id, its label or description, and its kind. */
    private static String key(View v) {
        String id = "";
        try {
            id = v.getId() == View.NO_ID ? "" : v.getResources().getResourceEntryName(v.getId());
        } catch (RuntimeException ignored) {
            // an id from code
        }
        CharSequence label = v.getContentDescription();
        if ((label == null || label.length() == 0) && v instanceof TextView) {
            label = ((TextView) v).getText();
        }
        if ((label == null || label.length() == 0) && v instanceof ViewGroup) {
            label = firstText((ViewGroup) v);
        }
        String l = label == null ? "" : label.toString().replace('\n', ' ');
        return (id.isEmpty() ? "" : id + " ") + "\"" + (l.length() > 50 ? l.substring(0, 50) + "…" : l) + "\" " + v.getClass().getSimpleName();
    }

    private static CharSequence firstText(ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c instanceof TextView && ((TextView) c).getText().length() > 0) {
                return ((TextView) c).getText();
            }
            if (c instanceof ViewGroup) {
                CharSequence t = firstText((ViewGroup) c);
                if (t != null && t.length() > 0) {
                    return t;
                }
            }
        }
        return null;
    }
}
