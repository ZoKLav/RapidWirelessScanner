package com.zoeykl.rapidbtscanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.nfc.tech.NfcA;
import android.nfc.tech.NfcB;
import android.nfc.tech.NfcF;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

public class NfcActivity extends Activity implements NfcAdapter.ReaderCallback {
    private NfcAdapter nfc;
    private SharedPreferences prefs;
    private TextView status;
    private ArrayAdapter<String> adapter;
    private final ArrayList<TagRecord> tags = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs=getSharedPreferences("prefs",MODE_PRIVATE);
        nfc=NfcAdapter.getDefaultAdapter(this);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (nfc==null) { status.setText("This device has no NFC adapter"); return; }
        if (!nfc.isEnabled()) { status.setText("NFC is off. Enable it in system settings."); return; }
        int flags=NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_NFC_B|NfcAdapter.FLAG_READER_NFC_F|NfcAdapter.FLAG_READER_NFC_V|NfcAdapter.FLAG_READER_NFC_BARCODE;
        if (prefs.getBoolean("nfcNoSound",false)) flags|=NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS;
        try { nfc.enableReaderMode(this,this,flags,null); status.setText("Reader active • hold an NFC tag near the phone"); }
        catch(Exception e){ status.setText("NFC reader could not start: "+e.getClass().getSimpleName()); }
    }

    @Override protected void onPause() {
        if(nfc!=null){try{nfc.disableReaderMode(this);}catch(Exception ignored){}}
        super.onPause();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(14),dp(14),dp(10));root.setBackgroundColor(Color.BLACK);
        TextView title=new TextView(this);title.setText("NFC Tag Scanner");title.setTextColor(accent());title.setTextSize(23f);title.setTypeface(title.getTypeface(),android.graphics.Typeface.BOLD);root.addView(title);
        status=new TextView(this);status.setTextColor(Color.LTGRAY);status.setTextSize(12.5f);status.setPadding(0,dp(5),0,dp(8));root.addView(status);
        TextView note=new TextView(this);note.setText("Read-only inspection. Shows tag UID, technology stack, NDEF records and technology-specific metadata when Android exposes it.");note.setTextColor(Color.GRAY);note.setTextSize(11.5f);note.setPadding(0,0,0,dp(8));root.addView(note);
        ListView list=new ListView(this);adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<String>()){@Override public View getView(int p,View cv,ViewGroup parent){TextView t=(TextView)super.getView(p,cv,parent);t.setTextColor(Color.WHITE);t.setTextSize(14.5f);t.setBackgroundColor(Color.rgb(18,18,18));t.setPadding(dp(12),dp(12),dp(12),dp(12));return t;}};list.setAdapter(adapter);list.setOnItemClickListener((p,v,pos,id)->show(tags.get(pos)));root.addView(list,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));setContentView(root);
    }

    @Override public void onTagDiscovered(Tag tag) {
        if(tag==null)return;
        final TagRecord r=inspect(tag);
        runOnUiThread(()->{
            tags.add(0,r);adapter.insert(r.title+"\n"+r.subtitle,0);adapter.notifyDataSetChanged();status.setText(tags.size()+" tag read"+(tags.size()==1?"":"s")+" this session");
            if(prefs.getBoolean("nfcAutoOpen",false)) show(r);
        });
    }

    private TagRecord inspect(Tag tag){
        String uid=hex(tag.getId()); String[] techs=tag.getTechList(); StringBuilder s=new StringBuilder();
        s.append("UID: ").append(uid).append('\n');s.append("Technologies:\n");for(String t:techs)s.append("  ").append(shortTech(t)).append('\n');
        try{NfcA x=NfcA.get(tag);if(x!=null){s.append("\nNFC-A ATQA: ").append(hex(x.getAtqa())).append('\n');s.append("NFC-A SAK: 0x").append(String.format(Locale.ROOT,"%02X",x.getSak())).append('\n');s.append("NFC-A max transceive: ").append(x.getMaxTransceiveLength()).append(" bytes\n");}}catch(Exception ignored){}
        try{NfcB x=NfcB.get(tag);if(x!=null){s.append("\nNFC-B application data: ").append(hex(x.getApplicationData())).append('\n');s.append("NFC-B protocol info: ").append(hex(x.getProtocolInfo())).append('\n');}}catch(Exception ignored){}
        try{NfcF x=NfcF.get(tag);if(x!=null){s.append("\nNFC-F manufacturer: ").append(hex(x.getManufacturer())).append('\n');s.append("NFC-F system code: ").append(hex(x.getSystemCode())).append('\n');}}catch(Exception ignored){}
        try{NfcV x=NfcV.get(tag);if(x!=null){s.append("\nNFC-V DSF ID: 0x").append(String.format(Locale.ROOT,"%02X",x.getDsfId())).append('\n');s.append("NFC-V response flags: 0x").append(String.format(Locale.ROOT,"%02X",x.getResponseFlags())).append('\n');}}catch(Exception ignored){}
        try{IsoDep x=IsoDep.get(tag);if(x!=null){s.append("\nISO-DEP max transceive: ").append(x.getMaxTransceiveLength()).append(" bytes\n");byte[] hb=x.getHistoricalBytes();if(hb!=null)s.append("Historical bytes: ").append(hex(hb)).append('\n');byte[] hi=x.getHiLayerResponse();if(hi!=null)s.append("Hi-layer response: ").append(hex(hi)).append('\n');}}catch(Exception ignored){}
        try{MifareClassic x=MifareClassic.get(tag);if(x!=null){s.append("\nMIFARE Classic type: ").append(mfcType(x.getType())).append('\n');s.append("Size: ").append(x.getSize()).append(" bytes\nSectors: ").append(x.getSectorCount()).append("\nBlocks: ").append(x.getBlockCount()).append('\n');}}catch(Exception ignored){}
        try{MifareUltralight x=MifareUltralight.get(tag);if(x!=null)s.append("\nMIFARE Ultralight type: ").append(x.getType()==MifareUltralight.TYPE_ULTRALIGHT_C?"Ultralight C":x.getType()==MifareUltralight.TYPE_ULTRALIGHT?"Ultralight":"unknown").append('\n');}catch(Exception ignored){}
        try{Ndef n=Ndef.get(tag);if(n!=null){s.append("\nNDEF type: ").append(n.getType()).append('\n');s.append("NDEF writable: ").append(n.isWritable()?"yes":"no").append('\n');s.append("NDEF capacity: ").append(n.getMaxSize()).append(" bytes\n");NdefMessage m=n.getCachedNdefMessage();if(m!=null){NdefRecord[] rs=m.getRecords();s.append("NDEF records: ").append(rs.length).append('\n');for(int i=0;i<rs.length;i++){s.append("\nRecord ").append(i+1).append(":\n").append(ndefRecord(rs[i]));}}}}catch(Exception e){s.append("\nNDEF parse error: ").append(e.getClass().getSimpleName()).append('\n');}
        String subtitle=uid+" • "+joinTechs(techs);return new TagRecord("NFC tag",subtitle,s.toString());
    }

    private String ndefRecord(NdefRecord r){StringBuilder s=new StringBuilder();s.append("TNF: ").append(r.getTnf()).append('\n');s.append("Type: ").append(asciiOrHex(r.getType())).append('\n');if(r.getId()!=null&&r.getId().length>0)s.append("ID: ").append(hex(r.getId())).append('\n');try{android.net.Uri u=r.toUri();if(u!=null)s.append("URI: ").append(u.toString()).append('\n');}catch(Exception ignored){}String txt=parseText(r);if(txt!=null)s.append("Text: ").append(txt).append('\n');s.append("Payload: ").append(hex(r.getPayload())).append('\n');return s.toString();}
    private String parseText(NdefRecord r){try{byte[] type=r.getType(),p=r.getPayload();if(type==null||type.length!=1||type[0]!='T'||p==null||p.length<1)return null;int lang=p[0]&0x3F;boolean utf16=(p[0]&0x80)!=0;if(1+lang>p.length)return null;return new String(p,1+lang,p.length-1-lang,Charset.forName(utf16?"UTF-16":"UTF-8"));}catch(Exception e){return null;}}
    private void show(TagRecord r){AlertDialog d=new AlertDialog.Builder(this).setTitle(r.title).setMessage(r.detail).setPositiveButton("Copy",null).setNegativeButton("Close",null).create();d.setOnShowListener(x->{d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent());d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(accent());d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("NFC tag",r.detail));Toast.makeText(this,"Copied",Toast.LENGTH_SHORT).show();});});d.show();}
    private String joinTechs(String[] ts){StringBuilder s=new StringBuilder();for(String t:ts){if(s.length()>0)s.append(", ");s.append(shortTech(t));}return s.toString();}
    private String shortTech(String t){int i=t.lastIndexOf('.');return i>=0?t.substring(i+1):t;}
    private String mfcType(int t){if(t==MifareClassic.TYPE_CLASSIC)return"Classic";if(t==MifareClassic.TYPE_PLUS)return"Plus";if(t==MifareClassic.TYPE_PRO)return"Pro";return"unknown";}
    private String asciiOrHex(byte[] b){if(b==null||b.length==0)return"";boolean printable=true;for(byte x:b)if((x&255)<32||(x&255)>126)printable=false;return printable?new String(b,Charset.forName("US-ASCII")):hex(b);}
    private String hex(byte[] b){if(b==null)return"";StringBuilder s=new StringBuilder();for(int i=0;i<b.length;i++){if(i>0)s.append(' ');s.append(String.format(Locale.ROOT,"%02X",b[i]&255));}return s.toString();}
    private int accent(){return prefs.getInt("accentColor",Color.rgb(229,57,53));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static final class TagRecord{final String title,subtitle,detail;TagRecord(String t,String s,String d){title=t;subtitle=s;detail=d;}}
}
