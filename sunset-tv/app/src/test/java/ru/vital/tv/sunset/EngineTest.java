package ru.vital.tv.sunset;

import org.junit.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public final class EngineTest {
    private static final long DAY=Instant.parse("2026-11-14T03:00:00Z").toEpochMilli();
    private static final long NIGHT=Instant.parse("2026-11-14T12:00:00Z").toEpochMilli();
    private static final class Fixture implements Engine.Store,Engine.Panel,Engine.Alarms {
        Engine.State state=new Engine.State(); int current=100;boolean allowed=true,failWrite=false,failSave=false,failAlarm=false;
        long automatic,rollback;List<String> operations=new ArrayList<>();Engine e=new Engine(this,this,this);
        @Override public Engine.State load(){return state.copy();}
        @Override public void save(Engine.State s){if(failSave)throw new IllegalStateException("disk full");state=s.copy();operations.add("save");}
        @Override public void requireReady(){if(!allowed)throw new SecurityException("no permission");}
        @Override public int read(){return current;}
        @Override public void write(int v){requireReady();if(failWrite)throw new IllegalStateException("provider unavailable");current=v;operations.add("panel:"+v);}
        @Override public void automatic(long at){if(failAlarm)throw new IllegalStateException("no alarm");automatic=at;operations.add("auto");}
        @Override public void rollback(long at){if(failAlarm)throw new IllegalStateException("no alarm");rollback=at;operations.add("rollback");}
        @Override public void cancelAutomatic(){automatic=0;}
        @Override public void cancelRollback(){rollback=0;}
        void verified(){state.verified=true;}
    }
    @Test public void zeroNightHasDurableBackupAndAlarmBeforeThePanelChanges() {
        Fixture f=new Fixture();f.e.startTest(Plan.defaults(),DAY);
        assertEquals(Integer.valueOf(100),f.state.testOriginal);assertEquals(0,f.current);
        assertEquals(DAY+10_000,f.rollback);
        assertTrue(f.operations.indexOf("save")<f.operations.indexOf("rollback"));
        assertTrue(f.operations.indexOf("rollback")<f.operations.indexOf("panel:0"));
    }
    @Test public void testRollsBackAfterProcessRecreationAndNeedsHumanConfirmation() {
        Fixture f=new Fixture();f.e.startTest(Plan.defaults(),DAY);
        f.e=new Engine(f,f,f);f.e.tick(Plan.defaults(),DAY+10_000,false);
        assertEquals(100,f.current);assertNull(f.state.testOriginal);assertTrue(f.state.testSucceeded);assertFalse(f.state.verified);
        f.e.confirmTest();assertTrue(f.state.verified);
    }
    @Test public void testDoesNotBecomePermanentUntilConfirmed() {
        Fixture f=new Fixture();f.e.startTest(Plan.defaults(),DAY);
        assertThrows(IllegalStateException.class,()->f.e.enable(Plan.defaults(),DAY+100));
        assertFalse(f.state.enabled);
    }
    @Test public void bootAndBackwardClockChangeImmediatelyUndoShortTests() {
        Fixture f=new Fixture();f.e.startTest(Plan.defaults(),DAY);
        f.e.tick(Plan.defaults(),DAY-3_600_000,true);
        assertEquals(100,f.current);assertFalse(f.state.testSucceeded);assertNull(f.state.testOriginal);
    }
    @Test public void missingPermissionNeverMutatesPanelOrCreatesAnAlarm() {
        Fixture f=new Fixture();f.allowed=false;
        assertThrows(SecurityException.class,()->f.e.startTest(Plan.defaults(),DAY));
        assertEquals(100,f.current);assertEquals(0,f.rollback);assertNull(f.state.testOriginal);
    }
    @Test public void failedBackupNeverDarkensThePanel() {
        Fixture f=new Fixture();f.failSave=true;
        assertThrows(IllegalStateException.class,()->f.e.startTest(Plan.defaults(),DAY));
        assertEquals(100,f.current);assertEquals(0,f.rollback);
    }
    @Test public void missingReturnAlarmPreventsDarkening() {
        Fixture f=new Fixture();f.failAlarm=true;
        assertThrows(IllegalStateException.class,()->f.e.startTest(Plan.defaults(),DAY));
        assertEquals(100,f.current);assertNull(f.state.testOriginal);assertFalse(f.state.testSucceeded);
    }
    @Test public void manualChangeDuringATestIsPreservedAndDoesNotPassTheTest() {
        Fixture f=new Fixture();f.e.startTest(Plan.defaults(),DAY);f.current=60;
        f.e.tick(Plan.defaults(),DAY+10_000,false);
        assertEquals(60,f.current);assertFalse(f.state.testSucceeded);
    }
    @Test public void sunsetZeroAndSunriseDayKeepTheOriginalSnapshot() {
        Fixture f=new Fixture();f.verified();f.current=78;
        f.e.enable(Plan.defaults(),NIGHT);assertEquals(0,f.current);assertEquals(Integer.valueOf(78),f.state.original);
        f.e.tick(Plan.defaults(),DAY+86_400_000,false);assertEquals(100,f.current);
        assertEquals(Integer.valueOf(78),f.state.original);assertTrue(f.automatic>DAY+86_400_000);
        f.e.disable();assertEquals(78,f.current);assertFalse(f.state.enabled);assertEquals(0,f.automatic);assertNull(f.state.original);
    }
    @Test public void openingAnExistingEnabledSessionDoesNotReplaceItsOriginalWithZero() {
        Fixture f=new Fixture();f.verified();f.e.enable(Plan.defaults(),NIGHT);
        f.e.enable(Plan.defaults(),NIGHT+1000);assertEquals(Integer.valueOf(100),f.state.original);
        f.e.disable();assertEquals(100,f.current);
    }
    @Test public void failedRestoreRemainsRecoverableAcrossAReboot() {
        Fixture f=new Fixture();f.verified();f.e.enable(Plan.defaults(),NIGHT);f.failWrite=true;
        assertThrows(IllegalStateException.class,()->f.e.disable());
        assertFalse(f.state.enabled);assertTrue(f.state.restorePending);assertEquals(Integer.valueOf(100),f.state.original);
        f.failWrite=false;f.e=new Engine(f,f,f);f.e.tick(Plan.defaults(),DAY,true);
        assertEquals(100,f.current);assertFalse(f.state.restorePending);assertNull(f.state.original);
    }
    @Test public void failedAutomaticApplyStopsAndPreservesRestorationData() {
        Fixture f=new Fixture();f.verified();f.failWrite=true;
        assertThrows(IllegalStateException.class,()->f.e.enable(Plan.defaults(),NIGHT));
        assertFalse(f.state.enabled);assertTrue(f.state.restorePending);assertEquals(Integer.valueOf(100),f.state.original);
    }
    @Test public void disabledBootDoesNotRewriteAnOwnersManualBrightness() {
        Fixture f=new Fixture();f.current=44;f.e.tick(Plan.defaults(),NIGHT,true);
        assertEquals(44,f.current);assertEquals(0,f.automatic);assertTrue(f.operations.isEmpty());
    }
    @Test public void futureBoundaryIsScheduledBeforeTheAutomaticWrite() {
        Fixture f=new Fixture();f.verified();f.e.enable(Plan.defaults(),NIGHT);
        assertTrue(f.operations.indexOf("auto")<f.operations.indexOf("panel:0"));
    }
    @Test public void upgradeKeepsTheOriginalSnapshotWhenApplyingIntermediateLevels() {
        Fixture f=new Fixture();f.verified();f.current=86;f.e.enable(Plan.defaults().withTwilight(false),DAY);
        assertEquals(Integer.valueOf(86),f.state.original);
        long dusk=Instant.parse("2026-11-14T07:40:00Z").toEpochMilli();
        f.e=new Engine(f,f,f);f.e.tick(Plan.defaults(),dusk,true);
        assertEquals(50,f.current);assertEquals(Integer.valueOf(86),f.state.original);
        f.e.disable();assertEquals(86,f.current);
    }
}
