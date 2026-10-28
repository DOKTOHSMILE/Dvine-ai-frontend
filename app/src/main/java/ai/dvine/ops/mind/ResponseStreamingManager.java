package ai.dvine.ops.mind;

import java.util.ArrayList;
import java.util.List;

/** Partials are not verified findings. */
public final class ResponseStreamingManager {
    public static final class Piece {
        public final String text;
        public final boolean verified;

        public Piece(String text, boolean verified) {
            this.text = text;
            this.verified = verified;
        }
    }

    public List<Piece> stream(String full) {
        List<Piece> out = new ArrayList<Piece>();
        if (full == null || full.isEmpty()) {
            return out;
        }
        int mid = Math.min(40, full.length());
        if (mid < full.length()) {
            out.add(new Piece(full.substring(0, mid), false));
        }
        out.add(new Piece(full, true));
        return out;
    }
}
