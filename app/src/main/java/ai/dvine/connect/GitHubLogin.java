package ai.dvine.connect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import ai.dvine.brain.MiniJson;

/**
 * Signing in to GitHub without typing a token: GitHub's device sign-in. DVINE asks GitHub for a short code, the user
 * opens github.com/login/device (signed in there with their email or username and password, two-factor and all),
 * types the code and approves; DVINE then receives a token for the repositories. GitHub doesn't let apps take a
 * password directly (it stopped in 2021), so this, or a pasted token, is how an app signs in. It needs the client ID
 * of a GitHub OAuth app with device sign-in turned on (public, not a secret). Pure Java.
 */
public final class GitHubLogin {
    /** What the user needs to see: the code to type and where. */
    public static final class Code {
        public final String deviceCode, userCode, verifyUrl;
        public final int intervalSeconds, expiresSeconds;

        Code(String deviceCode, String userCode, String verifyUrl, int interval, int expires) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verifyUrl = verifyUrl;
            this.intervalSeconds = interval;
            this.expiresSeconds = expires;
        }
    }

    /** One look at whether the user has approved yet. */
    public static final class Poll {
        /** The token, once approved; null while waiting. */
        public final String token;
        /** Seconds to wait before asking again (GitHub may ask for slower). */
        public final int waitSeconds;

        Poll(String token, int waitSeconds) {
            this.token = token;
            this.waitSeconds = waitSeconds;
        }
    }

    /**
     * What DVINE asks for: every repository the account can see (public, private, organisation and collaborator),
     * including push and pull, plus the user's name and which organisations they belong to.
     */
    public static final String SCOPES = "repo read:user read:org";

    private final Connectors.Http http;
    private final String clientId;

    public GitHubLogin(Connectors.Http http, String clientId) {
        this.http = http;
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    public Code start() throws IOException {
        if (clientId.isEmpty()) {
            throw new IOException("a GitHub OAuth app client ID is needed for browser sign-in");
        }
        Map<String, Object> m = post("https://github.com/login/device/code", "client_id=" + enc(clientId) + "&scope=" + enc(SCOPES));
        if (m.containsKey("error")) {
            throw new IOException(describe(m));
        }
        return new Code(MiniJson.str(m.get("device_code"), ""), MiniJson.str(m.get("user_code"), ""),
                MiniJson.str(m.get("verification_uri"), "https://github.com/login/device"),
                (int) MiniJson.num(m.get("interval"), 5), (int) MiniJson.num(m.get("expires_in"), 900));
    }

    /** Asks once whether the user approved; throws when they refused or the code ran out. */
    public Poll poll(Code code, int waitSeconds) throws IOException {
        Map<String, Object> m = post("https://github.com/login/oauth/access_token", "client_id=" + enc(clientId) + "&device_code="
                + enc(code.deviceCode) + "&grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code"));
        String token = MiniJson.str(m.get("access_token"), null);
        if (token != null && !token.isEmpty()) {
            return new Poll(token, 0);
        }
        String error = MiniJson.str(m.get("error"), "");
        if ("authorization_pending".equals(error)) {
            return new Poll(null, waitSeconds);
        }
        if ("slow_down".equals(error)) {
            return new Poll(null, (int) MiniJson.num(m.get("interval"), waitSeconds + 5));
        }
        throw new IOException(describe(m));
    }

    /** The GitHub username a token belongs to. */
    public static String user(Connectors.Http http, String token) throws IOException {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("Accept", "application/vnd.github+json");
        h.put("Authorization", "Bearer " + token);
        h.put("User-Agent", "DVINE");
        Connectors.Reply r = http.send("GET", "https://api.github.com/user", h, null);
        if (r.status == 401) {
            throw new IOException("GitHub refused the token (401)");
        }
        if (r.status < 200 || r.status >= 300) {
            throw new IOException("GitHub said " + r.status);
        }
        return MiniJson.str(MiniJson.obj(MiniJson.parse(r.body)).get("login"), "");
    }

    /**
     * Who is signed in, and whether this token can push and pull every repository it can see. A classic token lists
     * its scopes; a fine-grained token does not, and keeps whatever access it was given.
     */
    public static String access(Connectors.Http http, String token) throws IOException {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("Accept", "application/vnd.github+json");
        h.put("Authorization", "Bearer " + token);
        h.put("User-Agent", "DVINE");
        Connectors.Reply r = http.send("GET", "https://api.github.com/user", h, null);
        if (r.status == 401) {
            throw new IOException("GitHub refused the token (401)");
        }
        if (r.status < 200 || r.status >= 300) {
            throw new IOException("GitHub said " + r.status);
        }
        String login = MiniJson.str(MiniJson.obj(MiniJson.parse(r.body)).get("login"), "");
        String scopes = r.header("X-OAuth-Scopes");
        if (scopes == null || scopes.trim().isEmpty()) {
            return "Signed in as @" + login + ". This token doesn't list classic scopes; push and pull use the access it was given"
                    + " (a fine-grained token needs Contents: Read and write on the repositories).";
        }
        String low = scopes.toLowerCase(java.util.Locale.US);
        boolean repo = java.util.regex.Pattern.compile("(^|,)\\s*repo\\s*(,|$)").matcher(low).find();
        if (repo) {
            return "Signed in as @" + login + " with repo access: clone, pull, commit and push every repository this account can see.";
        }
        return "Signed in as @" + login + " but this token's scopes (" + scopes.trim() + ") cannot push or pull private repositories."
                + " Sign in again, or make a token with the repo scope (Connectors \u203A GitHub).";
    }

    /** GitHub's page for making a token, filled in with DVINE's name and the scopes it needs. */
    public static String newTokenUrl() {
        return "https://github.com/settings/tokens/new?description=DVINE&scopes=repo,read:user,read:org";
    }

    private Map<String, Object> post(String url, String form) throws IOException {
        Map<String, String> h = new LinkedHashMap<String, String>();
        h.put("Accept", "application/json");
        h.put("Content-Type", "application/x-www-form-urlencoded");
        h.put("User-Agent", "DVINE");
        Connectors.Reply r = http.send("POST", url, h, form.getBytes(StandardCharsets.UTF_8));
        try {
            return MiniJson.obj(MiniJson.parse(r.body));
        } catch (RuntimeException e) {
            throw new IOException("GitHub said " + r.status);
        }
    }

    private static String describe(Map<String, Object> m) {
        String error = MiniJson.str(m.get("error"), "");
        switch (error) {
            case "expired_token": return "the code ran out; start again";
            case "access_denied": return "the sign-in was cancelled on GitHub";
            case "device_flow_disabled": return "device sign-in is off for this OAuth app (turn on \"Enable Device Flow\" in its settings)";
            case "incorrect_client_credentials": return "GitHub doesn't know that client ID";
            default:
                String d = MiniJson.str(m.get("error_description"), "");
                return d.isEmpty() ? (error.isEmpty() ? "GitHub didn't sign in" : error) : d;
        }
    }

    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }
}
