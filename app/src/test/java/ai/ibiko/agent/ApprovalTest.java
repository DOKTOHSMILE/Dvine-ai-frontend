package ai.ibiko.agent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** What waits for the pilot's yes: connector calls that change something; reading goes ahead. */
public class ApprovalTest {
    @Test
    public void changesAskReadsDoNot() {
        for (String c : new String[] {"github.create_issue {\"title\":\"x\"}", "github.delete_branch main", "gmail.send to a",
                "github.merge_pull_request 4", "notion.updatePage x", "slack.post_message hi", "github.add_comment 3",
                "calendar.create_event x", "github.push_files x"}) {
            assertTrue(c, Approval.changes(c));
        }
        for (String c : new String[] {"github.repos", "github.get_file_contents a", "github.list_issues", "gmail.search x",
                "github.search_code y", "calendar.list_events", "github.get_me", "drive.read_file a"}) {
            assertFalse(c, Approval.changes(c));
        }
    }
}
