package ai.ibiko.agent;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A scripted brain and a quiet phone for agent tests. */
final class CodeAgentTestHelper {
    private CodeAgentTestHelper() {
    }

    static final class Script implements CodeAgent.Brain {
        final List<String> replies;

        Script(String... replies) {
            this.replies = new ArrayList<String>(Arrays.asList(replies));
        }

        @Override
        public String ask(String system, String transcript) {
            return replies.isEmpty() ? null : replies.remove(0);
        }
    }

    static final class Quiet implements CodeAgent.Host {
        @Override
        public boolean allowRun(String command) {
            return true;
        }

        @Override
        public String run(String command, File workspace) {
            return "";
        }

        @Override
        public String fetch(String url) {
            return null;
        }

        @Override
        public String connector(String call) {
            return "";
        }

        @Override
        public void step(String line) {
        }

        @Override
        public boolean stopped() {
            return false;
        }
    }
}
