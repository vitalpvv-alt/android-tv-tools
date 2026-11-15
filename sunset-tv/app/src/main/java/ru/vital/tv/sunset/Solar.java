package ru.vital.tv.sunset;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Apparent sunrise/sunset, altitude -0.833 degrees, Meeus/NOAA equations. East-positive longitude. */
public final class Solar {
    private Solar() {}
    public static final class Day {
        public final Instant sunrise, sunset;
        public final boolean polarDay;
        Day(Instant rise, Instant set, boolean light) { sunrise=rise; sunset=set; polarDay=light; }
    }
    private static double rad(double a) { return Math.toRadians(a); }
    private static double norm(double a) { return (a % 360 + 360) % 360; }
    // Return equation of time in minutes and declination in radians.
    private static double[] position(double julian) {
        double t=(julian-2451545.0)/36525.0;
        double l=norm(280.46646+t*(36000.76983+t*0.0003032));
        double m=357.52911+t*(35999.05029-0.0001537*t);
        double e=0.016708634-t*(0.000042037+0.0000001267*t);
        double c=Math.sin(rad(m))*(1.914602-t*(0.004817+0.000014*t))
                + Math.sin(rad(2*m))*(0.019993-0.000101*t)+Math.sin(rad(3*m))*0.000289;
        double omega=125.04-1934.136*t;
        double lambda=l+c-0.00569-0.00478*Math.sin(rad(omega));
        double ob=23+(26+(21.448-t*(46.815+t*(0.00059-t*0.001813)))/60)/60;
        ob+=0.00256*Math.cos(rad(omega));
        double decl=Math.asin(Math.sin(rad(ob))*Math.sin(rad(lambda)));
        double y=Math.pow(Math.tan(rad(ob)/2),2);
        double eq=y*Math.sin(2*rad(l))-2*e*Math.sin(rad(m))
                +4*e*y*Math.sin(rad(m))*Math.cos(2*rad(l))
                -0.5*y*y*Math.sin(4*rad(l))-1.25*e*e*Math.sin(2*rad(m));
        return new double[]{4*Math.toDegrees(eq),decl};
    }
    public static Day on(LocalDate date, double latitude, double longitude) {
        return on(date,latitude,longitude,-0.833);
    }
    /** Morning/evening crossings of a geometric solar altitude; -6 is the civil twilight limit. */
    public static Day on(LocalDate date,double latitude,double longitude,double altitude) {
        if (!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>89.0||Math.abs(longitude)>180)
            throw new IllegalArgumentException("Координаты вне допустимого диапазона");
        if(!Double.isFinite(altitude)||altitude<=-90||altitude>=90)throw new IllegalArgumentException("Некорректная высота солнца");
        long midnight=date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        double jd=midnight/86400000.0+2440587.5;
        double noon=720-4*longitude;
        for(int n=0;n<3;n++) noon=720-4*longitude-position(jd+noon/1440)[0];
        double[] p=position(jd+noon/1440);
        double cos=(Math.sin(rad(altitude))-Math.sin(rad(latitude))*Math.sin(p[1]))
                /(Math.cos(rad(latitude))*Math.cos(p[1]));
        if(cos>1) return new Day(null,null,false);
        if(cos< -1) return new Day(null,null,true);
        return new Day(event(midnight,jd,latitude,longitude,true,noon,altitude),
                event(midnight,jd,latitude,longitude,false,noon,altitude),false);
    }
    public static double elevation(long millis,double latitude,double longitude) {
        if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||Math.abs(latitude)>89||Math.abs(longitude)>180)
            throw new IllegalArgumentException("Координаты вне допустимого диапазона");
        double[] p=position(millis/86400000.0+2440587.5);
        double utcMinutes=Math.floorMod(millis,86_400_000L)/60_000.0;
        double solarMinutes=((utcMinutes+p[0]+4*longitude)%1440+1440)%1440;
        double hour=solarMinutes/4-180;
        double sine=Math.sin(rad(latitude))*Math.sin(p[1])+Math.cos(rad(latitude))*Math.cos(p[1])*Math.cos(rad(hour));
        return Math.toDegrees(Math.asin(Math.max(-1,Math.min(1,sine))));
    }
    private static Instant event(long midnight,double jd,double lat,double lon,boolean rise,double noon,double altitude) {
        double minutes=noon;
        for(int n=0;n<4;n++) {
            double[] p=position(jd+minutes/1440);
            double cos=(Math.sin(rad(altitude))-Math.sin(rad(lat))*Math.sin(p[1]))
                    /(Math.cos(rad(lat))*Math.cos(p[1]));
            if(cos>1||cos< -1) return null;
            double hour=Math.toDegrees(Math.acos(cos));
            minutes=720-4*lon-p[0]+(rise?-4:4)*hour;
        }
        return Instant.ofEpochMilli(midnight+Math.round(minutes*60000));
    }
}
