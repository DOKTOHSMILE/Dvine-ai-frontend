package ai.ibiko.llm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The AI providers the user set up, kept on this phone only: for each, its address, model, an optional CA
 * certificate, whether a test call worked, and its keys. The keys are encrypted with an AES key held in the
 * Android Keystore (it never leaves the phone's secure hardware where there is one), so the preferences file alone
 * doesn't reveal them. Nothing here is ever sent anywhere but the provider's own address.
 */
public final class ProviderStore {
    private static final String KEY_ALIAS = "ibiko_api_keys";
    private final SharedPreferences prefs;
    private final Context context;

    public ProviderStore(Context c) {
        context = c.getApplicationContext();
        prefs = context.getSharedPreferences("ibiko_providers", Context.MODE_PRIVATE);
    }

    public Providers active() {
        return Providers.named(prefs.getString("active", Providers.ON_PHONE.name()));
    }

    /** Makes this the primary brain; an API chosen this way goes to the front of the switching order. */
    public void setActive(Providers p) {
        java.util.List<Providers> o = order();
        o.remove(p);
        if (p.api()) {
            o.add(0, p);
        }
        prefs.edit().putString("active", p.name()).putString("order", join(trim(o))).apply();
    }

    /**
     * The APIs in the order they are tried (at most three): the primary first, then the backups. With automatic
     * switching off, only the primary.
     */
    public java.util.List<Providers> order() {
        java.util.List<Providers> o = new java.util.ArrayList<Providers>();
        for (String n : prefs.getString("order", "").split(",")) {
            Providers p = Providers.named(n.trim());
            if (p.api() && !o.contains(p)) {
                o.add(p);
            }
        }
        Providers a = active();
        if (a.api()) {
            o.remove(a);
            o.add(0, a);
        }
        return trim(o);
    }

    /** Where this API is in the switching order (0 primary, 1 and 2 backups), or -1. */
    public int place(Providers p) {
        return order().indexOf(p);
    }

    /** Adds an API as the next backup (at most three in all); false when the order is full. */
    public boolean addBackup(Providers p) {
        java.util.List<Providers> o = order();
        if (!p.api() || o.contains(p)) {
            return o.contains(p);
        }
        if (o.size() >= 3) {
            return false;
        }
        o.add(p);
        prefs.edit().putString("order", join(o)).apply();
        return true;
    }

    public void removeBackup(Providers p) {
        java.util.List<Providers> o = order();
        if (o.remove(p)) {
            prefs.edit().putString("order", join(o)).apply();
        }
    }

    public boolean autoSwitch() {
        return prefs.getBoolean("auto_switch", true);
    }

    public void setAutoSwitch(boolean on) {
        prefs.edit().putBoolean("auto_switch", on).apply();
    }

    /** The brains to use, in order, that are set up well enough to call (the primary only when switching is off). */
    public java.util.List<Providers> usable() {
        java.util.List<Providers> out = new java.util.ArrayList<Providers>();
        for (Providers p : order()) {
            if (RemoteBrain.check(address(p), model(p), key(p)) == null) {
                out.add(p);
            }
            if (!autoSwitch() && !out.isEmpty()) {
                break;
            }
        }
        return out;
    }

    /** Whether the user has any brain: an API set up, or nothing here (the phone's brain is checked elsewhere). */
    public boolean hasApi() {
        return !usable().isEmpty();
    }

    private static java.util.List<Providers> trim(java.util.List<Providers> o) {
        while (o.size() > 3) {
            o.remove(o.size() - 1);
        }
        return o;
    }

    private static String join(java.util.List<Providers> o) {
        StringBuilder b = new StringBuilder();
        for (Providers p : o) {
            b.append(b.length() == 0 ? "" : ",").append(p.name());
        }
        return b.toString();
    }

    /**
     * This phone's sign-in to its Ibiko server (a session token, renewed when it runs out), set by the app; null
     * while the app has none. The Ibiko server provider uses it instead of a key.
     */
    public static volatile java.util.function.Supplier<String> serverSession;
    /** The path of the server's own model (OpenAI form) under the server's address. */
    public static final String SERVER_MODEL_PATH = "/api/v1/llm";

    public String address(Providers p) {
        if (p == Providers.IBIKO_SERVER && !prefs.contains(p.name() + ".address")) {
            return serverRoot().replaceAll("/+$", "") + SERVER_MODEL_PATH; // the server the app already uses
        }
        return prefs.getString(p.name() + ".address", p.address);
    }

    /** The Ibiko server's address, as the app is set up with (Settings or the build's default). */
    private String serverRoot() {
        String set = context.getSharedPreferences("ibiko", Context.MODE_PRIVATE).getString("server", null);
        return set != null && !set.isEmpty() ? set : context.getString(ai.ibiko.R.string.api_base);
    }

    public String model(Providers p) {
        return prefs.getString(p.name() + ".model", p.model);
    }

    /**
     * Every model the user keeps for this provider, the one in use first: the brain picker lists them all, so the user
     * switches between them with one tap. Kept as one line, separated by commas.
     */
    public java.util.List<String> models(Providers p) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
        String now = model(p);
        if (now != null && !now.trim().isEmpty()) {
            out.add(now.trim());
        }
        for (String m : prefs.getString(p.name() + ".models", "").split(",")) {
            if (!m.trim().isEmpty()) {
                out.add(m.trim());
            }
        }
        return new java.util.ArrayList<String>(out);
    }

    /** Keeps {@code model} in this provider's list (nothing if it is there or empty). True when it was added. */
    public boolean addModel(Providers p, String model) {
        String m = model == null ? "" : model.trim().replace(",", "");
        java.util.List<String> all = models(p);
        if (m.isEmpty() || all.contains(m)) {
            return false;
        }
        all.add(m);
        keepModels(p, all);
        return true;
    }

    /** Takes {@code model} off this provider's list; the model in use stays (switch first). */
    public void removeModel(Providers p, String model) {
        java.util.List<String> all = models(p);
        if (model == null || model.equals(model(p))) {
            return;
        }
        all.remove(model);
        keepModels(p, all);
    }

    /** Answers with {@code model} from now on; the one it replaces stays on the list, to switch back to. */
    public void useModel(Providers p, String model) {
        String m = model == null ? "" : model.trim();
        if (m.isEmpty() || m.equals(model(p))) {
            return;
        }
        java.util.List<String> all = models(p);
        if (!all.contains(m)) {
            all.add(m);
        }
        prefs.edit().putString(p.name() + ".model", m).apply();
        keepModels(p, all);
    }

    private void keepModels(Providers p, java.util.List<String> all) {
        StringBuilder b = new StringBuilder();
        for (String m : all) {
            b.append(b.length() == 0 ? "" : ",").append(m);
        }
        prefs.edit().putString(p.name() + ".models", b.toString()).apply();
    }

    /** The fast model for simple questions; empty means the main model answers everything. */
    public String fastModel(Providers p) {
        return prefs.getString(p.name() + ".fast", p.fast);
    }

    public void setFastModel(Providers p, String fast) {
        prefs.edit().putString(p.name() + ".fast", fast == null ? "" : fast.trim()).apply();
    }

    /** Simple questions to the fast model, real work to the strong one (on by default). */
    public boolean smartRouting() {
        return prefs.getBoolean("smart_routing", true);
    }

    public void setSmartRouting(boolean on) {
        prefs.edit().putBoolean("smart_routing", on).apply();
    }

    public String certificate(Providers p) {
        return prefs.getString(p.name() + ".ca", "");
    }

    public boolean connected(Providers p) {
        return prefs.getBoolean(p.name() + ".ok", false);
    }

    public int window(Providers p) {
        return prefs.getInt(p.name() + ".window", p.window);
    }

    // ------------------------------------------------------------ advanced, per provider (all changeable)

    /** "auto", "on" or "off": whether the model is sent images. */
    public String vision(Providers p) {
        return prefs.getString(p.name() + ".vision", "auto");
    }

    /** "auto", "on" or "off": whether it is a reasoning model (room to think, no temperature). */
    /** A custom gateway's request format: "auto", "openai" or "anthropic". Other providers have theirs fixed. */
    public String format(Providers p) {
        return p == Providers.CUSTOM ? prefs.getString(p.name() + ".format", "auto") : "fixed";
    }

    public void saveFormat(Providers p, String format) {
        if (p == Providers.CUSTOM) {
            prefs.edit().putString(p.name() + ".format", format == null ? "auto" : format).apply();
        }
    }

    /**
     * The format requests go out in. A custom gateway on Auto starts from its address (…/messages or an "anthropic"
     * address is Anthropic's) and moves to the other format if the gateway says the path isn't there.
     */
    public RemoteBrain.Protocol protocol(Providers p) {
        if (p != Providers.CUSTOM) {
            return p.protocol;
        }
        String f = format(p);
        return "anthropic".equals(f) ? RemoteBrain.Protocol.ANTHROPIC : "openai".equals(f) ? RemoteBrain.Protocol.OPENAI
                : RemoteBrain.formatOf(address(p));
    }

    public String reasoning(Providers p) {
        return prefs.getString(p.name() + ".reasoning", "auto");
    }

    /** The most it may write in tokens; 0 lets Ibiko decide. */
    public int maxOutput(Providers p) {
        return prefs.getInt(p.name() + ".max_out", 0);
    }

    /**
     * Seconds the connection may stay silent. 180 was the old default, kept on every sheet that was saved: it cut off
     * large models that think for minutes before writing, so it reads as the new default.
     */
    public int timeout(Providers p) {
        int s = prefs.getInt(p.name() + ".timeout", RemoteBrain.DEFAULT_TIMEOUT_S);
        return s == 180 ? RemoteBrain.DEFAULT_TIMEOUT_S : s;
    }

    /** Extra request fields, JSON ("" for none). */
    public String extra(Providers p) {
        return prefs.getString(p.name() + ".extra", "");
    }

    public void saveAdvanced(Providers p, String vision, String reasoning, int maxOutput, int timeout, String extra, int window) {
        prefs.edit().putString(p.name() + ".vision", vision).putString(p.name() + ".reasoning", reasoning)
                .putInt(p.name() + ".max_out", Math.max(0, maxOutput)).putInt(p.name() + ".timeout", Math.max(10, timeout))
                .putString(p.name() + ".extra", extra == null ? "" : extra.trim())
                .putInt(p.name() + ".window", window > 0 ? window : p.window).apply();
    }

    private RemoteBrain configured(Providers p, RemoteBrain b) {
        return b.options(vision(p), reasoning(p), maxOutput(p), timeout(p), extra(p));
    }

    public String key(Providers p) {
        if (p == Providers.IBIKO_SERVER && serverSession != null) {
            return SIGNED_IN; // the live session is fetched at each request (RemoteBrain.keySource)
        }
        return open(prefs.getString(p.name() + ".key", ""));
    }

    /** Stands in for a key where the phone's sign-in is used instead. */
    static final String SIGNED_IN = "signed-in";

    public String backupKey(Providers p) {
        return open(prefs.getString(p.name() + ".backup", ""));
    }

    public boolean hasKey(Providers p) {
        if (p == Providers.IBIKO_SERVER) {
            return serverSession != null;
        }
        return !prefs.getString(p.name() + ".key", "").isEmpty();
    }

    /** Saves what the user typed; a changed setting clears "connected" until the next test. */
    public void save(Providers p, String address, String model, String key, String backup, String ca) {
        boolean changed = !RemoteBrain.normalise(address).equals(RemoteBrain.normalise(address(p))) || !model.trim().equals(model(p))
                || !key.trim().equals(key(p)) || !ca.trim().equals(certificate(p));
        if (!model.trim().isEmpty() && !model.trim().equals(model(p))) {
            java.util.List<String> all = models(p); // the model it replaces stays on the list, to switch back to
            all.add(model.trim());
            keepModels(p, all);
        }
        SharedPreferences.Editor e = prefs.edit()
                .putString(p.name() + ".address", RemoteBrain.normalise(address))
                .putString(p.name() + ".model", model.trim())
                .putString(p.name() + ".key", seal(key.trim()))
                .putString(p.name() + ".backup", seal(backup.trim()))
                .putString(p.name() + ".ca", ca.trim());
        if (changed) {
            e.putBoolean(p.name() + ".ok", false);
        }
        e.apply();
    }

    public void setConnected(Providers p, boolean ok) {
        prefs.edit().putBoolean(p.name() + ".ok", ok).apply();
    }

    /** Forgets this provider's keys and settings. */
    public void clear(Providers p) {
        SharedPreferences.Editor e = prefs.edit();
        for (String f : new String[]{"address", "model", "models", "fast", "key", "backup", "ca", "ok", "window", "vision", "reasoning", "max_out",
                "timeout", "extra"}) {
            e.remove(p.name() + "." + f);
        }
        if (active() == p) {
            e.putString("active", Providers.ON_PHONE.name());
        }
        java.util.List<Providers> o = order();
        o.remove(p);
        e.putString("order", join(o));
        e.apply();
    }

    /** The brain for this provider as set up, or null for the phone's own (or when it can't be used). */
    public RemoteBrain brain(Providers p) {
        if (!p.api() || RemoteBrain.check(address(p), model(p), key(p)) != null) {
            return null;
        }
        return signedIn(p, configured(p, new RemoteBrain(protocol(p), address(p), model(p), key(p), backupKey(p),
                RemoteBrain.https(sockets(certificate(p)))).autoFormat("auto".equals(format(p)))));
    }

    /** The Ibiko server signs in with the phone's session, fetched at each request; other providers keep their key. */
    private static RemoteBrain signedIn(Providers p, RemoteBrain b) {
        java.util.function.Supplier<String> s = serverSession;
        return p == Providers.IBIKO_SERVER && s != null ? b.keySource(s) : b;
    }

    /** The same provider on its fast model, or null when there is none (or smart routing is off). */
    public RemoteBrain fastBrain(Providers p) {
        String fast = fastModel(p);
        if (!smartRouting() || fast.isEmpty() || fast.equals(model(p)) || brain(p) == null) {
            return null;
        }
        return signedIn(p, configured(p, new RemoteBrain(protocol(p), address(p), fast, key(p), backupKey(p),
                RemoteBrain.https(sockets(certificate(p)))).autoFormat("auto".equals(format(p)))));
    }

    /**
     * Connections that trust the pasted CA certificate as well as the phone's own authorities; the chain is still
     * checked. Null (the phone's own only) when nothing was pasted or it isn't a certificate.
     */
    public static javax.net.ssl.SSLSocketFactory sockets(String pem) {
        if (pem == null || !pem.contains("BEGIN CERTIFICATE")) {
            return null;
        }
        try {
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            KeyStore extra = KeyStore.getInstance(KeyStore.getDefaultType());
            extra.load(null, null);
            int i = 0;
            for (java.security.cert.Certificate c : cf.generateCertificates(new java.io.ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)))) {
                extra.setCertificateEntry("ca" + i++, c);
            }
            final javax.net.ssl.X509TrustManager own = trust(null), added = trust(extra);
            javax.net.ssl.X509TrustManager both = new javax.net.ssl.X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String auth) throws java.security.cert.CertificateException {
                    own.checkClientTrusted(chain, auth);
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String auth) throws java.security.cert.CertificateException {
                    try {
                        own.checkServerTrusted(chain, auth);
                    } catch (java.security.cert.CertificateException e) {
                        added.checkServerTrusted(chain, auth); // still a full chain check, against the pasted CA
                    }
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    java.security.cert.X509Certificate[] a = own.getAcceptedIssuers(), b = added.getAcceptedIssuers();
                    java.security.cert.X509Certificate[] all = java.util.Arrays.copyOf(a, a.length + b.length);
                    System.arraycopy(b, 0, all, a.length, b.length);
                    return all;
                }
            };
            javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[]{both}, null);
            return ctx.getSocketFactory();
        } catch (Exception e) {
            return null;
        }
    }

    private static javax.net.ssl.X509TrustManager trust(KeyStore ks) throws Exception {
        javax.net.ssl.TrustManagerFactory f = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        f.init(ks);
        for (javax.net.ssl.TrustManager t : f.getTrustManagers()) {
            if (t instanceof javax.net.ssl.X509TrustManager) {
                return (javax.net.ssl.X509TrustManager) t;
            }
        }
        throw new IllegalStateException("no trust manager");
    }

    // ------------------------------------------------------------ the keys, sealed with the Keystore

    private static SecretKey secret() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator g = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new android.security.keystore.KeyGenParameterSpec.Builder(KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return g.generateKey();
    }

    public static String seal(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, secret());
            byte[] iv = c.getIV(), data = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP);
        } catch (Exception e) {
            return ""; // never keep a key in the clear: without the Keystore, it isn't kept
        }
    }

    public static String open(String sealed) {
        if (sealed == null || !sealed.contains(":")) {
            return "";
        }
        try {
            String[] f = sealed.split(":", 2);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(128, Base64.decode(f[0], Base64.NO_WRAP)));
            return new String(c.doFinal(Base64.decode(f[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
