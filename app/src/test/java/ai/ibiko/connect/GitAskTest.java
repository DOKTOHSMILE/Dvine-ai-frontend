package ai.ibiko.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Test;

/** A private repository's token stays in a file. The command the user sees does not contain it. */
public class GitAskTest {
    @Test
    public void onlyRemoteGitGetsTheToken() {
        assertFalse(GitAsk.network("git status"));
        assertFalse(GitAsk.network("ls -la"));
        assertTrue(GitAsk.network("cd /workspace && git clone 'https://github.com/a/b'"));
        assertTrue(GitAsk.network("git -C /workspace/repos/Ibiko-ai pull"));
        assertTrue(GitAsk.network("git push"));
        assertTrue(GitAsk.network("gh repo list"));
        assertFalse(GitAsk.prefix().contains("ghp_example"));
        assertFalse(GitAsk.script().contains("ghp_"));
        assertTrue(GitAsk.prefix().startsWith("export GIT_ASKPASS="));
        assertTrue(GitAsk.prefix().contains(GitAsk.CRED));
        assertTrue(GitAsk.cred().contains(GitAsk.TOKEN_FILE));
        assertFalse(GitAsk.cred().contains("ghp_"));
    }

    @Test
    public void theTokenIsNotLeftInTheOutput() throws Exception {
        assertEquals("remote: (token)", GitAsk.quiet("remote: ghp_secret", "ghp_secret"));
        assertEquals("ok", GitAsk.quiet("ok", "ghp_secret"));
        String denied = GitAsk.note("fatal: repository not found", false);
        assertTrue(denied.contains("Connectors"));
        assertFalse(denied.contains("ghp_"));
        assertTrue(GitAsk.note("fatal: Authentication failed", true).contains("Contents"));
        assertEquals("Already up to date.", GitAsk.note("Already up to date.", false));
        File root = java.nio.file.Files.createTempDirectory("gitask").toFile();
        GitAsk.install(root, "ghp_secret");
        assertEquals("ghp_secret", new String(java.nio.file.Files.readAllBytes(new File(root, "etc/ibiko-git-token").toPath()),
                java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(new File(root, "usr/local/bin/ibiko-git-ask").canExecute() || new File(root, "usr/local/bin/ibiko-git-ask").isFile());
        assertTrue(new String(java.nio.file.Files.readAllBytes(new File(root, "usr/local/bin/ibiko-git-cred").toPath()),
                java.nio.charset.StandardCharsets.UTF_8).contains("username=x-access-token"));
    }

    @Test
    public void aZipRedirectStaysOnGitHub() {
        assertTrue(GitHubGit.githubHost("https://codeload.github.com/o/r/legacy.zip/abc"));
        assertTrue(GitHubGit.githubHost("https://github.com/o/r"));
        assertFalse(GitHubGit.githubHost("https://example.com/zip"));
        assertFalse(GitHubGit.githubHost("not a url"));
    }
}
