package com.zoeykl.rapidbtscanner;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private int accent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        accent = prefs.getInt("accentColor", Color.rgb(229, 57, 53));
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int current = prefs.getInt("accentColor", Color.rgb(229, 57, 53));
        if (current != accent) recreate();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(24));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Rapid Wireless Scanner");
        title.setTextColor(accent);
        title.setTextSize(25f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button settings = button("Settings");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(settings);
        root.addView(header);

        TextView intro = text("One utility for the radios Android can actually expose. Scan modules enumerate nearby devices/signals; Hardware Inventory detects radios and vendor capabilities that do not support blind discovery.", 13f, Color.LTGRAY);
        intro.setPadding(0, dp(6), 0, dp(14));
        root.addView(intro);

        addModule(root, "Bluetooth", featureSummary(PackageManager.FEATURE_BLUETOOTH, "Classic + BLE scanner"), BluetoothActivity.class, has(PackageManager.FEATURE_BLUETOOTH));
        addModule(root, "Wi-Fi access points", featureSummary(PackageManager.FEATURE_WIFI, "SSID/BSSID, RSSI, frequency, security, Wi-Fi generation, RTT flag"), WifiActivity.class, has(PackageManager.FEATURE_WIFI));
        addModule(root, "Wi-Fi Direct", featureSummary("android.hardware.wifi.direct", "Nearby P2P peers exposed by Android"), WifiDirectActivity.class, has("android.hardware.wifi.direct"));
        addModule(root, "NFC tags", featureSummary("android.hardware.nfc", "Foreground reader for NFC-A/B/F/V, ISO-DEP and NDEF tags"), NfcActivity.class, has("android.hardware.nfc"));
        addModule(root, "Cellular radio", featureSummary("android.hardware.telephony", "Serving and neighboring GSM/WCDMA/LTE/5G NR cells"), CellularActivity.class, has("android.hardware.telephony"));
        addModule(root, "GNSS satellites", featureSummary(PackageManager.FEATURE_LOCATION_GPS, "GPS/Galileo/GLONASS/BeiDou/QZSS/NavIC satellite reception"), GnssActivity.class, has(PackageManager.FEATURE_LOCATION_GPS));
        addModule(root, "RF / hardware inventory", "Always available • UWB, Thread, Wi-Fi Aware/RTT, IR, satellite, USB radios, LoRa heuristics and vendor feature strings", HardwareActivity.class, true);

        TextView foot = text("Not every radio is discoverable. UWB requires a configured ranging peer, Wi-Fi Aware requires a service name, consumer IR is transmit-only, and LoRa has no standard Android API. The hardware screen calls those cases out instead of inventing scan results.", 11.5f, Color.GRAY);
        foot.setPadding(0, dp(16), 0, 0);
        root.addView(foot);
        setContentView(scroll);
    }

    private void addModule(LinearLayout root, String name, String desc, final Class<?> cls, boolean supported) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.rgb(18, 18, 18));
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), supported ? accent : Color.DKGRAY);
        box.setBackground(bg);

        TextView n = text(name + (supported ? "" : "  — unavailable"), 17f, supported ? Color.WHITE : Color.GRAY);
        n.setTypeface(n.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(n);
        TextView d = text(desc, 12f, supported ? Color.LTGRAY : Color.DKGRAY);
        d.setPadding(0, dp(3), 0, dp(7));
        box.addView(d);
        Button open = button(supported ? "Open" : "Not supported");
        open.setEnabled(supported);
        open.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, cls)));
        box.addView(open, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        root.addView(box, lp);
    }

    private String featureSummary(String feature, String suffix) {
        return (has(feature) ? "Supported" : "Not reported by device") + " • " + suffix;
    }

    private boolean has(String feature) {
        return getPackageManager().hasSystemFeature(feature);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.getBackground().setColorFilter(accent, PorterDuff.Mode.SRC_ATOP);
        return b;
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
