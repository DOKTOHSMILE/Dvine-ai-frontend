package ai.dvine.ops.ssh;

import java.util.LinkedHashMap;
import java.util.Map;

/** Trust on first use. A changed fingerprint is a hard failure, never a silent accept. */
public final class SshHostKeyVerifier {
    public enum Status { TRUSTED, FIRST_SEEN, CHANGED, MISSING }

    public static final class Verdict {
        public final Status status;
        public final String fingerprint;

        public Verdict(Status status, String fingerprint) {
            this.status = status;
            this.fingerprint = fingerprint == null ? "" : fingerprint;
        }
    }

    private final Map<String, String> trusted = new LinkedHashMap<String, String>();

    public Verdict check(String host, int port, String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            return new Verdict(Status.MISSING, "");
        }
        String key = host + ":" + port;
        String known = trusted.get(key);
        if (known == null) {
            return new Verdict(Status.FIRST_SEEN, fingerprint);
        }
        if (known.equals(fingerprint)) {
            return new Verdict(Status.TRUSTED, fingerprint);
        }
        return new Verdict(Status.CHANGED, fingerprint);
    }

    public void trust(String host, int port, String fingerprint) {
        if (host == null || fingerprint == null || fingerprint.isEmpty()) {
            return;
        }
        trusted.put(host + ":" + port, fingerprint);
    }

    public String known(String host, int port) {
        return trusted.get(host + ":" + port);
    }
}
