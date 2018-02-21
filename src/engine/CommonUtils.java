package engine;

import java.util.Locale;

public class CommonUtils {

    public static final long HOUR_MS = 1000L * 60L * 60L;

    public static boolean isEmpty(String str) {
        return (str == null || str.trim().equals(""));
    }

    public static String prettyPrint(long num) {
        return String.format(Locale.ITALY, "%,d", num);
    }

}
