package dev.mert.tvassistant;

import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Weather is a passive accessibility overlay on this owner's launcher only. */
final class HomeWeather implements SensorEventListener,SharedPreferences.OnSharedPreferenceChangeListener {
  static final String PREFS="home_weather";
  private final AccessibilityService service;
  private final Handler main=new Handler(Looper.getMainLooper());
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final SharedPreferences prefs;
  private final WindowManager windows;
  private final SensorManager sensors;
  private final Sensor ambient;
  private String sensorError;
  private boolean sensorRegistered,closed,homeVisible,loading,manualRefresh;
  private Float room;
  private long roomAt,retryAt;
  private int generation;
  private WeatherCardView card;
  private WeatherGeometry.Box placement;
  private WeatherData.Place place;
  private WeatherData.Reading reading;
  private volatile HttpURLConnection connection;
  private Future<?> request;
  private String network="not_checked",status="Загружаю погоду…",visibility="not_home";
  private JSONArray probes=new JSONArray();
  private final Runnable inspect=this::inspectHome;
  private final Runnable tick=new Runnable(){@Override public void run(){inspectHome();if(!closed&&homeVisible)main.postDelayed(this,30000);}};
  private final BroadcastReceiver screen=new BroadcastReceiver(){
    @Override public void onReceive(Context c,Intent i){
      if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))hide("screen_off");else schedule();
    }
  };
  HomeWeather(AccessibilityService service) {
    this.service=service;prefs=service.getSharedPreferences(PREFS,0);
    windows=(WindowManager)service.getSystemService(Context.WINDOW_SERVICE);
    sensors=(SensorManager)service.getSystemService(Context.SENSOR_SERVICE);
    Sensor found=null;
    try {if(sensors!=null)found=sensors.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);}
    catch(Exception e){sensorError="probe_failed";}
    ambient=found;
    place=WeatherData.place(prefs.getString("city","nakhodka"));loadCache();
    prefs.registerOnSharedPreferenceChangeListener(this);
    IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);
    if(Build.VERSION.SDK_INT>=33)service.registerReceiver(screen,f,Context.RECEIVER_NOT_EXPORTED);else service.registerReceiver(screen,f);
    schedule();
  }
  static void requestRefresh(Context c) {
    c.getSharedPreferences(PREFS,0).edit().putLong("refresh",System.currentTimeMillis()).apply();
  }
  @Override public void onSharedPreferenceChanged(SharedPreferences p,String key) {
    if(!Arrays.asList("city","enabled","alignment","large","refresh").contains(key))return;
    main.post(()->{
      cancel();place=WeatherData.place(prefs.getString("city","nakhodka"));reading=null;loadCache();retryAt=0;
      manualRefresh="refresh".equals(key);
      schedule();
    });
  }
  void event(AccessibilityEvent event) {
    int type=event.getEventType();
    if(type!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type!=AccessibilityEvent.TYPE_WINDOWS_CHANGED
        && type!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && type!=AccessibilityEvent.TYPE_VIEW_SCROLLED)return;
    String pkg=String.valueOf(event.getPackageName());
    if(type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&!WeatherData.HOME.equals(pkg)
        &&!service.getPackageName().equals(pkg))hide("other_app");
    if(type!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || homeVisible || WeatherData.HOME.equals(pkg))schedule();
  }
  private void schedule(){if(closed)return;main.removeCallbacks(inspect);main.postDelayed(inspect,120);}
  private void loadCache(){
    try {
      if(place.id.equals(prefs.getString("cached_city",""))) {
        WeatherData.Reading r=WeatherData.parse(place,prefs.getString("cache",""),prefs.getLong("cached_at",0));
        if(r.usable(System.currentTimeMillis()))reading=r;
      }
    }catch(Exception ignored){}
  }
  private void inspectHome() {
    if(closed)return;
    PowerManager power=(PowerManager)service.getSystemService(Context.POWER_SERVICE);
    if(power==null||!power.isInteractive()){hide("screen_off");return;}
    if(!prefs.getBoolean("enabled",true)){hide("disabled");return;}
    AccessibilityNodeInfo root=null;
    List<AccessibilityWindowInfo> list=null;
    try {
      list=service.getWindows();
      AccessibilityWindowInfo focused=null;
      if(list!=null)for(AccessibilityWindowInfo w:list){
        if(w.getType()==AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY)continue;
        if(w.isFocused()&&(focused==null||w.getLayer()>focused.getLayer()))focused=w;
      }
      // No package-name fallback: a modal, keyboard or unreadable window must hide the card.
      if(focused==null||focused.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION){hide("no_focused_home");return;}
      root=focused.getRoot();
      if(root==null||!WeatherData.HOME.contentEquals(root.getPackageName())){hide("other_app");return;}
      List<AccessibilityNodeInfo> grid=root.findAccessibilityNodeInfosByViewId(WeatherData.HOME+":id/grid");
      boolean hasGrid=false;
      for(AccessibilityNodeInfo n:grid){hasGrid|=n.isVisibleToUser();n.recycle();}
      if(!hasGrid){hide("home_dialog");return;}
      Rect bounds=new Rect();root.getBoundsInScreen(bounds);
      // WindowManager's ordinary layout coordinates use the usable display, not raw panel pixels.
      android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();windows.getDefaultDisplay().getMetrics(metrics);
      if(bounds.left!=0||bounds.top!=0||bounds.width()!=metrics.widthPixels||bounds.height()!=metrics.heightPixels){hide("non_fullscreen_home");return;}
      int header=0,dock=metrics.heightPixels;
      for(String id:new String[]{"leftBlock","topButtons","clock","date"}){
        List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(WeatherData.HOME+":id/"+id);
        for(AccessibilityNodeInfo n:nodes){Rect b=new Rect();n.getBoundsInScreen(b);if(n.isVisibleToUser())header=Math.max(header,b.bottom);n.recycle();}
      }
      List<AccessibilityNodeInfo> tiles=root.findAccessibilityNodeInfosByViewId(WeatherData.HOME+":id/card");
      boolean hasTiles=false;
      for(AccessibilityNodeInfo n:tiles){Rect b=new Rect();n.getBoundsInScreen(b);if(n.isVisibleToUser()&&b.height()>0&&b.bottom>header){dock=Math.min(dock,b.top);hasTiles=true;}n.recycle();}
      if(!hasTiles){hide("no_dock");return;}
      WeatherGeometry.Box hero=null;
      List<AccessibilityNodeInfo> names=root.findAccessibilityNodeInfosByViewId(WeatherData.HOME+":id/heroName");
      for(AccessibilityNodeInfo n:names){Rect b=new Rect();n.getBoundsInScreen(b);if(n.isVisibleToUser()&&n.getText()!=null&&n.getText().length()>0)hero=new WeatherGeometry.Box(b.left,b.top,b.right,b.bottom);n.recycle();}
      WeatherGeometry.Box box=WeatherGeometry.place(metrics.widthPixels,metrics.heightPixels,metrics.density,header,dock,hero,prefs.getInt("alignment",2),prefs.getBoolean("large",false));
      if(box==null){hide("no_clear_space");return;}
      show(box);refreshIfNeeded();
    }catch(Exception e){hide("home_unreadable");}
    finally {
      if(root!=null)root.recycle();
      if(list!=null)for(AccessibilityWindowInfo w:list)w.recycle();
    }
  }
  private void show(WeatherGeometry.Box box) {
    if(card==null) {
      card=new WeatherCardView(service);
      WindowManager.LayoutParams p=params(box);
      try{windows.addView(card,p);}catch(Exception e){card=null;visibility="overlay_unavailable";return;}
      if(ambient!=null)try{sensorRegistered=sensors.registerListener(this,ambient,SensorManager.SENSOR_DELAY_NORMAL);if(!sensorRegistered)sensorError="registration_failed";}catch(Exception e){sensorError="registration_failed";}
      main.removeCallbacks(tick);main.postDelayed(tick,30000);
    }else if(placement==null||placement.left!=box.left||placement.top!=box.top||placement.width()!=box.width()||placement.height()!=box.height()){
      windows.updateViewLayout(card,params(box));
    }
    placement=box;homeVisible=true;visibility="visible";updateCard();
  }
  private WindowManager.LayoutParams params(WeatherGeometry.Box b) {
    WindowManager.LayoutParams p=new WindowManager.LayoutParams(b.width(),b.height(),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT);
    p.gravity=Gravity.TOP|Gravity.LEFT;p.x=b.left;p.y=b.top;p.setTitle("Home weather");return p;
  }
  private void updateCard(){if(card!=null)card.content(place,reading,room!=null&&SystemClock.elapsedRealtime()-roomAt<=10*WeatherData.MINUTE?room:null,status,System.currentTimeMillis());}
  private void refreshIfNeeded() {
    if(!homeVisible||loading||closed||SystemClock.elapsedRealtime()<retryAt)return;
    long now=System.currentTimeMillis();
    if(!manualRefresh&&reading!=null&&reading.usable(now)&&now-reading.fetchedAt<WeatherData.REFRESH)return;
    manualRefresh=false;loading=true;status="Загружаю погоду…";updateCard();
    final int job=++generation;final WeatherData.Place requested=place;
    request=worker.submit(()->{
      HttpURLConnection http=null;String raw=null,problem=null;WeatherData.Reading result=null;
      long metExpires=0;String metModified=null;
      JSONArray attempts=new JSONArray();
      for(int provider=0;provider<2;provider++) {
      long providerStart=SystemClock.elapsedRealtime();int providerHttp=0;
      try {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
        boolean met=provider==1;
        boolean sameMetCity=requested.id.equals(prefs.getString("met_city",""));
        String metBody=sameMetCity?prefs.getString("met_cache",null):null;
        if(met&&metBody!=null&&System.currentTimeMillis()<prefs.getLong("met_expires",0)){
          raw=metBody;result=WeatherData.parse(requested,raw,prefs.getLong("met_cached_at",0));
          if(!result.usable(System.currentTimeMillis()))throw new IOException("old_data");problem=null;
          attempts.put(Json.obj("source","MET Norway","http",0,"kind","cache","elapsed_ms",SystemClock.elapsedRealtime()-providerStart));break;
        }
        http=(HttpURLConnection)new URL(met?requested.metUrl():requested.url()).openConnection();connection=http;
        http.setConnectTimeout(8000);http.setReadTimeout(8000);http.setInstanceFollowRedirects(true);
        http.setRequestProperty("Accept","application/json");http.setRequestProperty("User-Agent","VitalTVWeather/1.0 (personal Android TV weather card)");
        http.setRequestProperty("Accept-Encoding","gzip, deflate");
        if(met&&metBody!=null){String modified=prefs.getString("met_modified","");if(!modified.isEmpty())http.setRequestProperty("If-Modified-Since",modified);}
        int code=http.getResponseCode();providerHttp=code;if(code!=200&&!(met&&code==304&&metBody!=null))throw new IOException("http_"+code);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[2048];
        long deadline=SystemClock.elapsedRealtime()+12000;
        if(code==200)try(InputStream in=decoded(http)){
          int count;
          while((count=in.read(buffer))!=-1){
            if(Thread.currentThread().isInterrupted()||SystemClock.elapsedRealtime()>deadline)throw new InterruptedIOException();
            if(bytes.size()+count>65536)throw new IOException("response_size");bytes.write(buffer,0,count);
          }
        }
        raw=code==304?metBody:new String(bytes.toByteArray(),"UTF-8");long fetched=System.currentTimeMillis();
        result=WeatherData.parse(requested,raw,fetched);if(!result.usable(fetched))throw new IOException("old_data");
        if(met){metExpires=Math.max(fetched+WeatherData.REFRESH,http.getHeaderFieldDate("Expires",0));metModified=http.getHeaderField("Last-Modified");}
        problem=null;attempts.put(Json.obj("source",met?"MET Norway":"Open-Meteo","http",code,"kind","ok","elapsed_ms",SystemClock.elapsedRealtime()-providerStart));break;
      }catch(Exception e){
        String m=e.getMessage();problem=m!=null&&m.matches("(?:http_[0-9]{3}|weather_[a-z_]{1,35}|old_data|response_size)")?m:
          e instanceof java.net.UnknownHostException?"dns":e instanceof org.json.JSONException?"invalid_json":
          e instanceof java.net.SocketTimeoutException?"timeout":e instanceof javax.net.ssl.SSLException?"tls":
          e instanceof InterruptedIOException?"cancelled":"network_or_data";
        attempts.put(Json.obj("source",provider==1?"MET Norway":"Open-Meteo","http",providerHttp,"kind",problem,"elapsed_ms",SystemClock.elapsedRealtime()-providerStart));
      }finally{if(http!=null)http.disconnect();if(connection==http)connection=null;http=null;}
      }
      final WeatherData.Reading data=result;final String body=raw,error=problem,modified=metModified;final long expires=metExpires;
      main.post(()->{
        if(closed||job!=generation||!place.id.equals(requested.id))return;
        loading=false;request=null;
        probes=attempts;
        if(error==null&&data!=null){
          reading=data;network="ok";status="";retryAt=SystemClock.elapsedRealtime()+WeatherData.REFRESH;
          SharedPreferences.Editor edit=prefs.edit().putString("cache",body).putString("cached_city",requested.id).putLong("cached_at",data.fetchedAt);
          if(data.source.equals("MET Norway")&&expires>0){edit.putString("met_cache",body).putString("met_city",requested.id).putLong("met_cached_at",data.fetchedAt).putLong("met_expires",expires);if(modified!=null)edit.putString("met_modified",modified);}
          edit.apply();
        }else{network=error;status="Погода временно недоступна";retryAt=SystemClock.elapsedRealtime()+WeatherData.RETRY;}
        updateCard();
      });
    });
  }
  private static InputStream decoded(HttpURLConnection http) throws IOException {
    InputStream source=http.getInputStream();String coding=http.getContentEncoding();
    if("gzip".equalsIgnoreCase(coding))return new java.util.zip.GZIPInputStream(source);
    if("deflate".equalsIgnoreCase(coding))return new java.util.zip.InflaterInputStream(source);
    return source;
  }
  private void cancel(){generation++;loading=false;if(request!=null){request.cancel(true);request=null;}HttpURLConnection h=connection;if(h!=null)h.disconnect();}
  void hide(String reason) {
    homeVisible=false;visibility=reason;main.removeCallbacks(tick);cancel();
    if(card!=null){try{windows.removeViewImmediate(card);}catch(Exception ignored){}card=null;}
    if(sensorRegistered){sensors.unregisterListener(this);sensorRegistered=false;}
    placement=null;
  }
  @Override public void onSensorChanged(SensorEvent event){
    if(!homeVisible||event.sensor==null||event.sensor.getType()!=Sensor.TYPE_AMBIENT_TEMPERATURE||event.values.length==0)return;
    if(WeatherData.ambientValid(event.values[0],event.accuracy)){room=event.values[0];roomAt=SystemClock.elapsedRealtime();}
    else {room=null;roomAt=0;}updateCard();
  }
  @Override public void onAccuracyChanged(Sensor s,int accuracy){if(accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE){room=null;roomAt=0;updateCard();}}
  JSONObject report() {
    long now=System.currentTimeMillis();boolean usable=reading!=null&&reading.usable(now);
    return Json.obj("version",1,"city",place.name,"enabled",prefs.getBoolean("enabled",true),"visible",card!=null&&homeVisible,
      "visibility",visibility,"network",network,"probes",probes,"has_usable_weather",usable,"stale",usable&&reading.stale(now),
      "measured_at",usable?reading.measuredAt:JSONObject.NULL,"fetched_at",usable?reading.fetchedAt:JSONObject.NULL,"source",usable?reading.source:JSONObject.NULL,
      "ambient_sensor_available",ambient!=null,"ambient_probe_error",sensorError==null?JSONObject.NULL:sensorError,
      "ambient_sensor_type",ambient==null?JSONObject.NULL:ambient.getType(),
      "room_celsius",room!=null&&SystemClock.elapsedRealtime()-roomAt<=10*WeatherData.MINUTE?room:JSONObject.NULL,
      "placement",placement==null?JSONObject.NULL:Json.obj("x",placement.left,"y",placement.top,"width",placement.width(),"height",placement.height()));
  }
  void close(){if(closed)return;hide("service_stopped");closed=true;main.removeCallbacksAndMessages(null);prefs.unregisterOnSharedPreferenceChangeListener(this);try{service.unregisterReceiver(screen);}catch(Exception ignored){}worker.shutdownNow();}
}
