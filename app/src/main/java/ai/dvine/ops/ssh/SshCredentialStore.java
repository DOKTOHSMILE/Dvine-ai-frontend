package ai.dvine.ops.ssh;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Credentials stay behind a seal. {@link #toString()} and log lines never contain the secret.
 * A seal that is not strong refuses to persist anything.
 */
public final class SshCredentialStore {
    public interface Seal {
        boolean strong();

        byte[] seal(byte[] plain);

        byte[] open(byte[] sealed);
    }

    public static final class Profile {
        public final String name;
        public final String host;
        public final int port;
        public final String username;
        public final boolean keyBased;

        public Profile(String name, String host, int port, String username, boolean keyBased) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.username = username;
            this.keyBased = keyBased;
        }

        @Override
        public String toString() {
            return name + " " + username + "@" + host + ":" + port + (keyBased ? " key" : " password");
        }
    }

    private final Seal seal;
    private final Map<String, Profile> profiles = new LinkedHashMap<String, Profile>();
    private final Map<String, byte[]> secrets = new LinkedHashMap<String, byte[]>();

    public SshCredentialStore(Seal seal) {
        this.seal = seal;
    }

    public void save(Profile profile, char[] secret) {
        if (seal == null || !seal.strong()) {
            throw new IllegalStateException("refusing to store credentials without a strong seal");
        }
        if (profile == null || profile.name == null || secret == null) {
            throw new IllegalArgumentException("incomplete profile");
        }
        profiles.put(profile.name, profile);
        secrets.put(profile.name, seal.seal(new String(secret).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Arrays.fill(secret, '\0');
    }

    public Profile profile(String name) {
        return profiles.get(name);
    }

    public char[] open(String name) {
        byte[] sealed = secrets.get(name);
        if (sealed == null || seal == null) {
            return null;
        }
        byte[] plain = seal.open(sealed);
        if (plain == null) {
            return null;
        }
        char[] out = new String(plain, java.nio.charset.StandardCharsets.UTF_8).toCharArray();
        Arrays.fill(plain, (byte) 0);
        return out;
    }

    public boolean fileContainsSecret(byte[] stored, char[] secret) {
        if (stored == null || secret == null) {
            return false;
        }
        String blob = new String(stored, java.nio.charset.StandardCharsets.UTF_8);
        return blob.contains(new String(secret));
    }

    @Override
    public String toString() {
        return "SshCredentialStore{profiles=" + profiles.size() + "}";
    }
}
