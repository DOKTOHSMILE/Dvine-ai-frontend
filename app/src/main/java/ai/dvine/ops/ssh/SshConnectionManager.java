package ai.dvine.ops.ssh;

import java.util.List;

public final class SshConnectionManager {
    public enum State {
        DISCONNECTED, CONNECTING, CONNECTED, AUTHENTICATION_REQUIRED, HOST_VERIFICATION_REQUIRED,
        CONNECTION_FAILED, RECONNECTING, SESSION_CLOSED
    }

    public static final class Link {
        public State state = State.DISCONNECTED;
        public String detail = "";
        public String fingerprint = "";
        public String host = "";
    }

    private final SshTransport transport;
    private final SshHostKeyVerifier keys;
    private final Link link = new Link();

    public SshConnectionManager(SshTransport transport, SshHostKeyVerifier keys) {
        this.transport = transport;
        this.keys = keys;
    }

    public Link link() {
        return link;
    }

    public Link connect(String host, int port, SshTransport.Auth auth, boolean acceptFirstKey, long nowIgnored) {
        link.host = host;
        link.state = State.CONNECTING;
        if (transport == null) {
            link.state = State.CONNECTION_FAILED;
            link.detail = "No SSH transport is configured. I have not opened a connection.";
            return link;
        }
        SshTransport.Open open = transport.connect(host, port, auth, 4000);
        link.fingerprint = open.hostKeyFingerprint;
        if (open.networkDown) {
            link.state = State.CONNECTION_FAILED;
            link.detail = "The network failed before a session was established.";
            return link;
        }
        if (open.authFailed) {
            link.state = State.AUTHENTICATION_REQUIRED;
            link.detail = "The server rejected those credentials.";
            return link;
        }
        SshHostKeyVerifier.Verdict verdict = keys.check(host, port, open.hostKeyFingerprint);
        if (verdict.status == SshHostKeyVerifier.Status.CHANGED || verdict.status == SshHostKeyVerifier.Status.MISSING) {
            link.state = State.CONNECTION_FAILED;
            link.detail = verdict.status == SshHostKeyVerifier.Status.CHANGED
                    ? "The server's host key changed. I did not continue. Fingerprint " + open.hostKeyFingerprint + "."
                    : "The server didn't present a host key. I did not continue.";
            return link;
        }
        if (verdict.status == SshHostKeyVerifier.Status.FIRST_SEEN) {
            if (!acceptFirstKey) {
                link.state = State.HOST_VERIFICATION_REQUIRED;
                link.detail = "First connection. The host key fingerprint is " + open.hostKeyFingerprint + ". Say you trust it if you recognise it.";
                return link;
            }
            keys.trust(host, port, open.hostKeyFingerprint);
        }
        if (!open.ok) {
            link.state = State.CONNECTION_FAILED;
            link.detail = open.error.isEmpty() ? "The connection failed." : open.error;
            return link;
        }
        link.state = State.CONNECTED;
        link.detail = "Connected.";
        return link;
    }

    public SshTransport.Exec exec(List<String> argv, boolean confirmedDestructive) {
        if (link.state != State.CONNECTED) {
            return new SshTransport.Exec(1, "", "not connected", false);
        }
        if (argv == null) {
            return new SshTransport.Exec(1, "", "no command", false);
        }
        return transport.exec(argv, 4000);
    }

    public void markClosed() {
        link.state = State.SESSION_CLOSED;
    }
}
