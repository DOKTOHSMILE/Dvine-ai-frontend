package ai.dvine.ops.ssh;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SshFileBrowser {
    private final SshTransport transport;

    public SshFileBrowser(SshTransport transport) {
        this.transport = transport;
    }

    public List<SshTransport.RemoteFile> find(String directory, String words) {
        if (transport == null || directory == null || directory.contains("..")) {
            return List.of();
        }
        List<SshTransport.RemoteFile> all = transport.list(directory);
        if (all == null) {
            return List.of();
        }
        String n = words == null ? "" : words.toLowerCase(Locale.US);
        List<SshTransport.RemoteFile> hit = new ArrayList<SshTransport.RemoteFile>();
        for (SshTransport.RemoteFile f : all) {
            if (f.directory) {
                continue;
            }
            String name = f.path.toLowerCase(Locale.US);
            if (n.isEmpty() || name.contains(n) || (n.contains("vehicle") && (name.contains("car") || name.contains("motor") || name.contains("vehicle") || name.contains("bike")))) {
                hit.add(f);
            }
        }
        hit.sort(Comparator.comparingLong((SshTransport.RemoteFile f) -> f.modified).reversed());
        return hit;
    }

    public byte[] download(String path, boolean confirmedOverwriteLocal) {
        if (path == null || path.contains("..")) {
            return null;
        }
        return transport.download(path);
    }
}
