package ru.vital.tv.sunset;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** This adapter only accesses the known MediaTek/Xiaomi panel-backlight control, range 0..100. */
final class NativePanel implements Engine.Panel {
    static final String KEY="picture_backlight";
    private final Context context;
    NativePanel(Context c) {context=c.getApplicationContext();}
    boolean supported() {
        return "MiTV-MSSP1".equals(Build.MODEL)&&Settings.Global.getString(context.getContentResolver(),KEY)!=null;
    }
    boolean permitted() {return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)==PackageManager.PERMISSION_GRANTED;}
    @Override public void requireReady() {
        if(!supported()) throw new IllegalStateException("Эта сборка предназначена для Xiaomi MiTV-MSSP1 с доступным управлением подсветкой");
        if(!permitted()) throw new IllegalStateException("Нет разрешения на изменение подсветки. Запусти комплектный установщик на компьютере");
        read();
    }
    @Override public int read() {
        try {
            int value=Settings.Global.getInt(context.getContentResolver(),KEY);
            if(value<0||value>100) throw new IllegalStateException("Неожиданный диапазон подсветки; изменение отменено");
            return value;
        } catch(Settings.SettingNotFoundException e) {throw new IllegalStateException("Управление подсветкой не найдено в этой прошивке");}
    }
    @Override public void write(int value) {
        requireReady();
        if(value<0||value>100) throw new IllegalArgumentException("Подсветка должна быть от 0 до 100%");
        if(!Settings.Global.putInt(context.getContentResolver(),KEY,value)||read()!=value)
            throw new IllegalStateException("Телевизор не подтвердил новое значение подсветки");
    }
}
