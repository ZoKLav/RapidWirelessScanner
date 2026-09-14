package com.zoeykl.rapidbtscanner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class GnssActivity extends Activity {
    private static final int REQ_LOCATION = 2401;
    private SharedPreferences prefs;
    private LocationManager location;
    private TextView status;
    private ArrayAdapter<String> adapter;
    private final ArrayList<SatRow> rows = new ArrayList<>();
    private boolean gnssRegistered;
    private boolean locationRequested;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final GnssStatus.Callback gnssCallback = new GnssStatus.Callback() {
        @Override public void onStarted() { status.setText("GNSS receiver active • waiting for satellites…"); }
        @Override public void onStopped() { status.setText("GNSS receiver stopped"); }
        @Override public void onFirstFix(int ttffMillis) { status.setText("First fix in " + ttffMillis + " ms"); }
        @Override public void onSatelliteStatusChanged(GnssStatus s) { render(s); }
    };

    private final LocationListener locationListener = new LocationListener() {
        @Override public void onLocationChanged(Location l) {
            if (l != null) status.setText("GNSS active • fix accuracy " + String.format(Locale.ROOT, "%.1f m", l.getAccuracy()));
        }
        @Override public void onProviderEnabled(String provider) { }
        @Override public void onProviderDisabled(String provider) { status.setText("GPS/GNSS provider disabled in system settings"); }
        @SuppressWarnings("deprecation") @Override public void onStatusChanged(String provider, int state, Bundle extras) { }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        location = (LocationManager) getSystemService(LOCATION_SERVICE);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!hasLocation()) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
        else startGnss();
    }

    @Override protected void onPause() {
        stopGnss();
        super.onPause();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(10));
        root.setBackgroundColor(Color.BLACK);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("GNSS Satellites");
        title.setTextColor(accent());
        title.setTextSize(23f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button restart = button("Restart");
        restart.setOnClickListener(v -> { stopGnss(); startGnss(); });
        head.addView(restart);
        root.addView(head);

        status = new TextView(this);
        status.setText("GNSS idle");
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(12.5f);
        status.setPadding(0, dp(4), 0, dp(6));
        root.addView(status);

        TextView note = new TextView(this);
        note.setText("Live satellite reception reported by Android. Stronger C/N₀ generally means a cleaner received navigation signal; this is not satellite-communications scanning.");
        note.setTextColor(Color.GRAY);
        note.setTextSize(11.5f);
        note.setPadding(0, 0, 0, dp(8));
        root.addView(note);

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>()) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextColor(Color.WHITE);
                t.setTextSize(14.2f);
                t.setBackgroundColor(Color.rgb(18,18,18));
                t.setPadding(dp(12), dp(10), dp(12), dp(10));
                return t;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            SatRow r = rows.get(pos);
            new AlertDialog.Builder(this).setTitle(r.title).setMessage(r.detail).setPositiveButton("Close", null).show();
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private boolean hasLocation() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_LOCATION) {
            if (hasLocation()) startGnss();
            else status.setText("Precise location permission is required for GNSS satellite status");
        }
    }

    @SuppressWarnings("MissingPermission")
    private void startGnss() {
        if (location == null) { status.setText("Location/GNSS service unavailable"); return; }
        if (!hasLocation()) return;
        if (!location.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            status.setText("GPS/GNSS provider disabled in system settings");
            return;
        }
        try {
            if (!gnssRegistered) gnssRegistered = location.registerGnssStatusCallback(gnssCallback, handler);
            if (!locationRequested) {
                location.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper());
                locationRequested = true;
            }
            status.setText(gnssRegistered ? "GNSS receiver active • waiting for satellite status…" : "GNSS status callback was rejected by the device");
        } catch (SecurityException e) {
            status.setText("GNSS access blocked by permission settings");
        } catch (Exception e) {
            status.setText("GNSS start failed: " + e.getClass().getSimpleName());
        }
    }

    private void stopGnss() {
        if (location == null) return;
        try { if (gnssRegistered) location.unregisterGnssStatusCallback(gnssCallback); } catch (Exception ignored) { }
        try { if (locationRequested) location.removeUpdates(locationListener); } catch (Exception ignored) { }
        gnssRegistered = false;
        locationRequested = false;
    }

    private void render(GnssStatus s) {
        rows.clear();
        int used = 0;
        for (int i = 0; i < s.getSatelliteCount(); i++) {
            int constellation = s.getConstellationType(i);
            int svid = s.getSvid(i);
            float cn0 = s.getCn0DbHz(i);
            boolean fix = s.usedInFix(i);
            if (fix) used++;
            String name = constellationName(constellation);
            StringBuilder d = new StringBuilder();
            d.append("Constellation: ").append(name).append('\n');
            d.append("SVID: ").append(svid).append('\n');
            d.append("C/N0: ").append(String.format(Locale.ROOT, "%.1f dB-Hz", cn0)).append('\n');
            d.append("Elevation: ").append(String.format(Locale.ROOT, "%.1f°", s.getElevationDegrees(i))).append('\n');
            d.append("Azimuth: ").append(String.format(Locale.ROOT, "%.1f°", s.getAzimuthDegrees(i))).append('\n');
            d.append("Used in fix: ").append(fix ? "yes" : "no").append('\n');
            d.append("Ephemeris: ").append(s.hasEphemerisData(i) ? "yes" : "no").append('\n');
            d.append("Almanac: ").append(s.hasAlmanacData(i) ? "yes" : "no").append('\n');
            if (Build.VERSION.SDK_INT >= 26 && s.hasCarrierFrequencyHz(i)) d.append("Carrier: ").append(String.format(Locale.ROOT, "%.3f MHz", s.getCarrierFrequencyHz(i) / 1000000f)).append('\n');
            if (Build.VERSION.SDK_INT >= 30 && s.hasBasebandCn0DbHz(i)) d.append("Baseband C/N0: ").append(String.format(Locale.ROOT, "%.1f dB-Hz", s.getBasebandCn0DbHz(i))).append('\n');
            rows.add(new SatRow(name + " " + svid, d.toString(), cn0, fix));
        }
        Collections.sort(rows, new Comparator<SatRow>() {
            @Override public int compare(SatRow a, SatRow b) {
                if (a.used != b.used) return a.used ? -1 : 1;
                return Float.compare(b.cn0, a.cn0);
            }
        });
        adapter.clear();
        for (SatRow r : rows) adapter.add((r.used ? "★ " : "") + r.title + "\n" + String.format(Locale.ROOT, "%.1f dB-Hz", r.cn0) + (r.used ? " • used in fix" : ""));
        adapter.notifyDataSetChanged();
        status.setText(rows.size() + " satellite" + (rows.size() == 1 ? "" : "s") + " visible • " + used + " used in fix");
    }

    private String constellationName(int c) {
        switch (c) {
            case GnssStatus.CONSTELLATION_GPS: return "GPS";
            case GnssStatus.CONSTELLATION_SBAS: return "SBAS";
            case GnssStatus.CONSTELLATION_GLONASS: return "GLONASS";
            case GnssStatus.CONSTELLATION_QZSS: return "QZSS";
            case GnssStatus.CONSTELLATION_BEIDOU: return "BeiDou";
            case GnssStatus.CONSTELLATION_GALILEO: return "Galileo";
            case 7: return "NavIC/IRNSS";
            default: return "Unknown(" + c + ")";
        }
    }

    private int accent() { return prefs.getInt("accentColor", Color.rgb(229,57,53)); }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.getBackground().setColorFilter(accent(), PorterDuff.Mode.SRC_ATOP); return b; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static final class SatRow {
        final String title, detail;
        final float cn0;
        final boolean used;
        SatRow(String t, String d, float c, boolean u) { title=t; detail=d; cn0=c; used=u; }
    }
}
