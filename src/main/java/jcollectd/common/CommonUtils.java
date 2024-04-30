package jcollectd.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

public class CommonUtils {

    public static final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();

    private CommonUtils() {
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isBlank();
    }

    public static String smartElapsed(long elapsedNano) {
        return smartElapsed(elapsedNano, 1);
    }

    public static String smartElapsed(long elapsedNano, int scale) {
        if (elapsedNano < TimeUnit.MICROSECONDS.toNanos(1)) {
            return elapsedNano + " nsec";
        } else if (elapsedNano < TimeUnit.MILLISECONDS.toNanos(1)) {
            return BigDecimal.valueOf(elapsedNano).divide(BigDecimal.valueOf(TimeUnit.MICROSECONDS.toNanos(1)), scale, RoundingMode.HALF_UP) + " usec";
        } else if (elapsedNano < TimeUnit.SECONDS.toNanos(1)) {
            return BigDecimal.valueOf(elapsedNano).divide(BigDecimal.valueOf(TimeUnit.MILLISECONDS.toNanos(1)), scale, RoundingMode.HALF_UP) + " msec";
        } else if (elapsedNano < TimeUnit.MINUTES.toNanos(1)) {
            return BigDecimal.valueOf(elapsedNano).divide(BigDecimal.valueOf(TimeUnit.SECONDS.toNanos(1)), scale, RoundingMode.HALF_UP) + " sec";
        } else if (elapsedNano < TimeUnit.HOURS.toNanos(1)) {
            return BigDecimal.valueOf(elapsedNano).divide(BigDecimal.valueOf(TimeUnit.MINUTES.toNanos(1)), scale, RoundingMode.HALF_UP) + " min";
        } else {
            return BigDecimal.valueOf(elapsedNano).divide(BigDecimal.valueOf(TimeUnit.HOURS.toNanos(1)), scale, RoundingMode.HALF_UP) + " hours";
        }
    }

}
