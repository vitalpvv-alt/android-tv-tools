package dev.mert.tvassistant;

import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.hardware.*;
import android.view.*;
import java.io.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;
import android.view.accessibility.AccessibilityEvent;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE,qualifiers="w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WeatherAndroidTest {
  private static final long NOW=1794711600000L;
  private static WeatherData.Reading sample(String city,double temperature,int code,boolean day) {
    return new WeatherData.Reading(WeatherData.place(city),NOW,NOW,temperature,-7.2,4.2,73d,code,day,
      new long[]{NOW,NOW+86400000},new double[]{-5,-4},new double[]{3,2});
  }
  private void render(String filename,WeatherData.Reading data,Float room,String status,int width,int height) throws Exception {
    Activity a=Robolectric.buildActivity(Activity.class).setup().get();
    WeatherCardView v=new WeatherCardView(a);v.content(data==null?WeatherData.PLACES[0]:data.place,data,room,status,NOW);
    v.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(0,0,width,height);
    Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);v.draw(new Canvas(bitmap));
    assertFalse(v.isFocusable());assertFalse(v.isClickable());assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO,v.getImportantForAccessibility());
    // Rendering must paint actual glyphs and a background rather than an empty shadow Canvas.
    assertTrue(Color.alpha(bitmap.getPixel(width/2,height/2))>200);
    int bright=0;for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(Color.red(bitmap.getPixel(x,y))>150)bright++;
    assertTrue("Weather text was not rendered",bright>100);
    File dir=new File("../../reports/weather-render");dir.mkdirs();
    try(FileOutputStream out=new FileOutputStream(new File(dir,filename+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
    bitmap.recycle();a.finish();
  }
  @Test public void realAndroidCanvasHandlesLongRussianAndTemperatureExtremes() throws Exception {
    render("cloudy-nakhodka",sample("nakhodka",-5.4,3,true),null,"",852,260);
    render("snow-room-vladivostok",sample("vladivostok",-25.3,75,false),22.3f,"",852,260);
    render("long-label",sample("ussuriysk",-100,2,true),null,"",852,260);
    render("offline",null,null,"Погода временно недоступна",852,260);
    render("small-720p",sample("nakhodka",32,0,true),null,"",568,173);
    WeatherData.Reading old=new WeatherData.Reading(WeatherData.PLACES[0],NOW-2*60*WeatherData.MINUTE,NOW-2*60*WeatherData.MINUTE,7.8,null,3.5,54d,3,true,new long[0],new double[0],new double[0]);
    old.source="MET Norway";render("cached-offline",old,null,"",852,260);
  }
  @Test public void settingsAreUsableWithRemoteAndMissingSensorDoesNotCrash() {
    WeatherSettingsActivity activity=Robolectric.buildActivity(WeatherSettingsActivity.class).setup().get();
    assertNotNull(activity.findViewById(android.R.id.content));
    assertFalse(activity.getSharedPreferences(HomeWeather.PREFS,0).contains("api_key"));activity.finish();
  }
  @Test public void cardNeverTakesFocusAndLeavingHomeUnregistersAmbientSensor() throws Exception {
    NavigationService service=Robolectric.buildService(NavigationService.class).create().get();
    SensorManager manager=(SensorManager)service.getSystemService(Context.SENSOR_SERVICE);
    ShadowSensorManager shadow=Shadows.shadowOf(manager);Sensor ambient=ShadowSensor.newInstance(Sensor.TYPE_AMBIENT_TEMPERATURE);shadow.addSensor(ambient);
    HomeWeather weather=new HomeWeather(service);
    try {
      ReflectionHelpers.callInstanceMethod(weather,"show",ClassParameter.from(WeatherGeometry.Box.class,new WeatherGeometry.Box(478,228,904,358)));
      assertTrue(weather.report().getBoolean("visible"));assertTrue(shadow.hasListener(weather,ambient));
      WeatherCardView card=ReflectionHelpers.getField(weather,"card");WindowManager.LayoutParams p=(WindowManager.LayoutParams)card.getLayoutParams();
      assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,p.type);
      assertTrue((p.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);
      assertTrue((p.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);
      weather.onSensorChanged(SensorEventBuilder.newBuilder().setSensor(ambient).setValues(new float[]{22.4f}).setAccuracy(SensorManager.SENSOR_STATUS_ACCURACY_HIGH).build());
      assertEquals(22.4,weather.report().getDouble("room_celsius"),.01);
      weather.onSensorChanged(SensorEventBuilder.newBuilder().setSensor(ShadowSensor.newInstance(Sensor.TYPE_TEMPERATURE)).setValues(new float[]{66}).setAccuracy(3).build());
      assertEquals(22.4,weather.report().getDouble("room_celsius"),.01);
      weather.onAccuracyChanged(ambient,SensorManager.SENSOR_STATUS_UNRELIABLE);assertTrue(weather.report().isNull("room_celsius"));
      AccessibilityEvent event=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);event.setPackageName("ru.rt.video.app.tv");weather.event(event);event.recycle();
      assertFalse(weather.report().getBoolean("visible"));assertFalse(shadow.hasListener(weather));
    }finally{weather.close();service.onDestroy();}
  }
  @Test public void deviceTemperatureSensorIsNeverReportedAsRoomTemperature() throws Exception {
    NavigationService service=Robolectric.buildService(NavigationService.class).create().get();
    ShadowSensorManager shadow=Shadows.shadowOf((SensorManager)service.getSystemService(Context.SENSOR_SERVICE));
    shadow.addSensor(ShadowSensor.newInstance(Sensor.TYPE_TEMPERATURE));HomeWeather weather=new HomeWeather(service);
    try {assertFalse(weather.report().getBoolean("ambient_sensor_available"));assertTrue(weather.report().isNull("room_celsius"));}
    finally {weather.close();service.onDestroy();}
  }
}
