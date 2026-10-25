package net.programmierecke.radiodroid2.recording;

import android.app.TimePickerDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import net.programmierecke.radiodroid2.RadioDroidApp;
import net.programmierecke.radiodroid2.Utils;
import net.programmierecke.radiodroid2.station.DataRadioStation;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

/** Radio fork: compact one-screen list/add/cancel of timed recordings. */
public class ActivityRecordSchedules extends AppCompatActivity {
    private static final String[] DAYS = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
    private LinearLayout list;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Utils.wrapSkinFontScale(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        setTheme(Utils.getThemeResId(this));
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, p);
        Button add = new Button(this);
        add.setText("+ New scheduled recording");
        add.setOnClickListener(v -> showAdd(false));
        root.addView(add);
        Button now = new Button(this);
        now.setText("Record now silently");
        now.setOnClickListener(v -> showAdd(true));
        root.addView(now);
        TextView hint = text("Files are saved to Music/Radio. Silent recordings download the stream without playing it.", 12);
        root.addView(hint);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv);
        setContentView(root);
        refresh();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(Typeface.MONOSPACE);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private void refresh() {
        list.removeAllViews();
        List<RecordSchedules.Item> items = RecordSchedules.load(this);
        if (items.isEmpty()) list.addView(text("No scheduled recordings.", 14));
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (RecordSchedules.Item it : items) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            StringBuilder d = new StringBuilder();
            if (it.days == 0) d.append("once");
            else for (int i = 0; i < 7; i++) if ((it.days & (1 << i)) != 0) d.append(DAYS[i]).append(' ');
            String s = String.format("%s\n%02d:%02d  %d min  %s\n%s", it.stationName, it.hour, it.minute,
                    it.durationMin, d.toString().trim(),
                    it.enabled ? "next: " + df.format(new Date(it.nextFire)) : "done / disabled");
            TextView t = text(s, 14);
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            CheckBox silent = new CheckBox(this);
            silent.setText("silent");
            silent.setTextSize(11);
            silent.setChecked(it.silent);
            silent.setOnCheckedChangeListener((b2, on) -> RecordSchedules.setSilent(this, it.id, on));
            row.addView(silent);
            Button del = new Button(this);
            del.setText("Cancel");
            del.setOnClickListener(v -> {
                RecordSchedules.remove(this, it.id);
                refresh();
            });
            row.addView(del);
            list.addView(row);
        }
    }

    private void showAdd(boolean recordNow) {
        RadioDroidApp app = (RadioDroidApp) getApplication();
        LinkedHashMap<String, DataRadioStation> stations = new LinkedHashMap<>();
        for (DataRadioStation s : app.getFavouriteManager().getList()) stations.put(s.StationUuid, s);
        for (DataRadioStation s : app.getHistoryManager().getList()) if (!stations.containsKey(s.StationUuid)) stations.put(s.StationUuid, s);
        final List<DataRadioStation> st = new ArrayList<>(stations.values());
        if (st.isEmpty()) {
            Toast.makeText(this, "Star or play a station first", Toast.LENGTH_LONG).show();
            return;
        }
        List<String> names = new ArrayList<>();
        for (DataRadioStation s : st) names.add(s.Name);

        LinearLayout f = new LinearLayout(this);
        f.setOrientation(LinearLayout.VERTICAL);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        f.addView(sp);
        final int[] hm = {java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY), 0};
        Button time = new Button(this);
        time.setText(String.format("Start %02d:%02d", hm[0], hm[1]));
        time.setOnClickListener(v -> new TimePickerDialog(this, (tp, h, m) -> {
            hm[0] = h; hm[1] = m;
            time.setText(String.format("Start %02d:%02d", h, m));
        }, hm[0], hm[1], android.text.format.DateFormat.is24HourFormat(this)).show());
        if (!recordNow) f.addView(time);
        EditText dur = new EditText(this);
        dur.setInputType(InputType.TYPE_CLASS_NUMBER);
        dur.setHint("Duration (minutes)");
        dur.setText("60");
        f.addView(dur);
        CheckBox silentBox = new CheckBox(this);
        silentBox.setText("Record silently (don't play aloud)");
        silentBox.setChecked(true);
        if (!recordNow) f.addView(silentBox);
        TextView repeatLabel = text("Repeat (none = once):", 12);
        if (!recordNow) f.addView(repeatLabel);
        LinearLayout days = new LinearLayout(this);
        CheckBox[] cb = new CheckBox[7];
        for (int i = 0; i < 7; i++) {
            cb[i] = new CheckBox(this);
            cb[i].setText(DAYS[i]);
            cb[i].setTextSize(11);
            days.addView(cb[i], new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (!recordNow) f.addView(days);

        new AlertDialog.Builder(this)
                .setTitle(recordNow ? "Record now (silent)" : "Schedule recording")
                .setView(f)
                .setPositiveButton("Save", (dlg, w) -> {
                    int mask = 0;
                    for (int i = 0; i < 7; i++) if (cb[i].isChecked()) mask |= 1 << i;
                    int minutes = 60;
                    try { minutes = Integer.parseInt(dur.getText().toString()); } catch (Exception ignored) { }
                    DataRadioStation chosen = st.get(sp.getSelectedItemPosition());
                    if (recordNow) {
                        SilentRecordService.start(this, chosen.toJson().toString(), Math.max(1, minutes));
                        Toast.makeText(this, "Recording " + chosen.Name + " for " + Math.max(1, minutes) + " min", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    RecordSchedules.add(this, chosen, hm[0], hm[1], mask, minutes, silentBox.isChecked());
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
