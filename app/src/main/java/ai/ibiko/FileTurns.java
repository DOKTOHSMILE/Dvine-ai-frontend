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

/** Files the pilot picks or shares: the picker, receiving a file and showing what was chosen. Moved out of MainActivity. */
final class FileTurns {
    private final MainActivity a;

    FileTurns(MainActivity a) {
        this.a = a;
    }

    void openContentPicker(String kind, boolean learn) {
        a.pickerLearn = learn;
        if ("image".equals(kind) || "video".equals(kind)) {
            // photos and videos: Android's own photo picker (the Photos / Collections sheet, Google Photos included),
            // which needs no storage permission and shows the gallery instead of a file browser
            android.content.Intent media = new android.content.Intent(android.provider.MediaStore.ACTION_PICK_IMAGES);
            media.setType("image".equals(kind) ? "image/*" : "video/*");
            if (!learn) {
                media.putExtra(android.provider.MediaStore.EXTRA_PICK_IMAGES_MAX,
                        Math.min(20, android.provider.MediaStore.getPickImagesMaxLimit()));
            }
            try {
                a.startActivityForResult(media, a.PICK_CONTENT);
                return;
            } catch (android.content.ActivityNotFoundException | IllegalArgumentException e) {
                // no photo picker on this device: the file picker below
            }
        }
        android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if ("image".equals(kind)) {
            intent.setType("image/*");
        } else if ("video".equals(kind)) {
            intent.setType("video/*");
        } else if ("audio".equals(kind)) {
            intent.setType("audio/*");
        } else {
            intent.setType("*/*"); // any kind of file: documents, code, sheets, archives, models…
        }
        if (!learn) {
            intent.putExtra(android.content.Intent.EXTRA_ALLOW_MULTIPLE, true); // several at once, of any kinds
        }
        try {
            a.startActivityForResult(intent, a.PICK_CONTENT);
        } catch (android.content.ActivityNotFoundException e) {
            a.play(LocalTurn.speakAs(a.stage.agent(), "This device has no file picker I can open."),
                    null);
        }
    }

    void deliverFile(android.net.Uri uri, boolean learn, int token) {
        ai.ibiko.ops.vision.FileIntake.Report report;
        String name = "file";
        try {
            String mime = a.getContentResolver().getType(uri);
            android.database.Cursor cursor = a.getContentResolver().query(uri, null, null, null,
                    null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int i = cursor
                                .getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (i >= 0 && cursor.getString(i) != null) {
                            name = cursor.getString(i);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
            byte[] head = new byte[ai.ibiko.ops.vision.FileIntake.HEAD];
            int n = 0;
            long size = 0;
            try (java.io.InputStream in = a.getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new java.io.IOException("no stream");
                }
                int r;
                while (n < head.length && (r = in.read(head, n, head.length - n)) > 0) {
                    n += r;
                    size += r;
                }
                byte[] skip = new byte[8192];
                while ((r = in.read(skip)) > 0) {
                    size += r;
                }
            }
            report = new ai.ibiko.ops.vision.FileIntake().inspect(name, mime,
                    java.util.Arrays.copyOf(head, n), size, learn);
        } catch (SecurityException e) {
            report = new ai.ibiko.ops.vision.FileIntake.Report("unknown",
                    "I no longer have permission to read that file.", null, false, 0, 0);
        } catch (Exception e) {
            report = new ai.ibiko.ops.vision.FileIntake.Report("unknown",
                    "I couldn't read that file.", null, false, 0, 0);
        }
        final ai.ibiko.ops.vision.FileIntake.Report done = report;
        final String shown = name;
        a.handler.post(() -> {
            if (!a.alive(token)) {
                return;
            }
            if (learn && done.note != null && a.journal != null) {
                a.journal.add(LearningJournal.TAUGHT_FACT, shown, done.note);
            }
            if (done.showImage && !a.showSelected(uri, done.kind, shown)) {
                a.play(LocalTurn.speakAs(a.stage.agent(), done.speech + " The picture didn't open."),
                        null);
                return;
            }
            a.play(LocalTurn.speakAs(a.stage.agent(), done.speech), null);
        });
    }

    boolean showSelected(android.net.Uri uri, String kind, String name) {
        try {
            android.graphics.Bitmap bitmap = null;
            if ("image".equals(kind)) {
                android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (java.io.InputStream in = a.getContentResolver().openInputStream(uri)) {
                    android.graphics.BitmapFactory.decodeStream(in, null, bounds);
                }
                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                opts.inSampleSize = 1;
                int max = Math.max(bounds.outWidth, bounds.outHeight);
                while (max / opts.inSampleSize > 1024) {
                    opts.inSampleSize *= 2;
                }
                try (java.io.InputStream in = a.getContentResolver().openInputStream(uri)) {
                    bitmap = android.graphics.BitmapFactory.decodeStream(in, null, opts);
                }
            } else if ("pdf".equals(kind)) {
                android.os.ParcelFileDescriptor pfd = a.getContentResolver().openFileDescriptor(uri,
                        "r");
                if (pfd != null) {
                    try (android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(
                            pfd)) {
                        if (renderer.getPageCount() > 0) {
                            android.graphics.pdf.PdfRenderer.Page page = renderer.openPage(0);
                            int w = Math.max(1, page.getWidth());
                            int h = Math.max(1, page.getHeight());
                            float scale = 800f / Math.max(w, h);
                            bitmap = android.graphics.Bitmap.createBitmap(
                                    Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)),
                                    android.graphics.Bitmap.Config.ARGB_8888);
                            page.render(bitmap, null, null,
                                    android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                            page.close();
                        }
                    } finally {
                        pfd.close();
                    }
                }
            } else if ("video".equals(kind)) {
                android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                try {
                    retriever.setDataSource(a, uri);
                    bitmap = retriever.getFrameAtTime(0);
                } finally {
                    retriever.release();
                }
            }
            if (bitmap != null) {
                a.stage.showPhotograph(ai.ibiko.render.field.Raster.image(bitmap,
                        ai.ibiko.render.field.ParticleDisplay.SKIN_POINTS, 1.6f), name);
                bitmap.recycle();
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }
}
