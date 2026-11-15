package ru.vital.tv.sunset;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

/** Diagnostics/emergency stop, available to Android's shell/system, never to ordinary installed apps. */
public final class StatusReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent intent) {
        boolean stop="ru.vital.tv.sunset.STOP".equals(intent.getAction());
        if(!stop&&!"ru.vital.tv.sunset.STATUS".equals(intent.getAction())) return;
        try {
            if(stop){new Engine(new AppPrefs(c),new NativePanel(c),new AlarmPort(c)).disable();PanelWatcher.sync(c);}
            AppPrefs p=new AppPrefs(c);Engine.State s=p.load();Plan plan=p.plan();NativePanel panel=new NativePanel(c);
            JSONObject out=new JSONObject();out.put("version",c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName);out.put("supported",panel.supported());
            out.put("permission",panel.permitted());out.put("enabled",s.enabled);out.put("verified",s.verified);
            out.put("current",panel.supported()?panel.read():JSONObject.NULL);out.put("original",s.original==null?JSONObject.NULL:s.original);
            out.put("testUntil",s.testUntil);out.put("restorePending",s.restorePending);out.put("message",s.message);
            out.put("city",plan.city);out.put("zone",plan.zone.getId());out.put("day",plan.dayLevel);out.put("night",plan.nightLevel);
            out.put("twilight",plan.twilight);out.put("scheduled",plan.at(System.currentTimeMillis()).level);
            out.put("nextAt",s.nextAt);setResultCode(0);setResultData(out.toString());
        } catch(Exception e) {setResultCode(1);setResultData("Status unavailable: "+e.getMessage());}
    }
}
