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

/** Attachments: files Ibiko made, shared into the app, picked, sent and shown under a message. Moved out of MainActivity. */
final class Attachments {
    private final MainActivity a;

    Attachments(MainActivity a) {
        this.a = a;
    }

    /**
     * Files Ibiko made outside the cloned repositories (a script, a page, a report) become artifacts, openable and
     * shareable from the Artifacts chip; changes inside a clone show in the Diff instead.
     */
    void recordIbikoFiles(java.io.File workspace, java.util.List<String> changed) {
        ai.ibiko.chat.WorkLog log = new ai.ibiko.chat.WorkLog(a);
        java.util.Set<String> known = new java.util.HashSet<String>();
        for (ai.ibiko.chat.WorkLog.Entry e : log.artifacts()) {
            known.add(e.address);
        }
        for (String path : changed) {
            java.io.File f = new java.io.File(workspace, path);
            String top = path.contains("/") ? path.substring(0, path.indexOf('/')) : "";
            if (!f.isFile() || (!top.isEmpty() && new java.io.File(workspace, top + "/" + ai.ibiko.connect.GitHubGit.RECORD).isFile())) {
                continue; // inside a clone: that's the diff
            }
            try {
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(a, "ai.ibiko.files", f);
                if (known.contains(uri.toString())) {
                    continue;
                }
                String ext = f.getName().contains(".") ? f.getName().substring(f.getName().lastIndexOf('.') + 1) : "";
                String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(java.util.Locale.US));
                log.artifact(f.getName(), uri.toString(), "Made by Ibiko \u00B7 " + path, mime == null ? "text/plain" : mime);
            } catch (RuntimeException ignored) {
                // not shareable: it stays in the workspace
            }
        }
    }

    /**
     * Diff, Pull requests and Artifacts under the reply that produced them: what changed in {@code changed} (Ibiko's
     * files) and what was logged since {@code since} is kept with the chat, and the reply shows it as pills.
     */
    void attachWork(final java.util.List<String> changed, final long since) {
        final java.io.File workspace = a.chatSpace();
        new Thread(() -> {
            final String work = ai.ibiko.chat.ui.WorkChips.capture(a, workspace, changed, since);
            if (work != null) {
                a.handler.post(() -> {
                    if (a.chatPane != null && !a.isFinishing()) {
                        a.chatPane.work(work);
                    }
                });
            }
        }, "ibiko-work").start();
    }

    /**
     * Sends the words with what is attached: the chat shows the photos and file cards with the words, and the model
     * gets the images themselves (a model that can see) and the texts as data.
     */
    void sendWithAttachments(String words) {
        String forModel = a.attachments.forModel(words);
        showSentAttachments(words);
        a.attachments.clear();
        a.answer(forModel, true); // already shown: not noted again
    }

    /** The attachment sits at the top of the user's message, the words under it. The file's text is for the model only. */
    void showSentAttachments(String words) {
        if (a.chats == null || a.attachments == null) {
            return;
        }
        String persona = a.chats.current().persona;
        for (ai.ibiko.chat.ui.ComposerAttachments.Item it : a.attachments.items()) {
            if ("image".equals(it.kind)) {
                a.chats.add("user", "image", it.path, persona);
            } else if ("video".equals(it.kind) || "audio".equals(it.kind)) {
                a.chats.add("user", it.kind, it.path == null ? it.name : it.path, persona);
            } else {
                String kind = "doc".equals(it.kind) ? ai.ibiko.chat.ui.ComposerAttachments.pages(it)
                        : "text".equals(it.kind) ? "Text" : "File";
                a.chats.add("user", "file", it.name + "\n" + kind, persona);
            }
        }
        if (words != null && !words.trim().isEmpty()) {
            a.chats.add("user", "text", words.trim(), persona);
        }
        if (a.chatPane != null) {
            a.chatPane.show();
        }
    }

    /** Adds a picked file to the message being written (read off the main thread). */
    /**
     * Share › Ibiko from another app ({@link ai.ibiko.chat.ui.ShareActivity}): the files and pictures are attached to
     * the composer and shared text is put in the field. Nothing is sent until the user sends it.
     */
    void takeShared() {
        ai.ibiko.chat.ui.ShareActivity.Shared got = ai.ibiko.chat.ui.ShareActivity.take();
        if (got == null) {
            return;
        }
        for (android.net.Uri u : got.files) {
            attachPicked(u);
        }
        android.widget.EditText field = a.findViewById(R.id.et_message);
        if (got.text != null && !got.text.isEmpty() && field != null) {
            String before = field.getText().toString();
            field.setText(before.trim().isEmpty() ? got.text : before + "\n" + got.text);
            field.setSelection(field.getText().length());
        }
    }

    void attachPicked(final android.net.Uri uri) {
        a.note("Attaching\u2026");
        new Thread(() -> {
            ai.ibiko.chat.ui.ComposerAttachments.Item item;
            String why = null;
            try {
                item = ai.ibiko.chat.ui.AttachmentReader.read(a, uri, ai.ibiko.chat.Tuning.IMAGE_SIDE.get(a.prefs));
            } catch (java.io.IOException | RuntimeException e) {
                item = null;
                why = e.getMessage();
            }
            final ai.ibiko.chat.ui.ComposerAttachments.Item got = item;
            final String problem = why;
            a.handler.post(() -> {
                if (got == null) {
                    a.note("Couldn't attach it" + (problem == null ? "." : ": " + problem + "."));
                    return;
                }
                if (a.attachments != null) {
                    a.attachments.add(got);
                    a.note("Attached " + got.name + ". Add your instructions and send.");
                }
                android.widget.EditText field = a.findViewById(R.id.et_message);
                if (field != null) {
                    field.requestFocus();
                }
            });
        }, "ibiko-attach").start();
    }

    void showAttachMenu(android.view.View anchor) {
        ai.ibiko.chat.ui.BottomSheet.Item[] items = {
                new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_mic, "Speak", "Say it; the words go into the message box"),
                new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_image, "Photo", "From your gallery"),
                new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_video, "Video", null),
                new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_audio, "Audio", null),
                new ai.ibiko.chat.ui.BottomSheet.Item(R.drawable.ic_file, "Files", "Any kind, several at once") };
        ai.ibiko.chat.ui.BottomSheet.show(a, "Add to the chat", items, which -> {
            if (which == 0) {
                a.toggleDictation();
            } else if (which == 1) {
                a.openContentPicker("image", false);
            } else if (which == 2) {
                a.openContentPicker("video", false);
            } else if (which == 3) {
                a.openContentPicker("audio", false);
            } else if (which == 4) {
                a.openContentPicker("document", false);
            }
        });
    }
}
