package common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class CommonUtils {

    public static final long HOUR_MS = 1000L * 60L * 60L;

    private CommonUtils() {
    }

    public static boolean isEmpty(String str) {
        return (str == null || str.trim().equals(""));
    }

    public static String prettyPrint(long num) {
        return String.format(Locale.ITALY, "%,d", num);
    }

    public static String smartElapsed(long elapsedNano) {
        return smartElapsed(elapsedNano, 2);
    }

    public static String smartElapsed(long elapsedNano, int maxScale) {
        if (elapsedNano < TimeUnit.MICROSECONDS.toNanos(1)) {
            return Long.toString(elapsedNano) + " nsec";
        } else if (elapsedNano < TimeUnit.MILLISECONDS.toNanos(1)) {
            return BigDecimal.valueOf(elapsedNano).divide(BigDecimal.valueOf(TimeUnit.MICROSECONDS.toNanos(1)), Math.min((elapsedNano < TimeUnit.MICROSECONDS.toNanos(10) ? 2 : 1), maxScale), RoundingMode.HALF_UP).toString() + " usec";
        } else if (elapsedNano < TimeUnit.SECONDS.toNanos(1)) {
            return BigDecimal.valueOf(elapsedNano).divide(BigDecimal.valueOf(TimeUnit.MILLISECONDS.toNanos(1)), Math.min((elapsedNano < TimeUnit.MILLISECONDS.toNanos(10) ? 2 : 1), maxScale), RoundingMode.HALF_UP).toString() + " msec";
        } else if (elapsedNano < TimeUnit.MINUTES.toNanos(1)) {
            return BigDecimal.valueOf(elapsedNano).divide(BigDecimal.valueOf(TimeUnit.SECONDS.toNanos(1)), Math.min((elapsedNano < TimeUnit.SECONDS.toNanos(10) ? 2 : 1), maxScale), RoundingMode.HALF_UP).toString() + " sec";
        } else if (elapsedNano < TimeUnit.HOURS.toNanos(1)) {
            return BigDecimal.valueOf(elapsedNano).divide(BigDecimal.valueOf(TimeUnit.MINUTES.toNanos(1)), Math.min((elapsedNano < TimeUnit.MINUTES.toNanos(10) ? 2 : 1), maxScale), RoundingMode.HALF_UP).toString() + " min";
        } else {
            return BigDecimal.valueOf(elapsedNano).divide(BigDecimal.valueOf(TimeUnit.HOURS.toNanos(1)), Math.min((elapsedNano < TimeUnit.HOURS.toNanos(10) ? 2 : 1), maxScale), RoundingMode.HALF_UP).toString() + " hours";
        }
    }

}
