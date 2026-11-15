package ru.vital.tv.sunset;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;

/** Event-driven: no network, polling, wake locks or screen overlays. */
public final class PanelWatcher extends Service {
    private static final String CHANNEL="sunset_backlight";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final RateGuard guard=new RateGuard();
    private AppPrefs prefs;private NativePanel panel;private Engine engine;
    private final Runnable enforce=()->{
        Engine.State state=prefs.load();if(!state.enabled){stopSelf();return;}
        try {
            panel.requireReady();Plan p=prefs.plan();long now=System.currentTimeMillis();
            if(panel.read()==p.at(now).level)return;
            if(!guard.permit(SystemClock.elapsedRealtime())) {
                engine.disable();engine.recordFailure(new IllegalStateException("Прошивка несколько раз сбросила подсветку. Автоматика остановлена; исходная яркость возвращена"));stopSelf();return;
            }
            engine.tick(p,now,false);
        } catch(RuntimeException e) {
            try {engine.disable();}catch(RuntimeException ignored){}
            engine.recordFailure(e);stopSelf();
        }
    };
    private final ContentObserver observer=new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange){queue();}
    };
    private final BroadcastReceiver screen=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i){queue();}
    };
    static void sync(Context c) {
        Intent i=new Intent(c,PanelWatcher.class);
        if(new AppPrefs(c).load().enabled)c.startForegroundService(i);else c.stopService(i);
    }
    private void queue(){handler.removeCallbacks(enforce);handler.postDelayed(enforce,700);}
    @Override public void onCreate() {
        super.onCreate();prefs=new AppPrefs(this);panel=new NativePanel(this);engine=new Engine(prefs,panel,new AlarmPort(this));
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Автоматическая подсветка",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(1,new Notification.Builder(this,CHANNEL).setSmallIcon(ru.vital.tv.sunset.R.drawable.icon)
                .setContentTitle("Сумерки ТВ").setContentText("Подсветка по закату и рассвету").setContentIntent(open).setOngoing(true).setShowWhen(false).build());
        getContentResolver().registerContentObserver(Settings.Global.getUriFor(NativePanel.KEY),false,observer);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,new IntentFilter(Intent.ACTION_SCREEN_ON),Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen,new IntentFilter(Intent.ACTION_SCREEN_ON));
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){queue();return START_STICKY;}
    @Override public void onDestroy(){handler.removeCallbacks(enforce);getContentResolver().unregisterContentObserver(observer);unregisterReceiver(screen);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
