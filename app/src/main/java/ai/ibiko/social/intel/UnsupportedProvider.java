package ai.ibiko.social.intel;

import java.util.ArrayList;
import java.util.List;

/** A platform DVINE can't reach yet, reported plainly instead of returning nothing (TikTok, for one). Pure Java. */
public final class UnsupportedProvider implements SocialProvider {
    private final String platform, why;

    public UnsupportedProvider(String platform, String why) {
        this.platform = platform;
        this.why = why;
    }

    @Override public String platform() { return platform; }
    @Override public boolean available() { return false; }
    @Override public String unavailableReason() { return why; }
    @Override public boolean canSearch() { return false; }
    @Override public boolean canReadPublicProfile() { return false; }
    @Override public boolean canReadOwnProfile() { return false; }
    @Override public List<SocialPost> search(String query, int most) { return new ArrayList<SocialPost>(); }

    @Override
    public SocialProfile readPublic(String handle, int recent) {
        SocialProfile p = new SocialProfile(platform, handle, "", "", "", -1, -1, false);
        p.limits.add(platform + ": " + why);
        return p;
    }

    @Override
    public SocialProfile readOwn(int recent) {
        SocialProfile p = new SocialProfile(platform, "", "", "", "", -1, -1, true);
        p.limits.add(platform + ": " + why);
        return p;
    }
}
