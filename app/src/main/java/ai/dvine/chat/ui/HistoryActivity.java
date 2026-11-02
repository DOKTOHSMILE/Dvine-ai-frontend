package ai.dvine.chat.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

import ai.dvine.R;
import ai.dvine.chat.ChatBook;

/** Search, rename, pin, archive, and delete chats. The brain is not involved. */
public final class HistoryActivity extends AppCompatActivity {
    private ChatBook book;
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        book = new ChatBook(new File(getFilesDir(), "chats.json"));
        TextView back = findViewById(R.id.history_back);
        if (back != null) {
            back.setOnClickListener(v -> finish());
        }
        EditText search = findViewById(R.id.history_search);
        if (search != null) {
            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    query = s == null ? "" : s.toString().trim().toLowerCase();
                    fill();
                }

                public void afterTextChanged(Editable s) {
                }
            });
        }
        fill();
    }

    private void fill() {
        LinearLayout list = findViewById(R.id.history_rows);
        if (list == null || book == null) {
            return;
        }
        list.removeAllViews();
        for (ChatBook.Section s : book.sections()) {
            String title = s.title == null ? "Chat" : s.title;
            if (!query.isEmpty() && !title.toLowerCase().contains(query) && !preview(s).toLowerCase().contains(query)) {
                continue;
            }
            TextView row = new TextView(this);
            String mark = (s.pinned ? "Pinned · " : "") + (s.archived ? "Archived · " : "");
            row.setText(mark + title + "\n" + preview(s) + "\nRename  Pin  Archive  Delete");
            row.setTextColor(getColor(R.color.text_primary));
            row.setTextSize(15f);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            row.setPadding(pad, pad, pad, pad);
            row.setOnClickListener(v -> choose(s));
            list.addView(row);
        }
    }

    private void choose(ChatBook.Section s) {
        String[] items = new String[] { "Open", "Rename", s.pinned ? "Unpin" : "Pin",
                s.archived ? "Unarchive" : "Archive", "Delete" };
        new android.app.AlertDialog.Builder(this)
                .setTitle(s.title)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        book.use(s.id);
                        setResult(RESULT_OK);
                        finish();
                    } else if (which == 1) {
                        askName(s);
                    } else if (which == 2) {
                        book.pin(s.id, !s.pinned);
                        fill();
                    } else if (which == 3) {
                        book.archive(s.id, !s.archived);
                        fill();
                    } else {
                        book.remove(s.id);
                        setResult(RESULT_OK);
                        fill();
                    }
                })
                .show();
    }

    private void askName(ChatBook.Section s) {
        EditText field = new EditText(this);
        field.setText(s.title);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Rename")
                .setView(field)
                .setPositiveButton("Save", (d, w) -> {
                    book.rename(s.id, field.getText() == null ? "" : field.getText().toString());
                    setResult(RESULT_OK);
                    fill();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String preview(ChatBook.Section s) {
        for (int i = s.messages.size() - 1; i >= 0; i--) {
            ChatBook.Message m = s.messages.get(i);
            if (m.body != null && !m.body.isEmpty() && "text".equals(m.kind)) {
                String t = m.body.replace('\n', ' ').trim();
                return t.length() > 72 ? t.substring(0, 72) + "…" : t;
            }
        }
        return s.persona == null ? "" : s.persona;
    }
}
