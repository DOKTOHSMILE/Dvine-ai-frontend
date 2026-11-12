package ai.ibiko;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import ai.ibiko.act.Actions;
import ai.ibiko.brain.BrainAnswer;
import ai.ibiko.brain.LearningJournal;
import ai.ibiko.brain.OfflineBrain;
import ai.ibiko.brain.Onboarding;
import ai.ibiko.brain.PendingAction;
import ai.ibiko.stage.StageMode;
import ai.ibiko.stage.StageListener;
import ai.ibiko.stage.StageView;
import ai.ibiko.net.Connectivity;
import ai.ibiko.net.KnowledgeStore;
import ai.ibiko.net.SyncWorker;
import ai.ibiko.turn.LocalTurn;
import ai.ibiko.turn.TurnLine;
import ai.ibiko.turn.TurnRoute;
import ai.ibiko.voice.AmbientPad;
import ai.ibiko.voice.ListenBridge;
import ai.ibiko.voice.ListenCallback;
import ai.ibiko.voice.TalkBridge;

/** The composer's row of chips, dictation, and a fresh chat. Moved out of MainActivity. */
final class ComposerRow {
    private final MainActivity a;

    ComposerRow(MainActivity a) {
        this.a = a;
    }

    void toggleDictation() {
        android.widget.EditText field = a.findViewById(R.id.et_message);
        if (field == null || a.listen == null) {
            a.startListening(false);
            return;
        }
        if (a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[] { android.Manifest.permission.RECORD_AUDIO }, a.MIC);
            return;
        }
        if (a.dictation == null) {
            a.dictation = new ai.ibiko.voice.Dictation(field, a.findViewById(R.id.btn_voice), a.listen, a.getColor(R.color.accent), a::note);
        }
        if (a.talk != null && !a.dictation.active()) {
            a.talk.stop();
        }
        a.dictation.toggle();
    }

    void startNewChatSafely() {
        if (a.chats == null) {
            return;
        }
        // a new chat starts clean, on Obi: nothing still working, nothing carried over from the chat before
        if (a.generating || a.thinkPace != null) {
            a.stopGeneration();
        }
        if (a.chatPane != null) {
            a.chatPane.activityDrop();
        }
        // a clean start for all three: Ivie's and Ibiko's older chats stay in the list, but switching to them now opens
        // a new chat, not the old one
        ai.ibiko.chat.ChatBook.Section section = a.chats.freshForAll(StageView.OBI);
        if (a.chatPane != null) {
            a.chatPane.show();
        }
        a.examiner.clearTopic();
        a.selectPersona(section.persona);
        a.closeDrawer();
        a.repaintThink(); // a new chat starts with its own tools
    }

    void bindQuickRow() {
        android.view.View tools = a.findViewById(R.id.chip_tools), code = a.findViewById(R.id.chip_code),
                media = a.findViewById(R.id.chip_media), explore = a.findViewById(R.id.chip_explore), more = a.findViewById(R.id.chip_more);
        if (tools != null) {
            tools.setOnClickListener(v -> a.toolsMenu.showToolsSheet());
        }
        if (media != null) {
            media.setOnClickListener(a::showAttachMenu);
        }
        if (code != null) {
            code.setOnClickListener(v -> ai.ibiko.chat.ui.BottomSheet.show(a, "Code", new ai.ibiko.chat.ui.BottomSheet.Item[] {
                    new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_folder, "Workspace files", "This chat's repositories, memories and pictures"),
                    new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_terminal, "Shell", "The Linux built into Ibiko"),
                    new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_project, "Projects", "Chats kept together") }, which -> {
                        if (which == 0) {
                            a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.WorkspaceActivity.class));
                        } else if (which == 1) {
                            a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.TerminalActivity.class));
                        } else if (a.chatPane != null) {
                            a.chatPane.openProjects();
                        }
                    }));
        }
        if (explore != null) {
            explore.setOnClickListener(v -> ai.ibiko.chat.ui.BottomSheet.show(a, "Explore", new ai.ibiko.chat.ui.BottomSheet.Item[] {
                    new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_people, "Social accounts", "Sign in, post, comment, trending"),
                    new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_send, "Scheduled posts", "See or cancel what is planned"),
                    new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_plug, "Connectors", "GitHub, Gmail, calendar, Notion and others") }, which -> {
                        if (which == 0) {
                            a.startActivityForResult(new android.content.Intent(a, ai.ibiko.chat.ui.SocialActivity.class), a.SOCIAL);
                        } else if (which == 1) {
                            a.showScheduledPosts();
                        } else {
                            a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.ConnectorsActivity.class));
                        }
                    }));
        }
        if (more != null) {
            more.setOnClickListener(v -> ai.ibiko.chat.ui.BottomSheet.show(a, "More", new ai.ibiko.chat.ui.BottomSheet.Item[] {
                    new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_settings, "Settings", "Voice, chat, models, learning"),
                    new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_history, "Chats", "Earlier conversations"),
                    new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_deep, "What Ibiko remembers", "See or forget what was learned"),
                    new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_people, "Workload and workers", "Memory in use and the agents at work") }, which -> {
                        if (which == 0) {
                            a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.SettingsActivity.class));
                        } else if (which == 1) {
                            a.openDrawer();
                        } else if (which == 2) {
                            a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.MemoryActivity.class));
                        } else if (a.model != null) {
                            ai.ibiko.chat.ui.WorkloadSheet.show(a, a.model, a.brainLabel(), a.ibikoOwn());
                        }
                    }));
        }
    }
}
