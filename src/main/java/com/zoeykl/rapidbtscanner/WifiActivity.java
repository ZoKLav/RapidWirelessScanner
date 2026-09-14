package com.zoeykl.rapidbtscanner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class WifiActivity extends Activity {
    private static final int REQ_LOCATION = 2101;
    private static final String NEARBY_WIFI = "android.permission.NEARBY_WIFI_DEVICES";
    private WifiManager wifi;
    private WifiRttManager rtt;
    private SharedPreferences prefs;
    private TextView status;
    private WifiAdapter adapter;
    private boolean receiverRegistered;
    private long lastRequested;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable autoScan = new Runnable() {
        @Override public void run() {
            long interval = prefs.getLong("wifiIntervalMs", 30000L);
            if (interval > 0) {
                requestScan(false);
                handler.postDelayed(this, interval);
            }
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                boolean fresh = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                loadResults(fresh ? "Fresh scan" : "Cached results");
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (Build.VERSION.SDK_INT >= 28) rtt = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiverSafe();
        if (!hasPermissions()) requestWifiPermissions();
        else {
            loadResults("Current results");
            requestScan(true);
        }
        handler.removeCallbacks(autoScan);
        if (prefs.getLong("wifiIntervalMs", 30000L) > 0) handler.postDelayed(autoScan, prefs.getLong("wifiIntervalMs", 30000L));
    }

    @Override protected void onPause() {
        handler.removeCallbacks(autoScan);
        if (receiverRegistered) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) { }
            receiverRegistered = false;
        }
        super.onPause();
    }

    private void buildUi() {
        int accent = accent();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(10));
        root.setBackgroundColor(Color.BLACK);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Wi-Fi Scanner");
        title.setTextColor(accent);
        title.setTextSize(23f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button scan = button("Scan");
        scan.setOnClickListener(v -> requestScan(true));
        header.addView(scan);
        root.addView(header);

        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(12.5f);
        status.setPadding(0, dp(4), 0, dp(8));
        root.addView(status);

        TextView note = new TextView(this);
        note.setText("Android throttles foreground Wi-Fi scan requests. This screen also listens for scans performed by the system or other apps, so results can update even when a request is throttled.");
        note.setTextColor(Color.GRAY);
        note.setTextSize(11.5f);
        note.setPadding(0, 0, 0, dp(8));
        root.addView(note);

        ListView list = new ListView(this);
        adapter = new WifiAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> showDetails(adapter.getItem(pos)));
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void registerReceiverSafe() {
        if (receiverRegistered) return;
        IntentFilter f = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, f);
        receiverRegistered = true;
    }

    private boolean hasLocation() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasPermissions() {
        if (!hasLocation()) return false;
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(NEARBY_WIFI) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestWifiPermissions() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, NEARBY_WIFI}, REQ_LOCATION);
        else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && hasPermissions()) {
            loadResults("Permission granted");
            requestScan(true);
        } else if (requestCode == REQ_LOCATION) {
            status.setText("Precise location permission is required by Android for Wi-Fi scan results.");
        }
    }

    @SuppressWarnings("deprecation")
    private void requestScan(boolean userInitiated) {
        if (wifi == null) { status.setText("Wi-Fi service unavailable"); return; }
        if (!hasPermissions()) { requestWifiPermissions(); return; }
        if (!wifi.isWifiEnabled()) { status.setText("Wi-Fi is off. Cached results may still be visible."); loadResults("Wi-Fi off"); return; }
        long now = System.currentTimeMillis();
        if (!userInitiated && now - lastRequested < 29000L) return;
        lastRequested = now;
        try {
            boolean accepted = wifi.startScan();
            status.setText(accepted ? "Scan requested…" : "Scan request throttled/rejected; showing latest cached results");
            if (!accepted) loadResults("Cached results");
        } catch (SecurityException e) {
            status.setText("Wi-Fi scan blocked by permission/location settings");
        } catch (Exception e) {
            status.setText("Wi-Fi scan failed: " + e.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("deprecation")
    private void loadResults(String prefix) {
        if (wifi == null || !hasPermissions()) return;
        try {
            List<ScanResult> results = wifi.getScanResults();
            ArrayList<ScanResult> copy = new ArrayList<>(results == null ? Collections.<ScanResult>emptyList() : results);
            Collections.sort(copy, new Comparator<ScanResult>() {
                @Override public int compare(ScanResult a, ScanResult b) { return Integer.compare(b.level, a.level); }
            });
            adapter.submit(copy);
            status.setText(prefix + " • " + copy.size() + " access points");
        } catch (SecurityException e) {
            status.setText("Wi-Fi results blocked by Android permission/location settings");
        }
    }

    private void showDetails(ScanResult r) {
        if (r == null) return;
        String detail = detail(r);
        boolean canRange = Build.VERSION.SDK_INT >= 28 && rtt != null
                && getPackageManager().hasSystemFeature("android.hardware.wifi.rtt")
                && r.is80211mcResponder();
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(ssid(r))
                .setMessage(detail)
                .setPositiveButton("Copy", null)
                .setNegativeButton("Close", null);
        if (canRange) builder.setNeutralButton("RTT Range", null);
        AlertDialog d = builder.create();
        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent());
            d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accent());
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Wi-Fi access point", detail));
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
            });
            if (canRange && d.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
                d.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(accent());
                d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> rangeAccessPoint(r, d));
            }
        });
        d.show();
    }

    private void rangeAccessPoint(final ScanResult ap, final AlertDialog dialog) {
        if (Build.VERSION.SDK_INT < 28 || rtt == null) return;
        if (!hasPermissions()) { requestWifiPermissions(); return; }
        try {
            RangingRequest request = new RangingRequest.Builder().addAccessPoint(ap).build();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText("Ranging…");
            rtt.startRanging(request, getMainExecutor(), new RangingResultCallback() {
                @Override public void onRangingFailure(int code) {
                    Toast.makeText(WifiActivity.this, "RTT ranging failed: " + code, Toast.LENGTH_LONG).show();
                    if (dialog.isShowing()) { dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true); dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText("RTT Range"); }
                }
                @Override public void onRangingResults(List<RangingResult> results) {
                    if (results == null || results.isEmpty()) { onRangingFailure(-1); return; }
                    RangingResult x = results.get(0);
                    if (x.getStatus() == RangingResult.STATUS_SUCCESS) {
                        double meters = x.getDistanceMm() / 1000.0;
                        String msg = String.format(Locale.ROOT, "%.2f m  •  RSSI %d dBm", meters, x.getRssi());
                        Toast.makeText(WifiActivity.this, msg, Toast.LENGTH_LONG).show();
                        if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText(String.format(Locale.ROOT, "%.2f m", meters));
                    } else onRangingFailure(x.getStatus());
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "RTT unavailable: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    @SuppressWarnings("deprecation")
    private String detail(ScanResult r) {
        StringBuilder s = new StringBuilder();
        s.append("SSID: ").append(ssid(r)).append('\n');
        s.append("BSSID: ").append(r.BSSID).append('\n');
        s.append("RSSI: ").append(r.level).append(" dBm\n");
        s.append("Frequency: ").append(r.frequency).append(" MHz\n");
        s.append("Channel: ").append(channel(r.frequency)).append('\n');
        s.append("Channel width: ").append(channelWidth(r.channelWidth)).append('\n');
        if (Build.VERSION.SDK_INT >= 30) s.append("Wi-Fi standard: ").append(wifiStandard(r.getWifiStandard())).append('\n');
        if (Build.VERSION.SDK_INT >= 23) s.append("802.11mc RTT responder: ").append(r.is80211mcResponder() ? "yes" : "no").append('\n');
        s.append("Capabilities: ").append(r.capabilities == null ? "" : r.capabilities).append('\n');
        if (r.operatorFriendlyName != null && r.operatorFriendlyName.length() > 0) s.append("Operator: ").append(r.operatorFriendlyName).append('\n');
        if (r.venueName != null && r.venueName.length() > 0) s.append("Venue: ").append(r.venueName).append('\n');
        if (Build.VERSION.SDK_INT >= 23) {
            s.append("Center freq 0: ").append(r.centerFreq0).append(" MHz\n");
            s.append("Center freq 1: ").append(r.centerFreq1).append(" MHz\n");
        }
        return s.toString();
    }

    @SuppressWarnings("deprecation")
    private String ssid(ScanResult r) {
        return r == null || r.SSID == null || r.SSID.length() == 0 ? "(hidden SSID)" : r.SSID;
    }

    private String wifiStandard(int v) {
        switch (v) {
            case ScanResult.WIFI_STANDARD_LEGACY: return "legacy";
            case ScanResult.WIFI_STANDARD_11N: return "802.11n / Wi-Fi 4";
            case ScanResult.WIFI_STANDARD_11AC: return "802.11ac / Wi-Fi 5";
            case ScanResult.WIFI_STANDARD_11AX: return "802.11ax / Wi-Fi 6/6E";
            case ScanResult.WIFI_STANDARD_11AD: return "802.11ad / WiGig";
            case ScanResult.WIFI_STANDARD_11BE: return "802.11be / Wi-Fi 7";
            default: return "unknown";
        }
    }

    private String channelWidth(int v) {
        switch (v) {
            case ScanResult.CHANNEL_WIDTH_20MHZ: return "20 MHz";
            case ScanResult.CHANNEL_WIDTH_40MHZ: return "40 MHz";
            case ScanResult.CHANNEL_WIDTH_80MHZ: return "80 MHz";
            case ScanResult.CHANNEL_WIDTH_160MHZ: return "160 MHz";
            case ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ: return "80+80 MHz";
            case ScanResult.CHANNEL_WIDTH_320MHZ: return "320 MHz";
            default: return "unknown";
        }
    }

    private String channel(int mhz) {
        if (mhz == 2484) return "2.4 GHz ch 14";
        if (mhz >= 2412 && mhz <= 2472) return "2.4 GHz ch " + ((mhz - 2407) / 5);
        if (mhz >= 5000 && mhz < 5900) return "5 GHz ch " + ((mhz - 5000) / 5);
        if (mhz == 5935) return "6 GHz ch 2";
        if (mhz >= 5955 && mhz <= 7115) return "6 GHz ch " + ((mhz - 5950) / 5);
        if (mhz >= 58320 && mhz <= 70200) return "60 GHz / WiGig";
        return "unknown";
    }

    private int accent() { return prefs.getInt("accentColor", Color.rgb(229, 57, 53)); }
    private Button button(String s) { Button b=new Button(this); b.setText(s); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.getBackground().setColorFilter(accent(), PorterDuff.Mode.SRC_ATOP); return b; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private final class WifiAdapter extends BaseAdapter {
        private List<ScanResult> items = Collections.emptyList();
        void submit(List<ScanResult> x) { items = x; notifyDataSetChanged(); }
        @Override public int getCount() { return items.size(); }
        @Override public ScanResult getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { ScanResult r=getItem(p); return r.BSSID == null ? p : r.BSSID.hashCode(); }
        @Override public View getView(int p, View cv, ViewGroup parent) {
            LinearLayout row;
            TextView a,b;
            if (cv == null) {
                row = new LinearLayout(WifiActivity.this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(dp(12),dp(10),dp(12),dp(10)); row.setBackgroundColor(Color.rgb(18,18,18));
                a=new TextView(WifiActivity.this); a.setTextColor(Color.WHITE); a.setTextSize(16f);
                b=new TextView(WifiActivity.this); b.setTextColor(Color.LTGRAY); b.setTextSize(12f);
                row.addView(a); row.addView(b); row.setTag(new TextView[]{a,b}); cv=row;
            } else { TextView[] h=(TextView[])cv.getTag(); a=h[0]; b=h[1]; }
            ScanResult r=getItem(p); a.setText(ssid(r));
            String std = Build.VERSION.SDK_INT >= 30 ? wifiStandard(r.getWifiStandard()) : "Wi-Fi";
            b.setText(r.level + " dBm • " + r.frequency + " MHz • " + std + " • " + (r.BSSID == null ? "" : r.BSSID));
            return cv;
        }
    }
}
