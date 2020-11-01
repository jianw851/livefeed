package util;

public class LogUtils {
    public static String getStackTrace(Exception e) {
        StringBuilder stackTrace = new StringBuilder();
        StackTraceElement[] ele = e.getStackTrace();
        if(e.getCause() != null) {
            stackTrace.append(e.getCause().toString());
            ele = e.getCause().getStackTrace();
        } else {
            stackTrace.append(e.toString());
        }
        stackTrace.append("\n");
        for(StackTraceElement o : ele) {
            stackTrace.append(o.toString());
            stackTrace.append("\n");
        }
        return stackTrace.toString();
    }

}
