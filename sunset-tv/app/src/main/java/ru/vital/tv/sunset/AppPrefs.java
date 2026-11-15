package ru.vital.tv.sunset;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPrefs implements Engine.Store {
    private final SharedPreferences prefs;
    AppPrefs(Context c) {prefs=c.getSharedPreferences("sunset",Context.MODE_PRIVATE);}
    Plan plan() {
        Plan d=Plan.defaults();
        try {
            return new Plan(prefs.getString("city",d.city),
                    Double.parseDouble(prefs.getString("lat",String.valueOf(d.latitude))),
                    Double.parseDouble(prefs.getString("lon",String.valueOf(d.longitude))),
                    prefs.getString("zone",d.zone.getId()),prefs.getInt("day",d.dayLevel),prefs.getInt("night",d.nightLevel),
                    prefs.getBoolean("twilight",true));
        } catch(RuntimeException e) {return d;}
    }
    void plan(Plan p) {
        commit(prefs.edit().putString("city",p.city).putString("lat",String.valueOf(p.latitude))
                .putString("lon",String.valueOf(p.longitude)).putString("zone",p.zone.getId())
                .putInt("day",p.dayLevel).putInt("night",p.nightLevel).putBoolean("twilight",p.twilight));
    }
    @Override public Engine.State load() {
        Engine.State s=new Engine.State();
        s.enabled=prefs.getBoolean("enabled",false);s.verified=prefs.getBoolean("verified",false);
        s.testSucceeded=prefs.getBoolean("testSucceeded",false);s.restorePending=prefs.getBoolean("restorePending",false);
        s.original=integer("original");s.lastApplied=integer("lastApplied");
        s.testOriginal=integer("testOriginal");s.testApplied=integer("testApplied");
        s.testUntil=prefs.getLong("testUntil",0);s.nextAt=prefs.getLong("nextAt",0);
        s.message=prefs.getString("message",s.message);return s;
    }
    private Integer integer(String key) {return prefs.contains(key)?prefs.getInt(key,0):null;}
    private static void put(SharedPreferences.Editor e,String key,Integer v) {if(v==null)e.remove(key);else e.putInt(key,v);}
    @Override public void save(Engine.State s) {
        SharedPreferences.Editor e=prefs.edit().putBoolean("enabled",s.enabled).putBoolean("verified",s.verified)
                .putBoolean("testSucceeded",s.testSucceeded).putBoolean("restorePending",s.restorePending)
                .putLong("testUntil",s.testUntil).putLong("nextAt",s.nextAt).putString("message",s.message);
        put(e,"original",s.original);put(e,"lastApplied",s.lastApplied);
        put(e,"testOriginal",s.testOriginal);put(e,"testApplied",s.testApplied);commit(e);
    }
    private static void commit(SharedPreferences.Editor e) {
        if(!e.commit()) throw new IllegalStateException("Не удалось сохранить настройки. Подсветка не будет изменена без копии для возврата");
    }
}
