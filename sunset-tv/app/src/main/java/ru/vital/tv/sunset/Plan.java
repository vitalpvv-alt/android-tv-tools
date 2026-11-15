package ru.vital.tv.sunset;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Plan {
    public final double latitude,longitude;
    public final String city;
    public final ZoneId zone;
    public final int dayLevel,nightLevel;
    public final boolean twilight;
    private static final double[] THRESHOLDS={-6,-2,2,6};
    public Plan(String city,double lat,double lon,String zone,int day,int night) {
        this(city,lat,lon,zone,day,night,true);
    }
    public Plan(String city,double lat,double lon,String zone,int day,int night,boolean twilight) {
        this.city=city;latitude=lat;longitude=lon;this.zone=ZoneId.of(zone);this.twilight=twilight;
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>89||Math.abs(lon)>180)
            throw new IllegalArgumentException("Проверь координаты города");
        dayLevel=Math.max(0,Math.min(100,day));nightLevel=Math.max(0,Math.min(dayLevel,night));
    }
    public static Plan defaults(){return new Plan("Находка",42.824,132.892,"Asia/Vladivostok",100,0,true);}
    public Plan withTwilight(boolean enabled){return new Plan(city,latitude,longitude,zone.getId(),dayLevel,nightLevel,enabled);}
    public int levelForStage(int stage){
        return Math.round(nightLevel+(dayLevel-nightLevel)*Math.max(0,Math.min(4,stage))/4f);
    }
    public static final class Now {
        public final boolean night;
        public final int level,nextLevel,stage;
        public final String label;
        public final long nextAt;
        public final Solar.Day today;
        Now(int stage,int level,int nextLevel,long next,Solar.Day today,String label){
            this.stage=stage;night=stage==0;this.level=level;this.nextLevel=nextLevel;nextAt=next;this.today=today;this.label=label;
        }
    }
    private static final class Event {
        final Instant at;final int stage;
        Event(Instant at,int stage){this.at=at;this.stage=stage;}
    }
    private Solar.Day day(LocalDate date,double altitude){
        long localNoon=date.atTime(12,0).atZone(zone).toInstant().toEpochMilli();
        long nominalNoon=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()+Math.round((720-4*longitude)*60_000);
        long shift=Math.round((localNoon-nominalNoon)/86_400_000.0);
        return Solar.on(date.plusDays(shift),latitude,longitude,altitude);
    }
    public Now at(long millis){
        Instant now=Instant.ofEpochMilli(millis);LocalDate date=now.atZone(zone).toLocalDate();
        Solar.Day today=day(date,-0.833);
        double altitude=Solar.elevation(millis,latitude,longitude);
        int stage=0;
        if(twilight){for(double threshold:THRESHOLDS)if(altitude>=threshold)stage++;}
        else stage=altitude>=-0.833?4:0;
        List<Event> events=new ArrayList<>();
        for(int n=-2;n<=2;n++){
            LocalDate on=date.plusDays(n);
            if(twilight){
                for(int index=0;index<THRESHOLDS.length;index++){
                    Solar.Day d=day(on,THRESHOLDS[index]);
                    if(d.sunrise!=null)events.add(new Event(d.sunrise,index+1));
                    if(d.sunset!=null)events.add(new Event(d.sunset,index));
                }
            }else{
                Solar.Day d=day(on,-0.833);
                if(d.sunrise!=null)events.add(new Event(d.sunrise,4));
                if(d.sunset!=null)events.add(new Event(d.sunset,0));
            }
        }
        events.sort(Comparator.comparing(e->e.at));
        long next=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        int nextStage=stage;boolean future=false;
        for(Event e:events){
            if(!e.at.isAfter(now))stage=e.stage;
            else if(!future){next=e.at.toEpochMilli();nextStage=e.stage;future=true;}
        }
        if(!future){next=date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();nextStage=stage;}
        if(next<=millis)next=millis+60_000;
        String label;
        if(stage==0)label="Ночной режим";
        else if(stage==4)label="Дневной режим";
        else label=Solar.elevation(millis+60_000,latitude,longitude)>Solar.elevation(millis-60_000,latitude,longitude)
                ?"Утренние сумерки":"Вечерние сумерки";
        return new Now(stage,levelForStage(stage),levelForStage(nextStage),next,today,label);
    }
}
