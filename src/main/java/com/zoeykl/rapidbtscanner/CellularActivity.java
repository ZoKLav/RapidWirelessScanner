package com.zoeykl.rapidbtscanner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.telephony.CellIdentityCdma;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellIdentityTdscdma;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellInfoTdscdma;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
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
import java.util.List;

public class CellularActivity extends Activity {
    private static final int REQ = 2301;
    private TelephonyManager telephony;
    private SharedPreferences prefs;
    private TextView status;
    private ArrayAdapter<String> adapter;
    private final ArrayList<CellRow> rows = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs=getSharedPreferences("prefs",MODE_PRIVATE);
        telephony=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);
        buildUi();
    }

    @Override protected void onResume(){super.onResume();if(!hasLocation())requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ);else refresh();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(12),dp(14),dp(10));root.setBackgroundColor(Color.BLACK);
        LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);TextView title=new TextView(this);title.setText("Cellular Radio");title.setTextColor(accent());title.setTextSize(23f);title.setTypeface(title.getTypeface(),android.graphics.Typeface.BOLD);h.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));Button refresh=button("Refresh");refresh.setOnClickListener(v->refresh());h.addView(refresh);root.addView(h);
        status=new TextView(this);status.setTextColor(Color.LTGRAY);status.setTextSize(12.5f);status.setPadding(0,dp(4),0,dp(8));root.addView(status);
        TextView note=new TextView(this);note.setText("Shows the serving and neighboring cells Android exposes to this phone. Android rate-limits fresh radio measurements; on newer versions the first list may be cached.");note.setTextColor(Color.GRAY);note.setTextSize(11.5f);note.setPadding(0,0,0,dp(8));root.addView(note);
        ListView list=new ListView(this);adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,new ArrayList<String>()){@Override public View getView(int p,View cv,ViewGroup parent){TextView t=(TextView)super.getView(p,cv,parent);t.setTextColor(Color.WHITE);t.setTextSize(14.5f);t.setBackgroundColor(Color.rgb(18,18,18));t.setPadding(dp(12),dp(12),dp(12),dp(12));return t;}};list.setAdapter(adapter);list.setOnItemClickListener((p,v,pos,id)->{CellRow r=rows.get(pos);new AlertDialog.Builder(this).setTitle(r.title).setMessage(r.detail).setPositiveButton("Close",null).show();});root.addView(list,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));setContentView(root);
    }

    private boolean hasLocation(){return Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==REQ&&hasLocation())refresh();else if(requestCode==REQ)status.setText("Precise location permission is required for cell information");}

    private void refresh(){
        if(telephony==null){status.setText("Telephony service unavailable");return;} if(!hasLocation()){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQ);return;}
        status.setText("Reading cellular radio…");
        if(Build.VERSION.SDK_INT>=29){
            try{telephony.requestCellInfoUpdate(getMainExecutor(),new TelephonyManager.CellInfoCallback(){@Override public void onCellInfo(List<CellInfo> cellInfo){render(cellInfo,"Fresh radio update");}@Override public void onError(int errorCode,Throwable detail){loadCached("Update error "+errorCode+" • cached");}});}catch(Exception e){loadCached("Cached radio data");}
        } else loadCached("Cell data");
    }

    @SuppressWarnings("MissingPermission")
    private void loadCached(String prefix){try{render(telephony.getAllCellInfo(),prefix);}catch(SecurityException e){status.setText("Cell information blocked by permission settings");}catch(Exception e){status.setText("Cell read failed: "+e.getClass().getSimpleName());}}

    private void render(List<CellInfo> list,String prefix){
        rows.clear(); if(list!=null)for(CellInfo c:list){CellRow r=describe(c);if(r!=null)rows.add(r);} Collections.sort(rows,new Comparator<CellRow>(){@Override public int compare(CellRow a,CellRow b){if(a.registered!=b.registered)return a.registered?-1:1;return Integer.compare(b.dbm,a.dbm);}});adapter.clear();for(CellRow r:rows)adapter.add((r.registered?"★ ":"")+r.title+"\n"+(r.dbm<=-999?"signal n/a":r.dbm+" dBm")+" • "+(r.registered?"registered":"neighbor"));adapter.notifyDataSetChanged();status.setText(prefix+" • "+rows.size()+" cell"+(rows.size()==1?"":"s"));
    }

    private CellRow describe(CellInfo c){
        if(c==null)return null;boolean reg=c.isRegistered();String conn="";if(Build.VERSION.SDK_INT>=28)conn="\nConnection status: "+connection(c.getCellConnectionStatus());
        if(c instanceof CellInfoLte){CellInfoLte x=(CellInfoLte)c;CellIdentityLte i=x.getCellIdentity();int dbm=x.getCellSignalStrength().getDbm();StringBuilder d=base("LTE",reg,dbm,conn);d.append("MCC/MNC: ").append(i.getMcc()).append('/').append(i.getMnc()).append('\n');d.append("CI: ").append(i.getCi()).append("\nPCI: ").append(i.getPci()).append("\nTAC: ").append(i.getTac()).append("\nEARFCN: ").append(i.getEarfcn()).append('\n');if(Build.VERSION.SDK_INT>=28)d.append("Bandwidth: ").append(i.getBandwidth()).append(" kHz\n");return new CellRow("LTE",d.toString(),dbm,reg);}
        if(Build.VERSION.SDK_INT>=29&&c instanceof CellInfoNr){CellInfoNr x=(CellInfoNr)c;CellIdentityNr i=(CellIdentityNr)x.getCellIdentity();int dbm=x.getCellSignalStrength().getDbm();StringBuilder d=base("5G NR",reg,dbm,conn);d.append("MCC/MNC: ").append(i.getMccString()).append('/').append(i.getMncString()).append('\n');d.append("NCI: ").append(i.getNci()).append("\nPCI: ").append(i.getPci()).append("\nTAC: ").append(i.getTac()).append("\nNR-ARFCN: ").append(i.getNrarfcn()).append('\n');return new CellRow("5G NR",d.toString(),dbm,reg);}
        if(c instanceof CellInfoWcdma){CellInfoWcdma x=(CellInfoWcdma)c;CellIdentityWcdma i=x.getCellIdentity();int dbm=x.getCellSignalStrength().getDbm();StringBuilder d=base("WCDMA",reg,dbm,conn);d.append("MCC/MNC: ").append(i.getMcc()).append('/').append(i.getMnc()).append("\nCID: ").append(i.getCid()).append("\nLAC: ").append(i.getLac()).append("\nPSC: ").append(i.getPsc()).append("\nUARFCN: ").append(i.getUarfcn()).append('\n');return new CellRow("WCDMA",d.toString(),dbm,reg);}
        if(c instanceof CellInfoGsm){CellInfoGsm x=(CellInfoGsm)c;CellIdentityGsm i=x.getCellIdentity();int dbm=x.getCellSignalStrength().getDbm();StringBuilder d=base("GSM",reg,dbm,conn);d.append("MCC/MNC: ").append(i.getMcc()).append('/').append(i.getMnc()).append("\nCID: ").append(i.getCid()).append("\nLAC: ").append(i.getLac()).append("\nARFCN: ").append(i.getArfcn()).append("\nBSIC: ").append(i.getBsic()).append('\n');return new CellRow("GSM",d.toString(),dbm,reg);}
        if(c instanceof CellInfoCdma){CellInfoCdma x=(CellInfoCdma)c;CellIdentityCdma i=x.getCellIdentity();int dbm=x.getCellSignalStrength().getDbm();StringBuilder d=base("CDMA",reg,dbm,conn);d.append("Base station ID: ").append(i.getBasestationId()).append("\nNetwork ID: ").append(i.getNetworkId()).append("\nSystem ID: ").append(i.getSystemId()).append('\n');return new CellRow("CDMA",d.toString(),dbm,reg);}
        if(Build.VERSION.SDK_INT>=29&&c instanceof CellInfoTdscdma){CellInfoTdscdma x=(CellInfoTdscdma)c;CellIdentityTdscdma i=x.getCellIdentity();int dbm=x.getCellSignalStrength().getDbm();StringBuilder d=base("TD-SCDMA",reg,dbm,conn);d.append("MCC/MNC: ").append(i.getMccString()).append('/').append(i.getMncString()).append("\nCID: ").append(i.getCid()).append("\nLAC: ").append(i.getLac()).append("\nCPID: ").append(i.getCpid()).append("\nUARFCN: ").append(i.getUarfcn()).append('\n');return new CellRow("TD-SCDMA",d.toString(),dbm,reg);}
        return new CellRow(c.getClass().getSimpleName(),c.toString(),-999,reg);
    }

    private StringBuilder base(String tech,boolean reg,int dbm,String conn){StringBuilder d=new StringBuilder();d.append("Technology: ").append(tech).append("\nRegistered: ").append(reg?"yes":"no").append("\nSignal: ").append(dbm).append(" dBm").append(conn).append('\n');return d;}
    private String connection(int x){if(x==CellInfo.CONNECTION_PRIMARY_SERVING)return"primary serving";if(x==CellInfo.CONNECTION_SECONDARY_SERVING)return"secondary serving";if(x==CellInfo.CONNECTION_NONE)return"none";return"unknown";}
    private int accent(){return prefs.getInt("accentColor",Color.rgb(229,57,53));}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.getBackground().setColorFilter(accent(),PorterDuff.Mode.SRC_ATOP);return b;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private static final class CellRow{final String title,detail;final int dbm;final boolean registered;CellRow(String t,String d,int s,boolean r){title=t;detail=d;dbm=s;registered=r;}}
}
