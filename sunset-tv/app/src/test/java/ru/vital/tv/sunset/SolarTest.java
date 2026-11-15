package ru.vital.tv.sunset;

import org.junit.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.Assert.*;

public final class SolarTest {
    private static long at(String s){return Instant.parse(s).toEpochMilli();}
    @Test public void defaultsRespectTheRequestedZeroAtNight() {
        Plan p=Plan.defaults();assertEquals(0,p.nightLevel);assertEquals(100,p.dayLevel);
        assertEquals("Asia/Vladivostok",p.zone.getId());
        assertEquals(0,p.at(at("2026-11-14T12:00:00Z")).level);
        assertEquals(100,p.at(at("2026-11-14T03:00:00Z")).level);
    }
    @Test public void boundariesBelongToTheNewPeriod() {
        Plan p=Plan.defaults().withTwilight(false);Solar.Day d=Solar.on(LocalDate.of(2026,11,14),p.latitude,p.longitude);
        long rise=d.sunrise.toEpochMilli(),set=d.sunset.toEpochMilli();
        assertTrue(p.at(rise-1).night);assertFalse(p.at(rise).night);
        assertFalse(p.at(set-1).night);assertTrue(p.at(set).night);
        assertEquals(set,p.at(rise).nextAt);
    }
    @Test public void nightSpansMidnightAndRecalculatesForTheNextDate() {
        Plan p=Plan.defaults();long now=ZonedDateTime.of(2026,11,15,0,1,0,0,p.zone).toInstant().toEpochMilli();
        Plan.Now n=p.at(now);assertTrue(n.night);assertTrue(n.nextAt>now);
        assertEquals(LocalDate.of(2026,11,15),Instant.ofEpochMilli(n.nextAt).atZone(p.zone).toLocalDate());
    }
    @Test public void polarNightsAndDaysDoNotCrashOrInventSunrises() {
        Solar.Day winter=Solar.on(LocalDate.of(2026,12,21),78.22,15.64);
        Solar.Day summer=Solar.on(LocalDate.of(2026,6,21),78.22,15.64);
        assertNull(winter.sunrise);assertNull(winter.sunset);assertFalse(winter.polarDay);
        assertNull(summer.sunrise);assertNull(summer.sunset);assertTrue(summer.polarDay);
        Plan polar=new Plan("Longyearbyen",78.22,15.64,"Arctic/Longyearbyen",100,0);
        assertEquals(0,polar.at(at("2026-12-21T12:00:00Z")).level);
        assertEquals(100,polar.at(at("2026-06-21T12:00:00Z")).level);
    }
    @Test public void timeZoneDoesNotShiftAstronomicalEventsAtTheDateLine() {
        Plan p=new Plan("Kiritimati",1.872,-157.427,"Pacific/Kiritimati",100,0);
        long noon=ZonedDateTime.of(2026,11,14,12,0,0,0,p.zone).toInstant().toEpochMilli();
        assertFalse(p.at(noon).night);
        assertEquals(LocalDate.of(2026,11,14),p.at(noon).today.sunrise.atZone(p.zone).toLocalDate());
        long midnight=ZonedDateTime.of(2026,11,14,0,0,0,0,p.zone).toInstant().toEpochMilli();
        assertTrue(p.at(midnight).night);assertTrue(p.at(noon).nextAt>noon);
    }
    @Test public void daylightSavingChangesUseInstantsInsteadOfAddingTwentyFourHours() {
        Plan p=new Plan("New York",40.713,-74.006,"America/New_York",100,0);
        for(String s:new String[]{"2026-03-08T06:59:59Z","2026-03-08T07:00:00Z","2026-11-01T05:59:59Z","2026-11-01T06:00:00Z"}){
            Plan.Now n=p.at(at(s));assertTrue(n.night);assertTrue(n.nextAt>at(s));
            assertEquals(0,n.level);
        }
    }
    @Test public void locationsAndLevelsAreValidatedIncludingNonfiniteValues() {
        assertThrows(IllegalArgumentException.class,()->new Plan("bad",Double.NaN,132,"UTC",100,0));
        assertThrows(IllegalArgumentException.class,()->new Plan("bad",42,Double.POSITIVE_INFINITY,"UTC",100,0));
        assertThrows(IllegalArgumentException.class,()->Solar.on(LocalDate.now(),90,0));
        Plan p=new Plan("clamp",42,132,"Asia/Vladivostok",-1,105);assertEquals(0,p.dayLevel);assertEquals(0,p.nightLevel);
    }
    @Test public void seasonalDayLengthsArePlausibleAndEveryBoundaryIsInTheFuture() {
        Plan p=Plan.defaults();
        for(int day=0;day<366;day++) {
            LocalDate date=LocalDate.of(2026,1,1).plusDays(day);Solar.Day sun=Solar.on(date,p.latitude,p.longitude);
            long duration=sun.sunset.toEpochMilli()-sun.sunrise.toEpochMilli();assertTrue(duration>8*3600000L&&duration<16*3600000L);
            for(int hour:new int[]{0,6,12,18,23}) {
                long now=date.atTime(hour,0).atZone(p.zone).toInstant().toEpochMilli();
                Plan.Now n=p.at(now);assertTrue(n.nextAt>now);assertTrue(n.nextAt-now<25*3600000L);
            }
        }
    }
    @Test public void noaaBoulderSummerReferenceWithinFiveMinutes() {
        Solar.Day d=Solar.on(LocalDate.of(2026,6,21),40.015,-105.2705);
        ZoneId zone=ZoneId.of("America/Denver");
        int rise=d.sunrise.atZone(zone).getHour()*60+d.sunrise.atZone(zone).getMinute();
        int set=d.sunset.atZone(zone).getHour()*60+d.sunset.atZone(zone).getMinute();
        assertEquals(332,rise,5);assertEquals(1232,set,5);
    }
}
