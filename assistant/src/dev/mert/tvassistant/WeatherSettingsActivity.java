package dev.mert.tvassistant;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

/** Remote-friendly options; the home card itself never takes focus from app icons. */
public final class WeatherSettingsActivity extends Activity {
  private SharedPreferences prefs;
  @Override public void onCreate(Bundle state) {super.onCreate(state);prefs=getSharedPreferences(HomeWeather.PREFS,0);render();}
  private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
  private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,0,0,dp(10));return t;}
  private GradientDrawable background(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(10));d.setStroke(dp(1),stroke);return d;}
  private void render() {
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xff0a111f);
    LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);rows.setPadding(dp(40),dp(24),dp(40),dp(24));scroll.addView(rows);setContentView(scroll);
    TextView title=text("Погода на главном экране",26,0xfff5f8ff);title.setTypeface(null,Typeface.BOLD);rows.addView(title);
    rows.addView(text("Небольшая карточка над иконками. Во время просмотра приложений она скрыта.",15,0xffb5c6d9));
    add(rows,"Карточка: "+(prefs.getBoolean("enabled",true)?"включена":"выключена"),()->{prefs.edit().putBoolean("enabled",!prefs.getBoolean("enabled",true)).apply();render();});
    add(rows,"Город: "+WeatherData.place(prefs.getString("city","nakhodka")).name,()->{
      String[] names=new String[WeatherData.PLACES.length];for(int i=0;i<names.length;i++)names[i]=WeatherData.PLACES[i].name;
      new AlertDialog.Builder(this).setTitle("Город прогноза").setItems(names,(d,i)->{prefs.edit().putString("city",WeatherData.PLACES[i].id).apply();render();}).setNegativeButton("Отмена",null).show();
    });
    String[] positions={"слева","по центру","справа"};
    int align=Math.max(0,Math.min(2,prefs.getInt("alignment",2)));
    add(rows,"Положение: "+positions[align],()->new AlertDialog.Builder(this).setTitle("Карточка над иконками").setItems(positions,(d,i)->{prefs.edit().putInt("alignment",i).apply();render();}).setNegativeButton("Отмена",null).show());
    add(rows,"Размер: "+(prefs.getBoolean("large",false)?"крупнее":"небольшой"),()->{prefs.edit().putBoolean("large",!prefs.getBoolean("large",false)).apply();render();});
    add(rows,"Обновить при возвращении домой",()->{HomeWeather.requestRefresh(this);Toast.makeText(this,"Прогноз обновится на главном экране",Toast.LENGTH_LONG).show();});
    String sensorText;
    try {
      SensorManager manager=(SensorManager)getSystemService(SENSOR_SERVICE);
      Sensor sensor=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
      sensorText=sensor==null?"Комнатная температура: Android не обнаружил датчик воздуха. Строка в карточке скрыта."
        :"Датчик воздуха обнаружен. Температура появится в карточке после достоверного измерения.";
    }catch(Exception e){sensorText="Проверка датчика воздуха недоступна. Комнатная температура пока скрыта.";}
    rows.addView(text(sensorText,14,0xffb5c6d9));
    TextView credit=text("Прогноз: https://open-meteo.com и MET Norway — https://www.met.no · CC BY 4.0: https://creativecommons.org/licenses/by/4.0/ . Данные округлены и переведены. Погода рассчитана по метеомоделям. Обновление раз в 30 минут с учётом срока кэша сервиса. Без сети сохраняется последний прогноз с его временем. Ключ ИИ не нужен.",13,0xffb5c6d9);
    credit.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);credit.setLinkTextColor(0xff91d6fc);rows.addView(credit);
    if(NavigationService.instance==null)rows.addView(text("Для карточки включите службу «ТВ-помощник: управление экраном» в специальных возможностях Android.",14,0xffffcf7b));
    rows.addView(text("Если места между часами и иконками недостаточно, карточка скрывается. Выберите другое положение или небольшой размер.",13,0xffb5c6d9));
    add(rows,"Показать главный экран",()->{Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);try{startActivity(home);}catch(Exception e){Toast.makeText(this,"Нажмите кнопку «Домой» на пульте",Toast.LENGTH_LONG).show();}finish();});
    add(rows,"Назад",this::finish);
  }
  private void add(LinearLayout rows,String label,Runnable action) {
    TextView b=text(label,18,0xfff4f7fc);b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setGravity(Gravity.CENTER_VERTICAL);
    b.setMinHeight(dp(48));b.setFocusable(true);b.setClickable(true);
    b.setBackground(background(0xff17253a,0xff2a3c52));
    b.setOnFocusChangeListener((v,focused)->b.setBackground(background(focused?0xff1e3a50:0xff17253a,focused?0xff7dd3fc:0xff2a3c52)));
    b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);rows.addView(b,p);
  }
}
