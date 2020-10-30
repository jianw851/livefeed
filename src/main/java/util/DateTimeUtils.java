package util;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class DateTimeUtils {
    public static long weekInSec = 604800;
    public static String defaultTimeZone = "America/New_York";
    public static String utcTimeZone = "UTC";

    public static String epochToDateTimeString(long epochTimeInSec) {
        Instant instant = Instant.ofEpochSecond(epochTimeInSec);
        //ZoneId zone = ZoneId.of(defaultTimeZone);
        //ZonedDateTime zdt = instant.atZone(zone);
        //ZoneOffset offset = zdt.getOffset();
        ZonedDateTime date = ZonedDateTime.ofInstant(instant, ZoneId.of(defaultTimeZone));
        return date.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    public static String getCurrentTimeStringinUTC() {
        Instant now = Instant.now();
        ZonedDateTime date = ZonedDateTime.ofInstant(now, ZoneId.of(utcTimeZone));
        return date.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    /*
    input format: GMT-04:00 17:58
    output format: 2020-08-22T21:58:00Z[UTC]
    */
    public static String parseForeSignalTime(String inputString) {
        ZonedDateTime ret = DateTimeUtils.parseForeSignalTime2ZonedDateTime(inputString);
        // String format = ret.format(DateTimeFormatter.ofPattern("Y-M-D HH:mm:ss"));
        String format = ret.format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        return format;
    }

    /*
    input format: GMT-04:00 17:58
    output format: 16593434300
    */
    public static long parseForeSignalTimeEpoch(String inputString) {
        ZonedDateTime ret = DateTimeUtils.parseForeSignalTime2ZonedDateTime(inputString);
        return ret.toEpochSecond();
    }

    public static ZonedDateTime parseForeSignalTime2ZonedDateTime(String inputString) {
        Instant now = Instant.now();
        ZonedDateTime date = ZonedDateTime.ofInstant(now, ZoneId.of(utcTimeZone));
        String offset = inputString.split(" ")[0];
        String time = inputString.split(" ")[1];
        int hour = Integer.valueOf(time.split(":")[0]);
        int min = Integer.valueOf(time.split(":")[1]);
        int dayOffset = 0;
        if (offset.indexOf("-") > 0) {
            hour += Integer.valueOf(offset.substring(4, 6));
            if (hour >= 24) {
                hour = hour - 24;
                dayOffset += 1;
            }
        } else if (offset.indexOf("+") > 0) {
            hour -= Integer.valueOf(offset.substring(4, 6));
            if (hour < 0) {
                hour = 24 + hour;
                dayOffset -= 1;
            }
        }
        ZonedDateTime ret = date.with(LocalTime.of(hour, min, 0, 0));
        ret = ret.plusDays(dayOffset);
        return ret;
    }

    /*
    input: GMT-04:00 17:58, GMT-04:00 19:58
    output: 2
    */
    public static long diffForeSignalFromTillInSec(String from, String till) {
       ZonedDateTime fromTime = DateTimeUtils.parseForeSignalTime2ZonedDateTime(from);
       ZonedDateTime tillTime = DateTimeUtils.parseForeSignalTime2ZonedDateTime(till);
       long fromTimeInSec = fromTime.toEpochSecond();
       long tillTimeInSec = tillTime.toEpochSecond();
       long ret = tillTimeInSec - fromTimeInSec;
       if(fromTimeInSec > tillTimeInSec) {
           ret = tillTimeInSec + 86400l - fromTimeInSec;
       }
       return ret;
    }

    /*
    public static void main(String args[]) {
        long ret = DateTimeUtils.diffForeSignalFromTillInSec("GMT-04:00 22:00", "GMT-04:00 01:00");
        System.out.println(ret);
    }
    */
}
