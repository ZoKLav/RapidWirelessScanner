package com.zoeykl.rapidbtscanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BluetoothActivity extends Activity {
    private static final int PERMISSION_REQUEST = 1001;
    private static final int EXPORT_REQUEST = 1002;
    private static final String PERM_SCAN = "android.permission.BLUETOOTH_SCAN";
    private static final String PERM_CONNECT = "android.permission.BLUETOOTH_CONNECT";

    private final ConcurrentHashMap<String, DeviceRecord> devices = new ConcurrentHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private SharedPreferences prefs;
    private DeviceAdapter adapter;
    private TextView status;
    private EditText searchBox;
    private Button scanButton;
    private Button settingsButton;
    private Button clearButton;
    private Button exportButton;
    private boolean scanning;
    private boolean userPaused;
    private boolean classicReceiverRegistered;
    private int appliedAccent;
    private List<DeviceRecord> pendingExport = Collections.emptyList();

    private final Runnable refreshLoop = new Runnable() {
        @Override
        public void run() {
            refreshList();
            handler.postDelayed(this, getRefreshMs());
        }
    };

    private final Runnable restartClassicDiscovery = new Runnable() {
        @Override
        public void run() {
            if (scanning && getBoolean("classicDiscovery", false)) {
                startClassicDiscovery();
            }
        }
    };

    private final ScanCallback bleCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result != null) consumeBle(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (results == null) return;
            for (ScanResult result : results) {
                if (result != null) consumeBle(result);
            }
        }

        @Override
        public void onScanFailed(final int errorCode) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    status.setText("BLE scan failed: " + scanErrorName(errorCode));
                }
            });
        }
    };

    private final BroadcastReceiver classicReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                if (device != null) consumeClassic(device, rssi);
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                handler.removeCallbacks(restartClassicDiscovery);
                handler.postDelayed(restartClassicDiscovery, 1200L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager != null ? manager.getAdapter() : null;
        appliedAccent = getAccent();
        buildUi();
        ensurePermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int accent = getAccent();
        if (accent != appliedAccent) {
            recreate();
            return;
        }
        applyWindowPreferences();
        registerClassicReceiver();
        loadBondedDevices();
        if (!userPaused) startScanningIfReady();
        handler.removeCallbacks(refreshLoop);
        handler.post(refreshLoop);
    }

    @Override
    protected void onPause() {
        stopScanning();
        unregisterClassicReceiver();
        handler.removeCallbacks(refreshLoop);
        handler.removeCallbacks(restartClassicDiscovery);
        super.onPause();
    }

    private void buildUi() {
        final int accent = getAccent();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(16), dp(14), dp(16), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Bluetooth Scanner");
        title.setTextSize(24f);
        title.setTextColor(accent);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        settingsButton = makeButton("Settings", accent);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BluetoothActivity.this, SettingsActivity.class));
            }
        });
        header.addView(settingsButton);
        root.addView(header);

        status = new TextView(this);
        status.setText("Starting scanner...");
        status.setTextSize(13f);
        status.setTextColor(Color.LTGRAY);
        status.setPadding(0, dp(4), 0, dp(8));
        root.addView(status);

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("Filter by name, address, service UUID, manufacturer...");
        searchBox.setHintTextColor(Color.DKGRAY);
        searchBox.setTextColor(Color.WHITE);
        searchBox.setBackgroundTintList(ColorStateList.valueOf(accent));
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshList(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        root.addView(searchBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(8), 0, dp(8));

        scanButton = makeButton("Pause", accent);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (scanning) {
                    userPaused = true;
                    stopScanning();
                } else {
                    userPaused = false;
                    startScanningIfReady();
                }
                updateScanButton();
                refreshList();
            }
        });
        controls.addView(scanButton, weightedButtonParams());

        clearButton = makeButton("Clear", accent);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                devices.clear();
                loadBondedDevices();
                refreshList();
            }
        });
        controls.addView(clearButton, weightedButtonParams());

        exportButton = makeButton("Export", accent);
        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beginCsvExport();
            }
        });
        controls.addView(exportButton, weightedButtonParams());
        root.addView(controls);

        TextView hint = new TextView(this);
        hint.setText("Tap for details. Long-press to pin/unpin. BLE scans continuously while this screen is open; optional Classic discovery is in Settings.");
        hint.setTextSize(11.5f);
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0, 0, 0, dp(8));
        root.addView(hint);

        ListView list = new ListView(this);
        list.setBackgroundColor(Color.BLACK);
        list.setDividerHeight(dp(1));
        adapter = new DeviceAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            DeviceRecord device = adapter.getDevice(position);
            if (device != null) showDeviceDetails(device);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            DeviceRecord device = adapter.getDevice(position);
            if (device == null) return false;
            device.pinned = !device.pinned;
            savePinned(device.key, device.pinned);
            refreshList();
            Toast.makeText(BluetoothActivity.this, device.pinned ? "Pinned" : "Unpinned", Toast.LENGTH_SHORT).show();
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private Button makeButton(String text, int accent) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.getBackground().setColorFilter(accent, PorterDuff.Mode.SRC_ATOP);
        return button;
    }

    private void applyWindowPreferences() {
        if (getBoolean("keepScreenOn", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getAccent() {
        return prefs.getInt("accentColor", Color.rgb(229, 57, 53));
    }

    private boolean getBoolean(String key, boolean def) {
        return prefs.getBoolean(key, def);
    }

    private long getRefreshMs() {
        return prefs.getLong("refreshMs", 250L);
    }

    private long getStaleMs() {
        return prefs.getLong("staleMs", 60000L);
    }

    private int getMinRssi() {
        return prefs.getInt("minRssi", -100);
    }

    private int getScanMode() {
        return prefs.getInt("scanMode", ScanSettings.SCAN_MODE_LOW_LATENCY);
    }

    private String getSortMode() {
        return prefs.getString("sortMode", "signal");
    }

    private void savePinned(String key, boolean pinned) {
        Set<String> source = prefs.getStringSet("pinned", Collections.<String>emptySet());
        Set<String> copy = source == null ? new HashSet<String>() : new HashSet<>(source);
        if (pinned) copy.add(key); else copy.remove(key);
        prefs.edit().putStringSet("pinned", copy).apply();
    }

    private boolean isPinned(String key) {
        Set<String> set = prefs.getStringSet("pinned", Collections.<String>emptySet());
        return set != null && set.contains(key);
    }

    private void ensurePermissions() {
        ArrayList<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(PERM_SCAN) != PackageManager.PERMISSION_GRANTED) needed.add(PERM_SCAN);
            if (checkSelfPermission(PERM_CONNECT) != PackageManager.PERMISSION_GRANTED) needed.add(PERM_CONNECT);
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            loadBondedDevices();
            startScanningIfReady();
        }
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(PERM_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(PERM_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void startScanningIfReady() {
        if (bluetoothAdapter == null) {
            status.setText("Bluetooth is unavailable on this device");
            return;
        }
        if (!hasPermissions()) {
            status.setText(Build.VERSION.SDK_INT >= 31 ? "Nearby devices permission required" : "Location permission required by this Android version");
            ensurePermissions();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            status.setText("Bluetooth is off");
            return;
        }
        if (scanning) return;

        scanning = true;
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bleScanner != null) {
                ScanSettings settings = new ScanSettings.Builder()
                        .setScanMode(getScanMode())
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .setReportDelay(0L)
                        .build();
                try {
                    bleScanner.startScan(null, settings, bleCallback);
                } catch (Exception e) {
                    status.setText("BLE scan could not start: " + e.getClass().getSimpleName());
                }
            }
        }
        if (getBoolean("classicDiscovery", false)) startClassicDiscovery();
        updateScanButton();
        refreshList();
    }

    @SuppressLint("MissingPermission")
    private void stopScanning() {
        if (!scanning) return;
        try {
            if (bleScanner != null && hasPermissions()) bleScanner.stopScan(bleCallback);
        } catch (Exception ignored) { }
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering() && hasPermissions()) bluetoothAdapter.cancelDiscovery();
        } catch (Exception ignored) { }
        scanning = false;
        updateScanButton();
    }

    @SuppressLint("MissingPermission")
    private void startClassicDiscovery() {
        if (!scanning || bluetoothAdapter == null || !hasPermissions()) return;
        try {
            if (!bluetoothAdapter.isDiscovering()) bluetoothAdapter.startDiscovery();
        } catch (Exception ignored) { }
    }

    private void registerClassicReceiver() {
        if (classicReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(classicReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(classicReceiver, filter);
        }
        classicReceiverRegistered = true;
    }

    private void unregisterClassicReceiver() {
        if (!classicReceiverRegistered) return;
        try { unregisterReceiver(classicReceiver); } catch (Exception ignored) { }
        classicReceiverRegistered = false;
    }

    @SuppressLint("MissingPermission")
    private void loadBondedDevices() {
        if (!getBoolean("showBonded", true) || bluetoothAdapter == null || !hasPermissions()) return;
        try {
            Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
            if (bonded == null) return;
            long now = System.currentTimeMillis();
            for (BluetoothDevice d : bonded) {
                String address = safeAddress(d);
                String key = address != null ? address : "bonded:" + safeName(d);
                DeviceRecord r = devices.get(key);
                if (r == null) {
                    r = new DeviceRecord(key);
                    r.firstSeenMs = now;
                    r.lastSeenMs = 0L;
                    r.rssi = Integer.MIN_VALUE;
                    devices.put(key, r);
                }
                r.address = address;
                r.name = firstNonEmpty(safeName(d), r.name);
                r.bonded = true;
                r.classic = true;
                r.pinned = isPinned(key);
                fillClassicMetadata(r, d);
            }
        } catch (SecurityException ignored) { }
    }

    @SuppressLint("MissingPermission")
    private void consumeBle(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        String address = safeAddress(device);
        ScanRecord record = result.getScanRecord();
        String advName = record != null ? record.getDeviceName() : null;
        String deviceName = safeName(device);
        String name = firstNonEmpty(advName, deviceName);
        String key = address != null ? address : "ble:" + (name != null ? name : rawHash(record));
        final long now = System.currentTimeMillis();
        final boolean[] created = new boolean[1];

        devices.compute(key, (k, old) -> {
            DeviceRecord r = old;
            if (r == null) {
                r = new DeviceRecord(k);
                r.firstSeenMs = now;
                created[0] = true;
            }
            r.lastSeenMs = now;
            r.sightings++;
            r.address = address;
            r.name = firstNonEmpty(name, r.name);
            r.rssi = result.getRssi();
            r.ble = true;
            r.pinned = isPinned(k);
            if (record != null) {
                r.txPower = record.getTxPowerLevel();
                r.serviceUuids = formatUuids(record.getServiceUuids());
                r.manufacturerData = formatManufacturers(record.getManufacturerSpecificData());
                r.serviceData = formatServiceData(record.getServiceData());
                r.rawAdvertisement = toHex(record.getBytes());
            }
            if (Build.VERSION.SDK_INT >= 26) r.connectable = result.isConnectable();
            fillClassicMetadata(r, device);
            return r;
        });
        if (created[0]) notifyNewDevice();
    }

    @SuppressLint("MissingPermission")
    private void consumeClassic(BluetoothDevice device, int rssi) {
        String address = safeAddress(device);
        String name = safeName(device);
        String key = address != null ? address : "classic:" + (name != null ? name : System.identityHashCode(device));
        long now = System.currentTimeMillis();
        boolean created = !devices.containsKey(key);
        DeviceRecord r = devices.get(key);
        if (r == null) {
            r = new DeviceRecord(key);
            r.firstSeenMs = now;
            devices.put(key, r);
        }
        r.lastSeenMs = now;
        r.sightings++;
        r.address = address;
        r.name = firstNonEmpty(name, r.name);
        if (rssi != Short.MIN_VALUE) r.rssi = rssi;
        r.classic = true;
        r.pinned = isPinned(key);
        fillClassicMetadata(r, device);
        if (created) notifyNewDevice();
    }

    @SuppressLint("MissingPermission")
    private void fillClassicMetadata(DeviceRecord r, BluetoothDevice device) {
        if (device == null) return;
        try {
            int bond = device.getBondState();
            r.bonded = bond == BluetoothDevice.BOND_BONDED;
            r.bondState = bond == BluetoothDevice.BOND_BONDED ? "Bonded" : bond == BluetoothDevice.BOND_BONDING ? "Bonding" : "Not bonded";
        } catch (SecurityException ignored) { }
        try {
            int type = device.getType();
            if (type == BluetoothDevice.DEVICE_TYPE_CLASSIC) r.deviceType = "Classic";
            else if (type == BluetoothDevice.DEVICE_TYPE_LE) r.deviceType = "BLE";
            else if (type == BluetoothDevice.DEVICE_TYPE_DUAL) r.deviceType = "Dual-mode";
            else r.deviceType = "Unknown";
        } catch (SecurityException ignored) { }
        try {
            BluetoothClass bc = device.getBluetoothClass();
            if (bc != null) r.bluetoothClass = String.format(Locale.ROOT, "0x%06X", bc.getDeviceClass());
        } catch (SecurityException ignored) { }
    }

    private void notifyNewDevice() {
        if (!getBoolean("vibrateNew", false)) return;
        try {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(30L, VibrationEffect.DEFAULT_AMPLITUDE));
            else vibrator.vibrate(30L);
        } catch (Exception ignored) { }
    }

    private void refreshList() {
        if (adapter == null) return;
        long now = System.currentTimeMillis();
        long staleMs = getStaleMs();
        int minRssi = getMinRssi();
        boolean showUnnamed = getBoolean("showUnnamed", true);
        boolean showBonded = getBoolean("showBonded", true);
        String query = searchBox == null ? "" : searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);
        ArrayList<DeviceRecord> shown = new ArrayList<>();
        ArrayList<String> remove = new ArrayList<>();

        for (Map.Entry<String, DeviceRecord> entry : devices.entrySet()) {
            DeviceRecord d = entry.getValue();
            boolean live = d.lastSeenMs > 0;
            if (live && staleMs > 0 && now - d.lastSeenMs > staleMs && !d.pinned) {
                remove.add(entry.getKey());
                continue;
            }
            if (!live && !(showBonded && d.bonded)) continue;
            if (!showUnnamed && (d.name == null || d.name.trim().isEmpty())) continue;
            if (live && d.rssi != Integer.MIN_VALUE && d.rssi < minRssi) continue;
            if (!query.isEmpty() && !searchableText(d).contains(query)) continue;
            shown.add(d);
        }
        for (String key : remove) devices.remove(key);

        Collections.sort(shown, buildComparator());
        adapter.submit(shown, now);
        updateScanButton();
        String bleMode = scanModeName(getScanMode());
        String classic = getBoolean("classicDiscovery", false) ? " + Classic" : "";
        status.setText((scanning ? "Scanning " + bleMode + classic : "Paused") + "  •  " + devices.size() + " known / " + shown.size() + " shown");
    }

    private Comparator<DeviceRecord> buildComparator() {
        final String sort = getSortMode();
        Comparator<DeviceRecord> secondary;
        if ("recent".equals(sort)) {
            secondary = (a, b) -> Long.compare(b.lastSeenMs, a.lastSeenMs);
        } else if ("name".equals(sort)) {
            secondary = (a, b) -> displayName(a).compareToIgnoreCase(displayName(b));
        } else {
            secondary = (a, b) -> Integer.compare(normalizedRssi(b.rssi), normalizedRssi(a.rssi));
        }
        return (a, b) -> {
            if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
            return secondary.compare(a, b);
        };
    }

    private int normalizedRssi(int rssi) {
        return rssi == Integer.MIN_VALUE ? -999 : rssi;
    }

    private String searchableText(DeviceRecord d) {
        return (safe(d.name) + " " + safe(d.address) + " " + safe(d.serviceUuids) + " " + safe(d.manufacturerData)
                + " " + safe(d.serviceData) + " " + safe(d.deviceType) + " " + safe(d.bluetoothClass)).toLowerCase(Locale.ROOT);
    }

    private void updateScanButton() {
        if (scanButton != null) scanButton.setText(scanning ? "Pause" : "Scan");
    }

    private void showDeviceDetails(final DeviceRecord d) {
        final int accent = getAccent();
        ScrollView scroll = new ScrollView(this);
        TextView body = new TextView(this);
        body.setText(buildDetailText(d));
        body.setTextColor(Color.WHITE);
        body.setTextSize(14f);
        body.setPadding(dp(18), dp(12), dp(18), dp(12));
        body.setTextIsSelectable(true);
        scroll.addView(body);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(displayName(d))
                .setView(scroll)
                .setPositiveButton("Copy", null)
                .setNeutralButton(d.pinned ? "Unpin" : "Pin", null)
                .setNegativeButton("Close", null)
                .create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(accent);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accent);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Bluetooth device", buildDetailText(d)));
                Toast.makeText(BluetoothActivity.this, "Copied", Toast.LENGTH_SHORT).show();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                d.pinned = !d.pinned;
                savePinned(d.key, d.pinned);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText(d.pinned ? "Unpin" : "Pin");
                refreshList();
            });
        });
        dialog.show();
    }

    private String buildDetailText(DeviceRecord d) {
        StringBuilder s = new StringBuilder();
        s.append("Name: ").append(displayName(d)).append('\n');
        s.append("Address: ").append(value(d.address)).append('\n');
        s.append("Transport seen: ").append(d.ble && d.classic ? "BLE + Classic" : d.ble ? "BLE" : d.classic ? "Classic" : "Bonded only").append('\n');
        s.append("Device type: ").append(value(d.deviceType)).append('\n');
        s.append("Bond state: ").append(value(d.bondState)).append('\n');
        s.append("Bluetooth class: ").append(value(d.bluetoothClass)).append('\n');
        s.append("RSSI: ").append(d.rssi == Integer.MIN_VALUE ? "n/a" : d.rssi + " dBm").append('\n');
        s.append("Tx power: ").append(d.txPower == Integer.MIN_VALUE ? "n/a" : d.txPower + " dBm").append('\n');
        s.append("Connectable: ").append(d.connectable == null ? "unknown" : d.connectable ? "yes" : "no").append('\n');
        s.append("Sightings: ").append(d.sightings).append('\n');
        s.append("First seen: ").append(formatTime(d.firstSeenMs)).append('\n');
        s.append("Last seen: ").append(d.lastSeenMs <= 0 ? "not seen this session" : formatTime(d.lastSeenMs)).append("\n\n");
        s.append("Service UUIDs:\n").append(value(d.serviceUuids)).append("\n\n");
        s.append("Manufacturer data:\n").append(value(d.manufacturerData)).append("\n\n");
        s.append("Service data:\n").append(value(d.serviceData)).append("\n\n");
        s.append("Raw BLE advertisement:\n").append(value(d.rawAdvertisement));
        return s.toString();
    }

    private void beginCsvExport() {
        pendingExport = adapter == null ? Collections.<DeviceRecord>emptyList() : adapter.snapshot();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "rapidbt-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".csv");
        startActivityForResult(intent, EXPORT_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_REQUEST || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("No output stream");
            StringBuilder csv = new StringBuilder();
            csv.append("name,address,transport,rssi_dbm,tx_power_dbm,bonded,sightings,first_seen,last_seen,service_uuids,manufacturer_data,service_data,raw_advertisement\n");
            for (DeviceRecord d : pendingExport) {
                csv.append(csv(displayName(d))).append(',')
                        .append(csv(d.address)).append(',')
                        .append(csv(d.ble && d.classic ? "BLE+Classic" : d.ble ? "BLE" : d.classic ? "Classic" : "Bonded" )).append(',')
                        .append(d.rssi == Integer.MIN_VALUE ? "" : d.rssi).append(',')
                        .append(d.txPower == Integer.MIN_VALUE ? "" : d.txPower).append(',')
                        .append(d.bonded).append(',')
                        .append(d.sightings).append(',')
                        .append(csv(formatTime(d.firstSeenMs))).append(',')
                        .append(csv(d.lastSeenMs <= 0 ? "" : formatTime(d.lastSeenMs))).append(',')
                        .append(csv(d.serviceUuids)).append(',')
                        .append(csv(d.manufacturerData)).append(',')
                        .append(csv(d.serviceData)).append(',')
                        .append(csv(d.rawAdvertisement)).append('\n');
            }
            out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "CSV exported", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String csv(String s) {
        String v = s == null ? "" : s;
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    private String scanErrorName(int code) {
        switch (code) {
            case ScanCallback.SCAN_FAILED_ALREADY_STARTED: return "already started";
            case ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED: return "registration failed";
            case ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED: return "feature unsupported";
            case ScanCallback.SCAN_FAILED_INTERNAL_ERROR: return "internal error";
            default: return Integer.toString(code);
        }
    }

    private String scanModeName(int mode) {
        if (mode == ScanSettings.SCAN_MODE_LOW_POWER) return "BLE low-power";
        if (mode == ScanSettings.SCAN_MODE_BALANCED) return "BLE balanced";
        return "BLE low-latency";
    }

    private String displayName(DeviceRecord d) {
        return d.name == null || d.name.trim().isEmpty() ? "(unnamed device)" : d.name;
    }

    private String safeName(BluetoothDevice d) {
        if (d == null) return null;
        try { return d.getName(); } catch (SecurityException e) { return null; }
    }

    private String safeAddress(BluetoothDevice d) {
        if (d == null) return null;
        try { return d.getAddress(); } catch (SecurityException e) { return null; }
    }

    private String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a;
        if (b != null && !b.trim().isEmpty()) return b;
        return null;
    }

    private String rawHash(ScanRecord r) {
        if (r == null || r.getBytes() == null) return Integer.toString((int) System.nanoTime());
        return Integer.toString(java.util.Arrays.hashCode(r.getBytes()));
    }

    private String formatUuids(List<ParcelUuid> uuids) {
        if (uuids == null || uuids.isEmpty()) return null;
        StringBuilder s = new StringBuilder();
        for (ParcelUuid u : uuids) {
            if (s.length() > 0) s.append("\n");
            s.append(u.toString());
        }
        return s.toString();
    }

    private String formatManufacturers(SparseArray<byte[]> data) {
        if (data == null || data.size() == 0) return null;
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < data.size(); i++) {
            if (s.length() > 0) s.append("\n");
            int id = data.keyAt(i);
            s.append(String.format(Locale.ROOT, "0x%04X: ", id)).append(toHex(data.valueAt(i)));
        }
        return s.toString();
    }

    private String formatServiceData(Map<ParcelUuid, byte[]> data) {
        if (data == null || data.isEmpty()) return null;
        StringBuilder s = new StringBuilder();
        for (Map.Entry<ParcelUuid, byte[]> e : data.entrySet()) {
            if (s.length() > 0) s.append("\n");
            s.append(e.getKey()).append(": ").append(toHex(e.getValue()));
        }
        return s.toString();
    }

    private String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        StringBuilder s = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) s.append(' ');
            s.append(String.format(Locale.ROOT, "%02X", bytes[i] & 0xFF));
        }
        return s.toString();
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "n/a";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(ms));
    }

    private String value(String s) {
        return s == null || s.trim().isEmpty() ? "n/a" : s;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private static final class DeviceRecord {
        final String key;
        String name;
        String address;
        int rssi = Integer.MIN_VALUE;
        int txPower = Integer.MIN_VALUE;
        long firstSeenMs;
        long lastSeenMs;
        int sightings;
        boolean ble;
        boolean classic;
        boolean bonded;
        boolean pinned;
        Boolean connectable;
        String bondState;
        String deviceType;
        String bluetoothClass;
        String serviceUuids;
        String manufacturerData;
        String serviceData;
        String rawAdvertisement;

        DeviceRecord(String key) { this.key = key; }
    }

    private final class DeviceAdapter extends BaseAdapter {
        private List<DeviceRecord> items = Collections.emptyList();
        private long now;

        void submit(List<DeviceRecord> newItems, long nowMs) {
            items = new ArrayList<>(newItems);
            now = nowMs;
            notifyDataSetChanged();
        }

        List<DeviceRecord> snapshot() {
            return new ArrayList<>(items);
        }

        DeviceRecord getDevice(int position) {
            return position >= 0 && position < items.size() ? items.get(position) : null;
        }

        @Override public int getCount() { return items.size(); }
        @Override public DeviceRecord getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).key.hashCode(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(BluetoothActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(14), dp(11), dp(14), dp(11));
                row.setBackgroundColor(Color.rgb(18, 18, 18));

                TextView name = new TextView(BluetoothActivity.this);
                name.setTextSize(17f);
                name.setTextColor(Color.WHITE);
                TextView details = new TextView(BluetoothActivity.this);
                details.setTextSize(12.5f);
                details.setTextColor(Color.LTGRAY);
                ProgressBar bar = new ProgressBar(BluetoothActivity.this, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(100);
                bar.getProgressDrawable().setColorFilter(getAccent(), PorterDuff.Mode.SRC_IN);

                row.addView(name);
                row.addView(details);
                row.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
                holder = new RowHolder(name, details, bar);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            DeviceRecord d = getItem(position);
            String title = (d.pinned ? "★ " : "") + displayName(d);
            holder.name.setText(title);
            holder.name.setTextColor(d.pinned ? getAccent() : Color.WHITE);

            String transport = d.ble && d.classic ? "BLE+Classic" : d.ble ? "BLE" : d.classic ? "Classic" : "Bonded";
            String rssi = d.rssi == Integer.MIN_VALUE ? "RSSI n/a" : d.rssi + " dBm";
            String age = d.lastSeenMs <= 0 ? "paired" : String.format(Locale.ROOT, "%.1fs ago", Math.max(0L, now - d.lastSeenMs) / 1000.0);
            holder.details.setText(rssi + "  •  " + transport + "  •  " + value(d.address) + "  •  " + age);

            int strength = d.rssi == Integer.MIN_VALUE ? 0 : Math.max(0, Math.min(100, (d.rssi + 100) * 100 / 70));
            holder.bar.setProgress(strength);
            return convertView;
        }
    }

    private static final class RowHolder {
        final TextView name;
        final TextView details;
        final ProgressBar bar;
        RowHolder(TextView name, TextView details, ProgressBar bar) {
            this.name = name;
            this.details = details;
            this.bar = bar;
        }
    }
}
