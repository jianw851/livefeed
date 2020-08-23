package util;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DateTimeUtils {
    public static ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("America/New_York"));
    public static long weekInSec = 604800;
    public static String defaultTimeZone = "America/New_York";

    public static String epochToDateTimeString(long epochTimeInSec) {
        Instant instant = Instant.ofEpochSecond(epochTimeInSec);
        ZonedDateTime date = ZonedDateTime.ofInstant(instant, ZoneId.of(defaultTimeZone));
        return date.toString();
    }
}
