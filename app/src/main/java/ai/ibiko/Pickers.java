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

/** The system file pickers: content to attach and photos for a reconstruction, read and delivered. Moved out of MainActivity. */
final class Pickers {
    private final MainActivity a;

    Pickers(MainActivity a) {
        this.a = a;
    }

    /** In FileTurns. */
    void openContentPicker(String kind, boolean learn) {
        a.fileTurns.openContentPicker(kind, learn);
    }

    void openReconstructionPicker() {
        a.pickerReconstruct = true;
        a.pickerLearn = false;
        android.content.Intent intent = new android.content.Intent(android.provider.MediaStore.ACTION_PICK_IMAGES); // the gallery
        intent.setType("image/*");
        intent.putExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX, Math.min(20, android.provider.MediaStore.getPickImagesMaxLimit()));
        try {
            a.startActivityForResult(intent, a.PICK_CONTENT);
            a.note("Choose two or more overlapping photographs of one object.");
        } catch (android.content.ActivityNotFoundException e) {
            a.pickerReconstruct = false;
            a.note("This device has no file picker.");
        }
    }

    /** Every file the picker returned: several (ClipData) or one (getData). */
    static java.util.List<android.net.Uri> pickedUris(android.content.Intent data) {
        java.util.ArrayList<android.net.Uri> uris = new java.util.ArrayList<android.net.Uri>();
        android.content.ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                android.net.Uri uri = clip.getItemAt(i).getUri();
                if (uri != null && !uris.contains(uri)) {
                    uris.add(uri);
                }
            }
        }
        if (uris.isEmpty() && data.getData() != null) {
            uris.add(data.getData());
        }
        return uris;
    }

    void deliverReconstruction(java.util.List<android.net.Uri> uris, int token) {
        java.util.ArrayList<ai.ibiko.ops.recon.RasterImage> images = new java.util.ArrayList<ai.ibiko.ops.recon.RasterImage>();
        for (android.net.Uri uri : uris) {
            if (!a.alive(token)) {
                a.handler.post(a::endThink);
                return;
            }
            try {
                images.add(a.readRaster(uri));
            } catch (Exception ignored) {
            }
        }
        final ai.ibiko.ops.recon.PhotoReconstruction.Built built = ai.ibiko.ops.recon.PhotoReconstruction
                .from(images);
        a.handler.post(() -> {
            if (!a.alive(token)) {
                return;
            }
            a.endThink();
            if (built.model != null && a.stage.objects.showReconstruction(built.model) != null) {
                a.stage.becomeObject(built.model);
            }
            a.play(LocalTurn.speakAs(a.stage.agent(), built.speech), null);
        });
    }

    /** In FileTurns. */
    void deliverFile(android.net.Uri uri, boolean learn, int token) {
        a.fileTurns.deliverFile(uri, learn, token);
    }

    /** In FileTurns. */
    boolean showSelected(android.net.Uri uri, String kind, String name) {
        return a.fileTurns.showSelected(uri, kind, name);
    }
}
