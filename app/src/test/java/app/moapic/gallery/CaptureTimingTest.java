package app.moapic.gallery;

import org.junit.Test;
import static org.junit.Assert.*;

public class CaptureTimingTest {
    @Test public void displaysSevenThroughOneWithoutZeroBeforeDeadline(){
        long start=10000, deadline=start+7000;
        for(int elapsed=0;elapsed<7000;elapsed++)assertEquals(7-elapsed/1000,CaptureTiming.secondsRemaining(deadline,start+elapsed));
    }
    @Test public void lateCallbacksUseActualTimeRatherThanTickCount(){
        assertEquals(4,CaptureTiming.secondsRemaining(17000,13900));
        assertEquals(1,CaptureTiming.secondsRemaining(17000,16999));
        assertEquals(0,CaptureTiming.secondsRemaining(17000,17000));
        assertEquals(0,CaptureTiming.secondsRemaining(17000,22000));
    }
}
