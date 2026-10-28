package ai.dvine.ops.ssh;

import java.util.List;

/**
 * The only way a session is opened. Implementations must not shell out with a user-supplied command string.
 * SSHJ is the intended production transport; tests supply an in-memory one.
 */
public interface SshTransport {
    final class Auth {
        public final String username;
        public final char[] secret;
        public final boolean keyBased;

        public Auth(String username, char[] secret, boolean keyBased) {
            this.username = username;
            this.secret = secret;
            this.keyBased = keyBased;
        }
    }

    final class Open {
        public final boolean ok;
        public final String hostKeyFingerprint;
        public final String error;
        public final boolean authFailed;
        public final boolean networkDown;

        public Open(boolean ok, String hostKeyFingerprint, String error, boolean authFailed, boolean networkDown) {
            this.ok = ok;
            this.hostKeyFingerprint = hostKeyFingerprint == null ? "" : hostKeyFingerprint;
            this.error = error == null ? "" : error;
            this.authFailed = authFailed;
            this.networkDown = networkDown;
        }
    }

    final class Exec {
        public final int exit;
        public final String stdout;
        public final String stderr;
        public final boolean interrupted;

        public Exec(int exit, String stdout, String stderr, boolean interrupted) {
            this.exit = exit;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
            this.interrupted = interrupted;
        }
    }

    final class RemoteFile {
        public final String path;
        public final long size;
        public final long modified;
        public final boolean directory;

        public RemoteFile(String path, long size, long modified, boolean directory) {
            this.path = path;
            this.size = size;
            this.modified = modified;
            this.directory = directory;
        }
    }

    Open connect(String host, int port, Auth auth, long timeoutMs);

    Exec exec(List<String> argv, long timeoutMs);

    List<RemoteFile> list(String directory);

    byte[] download(String path);

    String upload(String path, byte[] bytes, boolean overwrite);
}
