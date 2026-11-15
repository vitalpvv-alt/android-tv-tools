package ru.vital.tv.sunset;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

final class AlarmPort implements Engine.Alarms {
    static final String AUTOMATIC="ru.vital.tv.sunset.AUTOMATIC";
    static final String ROLLBACK="ru.vital.tv.sunset.ROLLBACK";
    private final Context context;
    private final AlarmManager alarms;
    AlarmPort(Context c) {context=c.getApplicationContext();alarms=context.getSystemService(AlarmManager.class);}
    private PendingIntent intent(String action,int code) {
        return PendingIntent.getBroadcast(context,code,new Intent(context,EventReceiver.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    boolean exactAllowed() {return Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms();}
    @Override public void automatic(long at) {
        if(exactAllowed()) alarms.setExact(AlarmManager.RTC,at,intent(AUTOMATIC,1));
        else alarms.set(AlarmManager.RTC,at,intent(AUTOMATIC,1));
    }
    @Override public void rollback(long at) {
        if(!exactAllowed()) throw new IllegalStateException("Для безопасного теста разреши точное расписание в настройках приложения");
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,intent(ROLLBACK,2));
    }
    @Override public void cancelAutomatic() {alarms.cancel(intent(AUTOMATIC,1));}
    @Override public void cancelRollback() {alarms.cancel(intent(ROLLBACK,2));}
}
