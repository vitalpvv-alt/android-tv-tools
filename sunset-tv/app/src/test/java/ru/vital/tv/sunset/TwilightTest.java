package ru.vital.tv.sunset;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import static org.junit.Assert.*;

public final class TwilightTest {
    @Test public void defaultStepsAreOneHundredSeventyFiveFiftyTwentyFiveAndZero() {
        Plan p=Plan.defaults();assertTrue(p.twilight);
        int[] levels={0,25,50,75,100};
        for(int stage=0;stage<=4;stage++)assertEquals(levels[stage],p.levelForStage(stage));
    }
    @Test public void customDayAndNightLevelsInterpolateWithoutChangingTheEndpoints() {
        Plan p=new Plan("Находка",42.824,132.892,"Asia/Vladivostok",80,20);
        int[] levels={20,35,50,65,80};
        for(int stage=0;stage<=4;stage++)assertEquals(levels[stage],p.levelForStage(stage));
        Plan off=p.withTwilight(false);assertFalse(off.twilight);assertEquals(p.city,off.city);
        assertEquals(80,off.dayLevel);assertEquals(20,off.nightLevel);assertEquals(p.zone,off.zone);
    }
    @Test public void sunsetIsIntermediateAndZeroStartsAfterCivilTwilight() {
        Plan p=Plan.defaults();Solar.Day d=Solar.on(LocalDate.of(2026,11,14),p.latitude,p.longitude);
        long sunset=d.sunset.toEpochMilli();
        assertEquals(50,p.at(sunset).level);assertEquals("Вечерние сумерки",p.at(sunset).label);
        assertEquals(0,p.at(Instant.parse("2026-11-14T09:00:00Z").toEpochMilli()).level);
        assertEquals(50,p.at(d.sunrise.toEpochMilli()).level);
        assertEquals("Утренние сумерки",p.at(d.sunrise.toEpochMilli()).label);
        assertEquals(0,p.withTwilight(false).at(sunset+1000).level);
    }
    @Test public void thresholdCrossingsMatchAnIndependentOracleAndSwitchInBothDirections() throws Exception {
        int count=0;
        try(BufferedReader r=new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/twilight-oracle.csv")),StandardCharsets.UTF_8))){
            String line;
            while((line=r.readLine())!=null){
                if(line.startsWith("#")||line.startsWith("city|"))continue;
                String[] v=line.split("\\|");double altitude=Double.parseDouble(v[5]);
                Plan p=new Plan(v[0],Double.parseDouble(v[1]),Double.parseDouble(v[2]),v[3],100,0);
                long rise=Long.parseLong(v[6]),set=Long.parseLong(v[7]);int lower=(int)((altitude+6)/4);
                assertEquals(v[0]+" rising before "+v[4],lower*25,p.at(rise-240_000).level);
                assertEquals(v[0]+" rising after "+v[4],(lower+1)*25,p.at(rise+240_000).level);
                assertEquals(v[0]+" setting before "+v[4],(lower+1)*25,p.at(set-240_000).level);
                assertEquals(v[0]+" setting after "+v[4],lower*25,p.at(set+240_000).level);
                Plan.Now next=p.at(rise-240_000);assertEquals(rise,next.nextAt,120_000);assertEquals((lower+1)*25,next.nextLevel);
                next=p.at(set-240_000);assertEquals(set,next.nextAt,120_000);assertEquals(lower*25,next.nextLevel);
                assertEquals(altitude,Solar.elevation(rise,p.latitude,p.longitude),0.04);
                assertEquals(altitude,Solar.elevation(set,p.latitude,p.longitude),0.04);
                count++;
            }
        }
        assertEquals(672,count);
    }
    @Test public void malformedElevationsCannotCreateAPlan() {
        assertThrows(IllegalArgumentException.class,()->Solar.on(LocalDate.of(2026,11,14),42,132,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->Solar.on(LocalDate.of(2026,11,14),42,132,90));
    }
}
