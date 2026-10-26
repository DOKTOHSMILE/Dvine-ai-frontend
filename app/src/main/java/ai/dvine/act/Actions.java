package ai.dvine.act;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import ai.dvine.brain.PendingAction;

/**
 * Runs a confirmed action. Share hands the text to the social app's post
 * screen; the user presses send there. This is a hand-off, not a post.
 */
public final class Actions {
    private Actions() {
    }

    /** Puts text on the clipboard. Returns false if the system refused. */
    public static boolean copy(Context context, String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            return false;
        }
        cm.setPrimaryClip(ClipData.newPlainText("DVINE", text));
        return true;
    }

    /** Opens the target app (or the share menu) with the text. Returns false if nothing could open it. */
    public static boolean share(Context context, PendingAction a) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, a.content);
        send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String pkg = packageFor(a.app);
        if (pkg != null) {
            Intent direct = new Intent(send).setPackage(pkg);
            try {
                context.startActivity(direct);
                return true;
            } catch (ActivityNotFoundException notInstalled) {
                if ("x".equals(a.app)) {
                    Intent web = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://twitter.com/intent/tweet?text=" + Uri.encode(a.content)));
                    web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(web);
                        return true;
                    } catch (ActivityNotFoundException ignored) {
                        // fall through to the chooser
                    }
                }
            }
        }
        try {
            Intent chooser = Intent.createChooser(send, "Share with");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    static String packageFor(String app) {
        if (app == null) {
            return null;
        }
        switch (app) {
            case "whatsapp": return "com.whatsapp";
            case "x": return "com.twitter.android";
            case "facebook": return "com.facebook.katana";
            case "instagram": return "com.instagram.android";
            case "linkedin": return "com.linkedin.android";
            case "telegram": return "org.telegram.messenger";
            default: return null;
        }
    }
}
