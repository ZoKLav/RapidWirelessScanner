package com.zoeykl.rapidbtscanner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

public class WifiDirectActivity extends Activity {
    private static final int REQ = 2201;
    private static final String NEARBY_WIFI = "android.permission.NEARBY_WIFI_DEVICES";
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private TextView status;
    private ArrayAdapter<String> adapter;
    private final ArrayList<WifiP2pDevice> devices = new ArrayList<>();
    private boolean registered;
    private SharedPreferences prefs;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(a)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) status.setText("Wi-Fi Direct is disabled");
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(a)) {
                requestPeers();
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (manager != null) channel = manager.initialize(this, getMainLooper(), null);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        register();
        if (!hasPermission()) requestNeeded(); else discover();
    }

    @Override protected void onPause() {
        if (registered) { try { unregisterReceiver(receiver); } catch (Exception ignored) {} registered=false; }
        super.onPause();
    }

    private void buildUi() {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(14),dp(12),dp(14),dp(10)); root.setBackgroundColor(Color.BLACK);
        LinearLayout h=new LinearLayout(this); h.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this); title.setText("Wi-Fi Direct Peers"); title.setTextColor(accent()); title.setTextSize(23f); title.setTypeface(title.getTypeface(),android.graphics.Typeface.BOLD); h.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        Button scan=button("Discover"); scan.setOnClickListener(v->discover()); h.addView(scan); root.addView(h);
        status=new TextView(this); status.setTextColor(Color.LTGRAY); status.setTextSize(12.5f); status.setPadding(0,dp(4),0,dp(8)); root.addView(status);
        TextView note=new TextView(this); note.setText("Discovers Android Wi-Fi P2P peers. Location services must be enabled by the OS even on versions that use the Nearby Wi-Fi permission."); note.setTextColor(Color.GRAY); note.setTextSize(11.5f); note.setPadding(0,0,0,dp(8)); root.addView(note);
        ListView list=new ListView(this); adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<String>()) {
            @Override public View getView(int pos, View cv, ViewGroup parent) { TextView t=(TextView)super.getView(pos,cv,parent); t.setTextColor(Color.WHITE); t.setTextSize(15f); t.setBackgroundColor(Color.rgb(18,18,18)); t.setPadding(dp(12),dp(12),dp(12),dp(12)); return t; }
        }; list.setAdapter(adapter); list.setOnItemClickListener((p,v,pos,id)->show(devices.get(pos))); root.addView(list,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f)); setContentView(root);
    }

    private void register() {
        if (registered) return;
        IntentFilter f=new IntentFilter(); f.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION); f.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        if (Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,Context.RECEIVER_EXPORTED); else registerReceiver(receiver,f);
        registered=true;
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT>=33) return checkSelfPermission(NEARBY_WIFI)==PackageManager.PERMISSION_GRANTED;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }

    private void requestNeeded() {
        if (Build.VERSION.SDK_INT>=33) requestPermissions(new String[]{NEARBY_WIFI},REQ); else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ);
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results); if (requestCode==REQ && hasPermission()) discover(); else if(requestCode==REQ) status.setText("Nearby-device permission is required for Wi-Fi Direct discovery");
    }

    @SuppressWarnings("MissingPermission")
    private void discover() {
        if (manager==null || channel==null) { status.setText("Wi-Fi Direct service unavailable"); return; }
        if (!hasPermission()) { requestNeeded(); return; }
        status.setText("Discovering peers…");
        try {
            manager.discoverPeers(channel,new WifiP2pManager.ActionListener(){
                @Override public void onSuccess(){ status.setText("Discovery running…"); }
                @Override public void onFailure(int reason){ status.setText("Discovery failed: "+reasonName(reason)); requestPeers(); }
            });
        } catch(SecurityException e){ status.setText("Discovery blocked by permission settings"); }
    }

    @SuppressWarnings("MissingPermission")
    private void requestPeers() {
        if (manager==null || channel==null || !hasPermission()) return;
        try {
            manager.requestPeers(channel,new WifiP2pManager.PeerListListener(){ @Override public void onPeersAvailable(WifiP2pDeviceList list){
                devices.clear(); Collection<WifiP2pDevice> c=list==null?null:list.getDeviceList(); if(c!=null) devices.addAll(c); adapter.clear(); for(WifiP2pDevice d:devices) adapter.add(label(d)); adapter.notifyDataSetChanged(); status.setText(devices.size()+" peer"+(devices.size()==1?"":"s")+" visible");
            }});
        } catch(SecurityException e){ status.setText("Peer list blocked by permission settings"); }
    }

    private String label(WifiP2pDevice d){ String n=d.deviceName==null||d.deviceName.trim().isEmpty()?"(unnamed peer)":d.deviceName; return n+"\n"+d.deviceAddress+" • "+state(d.status); }
    private void show(WifiP2pDevice d){ StringBuilder s=new StringBuilder(); s.append("Name: ").append(d.deviceName).append('\n'); s.append("Address: ").append(d.deviceAddress).append('\n'); s.append("Status: ").append(state(d.status)).append('\n'); s.append("Primary type: ").append(d.primaryDeviceType).append('\n'); s.append("Secondary type: ").append(d.secondaryDeviceType).append('\n'); s.append("Group owner: ").append(d.isGroupOwner()?"yes":"no").append('\n'); s.append("WPS PBC: ").append(d.wpsPbcSupported()?"yes":"no").append('\n'); s.append("WPS keypad: ").append(d.wpsKeypadSupported()?"yes":"no").append('\n'); s.append("WPS display: ").append(d.wpsDisplaySupported()?"yes":"no"); new AlertDialog.Builder(this).setTitle(d.deviceName==null?"Wi-Fi Direct peer":d.deviceName).setMessage(s.toString()).setPositiveButton("Close",null).show(); }
    private String state(int s){ switch(s){case WifiP2pDevice.AVAILABLE:return"available";case WifiP2pDevice.CONNECTED:return"connected";case WifiP2pDevice.INVITED:return"invited";case WifiP2pDevice.FAILED:return"failed";case WifiP2pDevice.UNAVAILABLE:return"unavailable";default:return"unknown";} }
    private String reasonName(int r){ if(r==WifiP2pManager.BUSY)return"busy"; if(r==WifiP2pManager.ERROR)return"internal error"; if(r==WifiP2pManager.P2P_UNSUPPORTED)return"unsupported"; return Integer.toString(r); }
    private int accent(){return prefs.getInt("accentColor",Color.rgb(229,57,53));}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.getBackground().setColorFilter(accent(),PorterDuff.Mode.SRC_ATOP);return b;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
