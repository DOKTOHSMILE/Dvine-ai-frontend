package ai.ibiko.social;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The user's social accounts at work on the phone: each account's sign-in (the site's cookies) kept encrypted with a
 * key in the Android Keystore and swapped in when that account is used; and reading with an account, in a WebView
 * that isn't shown, the way the user would see the site: searches and pages, within {@link SocialLimits}, stopping and
 * pausing the account when the site shows a warning or a check. Only the network's own pages are opened. Reading is
 * blocking: call it off the main thread.
 */
public final class SocialWeb {
    private SocialWeb() {
    }

    private static SocialAccounts accounts;
    private static SocialLimits limits;
    private static final Object READING = new Object();
    static final long PAUSE_MS = 24 * 3_600_000L;
    /** How long a page may take; then it is read as it is. */
    static final long PAGE_MS = 25_000L, SETTLE_MS = 3_500L;

    public static synchronized SocialAccounts accounts(Context c) {
        telegramIn = telegramIn || c.getSharedPreferences("ibiko", Context.MODE_PRIVATE).getBoolean("telegram_in", false);
        if (accounts == null) {
            accounts = new SocialAccounts(new File(new File(c.getFilesDir(), "social"), "accounts.txt"));
        }
        SharedPreferences prefs = prefs(c);
        for (Platform p : Platform.values()) {
            accounts.setOn(p, on(prefs, p)); // the switches in Connectors
        }
        return accounts;
    }

    /** The network's switch in Connectors (on unless switched off). */
    public static boolean on(SharedPreferences prefs, Platform p) {
        return prefs.getBoolean("social_on_" + p.name(), true);
    }

    public static void setOn(Context c, Platform p, boolean on) {
        prefs(c).edit().putBoolean("social_on_" + p.name(), on).apply();
        accounts(c);
    }

    public static synchronized SocialLimits limits(Context c) {
        SharedPreferences prefs = prefs(c);
        if (limits == null) {
            limits = new SocialLimits();
            limits.restore(prefs.getString("social_limits", null));
        }
        limits.scale("careful".equals(prefs.getString("social_pace", "normal")) ? 0.5f : 1f);
        return limits;
    }

    public static synchronized void keepLimits(Context c) {
        if (limits != null) {
            prefs(c).edit().putString("social_limits", limits.save()).apply();
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("ibiko", Context.MODE_PRIVATE);
    }

    // ---- sign-ins

    /** Whether the network's cookies on the phone now hold a signed-in session. */
    public static boolean signedInNow(Platform p) {
        if (p == Platform.TELEGRAM) {
            return telegramIn; // Telegram Web keeps its sign-in in the page's storage, not a cookie
        }
        CookieManager cm = CookieManager.getInstance();
        for (String site : p.sites) {
            if (p.signedIn(cm.getCookie(site))) {
                return true;
            }
        }
        return false;
    }

    /** Keeps the account's sign-in as it is now on the phone (encrypted), so it can be swapped back in later. */
    public static void keep(Context c, SocialAccounts.Account a) {
        CookieManager cm = CookieManager.getInstance();
        StringBuilder b = new StringBuilder();
        for (String site : a.platform.sites) {
            String line = cm.getCookie(site);
            if (line != null && !line.trim().isEmpty()) {
                b.append(site).append('\t').append(line).append('\n');
            }
        }
        if (b.length() == 0) {
            return;
        }
        byte[] sealed = seal(b.toString().getBytes(StandardCharsets.UTF_8));
        if (sealed == null) {
            return; // no Keystore: the live sign-in still works, it just can't be swapped
        }
        try {
            File f = cookieFile(c, a.id);
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), sealed);
        } catch (java.io.IOException ignored) {
            // not kept
        }
    }

    /** Signs the network out on the phone (its cookies only; other sites keep theirs). */
    public static void clear(Platform p) {
        CookieManager cm = CookieManager.getInstance();
        for (String site : p.sites) {
            String line = cm.getCookie(site);
            if (line == null) {
                continue;
            }
            String domain = "." + base(site);
            for (String part : line.split(";")) {
                int eq = part.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String name = part.substring(0, eq).trim();
                cm.setCookie(site, name + "=; Max-Age=0; Path=/");
                cm.setCookie(site, name + "=; Max-Age=0; Path=/; Domain=" + domain);
            }
        }
        cm.flush();
    }

    /** Puts the account's kept sign-in back on the phone; false when there is none to put back. */
    public static boolean restore(Context c, SocialAccounts.Account a) {
        byte[] plain;
        try {
            File f = cookieFile(c, a.id);
            plain = f.isFile() ? open(Files.readAllBytes(f.toPath())) : null;
        } catch (java.io.IOException e) {
            plain = null;
        }
        if (plain == null) {
            return false;
        }
        clear(a.platform);
        CookieManager cm = CookieManager.getInstance();
        for (String line : new String(plain, StandardCharsets.UTF_8).split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) {
                continue;
            }
            String site = line.substring(0, tab);
            for (String part : line.substring(tab + 1).split(";")) {
                String nv = part.trim();
                if (nv.indexOf('=') > 0) {
                    cm.setCookie(site, nv + "; Path=/; Domain=." + base(site) + "; Secure; Max-Age=31536000");
                }
            }
        }
        cm.flush();
        return true;
    }

    /** Uses another account on its network: the one in use now is kept, and this one's sign-in put in its place. */
    public static boolean switchTo(Context c, String id) {
        SocialAccounts all = accounts(c);
        SocialAccounts.Account to = all.get(id);
        if (to == null) {
            return false;
        }
        SocialAccounts.Account now = all.inUse(to.platform);
        if (now != null && now.id.equals(id)) {
            return true;
        }
        if (now != null && signedInNow(to.platform)) {
            keep(c, now);
        }
        boolean back = restore(c, to);
        all.use(id);
        all.signedIn(id, back && signedInNow(to.platform));
        return back;
    }

    /** Forgets an account: its kept sign-in, its counts, and (when it is in use) the sign-in on the phone. */
    public static void remove(Context c, String id) {
        SocialAccounts all = accounts(c);
        SocialAccounts.Account a = all.get(id);
        if (a == null) {
            return;
        }
        if (a.inUse) {
            clear(a.platform);
        }
        cookieFile(c, id).delete();
        limits(c).forget(id);
        keepLimits(c);
        all.remove(id);
        SocialAccounts.Account next = all.inUse(a.platform);
        if (next != null) {
            restore(c, next);
        }
    }

    static File cookieFile(Context c, String id) {
        return new File(new File(c.getFilesDir(), "social"), id.replaceAll("[^a-z0-9-]", "") + ".ck");
    }

    /** "x.com" from "https://www.x.com". */
    static String base(String site) {
        String h = site.replaceFirst("^https?://", "").replaceAll("/.*$", "");
        return h.replaceFirst("^(www|m|mbasic|lm|touch|old|mobile)\\.", "");
    }

    /** Whether Telegram Web is signed in on this device (seen in its page storage when signing in, or on a read). */
    private static volatile boolean telegramIn;

    /** Telegram's sign-in as seen now (its page storage), kept for the next start. */
    public static void telegramSignedIn(Context c, boolean in) {
        telegramIn = in;
        c.getSharedPreferences("ibiko", Context.MODE_PRIVATE).edit().putBoolean("telegram_in", in).apply();
    }

    /**
     * The script that tells whether Telegram Web is signed in: the sign-in form is gone and the page's storage holds a
     * signed-in account (number_of_accounts above 0, or an account with the user's id). The data-centre keys
     * ("dc2_auth_key", the account1… records without a user) are made as soon as the page opens, before anyone signs
     * in, so they don't count.
     */
    public static final String TELEGRAM_SIGNED_IN = "(function(){try{var t=document.body?document.body.innerText:'';"
            + "if(/Log in by QR Code|Log in to Telegram by QR Code|Log in by phone Number|LOG IN BY PHONE NUMBER|Please confirm your country code|Sign in to Telegram/.test(t))return false;"
            + "if(parseInt(localStorage.getItem('number_of_accounts')||'0',10)>0)return true;"
            + "var keys=['user_auth','account1','account2','account3','account4'];"
            + "for(var i=0;i<keys.length;i++){var v=localStorage.getItem(keys[i]);if(!v)continue;var o=JSON.parse(v);"
            + "if(o&&(o.userId||o.id||(o.userAuth&&(o.userAuth.id||o.userAuth.userId))))return true;}"
            + "return false;}catch(e){return false;}})()";

    /** Telegram's sign-in page, by its words: the account is signed out. */
    static boolean telegramSignedOut(String text) {
        String t = text == null ? "" : text;
        return t.contains("Log in to Telegram by QR Code") || t.contains("Log in by QR Code") || t.contains("Log in by phone Number")
                || t.contains("LOG IN BY PHONE NUMBER") || t.contains("Sign in to Telegram");
    }

    // ---- reading

    /**
     * Looks something up on one network (or each signed-in one, when {@code only} is null) with the user's accounts:
     * the posts as labelled text for the model, and what couldn't be read, and why.
     */
    /** Told each page read (set by the app): Ibiko 1 learns language from it. */
    public static volatile java.util.function.Consumer<String> onRead;

    public static String search(Context c, Platform only, String query, int most) {
        SocialAccounts all = accounts(c);
        List<Platform> nets = only != null ? java.util.Collections.singletonList(only) : all.ready();
        if (nets.isEmpty()) {
            return "No social account is signed in on this device. The user can add one: menu → Social accounts.";
        }
        StringBuilder out = new StringBuilder();
        int each = Math.max(1200, most / nets.size());
        for (Platform p : nets) {
            if (p == Platform.TELEGRAM && !SocialAsk.telegramChat(query)) {
                // the chat list is not a search; do not hand it back as if the words were found
                out.append("Telegram: no word search. This connector only opens a chat named @name or t.me/name.\n\n");
                continue;
            }
            String r = read(c, p, p.searchUrl(query), p.otherSearchUrl(query), "search for \u201c" + query + "\u201d", each, query);
            out.append(r).append("\n\n");
        }
        return out.toString().trim();
    }

    /** Lifts every paused account. The user said the site's check is finished. */
    public static int resumePaused(Context c) {
        SocialLimits lim = limits(c);
        long now = System.currentTimeMillis();
        int n = 0;
        for (SocialAccounts.Account a : accounts(c).all()) {
            if (lim.pausedWhy(a.id, now) != null) {
                lim.resume(a.id);
                n++;
            }
        }
        if (n > 0) {
            keepLimits(c);
        }
        return n;
    }

    /** What is trending on one network (or each signed-in one), as labelled text for the model. */
    public static String trending(Context c, Platform only, int most) {
        List<Platform> nets = only != null ? java.util.Collections.singletonList(only) : accounts(c).ready();
        if (nets.isEmpty()) {
            return "No social account is signed in on this device. The user can add one: menu \u2192 Social accounts.";
        }
        StringBuilder out = new StringBuilder();
        int each = Math.max(1500, most / nets.size());
        for (Platform p : nets) {
            if (p == Platform.TELEGRAM) {
                out.append("Telegram: no trending page, so nothing was read.\n\n");
                continue;
            }
            String what = p == Platform.INSTAGRAM ? "Instagram Explore, not a trends list"
                    : p == Platform.FACEBOOK ? "Facebook Watch, not a trends list" : "what is trending now";
            out.append(read(c, p, p.trendingUrl(), what, each)).append("\n\n");
        }
        return out.toString().trim();
    }

    /** Opens one page of a network (a post, a profile) with the user's account. */
    public static String open(Context c, String url, int most) {
        for (Platform p : Platform.values()) {
            if (p.owns(url)) {
                return read(c, p, url, url, most);
            }
        }
        if (url != null && url.matches("(?i)^https?://t\\.me/[A-Za-z0-9_]+/?$")) {
            String name = url.replaceAll("(?i)^https?://t\\.me/|/$", "");
            return read(c, Platform.TELEGRAM, "https://web.telegram.org/k/#@" + name, url, most);
        }
        return "Only Facebook, X, Instagram, Reddit and Telegram pages are opened with the user's accounts.";
    }

    /** One page, within the limits; a warning pauses the account. A pause does not block a search the user just asked for. */
    static String read(Context c, Platform p, String url, String what, int most) {
        return read(c, p, url, null, what, most, null);
    }

    static String read(Context c, Platform p, String url, String alt, String what, int most, String query) {
        SocialAccounts all = accounts(c);
        SocialAccounts.Account a = all.inUse(p);
        if (a == null) {
            return p.shown + ": no account on this device. The user can add one: menu \u2192 Social accounts.";
        }
        if (!all.on(p, true)) {
            return p.shown + " is switched off in Connectors, so it wasn't read.";
        }
        SocialLimits lim = limits(c);
        synchronized (READING) {
            boolean wasPaused = lim.pausedWhy(a.id, System.currentTimeMillis()) != null;
            SocialLimits.Verdict v = lim.check(a.id, p, SocialLimits.Kind.READ, System.currentTimeMillis(), !wasPaused);
            if (!v.ok && v.waitMs > 0 && v.waitMs <= 20_000) {
                try {
                    Thread.sleep(v.waitMs); // a short gap, as a person would leave
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return p.shown + ": stopped.";
                }
                v = lim.check(a.id, p, SocialLimits.Kind.READ, System.currentTimeMillis(), !wasPaused);
            }
            if (!v.ok) {
                return p.shown + ": not read. " + v.why;
            }
            lim.record(a.id, SocialLimits.Kind.READ, System.currentTimeMillis());
            keepLimits(c);
            String[] page = load(c, p, url, alt == null ? PAGE_MS : 12_000L);
            if (warning(page)) {
                return stopped(c, lim, a, p, page);
            }
            if (!posts(page) && alt != null && !alt.equals(url)) {
                String[] next = load(c, p, alt, 10_000L);
                if (warning(next)) {
                    return stopped(c, lim, a, p, next);
                }
                if (posts(next) || (!worth(page) && worth(next))) {
                    page = next;
                }
            }
            if (!posts(page)) {
                String[] plain = fetchPlain(c, url);
                if (warning(plain)) {
                    return stopped(c, lim, a, p, plain);
                }
                if (posts(plain)) {
                    page = plain;
                }
            }
            if (!posts(page) && alt != null && !alt.equals(url)) {
                String[] plain = fetchPlain(c, alt);
                if (warning(plain)) {
                    return stopped(c, lim, a, p, plain);
                }
                if (posts(plain)) {
                    page = plain;
                }
            }
            if (posts(page)) {
                return show(c, all, lim, a, p, page, what, most, wasPaused);
            }
            String pub = publicHits(c, p, query);
            if (page != null && SocialPage.looksSignedOut(page[1])) {
                String note = p.shown + ": the account \u201c" + a.label + "\u201d looks signed out, so the feed wasn't read.";
                return pub.isEmpty() ? note + " The user can sign in again under Social accounts." : note + pub;
            }
            if (!pub.isEmpty()) {
                return p.shown + ": the signed-in page didn't load." + pub;
            }
            return p.shown + ": the page didn't load. I tried the plain page and the full page.";
        }
    }

    /** A warning or a check on the page: pause, and say so. */
    private static boolean warning(String[] page) {
        return page != null && page[1] != null && SocialPage.of(page[0], page[1]).state == SocialPage.State.WARNING;
    }

    /** Real posts: not a blank shell, a login form, or a warning. */
    private static boolean posts(String[] page) {
        if (page == null || page[1] == null) {
            return false;
        }
        if (SocialPage.of(page[0], page[1]).state != SocialPage.State.OK || SocialPage.looksSignedOut(page[1])) {
            return false;
        }
        return SocialPage.usable(page[1]);
    }

    /** Posts, a sign-out, or a warning: something to act on. A blank shell is not. */
    private static boolean worth(String[] page) {
        if (page == null || page[1] == null) {
            return false;
        }
        SocialPage.State s = SocialPage.of(page[0], page[1]).state;
        if (s != SocialPage.State.OK) {
            return true;
        }
        if (SocialPage.looksSignedOut(page[1])) {
            return true;
        }
        return SocialPage.usable(page[1]);
    }

    private static String stopped(Context c, SocialLimits lim, SocialAccounts.Account a, Platform p, String[] page) {
        SocialPage state = SocialPage.of(page[0], page[1]);
        lim.pause(a.id, state.why, System.currentTimeMillis() + PAUSE_MS);
        keepLimits(c);
        ai.ibiko.act.ActionLog.add(c.getFilesDir(), "pause", p.shown + " \u00b7 " + a.label, "auto", "paused 24 h", state.why);
        return p.shown + ": stopped and paused this account for 24 hours, because " + state.why
                + ". If you've finished the check in the " + p.shown + " app, say so (\u201cI cleared it\u201d) and I'll look again.";
    }

    private static String show(Context c, SocialAccounts all, SocialLimits lim, SocialAccounts.Account a, Platform p,
            String[] page, String what, int most, boolean wasPaused) {
        SocialPage state = SocialPage.of(page[0], page[1]);
        if (p == Platform.TELEGRAM && telegramSignedOut(page[1])) {
            telegramSignedIn(c, false);
            state = SocialPage.of("https://web.telegram.org/login", "");
        }
        if (state.state == SocialPage.State.SIGNED_OUT || SocialPage.looksSignedOut(page[1])) {
            all.signedIn(a.id, false);
            return p.shown + ": the account \u201c" + a.label + "\u201d is signed out. The user can sign in again under Social accounts.";
        }
        if (wasPaused) {
            lim.resume(a.id); // the check is gone: the account can be used again
            keepLimits(c);
        }
        all.signedIn(a.id, true);
        String links = SocialPage.linksIn(page[1]);
        String text = SocialPage.text(page[1], Math.min(most, 2200));
        if (!links.isEmpty()) {
            StringBuilder extra = new StringBuilder();
            for (String link : links.split("\n")) {
                if (!text.contains(link)) {
                    extra.append("LINK ").append(link).append('\n');
                }
            }
            text = (text + "\n" + extra).trim();
        }
        java.util.function.Consumer<String> learn = onRead;
        if (learn != null && text != null) {
            learn.accept(text); // people's words: Ibiko 1 reads them for language only, never as facts
        }
        String lead = wasPaused ? p.shown + ": the pause is off, the page loaded.\n" : "";
        return lead + SocialPage.forModel(p, what, text);
    }

    /** The plain page over HTTPS, with the account's cookies, when the signed-in browser view didn't draw. */
    private static String[] fetchPlain(Context c, String url) {
        if (url == null || !url.startsWith("https://")) {
            return null;
        }
        String cookie = null;
        try {
            cookie = CookieManager.getInstance().getCookie(url);
        } catch (RuntimeException ignored) {
            cookie = null;
        }
        if (cookie != null) {
            cookie = cookie.replace("\r", "").replace("\n", "").trim();
            if (cookie.isEmpty()) {
                cookie = null;
            }
        }
        ai.ibiko.net.WebGet.Page got = ai.ibiko.net.WebGet.page(url, 10_000, 500_000, cookie);
        if (got == null || got.body == null || got.body.isEmpty()) {
            return null;
        }
        return new String[]{url, SocialPage.fromHtml(got.body, url)};
    }

    /** Public web results for the same words, so a comment can still be written when the account page fails. */
    private static String publicHits(Context c, Platform p, String query) {
        if (query == null || query.trim().isEmpty() || p == Platform.TELEGRAM) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        try {
            String q = java.net.URLEncoder.encode(query + " " + p.shown, java.nio.charset.StandardCharsets.UTF_8.name()).replace("+", "%20");
            ai.ibiko.net.WebGet.Page got = ai.ibiko.net.WebGet.page("https://html.duckduckgo.com/html/?q=" + q, 10_000, 500_000);
            String found = got == null ? "" : SocialPage.webResults(got.body);
            if (!found.isEmpty()) {
                b.append("\n\nPublic pages for the same words:\n").append(found);
            }
        } catch (java.io.UnsupportedEncodingException | RuntimeException ignored) {
            // the signed-in page already failed: a public lookup is only a help
        }
        if (c != null && b.length() == 0) {
            try {
                java.util.List<ai.ibiko.net.WebSearch.Hit> hits = ai.ibiko.net.GoogleWeb.search(c, query + " " + p.shown, 5);
                StringBuilder on = new StringBuilder();
                StringBuilder off = new StringBuilder();
                if (hits != null) {
                    for (ai.ibiko.net.WebSearch.Hit h : hits) {
                        if (h.url == null || h.url.isEmpty()) {
                            continue;
                        }
                        StringBuilder line = new StringBuilder();
                        line.append("\n- ").append(h.title == null ? "" : h.title).append("\n  ");
                        if (about(p, h.url)) {
                            line.append("LINK ");
                        }
                        line.append(h.url);
                        if (h.snippet != null && !h.snippet.isEmpty()) {
                            line.append("\n  ").append(h.snippet);
                        }
                        (about(p, h.url) ? on : off).append(line);
                    }
                }
                if (on.length() > 0) {
                    b.append("\n\nPublic pages on ").append(p.shown).append(":").append(on);
                } else if (off.length() > 0) {
                    return "\n\nNo " + p.shown + " post link came back. These public pages mention the same words:" + off
                            + "\nWrite the comment in the reply. Do not post it until a " + p.shown + " post link is in hand.";
                }
            } catch (RuntimeException ignored) {
                // a robot check, or no browser: the caller says the page didn't load
            }
        }
        if (b.length() == 0) {
            return "";
        }
        b.append("\nTo comment under a post, write \u00A7SO COMMENT ").append(p.name().toLowerCase(java.util.Locale.US))
                .append(": <the comment> <the post link>. The user says yes before it is posted.");
        return b.toString();
    }

    /** Whether a public link is on this network (a comment has to land on its own site). */
    private static boolean about(Platform p, String url) {
        String u = url.toLowerCase(java.util.Locale.US);
        switch (p) {
            case FACEBOOK:
                return u.contains("facebook.com") || u.contains("fb.watch");
            case X:
                return u.contains("x.com") || u.contains("twitter.com");
            case REDDIT:
                return u.contains("reddit.com");
            case INSTAGRAM:
                return u.contains("instagram.com");
            default:
                return false;
        }
    }

    /** Text plus the post links on the page, for the model. */
    static final String READ_PAGE = "(function(){var t=document.body?document.body.innerText:'';var links=[],seen={};"
            + "var as=document.links||[];for(var i=0;i<as.length&&links.length<8;i++){var h=as[i].href||'';"
            + "if(!h||seen[h])continue;var l=h.toLowerCase();"
            + "if(l.indexOf('/posts/')>=0||l.indexOf('/permalink/')>=0||l.indexOf('story.php')>=0||l.indexOf('/status/')>=0"
            + "||l.indexOf('/comments/')>=0||l.indexOf('/photo.php')>=0||l.indexOf('reddit.com/r/')>=0){seen[h]=1;links.push('LINK '+h);}}"
            + "return t+(links.length?'\\n'+links.join('\\n'):'');})()";

    /** The page in a WebView that isn't shown: where it ended up and its text, or null when it didn't load. */
    static String[] load(Context c, final Platform p, final String url) {
        return load(c, p, url, PAGE_MS);
    }

    static String[] load(Context c, final Platform p, final String url, long budget) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("reading a page blocks: call it off the main thread");
        }
        final Context app = c.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final CountDownLatch done = new CountDownLatch(1);
        final String[] out = new String[2];
        final WebView[] holder = new WebView[1];
        main.post(() -> {
            try {
                WebView w = new WebView(app);
                holder[0] = w;
                WebSettings s = w.getSettings();
                s.setJavaScriptEnabled(true);
                s.setDomStorageEnabled(true);
                s.setAllowFileAccess(false);
                s.setAllowContentAccess(false);
                s.setUserAgentString(userAgent(app));
                CookieManager.getInstance().setAcceptCookie(true);
                w.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY));
                w.layout(0, 0, 1080, 2400);
                final boolean[] started = {false};
                w.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        // the network's own pages only; anything else isn't followed
                        String to = request.getUrl().toString();
                        return !(p.owns(to) || to.contains("." + base(p.sites[0])) || SocialPage.of(to, "").state != SocialPage.State.OK);
                    }

                    @Override
                    public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                        if (request == null || !request.isForMainFrame() || started[0]) {
                            return;
                        }
                        started[0] = true;
                        out[0] = request.getUrl().toString();
                        out[1] = "";
                        done.countDown();
                    }

                    @Override
                    public void onPageFinished(WebView view, String at) {
                        if (started[0]) {
                            return;
                        }
                        started[0] = true;
                        // sites draw their posts after the page loads: wait, scroll once, wait, then read
                        main.postDelayed(() -> view.evaluateJavascript("window.scrollBy(0, 2400);", null), SETTLE_MS);
                        main.postDelayed(() -> view.evaluateJavascript(READ_PAGE, v -> {
                            out[0] = view.getUrl();
                            out[1] = unjson(v);
                            done.countDown();
                        }), SETTLE_MS * 2);
                    }
                });
                w.loadUrl(url);
            } catch (RuntimeException e) {
                done.countDown(); // no WebView on this phone
            }
        });
        try {
            done.await(budget, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (out[1] == null && holder[0] != null) {
            CountDownLatch late = new CountDownLatch(1);
            main.post(() -> {
                try {
                    holder[0].evaluateJavascript(READ_PAGE, v -> {
                        out[0] = holder[0].getUrl();
                        out[1] = unjson(v);
                        late.countDown();
                    });
                } catch (RuntimeException e) {
                    late.countDown();
                }
            });
            try {
                late.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        main.post(() -> {
            if (holder[0] != null) {
                holder[0].stopLoading();
                holder[0].destroy();
            }
        });
        CookieManager.getInstance().flush();
        return out[1] == null ? null : out;
    }

    /** The phone's own browser identity, without the "wv" mark some sites refuse to sign in with. */
    public static String userAgent(Context c) {
        return WebSettings.getDefaultUserAgent(c).replace("; wv)", ")");
    }

    static String unjson(String v) {
        if (v == null || v.equals("null")) {
            return "";
        }
        try {
            return new org.json.JSONArray("[" + v + "]").getString(0);
        } catch (org.json.JSONException e) {
            return v;
        }
    }

    // ---- the Keystore

    private static final String KEY = "ibiko_social_key";

    static byte[] seal(byte[] plain) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV(), ct = c.doFinal(plain);
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            return all;
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] open(byte[] sealed) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, sealed, 0, 12));
            return c.doFinal(sealed, 12, sealed.length - 12);
        } catch (Exception e) {
            return null;
        }
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY, null)).getSecretKey();
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gen.generateKey();
    }
}
