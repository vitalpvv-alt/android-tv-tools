package ru.vital.tv.sunset;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class MainActivity extends Activity {
    private AppPrefs prefs; private NativePanel panel; private Engine engine;
    private TextView stateText,timesText,dayLabel,nightLabel;
    private Button autoButton,cityButton,confirmButton,testButton,twilightButton;
    private SeekBar dayBar,nightBar;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean rendering;
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            Engine.State s=prefs.load();
            if(s.testOriginal!=null&&System.currentTimeMillis()>=s.testUntil) runAction(()->engine.tick(prefs.plan(),System.currentTimeMillis(),false));
            render();handler.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);prefs=new AppPrefs(this);panel=new NativePanel(this);
        engine=new Engine(prefs,panel,new AlarmPort(this));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(44),dp(24),dp(44),dp(32));
        scroll.addView(page);setContentView(scroll);
        TextView title=text("Сумерки ТВ",30,Color.WHITE);title.setTypeface(null,Typeface.BOLD);page.addView(title);
        page.addView(text("Яркость от заката до рассвета · Xiaomi MiTV-MSSP1",15,0xffb9c6d7));
        stateText=text("",17,0xffffbc71);stateText.setPadding(0,dp(15),0,dp(9));page.addView(stateText);
        timesText=text("",16,0xffdce5f1);page.addView(timesText);
        cityButton=button("",this::chooseCity);page.addView(cityButton);
        twilightButton=button("",()->runAction(()->{
            Plan p=prefs.plan().withTwilight(!prefs.plan().twilight);prefs.plan(p);
            if(prefs.load().enabled)engine.tick(p,System.currentTimeMillis(),false);
        }));page.addView(twilightButton);
        dayLabel=text("",18,Color.WHITE);page.addView(dayLabel);dayBar=slider(page,true);
        nightLabel=text("",18,Color.WHITE);page.addView(nightLabel);nightBar=slider(page,false);
        autoButton=button("",()->runAction(()->{if(prefs.load().enabled)engine.disable();else engine.enable(prefs.plan(),System.currentTimeMillis());}));page.addView(autoButton);
        testButton=button("",()->runAction(()->engine.startTest(prefs.plan(),System.currentTimeMillis())));page.addView(testButton);
        confirmButton=button("Экран стал темнее — подтвердить и включить авто",()->runAction(()->{engine.confirmTest();engine.enable(prefs.plan(),System.currentTimeMillis());}));page.addView(confirmButton);
        page.addView(button("Выключить и вернуть исходную подсветку",()->runAction(engine::disable)));
        if(Build.VERSION.SDK_INT>=31) page.addView(button("Разрешить точное расписание",()->startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())))));
        TextView help=text("Тест затемнит экран на 10 секунд и вернёт прежнюю подсветку. Затем подтверди результат.\nАвтоматика поддерживает выбранную подсветку и после переключения приложений. Расчёт — на ТВ без интернета. Для ручного изменения выключи автоматику.\nРасписание сохраняется после перезагрузки. Управление — стрелками пульта.",14,0xffa9b8ce);
        help.setPadding(0,dp(12),0,0);page.addView(help);
        render();autoButton.requestFocus();
    }
    @Override protected void onResume() {
        super.onResume();
        Engine.State s=prefs.load();
        if(s.enabled||s.restorePending||s.testOriginal!=null)
            runAction(()->engine.tick(prefs.plan(),System.currentTimeMillis(),false));
        handler.removeCallbacks(refresh);handler.post(refresh);
    }
    @Override protected void onPause() {handler.removeCallbacks(refresh);super.onPause();}
    private void runAction(Runnable r) {
        try {r.run();PanelWatcher.sync(this);} catch(RuntimeException e) {engine.recordFailure(e);}
        render();
    }
    private void render() {
        if(stateText==null)return;rendering=true;
        Plan p=prefs.plan();Engine.State s=prefs.load();long now=System.currentTimeMillis();Plan.Now plan=p.at(now);
        String status=s.message;
        if(!panel.supported())status="Управление подсветкой не найдено: эта сборка для MiTV-MSSP1";
        else if(!panel.permitted())status="Нужно разрешение на изменение подсветки. Запусти комплектный установщик";
        else if(s.testOriginal!=null)status="Тест: "+p.nightLevel+"% · возврат через "+Math.max(0,(s.testUntil-now+999)/1000)+" сек.";
        stateText.setText(status);
        String rise=plan.today.sunrise==null?"нет":time(plan.today.sunrise,p);
        String set=plan.today.sunset==null?"нет":time(plan.today.sunset,p);
        String current="—";try {current=panel.read()+"%";} catch(RuntimeException ignored) {}
        String next=s.enabled?"\nСледующее переключение: "+DateTimeFormatter.ofPattern("dd.MM HH:mm",Locale.forLanguageTag("ru")).withZone(p.zone).format(Instant.ofEpochMilli(plan.nextAt))+" → "+plan.nextLevel+"%":"";
        timesText.setText("Сегодня: рассвет "+rise+" · закат "+set+"    |    Подсветка сейчас: "+current+next);
        cityButton.setText("Город: "+p.city+" · "+p.zone.getId()+"  ›");
        twilightButton.setText(p.twilight?"Сумерки включены: "+p.levelForStage(4)+" → "+p.levelForStage(3)+" → "+p.levelForStage(2)+" → "+p.levelForStage(1)+" → "+p.levelForStage(0)+"%":"Сумерки выключены — включить промежуточную яркость");
        dayLabel.setText("Днём: "+p.dayLevel+"%     ◀ ▶");nightLabel.setText((p.twilight?"Когда темно: ":"От заката до рассвета: ")+p.nightLevel+"%     ◀ ▶");
        dayBar.setProgress(p.dayLevel/5);nightBar.setProgress(p.nightLevel/5);
        autoButton.setText(s.enabled?"Автоматика включена — выключить":"Включить автоматику");
        testButton.setText("Проверить ночные "+p.nightLevel+"% на 10 секунд");
        testButton.setEnabled(!s.enabled&&s.testOriginal==null&&!s.restorePending&&panel.supported()&&panel.permitted());
        confirmButton.setVisibility(s.testSucceeded&&!s.enabled?View.VISIBLE:View.GONE);
        dayBar.setEnabled(s.testOriginal==null);nightBar.setEnabled(s.testOriginal==null);cityButton.setEnabled(s.testOriginal==null);twilightButton.setEnabled(s.testOriginal==null);
        rendering=false;
    }
    private static String time(Instant at,Plan p) {return DateTimeFormatter.ofPattern("HH:mm",Locale.ROOT).withZone(p.zone).format(at);}
    private SeekBar slider(LinearLayout parent,boolean day) {
        SeekBar bar=new SeekBar(this);bar.setMax(20);bar.setKeyProgressIncrement(1);bar.setMinimumHeight(dp(40));
        bar.setContentDescription(day?"Яркость днём":"Яркость ночью");parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(44)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar b,int progress,boolean fromUser) {
                if(rendering||!fromUser)return;
                Plan p=prefs.plan();
                prefs.plan(new Plan(p.city,p.latitude,p.longitude,p.zone.getId(),day?progress*5:p.dayLevel,day?p.nightLevel:progress*5,p.twilight));
                if(prefs.load().enabled)runAction(()->engine.tick(prefs.plan(),System.currentTimeMillis(),false));else render();
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });return bar;
    }
    private void chooseCity() {
        String[] names={"Находка","Владивосток","Москва","Другой город — ввести координаты"};
        new AlertDialog.Builder(this).setTitle("Где находится телевизор?").setItems(names,(d,which)->{
            if(which==3){customCity();return;}
            Plan p=prefs.plan();double[] lat={42.824,43.116,55.756},lon={132.892,131.882,37.617};
            saveLocation(new Plan(names[which],lat[which],lon[which],which==2?"Europe/Moscow":"Asia/Vladivostok",p.dayLevel,p.nightLevel,p.twilight));
        }).setNegativeButton("Отмена",null).show();
    }
    private void customCity() {
        Plan p=prefs.plan();LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(12),dp(24),0);
        EditText name=field(box,"Город",p.city,false),lat=field(box,"Широта (−89…89)",String.valueOf(p.latitude),true),
                lon=field(box,"Долгота (−180…180)",String.valueOf(p.longitude),true),zone=field(box,"Часовой пояс, например Asia/Vladivostok",p.zone.getId(),false);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Город для расчёта солнца").setView(box).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            try {
                String city=name.getText().toString().trim();if(city.isEmpty())throw new IllegalArgumentException("Введи название города");
                Plan selected=new Plan(city,Double.parseDouble(lat.getText().toString().replace(',','.')),Double.parseDouble(lon.getText().toString().replace(',','.')),zone.getText().toString().trim(),p.dayLevel,p.nightLevel,p.twilight);
                saveLocation(selected);dialog.dismiss();
            } catch(RuntimeException e){zone.setError("Проверь координаты и часовой пояс");}
        }));dialog.show();
    }
    private void saveLocation(Plan p) {runAction(()->{prefs.plan(p);if(prefs.load().enabled)engine.tick(p,System.currentTimeMillis(),false);});}
    private EditText field(LinearLayout box,String hint,String value,boolean number) {
        EditText e=new EditText(this);e.setHint(hint);e.setContentDescription(hint);e.setText(value);e.setSingleLine();
        if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        box.addView(e);return e;
    }
    private TextView text(String value,int size,int color) {TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);return t;}
    private Button button(String label,Runnable action) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);b.setTextColor(Color.WHITE);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setPadding(dp(18),0,dp(18),0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.topMargin=dp(10);lp.bottomMargin=dp(6);b.setLayoutParams(lp);
        b.setOnFocusChangeListener((v,focused)->{GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(10));bg.setColor(focused?0xff644c36:0xff202c3d);bg.setStroke(dp(focused?2:1),focused?0xffffbc71:0xff34465e);v.setBackground(bg);});
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff202c3d);bg.setCornerRadius(dp(10));b.setBackground(bg);b.setOnClickListener(v->action.run());return b;
    }
    private int dp(int value) {return Math.round(value*getResources().getDisplayMetrics().density);}
}
