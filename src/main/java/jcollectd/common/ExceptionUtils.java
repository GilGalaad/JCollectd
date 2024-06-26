package jcollectd.common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class ExceptionUtils {

    private static final String ROOT_PACKAGE = ExceptionUtils.class.getCanonicalName().substring(0, ExceptionUtils.class.getCanonicalName().indexOf("."));

    private ExceptionUtils() {
    }

    public static String getCanonicalFormWithoutCause(Throwable throwable) {
        return throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
    }

    public static String getCanonicalFormWithCause(Throwable throwable, Throwable rootCause) {
        return getCanonicalFormWithoutCause(throwable) + " [caused by " + getCanonicalFormWithoutCause(rootCause) + "]";
    }

    public static String getCanonicalForm(Throwable throwable) {
        if (throwable.getCause() == null) {
            return getCanonicalFormWithoutCause(throwable);
        }
        Throwable rootCause = getRootCause(throwable);
        if (rootCause != null && rootCause != throwable) {
            return getCanonicalFormWithCause(throwable, rootCause);
        }
        return getCanonicalFormWithoutCause(throwable);
    }

    public static String getCanonicalFormWithStackTrace(Throwable throwable) {
        return getCanonicalForm(throwable) + "\n" + getRelevantStackTrace(throwable);
    }

    public static List<Throwable> getThrowableList(Throwable throwable) {
        List<Throwable> list = new LinkedList<>();
        while (throwable != null && !list.contains(throwable)) {
            list.add(throwable);
            throwable = throwable.getCause();
        }
        return list.isEmpty() ? Collections.emptyList() : list;
    }

    public static Throwable getRootCause(Throwable throwable) {
        List<Throwable> list = getThrowableList(throwable);
        return list.isEmpty() ? null : list.getLast();
    }

    public static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    public static List<String> getStackTraceLines(Throwable throwable) {
        return Arrays.asList(getStackTrace(throwable).split("\\r?\\n"));
    }

    public static String getRelevantStackTrace(Throwable throwable) {
        List<String> lines = getStackTraceLines(throwable);
        return lines.stream().filter(l -> isRelevantLine(l, ROOT_PACKAGE)).collect(Collectors.joining("\n"));
    }

    public static String getRelevantStackTrace(Throwable throwable, String prefix) {
        List<String> lines = getStackTraceLines(throwable);
        return lines.stream().filter(l -> isRelevantLine(l, prefix)).collect(Collectors.joining("\n"));
    }

    public static String getRelevantStackTrace(Throwable throwable, List<String> prefixes) {
        List<String> lines = getStackTraceLines(throwable);
        return lines.stream().filter(l -> prefixes.stream().anyMatch(pr -> isRelevantLine(l, pr))).collect(Collectors.joining("\n"));
    }

    private static boolean isRelevantLine(String line, String prefix) {
        return !line.startsWith("\t") || (!line.contains("$") && (line.contains("at " + prefix + ".") || (line.contains("at ") && line.contains("/" + prefix + "."))));
    }

}
