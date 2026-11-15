package ru.vital.tv.sunset;
import org.junit.Test;
import static org.junit.Assert.*;

public final class RateGuardTest {
    @Test public void stopsRapidFirmwareConflictsAndRecoversAfterTheWindow() {
        RateGuard guard=new RateGuard();assertTrue(guard.permit(1000));assertTrue(guard.permit(2000));assertTrue(guard.permit(3000));
        assertFalse(guard.permit(4000));assertFalse(guard.permit(30_999));assertTrue(guard.permit(31_000));
    }
    @Test public void normalProfileChangesAreAllowedOverTime() {
        RateGuard guard=new RateGuard();for(long time=0;time<3_600_000;time+=60_000)assertTrue(guard.permit(time));
    }
}
