package ru.vital.tv.sunset;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class EventReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        AppPrefs prefs=new AppPrefs(context);
        AlarmPort alarms=new AlarmPort(context);
        Engine engine=new Engine(prefs,new NativePanel(context),alarms);
        String action=intent.getAction();
        boolean recovery=Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)
                ||Intent.ACTION_TIMEZONE_CHANGED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        try {engine.tick(prefs.plan(),System.currentTimeMillis(),recovery);PanelWatcher.sync(context);}
        catch(RuntimeException e) {
            engine.recordFailure(e);
            Log.e("SunsetTV","Backlight update failed",e);
            // A transient provider failure must not strand a short darkening test.
            Engine.State s=prefs.load();
            if(s.testOriginal!=null||s.restorePending) {
                try {alarms.rollback(System.currentTimeMillis()+60_000);} catch(RuntimeException ignored) { /* Recovery remains persisted. */ }
            }
        }
    }
}
