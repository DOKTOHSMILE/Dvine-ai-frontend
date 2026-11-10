package ai.dvine.social;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Posts on the pilot's own account, from inside DVINE, after their yes: the network's page is opened with the account
 * they signed in to (Social accounts), the words are typed into its composer and its own Post button is pressed, as
 * they would. It says "posted" only when the network shows it went (the composer closed, or its "sent" notice); if
 * anything is not where it should be, nothing is guessed: the post is handed to the app instead, for the pilot to
 * press Post. Facebook and X; the others take a photo, a community or a chat first, so they keep the hand-off.
 * Within the account's posting limits ({@link SocialLimits.Kind#POST}).
 */
public final class SocialPoster {
    private SocialPoster() {
    }

    /** How long a post may take, from opening the page to the network confirming it. */
    static final long POST_MS = 60_000;
    static final long TICK_MS = 1_500;

    /** What happened. */
    public static final class Result {
        public final boolean posted;
        public final String said;

        Result(boolean posted, String said) {
            this.posted = posted;
            this.said = said;
        }
    }

    /** The network for an app name ("facebook", "x"), when DVINE can post there itself; null otherwise. */
    public static Platform direct(String app) {
        if ("facebook".equals(app)) {
            return Platform.FACEBOOK;
        }
        if ("x".equals(app) || "twitter".equals(app)) {
            return Platform.X;
        }
        return null;
    }

    /** True when DVINE can post on this network now: a signed-in account in use and the network switched on. */
    public static boolean ready(Context c, Platform p) {
        if (p == null) {
            return false;
        }
        SocialAccounts all = SocialWeb.accounts(c);
        SocialAccounts.Account a = all.inUse(p);
        return a != null && all.on(p, true);
    }

    /** Where the composer is: X opens it with the words already in; Facebook's is opened from its feed. */
    static String composeUrl(Platform p, String text) {
        if (p == Platform.X) {
            return "https://x.com/intent/post?text=" + android.net.Uri.encode(text);
        }
        return "https://m.facebook.com/";
    }

    /** As {@link #script(String, String, java.util.List, boolean)}: a new post, no tags. */
    static String script(String net, String text) {
        return script(net, text, null, false);
    }

    /**
     * The steps on the page, run every {@link #TICK_MS}: open the composer (or the comment box), type the words, tag
     * each named person (typed as @Name, then the network's first suggestion picked), press the network's own Post
     * (or Comment, Reply) button once, then watch for the box to clear or the "sent" notice. Returns the stage.
     */
    static String script(String net, String text, java.util.List<String> tags, boolean comment) {
        StringBuilder t = new StringBuilder("[");
        if (tags != null) {
            for (int i = 0; i < tags.size(); i++) {
                t.append(i == 0 ? "" : ",").append(quote(tags.get(i)));
            }
        }
        t.append(']');
        return "(function(net,text,tags,comment){var d=window.__dv||(window.__dv={stage:0,ti:0,typed:false,tries:0,missed:[]});"
                + "function vis(e){return e&&e.getBoundingClientRect&&e.getBoundingClientRect().height>0;}"
                + "function q(s){var a=document.querySelectorAll(s);for(var i=0;i<a.length;i++)if(vis(a[i]))return a[i];return null;}"
                + "function byText(re){var a=document.querySelectorAll('[role=button],button,div[aria-label],span');"
                + "for(var i=0;i<a.length;i++){var t=(a[i].getAttribute('aria-label')||a[i].innerText||'').trim();"
                + "if(t.length<40&&re.test(t)&&vis(a[i]))return a[i];}return null;}"
                + "function box(){return q('[data-testid=tweetTextarea_0]')"
                + "||(comment?(q('[aria-label*=omment][contenteditable=true]')||q('textarea[name=comment_text]')||q('textarea[aria-label*=omment]')):null)"
                + "||q('div[contenteditable=true][role=textbox]')||q('textarea[name=xc_message]')||q('textarea');}"
                + "function said(b){return b?((b.tagName=='TEXTAREA'?b.value:b.innerText)||'').trim():'';}"
                + "function put(b,s){b.focus();if(b.tagName=='TEXTAREA'){var set=Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set;"
                + "set.call(b,(b.value||'')+s);b.dispatchEvent(new Event('input',{bubbles:true}));}else{document.execCommand('insertText',false,s);}}"
                + "var head=text.substring(0,Math.min(20,text.length));"
                + "if(/login|checkpoint/.test(location.href))return 'signedout';"
                + "if(d.stage==0){var b=box();"
                + "if(!b&&net=='facebook'&&!comment){var o=byText(/what.?s on your mind|write something/i);if(o){o.click();return 'opening';}}"
                + "if(!b&&comment){var c=byText(/^(comment|write a comment.*|reply)$/i);if(c){c.click();return 'opening';}}"
                + "if(!b)return 'waiting';if(said(b).indexOf(head)<0)put(b,text);d.stage=1;return 'typed';}"
                + "if(d.stage==1){if(d.ti>=tags.length){d.stage=2;return 'tagsdone';}var b1=box();if(!b1)return 'nottyped';"
                + "if(!d.typed){put(b1,' @'+tags[d.ti]);d.typed=true;d.tries=0;return 'tagging';}"
                + "var opt=q('[data-testid=typeaheadResult]')||q('[role=listbox] [role=option]')||q('li[role=option]')||q('[role=option]');"
                + "if(opt){opt.click();d.ti++;d.typed=false;return 'tagged';}"
                + "if(++d.tries>4){d.missed.push(tags[d.ti]);d.ti++;d.typed=false;return 'notag';}return 'tagwait';}"
                + "if(d.stage==2){var b2=box();if(b2&&said(b2).indexOf(head)<0)return 'nottyped';"
                + "var btn=q('[data-testid=tweetButton]')||q('[data-testid=tweetButtonInline]')"
                + "||byText(comment?/^(comment|post|reply|send)$/i:/^(post|share now|publish)$/i);"
                + "if(!btn)return 'nobutton';if(btn.getAttribute('aria-disabled')=='true'||btn.disabled)return 'disabled';"
                + "btn.click();d.stage=3;return 'clicked';}"
                + "if(d.stage==3){var b3=box();var body=document.body?document.body.innerText:'';"
                + "if(/your post was sent|your post is now|posted|shared to|your reply was sent/i.test(body))return 'done';"
                + "if(!b3||said(b3)=='')return 'done';return 'checking';}"
                + "return 'waiting';})(" + quote(net) + "," + quote(text) + "," + t + "," + comment + ")";
    }

    /** A JavaScript string literal of {@code s}: quotes, backslashes, line ends and other control characters escaped. */
    static String quote(String s) {
        String t = s == null ? "" : s;
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '"' || c == '\\') {
                b.append('\\').append(c);
            } else if (c == '\n') {
                b.append("\\n");
            } else if (c == '\r') {
                b.append("\\r");
            } else if (c < 0x20 || c == 0x2028 || c == 0x2029 || c == '<') {
                b.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c)); // "<" too: no </script> tricks
            } else {
                b.append(c);
            }
        }
        return b.append('"').toString();
    }

    /** Posts {@code text} on {@code p} with the account in use. Call it off the main thread. */
    public static Result post(Context c, Platform p, String text) {
        return post(c, p, text, null, null);
    }

    /**
     * Posts {@code text} on {@code p} with the account in use, tagging {@code tags}; with {@code commentOn} (a post's
     * link) it is a comment under that post instead. Call it off the main thread.
     */
    public static Result post(Context c, Platform p, String text, java.util.List<String> tags, String commentOn) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("posting waits for the page: call it off the main thread");
        }
        if (text == null || text.trim().isEmpty()) {
            return new Result(false, "There was nothing to post.");
        }
        SocialAccounts all = SocialWeb.accounts(c);
        SocialAccounts.Account a = all.inUse(p);
        if (a == null) {
            return new Result(false, "No " + p.shown + " account is signed in here (menu › Social accounts).");
        }
        SocialLimits lim = SocialWeb.limits(c);
        final boolean comment = commentOn != null && !commentOn.isEmpty();
        final SocialLimits.Kind kind = comment ? SocialLimits.Kind.COMMENT : SocialLimits.Kind.POST;
        SocialLimits.Verdict v = lim.check(a.id, p, kind, System.currentTimeMillis());
        if (!v.ok) {
            return new Result(false, "Not posted: " + v.why);
        }
        final String net = p == Platform.X ? "x" : "facebook";
        final String js = script(net, text.trim(), tags, comment);
        final String[] missed = {""};
        final Context app = c.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        final CountDownLatch done = new CountDownLatch(1);
        final String[] stage = {"waiting"};
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
                s.setUserAgentString(SocialWeb.userAgent(app));
                CookieManager.getInstance().setAcceptCookie(true);
                w.measure(android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY));
                w.layout(0, 0, 1080, 2400);
                w.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        return !p.owns(request.getUrl().toString()); // the network's own pages only
                    }
                });
                final long until = System.currentTimeMillis() + POST_MS;
                Runnable tick = new Runnable() {
                    @Override
                    public void run() {
                        if (done.getCount() == 0) {
                            return;
                        }
                        w.evaluateJavascript(js, value -> {
                            String st = value == null ? "" : value.replace("\"", "");
                            stage[0] = st;
                            if ("done".equals(st) || "signedout".equals(st) || System.currentTimeMillis() > until) {
                                w.evaluateJavascript("(window.__dv&&window.__dv.missed||[]).join(', ')", m -> {
                                    missed[0] = m == null ? "" : m.replace("\"", "");
                                    done.countDown();
                                });
                            } else {
                                main.postDelayed(this, TICK_MS);
                            }
                        });
                    }
                };
                w.loadUrl(comment ? commentOn : composeUrl(p, text.trim()));
                main.postDelayed(tick, 3_000); // the page draws its composer after it loads
            } catch (RuntimeException e) {
                stage[0] = "nowebview";
                done.countDown();
            }
        });
        try {
            done.await(POST_MS + 10_000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        main.post(() -> {
            if (holder[0] != null) {
                holder[0].stopLoading();
                holder[0].destroy();
            }
        });
        CookieManager.getInstance().flush();
        String st = stage[0];
        if ("done".equals(st)) {
            lim.record(a.id, SocialLimits.Kind.POST, System.currentTimeMillis());
            SocialWeb.keepLimits(c);
            ai.dvine.act.ActionLog.add(c.getFilesDir(), "post", p.shown + " · " + a.label, "approved", "posted", text);
            return new Result(true, "Posted on " + p.shown + " from your account (" + a.label + ").");
        }
        if ("clicked".equals(st) || "checking".equals(st)) {
            // Post was pressed but the network never showed it went: it may have; it is not said that it did
            ai.dvine.act.ActionLog.add(c.getFilesDir(), "post", p.shown + " · " + a.label, "approved", "unconfirmed", text);
            return new Result(true, "I pressed Post on " + p.shown + ", but the page didn't confirm it went out. Check your profile;"
                    + " I won't post it again, so it isn't doubled.");
        }
        if ("signedout".equals(st)) {
            all.signedIn(a.id, false);
            return new Result(false, "Your " + p.shown + " account is signed out here. Sign in again under Social accounts.");
        }
        String why = "nobutton".equals(st) ? "its Post button wasn't on the page" : "disabled".equals(st) ? "its Post button stayed greyed out"
                : "nottyped".equals(st) ? "the words didn't go into its composer" : "its composer didn't open";
        return new Result(false, "I couldn't post on " + p.shown + " myself: " + why + ".");
    }
}
