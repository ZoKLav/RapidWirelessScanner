package com.zoeykl.rapidbtscanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.ConsumerIrManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class HardwareActivity extends Activity {
    private SharedPreferences prefs;
    private ArrayAdapter<String> adapter;
    private final ArrayList<Row> rows = new ArrayList<>();
    private TextView status;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs=getSharedPreferences("prefs",MODE_PRIVATE);
        buildUi();
        refresh();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(12),dp(14),dp(10));root.setBackgroundColor(Color.BLACK);
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);TextView title=new TextView(this);title.setText("RF / Hardware Inventory");title.setTextColor(accent());title.setTextSize(23f);title.setTypeface(title.getTypeface(),android.graphics.Typeface.BOLD);h.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));Button refresh=new Button(this);refresh.setText("Refresh");refresh.setAllCaps(false);refresh.setOnClickListener(v->refresh());h.addView(refresh);root.addView(h);
        status=new TextView(this);status.setTextColor(Color.LTGRAY);status.setTextSize(12.5f);status.setPadding(0,dp(4),0,dp(8));root.addView(status);
        TextView note=new TextView(this);note.setText("Capability detection, not pretend RF sniffing. This page checks Android hardware features, vendor feature strings, USB devices and a few safe filesystem hints for radios the standard framework does not understand.");note.setTextColor(Color.GRAY);note.setTextSize(11.5f);note.setPadding(0,0,0,dp(8));root.addView(note);
        ListView list=new ListView(this);adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<String>()){@Override public View getView(int p,View cv,ViewGroup parent){TextView t=(TextView)super.getView(p,cv,parent);t.setTextColor(Color.WHITE);t.setTextSize(14.2f);t.setBackgroundColor(Color.rgb(18,18,18));t.setPadding(dp(12),dp(11),dp(12),dp(11));return t;}};list.setAdapter(adapter);list.setOnItemClickListener((p,v,pos,id)->{Row r=rows.get(pos);new AlertDialog.Builder(this).setTitle(r.title).setMessage(r.detail).setPositiveButton("Close",null).show();});root.addView(list,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));setContentView(root);
    }

    private void refresh(){
        rows.clear();
        add("Device",Build.MANUFACTURER+" "+Build.MODEL,true,"Manufacturer: "+Build.MANUFACTURER+"\nModel: "+Build.MODEL+"\nDevice: "+Build.DEVICE+"\nProduct: "+Build.PRODUCT+"\nHardware: "+Build.HARDWARE+"\nBoard: "+Build.BOARD+"\nAndroid: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")");
        cap("Bluetooth Classic",PackageManager.FEATURE_BLUETOOTH,"Standard Android Bluetooth radio");
        cap("Bluetooth LE",PackageManager.FEATURE_BLUETOOTH_LE,"BLE scanning and GATT support");
        cap("BLE Channel Sounding","android.hardware.bluetooth_le.channel_sounding","Android 16/API 36 hardware feature for BLE ranging/channel sounding");
        cap("Wi-Fi",PackageManager.FEATURE_WIFI,"Infrastructure Wi-Fi radio");
        cap("Wi-Fi Direct","android.hardware.wifi.direct","Peer-to-peer Wi-Fi discovery/connectivity");
        cap("Wi-Fi Aware","android.hardware.wifi.aware","Neighbor Awareness Networking; discovery is scoped to a known service name");
        cap("Wi-Fi RTT","android.hardware.wifi.rtt","802.11mc/FTM ranging to compatible access points");
        cap("Wi-Fi Passpoint","android.hardware.wifi.passpoint","Hotspot 2.0 / Passpoint support");
        cap("NFC reader","android.hardware.nfc","NFC tag reader/writer hardware");
        cap("NFC HCE","android.hardware.nfc.hce","Host card emulation");
        cap("NFC-F HCE","android.hardware.nfc.hcef","NFC-F host card emulation");
        cap("NFC eSE","android.hardware.nfc.ese","Embedded secure-element off-host card emulation");
        cap("NFC UICC","android.hardware.nfc.uicc","SIM/UICC off-host card emulation");
        cap("Ultra-wideband (UWB)","android.hardware.uwb","UWB hardware. Generic blind discovery is not exposed; ranging requires peer/session parameters.");
        cap("Thread","android.hardware.thread_network","802.15.4 Thread networking capability reported on newer Android devices");
        cap("GNSS / GPS",PackageManager.FEATURE_LOCATION_GPS,"Satellite navigation receiver");
        cap("Telephony radio access","android.hardware.telephony.radio.access","Cellular radio APIs");
        cap("Satellite telephony","android.hardware.telephony.satellite","Android satellite connectivity feature");
        cap("USB host",PackageManager.FEATURE_USB_HOST,"Can enumerate attached USB devices, including external serial/radio hardware");
        cap("USB accessory",PackageManager.FEATURE_USB_ACCESSORY,"Android Open Accessory support");
        addIr();
        addUsb();
        addLoraSummary();
        addVendorFeatures();
        adapter.clear();for(Row r:rows)adapter.add((r.ok?"✓ ":"— ")+r.title+"\n"+r.subtitle);adapter.notifyDataSetChanged();status.setText(rows.size()+" capability / hardware entries");
    }

    private void cap(String title,String feature,String detail){boolean ok=getPackageManager().hasSystemFeature(feature);add(title,ok?"reported by Android":"not reported",ok,detail+"\nFeature string: "+feature);}
    private void add(String title,String subtitle,boolean ok,String detail){rows.add(new Row(title,subtitle,ok,detail));}

    private void addIr(){
        boolean feature=getPackageManager().hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR);StringBuilder d=new StringBuilder("Android ConsumerIrManager exposes transmission only; it is not an IR receiver/scanner.\n");
        if(feature){try{ConsumerIrManager ir=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);if(ir!=null){d.append("Emitter present: ").append(ir.hasIrEmitter()).append('\n');ConsumerIrManager.CarrierFrequencyRange[] rs=ir.getCarrierFrequencies();if(rs!=null)for(ConsumerIrManager.CarrierFrequencyRange r:rs)d.append("Carrier range: ").append(r.getMinFrequency()).append("-").append(r.getMaxFrequency()).append(" Hz\n");}}catch(Exception e){d.append("Query failed: ").append(e.getClass().getSimpleName());}}
        add("Consumer IR",feature?"IR emitter reported":"not reported",feature,d.toString());
    }

    private void addUsb(){
        UsbManager um=(UsbManager)getSystemService(USB_SERVICE);if(um==null){add("USB devices","USB manager unavailable",false,"");return;}HashMap<String,UsbDevice> map=um.getDeviceList();if(map==null||map.isEmpty()){add("Attached USB devices","none",false,"No USB host devices are currently enumerated.");return;}
        for(UsbDevice d:map.values()){
            String product=safe(d.getProductName()),man=safe(d.getManufacturerName());String all=(product+" "+man).toLowerCase(Locale.ROOT);String hint=radioHint(all,d);StringBuilder x=new StringBuilder();x.append("Manufacturer: ").append(man).append("\nProduct: ").append(product).append("\nVID:PID: ").append(String.format(Locale.ROOT,"%04X:%04X",d.getVendorId(),d.getProductId())).append("\nDevice class: ").append(d.getDeviceClass()).append("\nSubclass/protocol: ").append(d.getDeviceSubclass()).append('/').append(d.getDeviceProtocol()).append("\nVersion: ").append(d.getVersion()).append("\nInterfaces: ").append(d.getInterfaceCount()).append('\n');
            for(int i=0;i<d.getInterfaceCount();i++){UsbInterface f=d.getInterface(i);x.append("  IF ").append(i).append(": class ").append(f.getInterfaceClass()).append(" sub ").append(f.getInterfaceSubclass()).append(" proto ").append(f.getInterfaceProtocol()).append(" endpoints ").append(f.getEndpointCount()).append('\n');for(int j=0;j<f.getEndpointCount();j++){UsbEndpoint e=f.getEndpoint(j);x.append("    EP ").append(j).append(": type ").append(e.getType()).append(" dir ").append(e.getDirection()).append(" maxPacket ").append(e.getMaxPacketSize()).append('\n');}}
            x.append("\nHeuristic: ").append(hint);add("USB: "+(product.length()>0?product:String.format(Locale.ROOT,"%04X:%04X",d.getVendorId(),d.getProductId())),hint,!"generic USB device".equals(hint),x.toString());
        }
    }

    private String radioHint(String text,UsbDevice d){
        if(containsAny(text,"lora","meshtastic","sx126","sx127","sx128","heltec","rakwireless","rak ","lilygo","t-beam","tbeam","t-echo","techo","t-deck","tdeck","rfm9"))return"likely LoRa / Meshtastic-class radio";
        if(containsAny(text,"hackrf","rtl2832","rtl-sdr","airspy","bladerf","lime sdr","limesdr","sdrplay"))return"software-defined radio";
        int vid=d.getVendorId();if(vid==0x10C4||vid==0x1A86||vid==0x0403||vid==0x303A)return"USB serial/MCU bridge; possible external radio, protocol unknown";
        for(int i=0;i<d.getInterfaceCount();i++)if(d.getInterface(i).getInterfaceClass()==2)return"USB CDC serial device; possible external radio, protocol unknown";
        return"generic USB device";
    }

    private void addLoraSummary(){
        ArrayList<String> hits=new ArrayList<>();FeatureInfo[] fs=getPackageManager().getSystemAvailableFeatures();if(fs!=null)for(FeatureInfo f:fs){String n=f.name;if(n!=null&&isLoraText(n.toLowerCase(Locale.ROOT)))hits.add("feature: "+n);}String[] dev=new File("/dev").list();if(dev!=null)for(String n:dev)if(isLoraText(n.toLowerCase(Locale.ROOT)))hits.add("/dev/"+n);
        UsbManager um=(UsbManager)getSystemService(USB_SERVICE);if(um!=null){HashMap<String,UsbDevice> map=um.getDeviceList();if(map!=null)for(UsbDevice u:map.values()){String t=(safe(u.getProductName())+" "+safe(u.getManufacturerName())).toLowerCase(Locale.ROOT);if(isLoraText(t))hits.add("USB: "+safe(u.getManufacturerName())+" "+safe(u.getProductName()));}}
        StringBuilder d=new StringBuilder();d.append("Android has no standard LoRa framework API. Detection is therefore heuristic: vendor system-feature names, explicitly named device nodes, and attached USB descriptors. A built-in radio can still be invisible if its OEM exposes it only through a proprietary service/SDK.\n\n");if(hits.isEmpty())d.append("No explicit LoRa/Meshtastic/SX12xx exposure detected.");else for(String h:hits)d.append(h).append('\n');add("LoRa / sub-GHz autodetect",hits.isEmpty()?"no obvious LoRa interface":"possible LoRa interface detected",!hits.isEmpty(),d.toString());
    }

    private void addVendorFeatures(){
        FeatureInfo[] fs=getPackageManager().getSystemAvailableFeatures();ArrayList<String> found=new ArrayList<>();if(fs!=null)for(FeatureInfo f:fs){String n=f.name;if(n==null)continue;String l=n.toLowerCase(Locale.ROOT);if(!n.startsWith("android.")||containsAny(l,"lora","uhf","vhf","dmr","ptt","mesh","radio","satellite","thread","uwb","zigbee","802.15.4","ant+","ant.",".fm"))found.add(n+(f.version>0?" (v"+f.version+")":""));}
        Collections.sort(found);StringBuilder d=new StringBuilder();for(String n:found)d.append(n).append('\n');add("Vendor / radio feature strings",found.size()+" interesting entries",!found.isEmpty(),d.length()==0?"No nonstandard or radio-related feature strings were reported.":d.toString());
    }

    private boolean isLoraText(String s){return containsAny(s,"lora","meshtastic","sx126","sx127","sx128","heltec","rakwireless","lilygo","t-beam","tbeam","t-echo","techo","t-deck","tdeck","rfm9");}
    private boolean containsAny(String s,String... needles){for(String n:needles)if(s.contains(n))return true;return false;}
    private String safe(String s){return s==null?"":s;}
    private int accent(){return prefs.getInt("accentColor",Color.rgb(229,57,53));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static final class Row{final String title,subtitle,detail;final boolean ok;Row(String t,String s,boolean o,String d){title=t;subtitle=s;ok=o;detail=d;}}
}
