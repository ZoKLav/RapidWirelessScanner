package com.zoeykl.rapidbtscanner;

import android.app.Activity;
import android.bluetooth.le.ScanSettings;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout content;
    private int accent;
    private EditText accentHex;
    private TextView rssiValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        accent = prefs.getInt("accentColor", Color.rgb(229, 57, 53));
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(24));
        scroll.addView(content);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Wireless Scanner Settings");
        title.setTextSize(24f);
        title.setTextColor(accent);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button done = makeButton("Done");
        done.setOnClickListener(v -> finish());
        header.addView(done);
        content.addView(header);

        TextView subtitle = new TextView(this);
        subtitle.setText("Changes are saved immediately. Radio-specific settings apply when that scanner next resumes.");
        subtitle.setTextSize(12f);
        subtitle.setTextColor(Color.GRAY);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        content.addView(subtitle);

        addSection("Appearance");
        addAccentPicker();

        addSection("Bluetooth scanning");
        addSpinner("BLE scan mode", new String[]{"Low latency", "Balanced", "Low power"}, scanModeIndex(), position -> {
            int value = position == 0 ? ScanSettings.SCAN_MODE_LOW_LATENCY : position == 1 ? ScanSettings.SCAN_MODE_BALANCED : ScanSettings.SCAN_MODE_LOW_POWER;
            prefs.edit().putInt("scanMode", value).apply();
        });
        addCheckBox("Include Classic Bluetooth discovery", "Find discoverable Classic/dual-mode devices too. This is slower and more power-hungry than BLE scanning, so it is off by default.", "classicDiscovery", false);
        addCheckBox("Show paired devices", "Keep already-bonded devices in the list even when they are not currently advertising.", "showBonded", true);
        addCheckBox("Show unnamed devices", "Include advertisements that do not expose a device name.", "showUnnamed", true);
        addRssiControl();

        addSection("Wi-Fi scanning");
        addSpinner("Automatic Wi-Fi scan interval", new String[]{"Manual only", "30 seconds", "1 minute", "2 minutes"}, wifiIntervalIndex(), position -> {
            long[] values = {0L, 30000L, 60000L, 120000L};
            prefs.edit().putLong("wifiIntervalMs", values[position]).apply();
        });
        TextView wifiNote = new TextView(this);
        wifiNote.setText("Android may throttle scan requests regardless of this setting. The Wi-Fi screen also listens for system scan-result broadcasts.");
        wifiNote.setTextSize(11.5f);
        wifiNote.setTextColor(Color.GRAY);
        wifiNote.setPadding(0, 0, 0, dp(7));
        content.addView(wifiNote);

        addSection("NFC");
        addCheckBox("Silence NFC discovery sound", "Ask reader mode not to play the platform tag-discovery sound.", "nfcNoSound", false);
        addCheckBox("Open NFC details immediately", "Automatically open the detail dialog whenever a tag is read.", "nfcAutoOpen", false);

        addSection("Results");
        addSpinner("Sort results", new String[]{"Strongest signal", "Most recently seen", "Name"}, sortIndex(), position -> {
            String value = position == 1 ? "recent" : position == 2 ? "name" : "signal";
            prefs.edit().putString("sortMode", value).apply();
        });
        addSpinner("Remove stale devices after", new String[]{"15 seconds", "30 seconds", "1 minute", "5 minutes", "Never"}, staleIndex(), position -> {
            long[] values = {15000L, 30000L, 60000L, 300000L, 0L};
            prefs.edit().putLong("staleMs", values[position]).apply();
        });
        addSpinner("UI refresh rate", new String[]{"100 ms", "250 ms", "500 ms", "1 second", "2 seconds"}, refreshIndex(), position -> {
            long[] values = {100L, 250L, 500L, 1000L, 2000L};
            prefs.edit().putLong("refreshMs", values[position]).apply();
        });

        addSection("Behavior");
        addCheckBox("Vibrate for newly seen devices", "A short vibration occurs the first time a device appears in the current session.", "vibrateNew", false);
        addCheckBox("Keep screen awake", "Useful when using the phone as a live scanner display.", "keepScreenOn", false);

        addSection("Reset");
        Button reset = makeButton("Reset settings to defaults");
        reset.setOnClickListener(v -> {
            boolean keepPins = true;
            java.util.Set<String> pinned = prefs.getStringSet("pinned", java.util.Collections.<String>emptySet());
            java.util.Set<String> copy = pinned == null ? new java.util.HashSet<String>() : new java.util.HashSet<>(pinned);
            prefs.edit().clear().apply();
            if (keepPins && !copy.isEmpty()) prefs.edit().putStringSet("pinned", copy).apply();
            Toast.makeText(SettingsActivity.this, "Settings reset; pins kept", Toast.LENGTH_SHORT).show();
            recreate();
        });
        content.addView(reset, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView privacy = new TextView(this);
        privacy.setText("Rapid Wireless Scanner does not request Internet or storage access. Bluetooth uses Nearby devices on Android 12+. Wi-Fi scans and cellular cell information require precise-location permission because Android treats those radio observations as location-sensitive. Wi-Fi Direct uses Nearby Wi-Fi devices on Android 13+. NFC uses the normal NFC permission only.");
        privacy.setTextSize(11.5f);
        privacy.setTextColor(Color.GRAY);
        privacy.setPadding(0, dp(14), 0, 0);
        content.addView(privacy);

        setContentView(scroll);
    }

    private void addAccentPicker() {
        TextView label = label("Accent color");
        content.addView(label);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        accentHex = new EditText(this);
        accentHex.setSingleLine(true);
        accentHex.setInputType(InputType.TYPE_CLASS_TEXT);
        accentHex.setText(String.format(Locale.ROOT, "#%06X", 0xFFFFFF & accent));
        accentHex.setTextColor(Color.WHITE);
        accentHex.setBackgroundTintList(ColorStateList.valueOf(accent));
        row.addView(accentHex, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button apply = makeButton("Apply");
        apply.setOnClickListener(v -> applyHexColor());
        row.addView(apply);
        content.addView(row);

        int[] colors = {
                Color.rgb(229, 57, 53), Color.rgb(255, 82, 82), Color.rgb(255, 145, 0), Color.rgb(255, 214, 0),
                Color.rgb(0, 200, 83), Color.rgb(0, 188, 212), Color.rgb(41, 121, 255), Color.rgb(124, 77, 255),
                Color.rgb(213, 0, 249), Color.rgb(255, 64, 129), Color.rgb(176, 190, 197), Color.WHITE
        };
        LinearLayout palette = new LinearLayout(this);
        palette.setOrientation(LinearLayout.HORIZONTAL);
        palette.setPadding(0, dp(6), 0, dp(8));
        for (final int color : colors) {
            Button swatch = new Button(this);
            swatch.setText("");
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(color);
            bg.setStroke(dp(1), Color.DKGRAY);
            swatch.setBackground(bg);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(38), 1f);
            p.setMargins(dp(2), 0, dp(2), 0);
            palette.addView(swatch, p);
            swatch.setOnClickListener(v -> saveAccent(color));
        }
        HorizontalScrollWrapper wrapper = new HorizontalScrollWrapper(this);
        wrapper.addView(palette, new ViewGroup.LayoutParams(Math.max(dp(600), getResources().getDisplayMetrics().widthPixels), ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(wrapper);
    }

    private void applyHexColor() {
        try {
            String value = accentHex.getText().toString().trim();
            if (!value.startsWith("#")) value = "#" + value;
            int color = Color.parseColor(value);
            saveAccent(color);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Use a color like #E53935", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAccent(int color) {
        prefs.edit().putInt("accentColor", color).apply();
        accent = color;
        Toast.makeText(this, "Accent saved", Toast.LENGTH_SHORT).show();
        recreate();
    }

    private void addRssiControl() {
        TextView title = label("Minimum signal strength");
        content.addView(title);
        rssiValue = new TextView(this);
        rssiValue.setTextColor(Color.LTGRAY);
        rssiValue.setTextSize(12f);
        content.addView(rssiValue);

        SeekBar bar = new SeekBar(this);
        bar.setMax(70);
        int current = prefs.getInt("minRssi", -100);
        bar.setProgress(Math.max(0, Math.min(70, current + 100)));
        bar.getProgressDrawable().setColorFilter(accent, PorterDuff.Mode.SRC_IN);
        bar.getThumb().setColorFilter(accent, PorterDuff.Mode.SRC_IN);
        updateRssiText(current);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int rssi = progress - 100;
                updateRssiText(rssi);
                if (fromUser) prefs.edit().putInt("minRssi", rssi).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        content.addView(bar);
    }

    private void updateRssiText(int rssi) {
        if (rssiValue != null) rssiValue.setText(rssi + " dBm  — devices weaker than this are hidden");
    }

    private void addCheckBox(String title, String description, String key, boolean def) {
        CheckBox box = new CheckBox(this);
        box.setText(title);
        box.setTextColor(Color.WHITE);
        box.setButtonTintList(ColorStateList.valueOf(accent));
        box.setChecked(prefs.getBoolean(key, def));
        box.setOnCheckedChangeListener((buttonView, isChecked) -> prefs.edit().putBoolean(key, isChecked).apply());
        content.addView(box);
        TextView desc = new TextView(this);
        desc.setText(description);
        desc.setTextSize(11.5f);
        desc.setTextColor(Color.GRAY);
        desc.setPadding(dp(30), 0, 0, dp(7));
        content.addView(desc);
    }

    private void addSpinner(String title, String[] values, int selected, final SelectionHandler handler) {
        TextView label = label(title);
        content.addView(label);
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            private int lastPosition = selected;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == lastPosition) return;
                lastPosition = position;
                handler.onSelected(position);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(7));
        content.addView(spinner, p);
    }

    private TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.LTGRAY);
        v.setTextSize(13f);
        v.setPadding(0, dp(4), 0, dp(2));
        return v;
    }

    private void addSection(String title) {
        TextView v = new TextView(this);
        v.setText(title.toUpperCase(Locale.ROOT));
        v.setTextColor(accent);
        v.setTextSize(13f);
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        v.setPadding(0, dp(16), 0, dp(6));
        content.addView(v);
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.getBackground().setColorFilter(accent, PorterDuff.Mode.SRC_ATOP);
        return button;
    }

    private int scanModeIndex() {
        int v = prefs.getInt("scanMode", ScanSettings.SCAN_MODE_LOW_LATENCY);
        if (v == ScanSettings.SCAN_MODE_BALANCED) return 1;
        if (v == ScanSettings.SCAN_MODE_LOW_POWER) return 2;
        return 0;
    }

    private int sortIndex() {
        String v = prefs.getString("sortMode", "signal");
        return "recent".equals(v) ? 1 : "name".equals(v) ? 2 : 0;
    }

    private int staleIndex() {
        long v = prefs.getLong("staleMs", 60000L);
        if (v == 15000L) return 0;
        if (v == 30000L) return 1;
        if (v == 300000L) return 3;
        if (v == 0L) return 4;
        return 2;
    }

    private int wifiIntervalIndex() {
        long v = prefs.getLong("wifiIntervalMs", 30000L);
        if (v == 0L) return 0;
        if (v == 60000L) return 2;
        if (v == 120000L) return 3;
        return 1;
    }

    private int refreshIndex() {
        long v = prefs.getLong("refreshMs", 250L);
        if (v == 100L) return 0;
        if (v == 500L) return 2;
        if (v == 1000L) return 3;
        if (v == 2000L) return 4;
        return 1;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface SelectionHandler {
        void onSelected(int position);
    }

    private static final class HorizontalScrollWrapper extends android.widget.HorizontalScrollView {
        HorizontalScrollWrapper(android.content.Context context) {
            super(context);
            setHorizontalScrollBarEnabled(false);
            setFillViewport(true);
        }
    }
}
