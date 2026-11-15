package dev.mert.tvassistant;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.TreeMap;
import org.json.*;

/** Weather only: no assistant credentials, location permission or inference endpoint. */
final class WeatherData {
  static final long MINUTE = 60000L, REFRESH = 30 * MINUTE, RETRY = 5 * MINUTE;
  static final String HOME = "com.gathin.tvhome";
  static final Place[] PLACES = {
    new Place("nakhodka", "Находка", 42.824, 132.892, "Asia/Vladivostok"),
    new Place("vladivostok", "Владивосток", 43.1155, 131.8855, "Asia/Vladivostok"),
    new Place("artem", "Артём", 43.3595, 132.1889, "Asia/Vladivostok"),
    new Place("ussuriysk", "Уссурийск", 43.8029, 131.9458, "Asia/Vladivostok"),
    new Place("moscow", "Москва", 55.7558, 37.6173, "Europe/Moscow")
  };
  static final class Place {
    final String id, name, timezone;
    final double lat, lon;
    Place(String id, String name, double lat, double lon, String timezone) {
      this.id=id; this.name=name; this.lat=lat; this.lon=lon; this.timezone=timezone;
    }
    String url() throws Exception {
      return "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
        + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m"
        + "&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days=3&wind_speed_unit=ms"
        + "&timeformat=unixtime&timezone=" + URLEncoder.encode(timezone,"UTF-8");
    }
    String metUrl(){return "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat="+lat+"&lon="+lon;}
  }
  static Place place(String id) {
    for (Place p:PLACES) if(p.id.equals(id)) return p;
    return PLACES[0];
  }
  static final class Reading {
    final Place place;
    final long measuredAt, fetchedAt;
    final double temperature;
    final Double feels, wind, humidity;
    final int code;
    final boolean day;
    String source="Open-Meteo";
    final long[] dates;
    final double[] lows, highs;
    Reading(Place p, long at, long fetched, double t, Double f, Double w, Double h,
        int c, boolean day, long[] dates, double[] lows, double[] highs) {
      place=p; measuredAt=at; fetchedAt=fetched; temperature=t; feels=f; wind=w; humidity=h;
      code=c; this.day=day; this.dates=dates; this.lows=lows; this.highs=highs;
    }
    boolean usable(long now) {return measuredAt<=now+20*MINUTE && measuredAt>=now-24*60*MINUTE && fetchedAt<=now+MINUTE;}
    boolean stale(long now) {return now-measuredAt>90*MINUTE;}
    int tomorrow(long now) {
      SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);
      date.setTimeZone(TimeZone.getTimeZone(place.timezone));
      String wanted=date.format(new Date(now+24*60*MINUTE));
      for(int i=0;i<dates.length;i++) if(date.format(new Date(dates[i])).equals(wanted))return i;
      return -1;
    }
    String time() {
      SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.ROOT);
      f.setTimeZone(TimeZone.getTimeZone(place.timezone)); return f.format(new Date(measuredAt));
    }
    String dateTime() {
      SimpleDateFormat f=new SimpleDateFormat("dd.MM HH:mm",Locale.ROOT);
      f.setTimeZone(TimeZone.getTimeZone(place.timezone));return f.format(new Date(measuredAt));
    }
  }
  static Reading parse(Place place, String body, long fetchedAt) throws Exception {
    if(body==null || body.length()>65536) throw new IllegalArgumentException("weather_size");
    JSONObject j=new JSONObject(body);
    if(j.has("properties"))return parseMet(place,j,fetchedAt);
    JSONObject c=j.getJSONObject("current"), u=j.getJSONObject("current_units");
    if(!"°C".equals(u.optString("temperature_2m")) || !"m/s".equals(u.optString("wind_speed_10m"))
        || !"unixtime".equals(u.optString("time"))) throw new IllegalArgumentException("weather_units");
    Double temp=number(c,"temperature_2m",-100,65);
    Double time=number(c,"time",1,100000000000L), code=number(c,"weather_code",0,99), day=number(c,"is_day",0,1);
    if(temp==null||time==null||code==null||day==null||code!=Math.rint(code)||day!=Math.rint(day))
      throw new IllegalArgumentException("weather_current");
    long[] dates=new long[0]; double[] lows=new double[0], highs=new double[0];
    // A broken optional forecast must not erase a good current reading.
    try {
      JSONObject d=j.getJSONObject("daily"); JSONArray ts=d.getJSONArray("time"), lo=d.getJSONArray("temperature_2m_min"), hi=d.getJSONArray("temperature_2m_max");
      int n=Math.min(3,ts.length());
      if(lo.length()<n||hi.length()<n) throw new IllegalArgumentException();
      long[] ds=new long[n]; double[] ls=new double[n], hs=new double[n];
      for(int i=0;i<n;i++) {
        ds[i]=(long)required(ts.get(i),1,100000000000L)*1000;
        ls[i]=required(lo.get(i),-100,65); hs[i]=required(hi.get(i),-100,65);
        if(ls[i]>hs[i]||(i>0&&ds[i]<=ds[i-1]))throw new IllegalArgumentException();
      }
      dates=ds;lows=ls;highs=hs;
    } catch(Exception ignored) {}
    return new Reading(place,time.longValue()*1000,fetchedAt,temp,number(c,"apparent_temperature",-120,85),
      number(c,"wind_speed_10m",0,150),number(c,"relative_humidity_2m",0,100),code.intValue(),day==1,dates,lows,highs);
  }
  private static long iso(String value) throws Exception {
    SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.ROOT);f.setTimeZone(TimeZone.getTimeZone("UTC"));f.setLenient(false);
    java.text.ParsePosition pos=new java.text.ParsePosition(0);Date date=f.parse(value,pos);
    if(date==null||pos.getIndex()!=value.length())throw new IllegalArgumentException("weather_time");return date.getTime();
  }
  private static Reading parseMet(Place place,JSONObject j,long now) throws Exception {
    JSONObject p=j.getJSONObject("properties"),meta=p.getJSONObject("meta"),units=meta.getJSONObject("units");
    if(!"celsius".equals(units.optString("air_temperature"))||!"m/s".equals(units.optString("wind_speed")))throw new IllegalArgumentException("weather_units");
    long updated=iso(meta.getString("updated_at"));
    if(updated>now+20*MINUTE||updated<now-48*60*MINUTE)throw new IllegalArgumentException("weather_old_model");
    JSONArray times=p.getJSONArray("timeseries");JSONObject chosen=null;long selected=0;
    SimpleDateFormat dayFormat=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);dayFormat.setTimeZone(TimeZone.getTimeZone(place.timezone));
    TreeMap<String,double[]> days=new TreeMap<>();
    for(int i=0;i<times.length()&&i<300;i++){
      JSONObject t=times.getJSONObject(i);long at=iso(t.getString("time"));
      if(at<=now && at>selected){selected=at;chosen=t.getJSONObject("data");}
      if(at<now-24*60*MINUTE||at>now+72*60*MINUTE)continue;
      Double temp=number(t.getJSONObject("data").getJSONObject("instant").getJSONObject("details"),"air_temperature",-100,65);
      if(temp==null)continue;
      String date=dayFormat.format(new Date(at));double[] range=days.get(date);
      if(range==null)days.put(date,new double[]{at,temp,temp});else{range[1]=Math.min(range[1],temp);range[2]=Math.max(range[2],temp);}
    }
    if(chosen==null||now-selected>2*60*MINUTE)throw new IllegalArgumentException("weather_no_current_forecast");
    JSONObject detail=chosen.getJSONObject("instant").getJSONObject("details");Double temp=number(detail,"air_temperature",-100,65);
    if(temp==null)throw new IllegalArgumentException("weather_current");
    String symbol="";
    for(String step:new String[]{"next_1_hours","next_6_hours","next_12_hours"}){
      JSONObject next=chosen.optJSONObject(step);if(next!=null&&next.optJSONObject("summary")!=null){symbol=next.getJSONObject("summary").optString("symbol_code","");if(!symbol.isEmpty())break;}
    }
    if(symbol.isEmpty())throw new IllegalArgumentException("weather_symbol");
    int n=days.size(),i=0;long[] dates=new long[n];double[] lows=new double[n],highs=new double[n];
    for(double[] d:days.values()){dates[i]=(long)d[0];lows[i]=d[1];highs[i]=d[2];i++;}
    Reading result=new Reading(place,selected,now,temp,null,number(detail,"wind_speed",0,150),number(detail,"relative_humidity",0,100),metCode(symbol),!symbol.endsWith("_night"),dates,lows,highs);
    result.source="MET Norway";return result;
  }
  static int metCode(String s){
    if(s.contains("thunder"))return 95;
    if(s.contains("sleet"))return 69;
    if(s.contains("snow"))return s.startsWith("heavy")?75:s.startsWith("light")?71:73;
    if(s.contains("rain"))return s.contains("showers")?80:s.startsWith("heavy")?65:s.startsWith("light")?61:63;
    if(s.startsWith("clearsky"))return 0;if(s.startsWith("fair"))return 1;if(s.startsWith("partlycloudy"))return 2;
    if(s.startsWith("cloudy"))return 3;if(s.startsWith("fog"))return 45;return 100;
  }
  private static double required(Object o,double lo,double hi) {
    if(!(o instanceof Number))throw new IllegalArgumentException();
    double n=((Number)o).doubleValue();
    if(Double.isNaN(n)||Double.isInfinite(n)||n<lo||n>hi)throw new IllegalArgumentException();return n;
  }
  private static Double number(JSONObject j,String key,double lo,double hi) {
    try{return required(j.get(key),lo,hi);}catch(Exception e){return null;}
  }
  static String degrees(double n) {
    long rounded=Math.round(n);return (rounded>0?"+":rounded<0?"−":"")+Math.abs(rounded)+"°";
  }
  static String condition(int c,boolean day) {
    switch(c) {
      case 0:return day?"Ясно":"Ясная ночь";
      case 1:return "Малооблачно"; case 2:return "Переменная облачность";case 3:return "Пасмурно";
      case 45:case 48:return "Туман";
      case 51:case 53:case 55:return "Морось";case 56:case 57:return "Ледяная морось";
      case 61:return "Небольшой дождь";case 63:return "Дождь";case 65:return "Сильный дождь";
      case 66:case 67:return "Ледяной дождь";
      case 69:return "Мокрый снег";
      case 71:return "Небольшой снег";case 73:return "Снег";case 75:return "Сильный снег";case 77:return "Снежные зёрна";
      case 80:case 81:case 82:return "Ливень";case 85:case 86:return "Снегопад";
      case 95:return "Гроза";case 96:case 99:return "Гроза с градом";default:return "Погода";
    }
  }
  static boolean ambientValid(float value,int accuracy) {
    return accuracy>0 && !Float.isNaN(value) && !Float.isInfinite(value) && value>=-40 && value<=85;
  }
}
