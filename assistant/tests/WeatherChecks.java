package dev.mert.tvassistant;

import java.nio.file.*;
import org.json.*;

public final class WeatherChecks {
  private static int count;
  private static void yes(boolean value){if(!value)throw new AssertionError();count++;}
  private static void rejects(RunnableWithException task) throws Exception {boolean rejected=false;try{task.run();}catch(Exception e){rejected=true;}yes(rejected);}
  private interface RunnableWithException{void run()throws Exception;}
  static JSONObject sample(long now) {
    return Json.obj("current_units",Json.obj("time","unixtime","temperature_2m","°C","wind_speed_10m","m/s"),
      "current",Json.obj("time",now/1000,"temperature_2m",-4.6,"weather_code",3,"is_day",1,"apparent_temperature",-8,"wind_speed_10m",3.2,"relative_humidity_2m",63),
      "daily",Json.obj("time",Json.arr(now/1000,now/1000+86400),"temperature_2m_min",Json.arr(-7,-5),"temperature_2m_max",Json.arr(1,2)));
  }
  public static void main(String[] args)throws Exception {
    long now=1794711600000L;WeatherData.Place city=WeatherData.PLACES[0];JSONObject good=sample(now);
    WeatherData.Reading r=WeatherData.parse(city,good.toString(),now);
    yes(r.temperature==-4.6&&r.wind==3.2&&r.humidity==63&&r.tomorrow(now)==1&&r.source.equals("Open-Meteo"));
    yes(r.usable(now)&&!r.stale(now)&&r.stale(now+91*WeatherData.MINUTE)&&!r.usable(now+25*60*WeatherData.MINUTE));
    yes(!r.usable(now-21*WeatherData.MINUTE));
    JSONObject missing=sample(now);missing.getJSONObject("current").put("temperature_2m",JSONObject.NULL);
    rejects(()->WeatherData.parse(city,missing.toString(),now));
    JSONObject wrong=sample(now);wrong.getJSONObject("current_units").put("wind_speed_10m","km/h");rejects(()->WeatherData.parse(city,wrong.toString(),now));
    JSONObject nullExtras=sample(now);nullExtras.getJSONObject("current").put("relative_humidity_2m",JSONObject.NULL).put("wind_speed_10m",JSONObject.NULL).put("apparent_temperature",JSONObject.NULL);
    WeatherData.Reading optional=WeatherData.parse(city,nullExtras.toString(),now);yes(optional.wind==null&&optional.humidity==null&&optional.feels==null);
    JSONObject brokenForecast=sample(now);brokenForecast.getJSONObject("daily").put("temperature_2m_min",Json.arr(999));
    yes(WeatherData.parse(city,brokenForecast.toString(),now).dates.length==0);
    rejects(()->WeatherData.parse(city,new String(new char[65537]),now));
    yes(WeatherData.degrees(-0.1).equals("0°")&&WeatherData.degrees(-4.6).equals("−5°")&&WeatherData.degrees(5).equals("+5°"));
    for(WeatherData.Place p:WeatherData.PLACES){yes(p.url().startsWith("https://api.open-meteo.com/v1/forecast?"));yes(p.metUrl().startsWith("https://api.met.no/weatherapi/locationforecast/2.0/compact?"));}
    yes(WeatherData.place("untrusted-url")==city);
    yes(!WeatherData.ambientValid(22,0)&&!WeatherData.ambientValid(Float.NaN,3)&&!WeatherData.ambientValid(Float.POSITIVE_INFINITY,3)&&!WeatherData.ambientValid(90,3)&&WeatherData.ambientValid(22.2f,3));
    yes(WeatherData.metCode("heavysleetshowers_night")==69&&WeatherData.metCode("heavysnow")==75&&WeatherData.metCode("unknown")>99);
    for(float scale:new float[]{1,1.5f,2,4}){
      int w=Math.round(960*scale),h=Math.round(540*scale),head=Math.round(142*scale),dock=Math.round(382*scale);
      WeatherGeometry.Box hero=new WeatherGeometry.Box(Math.round(68*scale),Math.round(288*scale),Math.round(280*scale),Math.round(338*scale));
      for(int align=0;align<3;align++)for(boolean large:new boolean[]{false,true}){
        WeatherGeometry.Box b=WeatherGeometry.place(w,h,scale,head,dock,hero,align,large);
        if(b!=null){yes(b.top>head&&b.bottom<dock&&b.left>=0&&b.right<=w&&!b.intersects(hero));}
        else yes(align!=2);
      }
    }
    yes(WeatherGeometry.place(960,540,1,230,300,null,2,false)==null);
    yes(WeatherGeometry.place(0,540,1,100,350,null,2,false)==null);
    if(args.length>0){
      String live=new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8");
      WeatherData.Reading met=WeatherData.parse(city,live,System.currentTimeMillis());
      yes(met.source.equals("MET Norway")&&met.usable(System.currentTimeMillis())&&met.tomorrow(System.currentTimeMillis())>=0&&met.wind!=null&&met.humidity!=null);
      System.out.println("Live MET response parsed with production code: "+WeatherData.degrees(met.temperature)+", "+WeatherData.condition(met.code,met.day)+", wind="+met.wind+", tomorrow available");
      JSONObject badMet=new JSONObject(live);badMet.getJSONObject("properties").getJSONObject("meta").getJSONObject("units").put("air_temperature","fahrenheit");
      rejects(()->WeatherData.parse(city,badMet.toString(),System.currentTimeMillis()));
    }
    System.out.println("Weather checks: "+count+" passed");
  }
}
