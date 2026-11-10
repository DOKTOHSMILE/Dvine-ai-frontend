package ai.ibiko.social.intel;

import java.io.IOException;
import java.util.List;

/**
 * One platform's adapter. The intelligence engine only talks to this, so adding a platform never changes the
 * engine. An adapter says what it can do and, when it can't, why (not signed in, no public API, private account);
 * it never makes up data. Credentials stay inside the adapter: nothing it returns carries a token or cookie.
 */
public interface SocialProvider {
    String platform();

    /** True when it can be used now (signed in, or a public API). */
    boolean available();

    /** Why it can't be used now ("" when it can). */
    String unavailableReason();

    boolean canSearch();

    boolean canReadPublicProfile();

    boolean canReadOwnProfile();

    List<SocialPost> search(String query, int most) throws IOException;

    /** A public profile by handle; one that is private comes back with a limit saying so, never its content. */
    SocialProfile readPublic(String handle, int recent) throws IOException;

    /** The user's own profile, read with their sign-in. */
    SocialProfile readOwn(int recent) throws IOException;
}
