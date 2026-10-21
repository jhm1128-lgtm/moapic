package app.moapic.gallery;

/** Deadline-based clock so slow notification work cannot lengthen the countdown. */
public final class CaptureTiming {
    private CaptureTiming() {}
    public static int secondsRemaining(long deadlineMillis, long nowMillis) {
        long left=Math.max(0,deadlineMillis-nowMillis);
        return (int)Math.min(Integer.MAX_VALUE,(left+999)/1000);
    }
}
