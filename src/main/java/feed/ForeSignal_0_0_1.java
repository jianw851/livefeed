package feed;

import event.EventPublisher;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import util.DateTimeUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Thread.sleep;

public class ForeSignal_0_0_1 implements Feed {

    // logger
    private final static Logger logger = Logger.getLogger(ForeSignal_0_0_1.class);
    private static String TOPIC = null;
    private static String INSTRUMENT = null;
    private EventPublisher publisher = null;
    private final String baseUrl = "https://foresignal.com/en/signals/";
    private Map<String, Long> lastSignalDict = new HashMap<>();

    // app specific params
    private static ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of(DateTimeUtils.defaultTimeZone));
    private final ForeSignalAuth auth = new ForeSignalAuth();

    // html parser
    static class Parser {
        private static final String IDENTIFIER = "Filled ";
        private static Document doc = null;
        private static String title = null;
        private static String body = null;
        private static boolean canSend = false;

        /*
        CURRENCY|IDENTIFIED_TIME|DELIVER_TIME|BREAKOUT_PRICE|FORECAST_PRICE|STOPLOSS_PRICE|PROBABILITY|MIN_INTERVAL|MAX_INTERVAL|
         */
        static String Parse(String link, ForeSignalAuth auth, Map.Entry<String, Long> entry, Map<String, Long> map) throws Exception {
            doc = Jsoup.parse(auth.getTargetContent(link));
            body = doc.body().text();
            if(body.indexOf(IDENTIFIER) > 0) {
                canSend = false;
                //logger.info("Signal expired :" + entry.getKey());
                return "Signal Expired";
            }
            int fromIdx = body.indexOf("From GMT");
            int tillIdx = body.indexOf("Till GMT");
            String fromTimeString = body.substring(fromIdx+5, tillIdx-1);
            long currSignalIdentifiedTime = DateTimeUtils.parseForeSignalTimeEpoch(fromTimeString);
            if(entry.getValue() >= currSignalIdentifiedTime) {
                canSend = false;
                //logger.info("Identical or elder signal, ignore!");
                return "Identical Signal";
            } else {
                //logger.info("parser detected a new signal for " + entry.getKey());
                map.put(entry.getKey(), currSignalIdentifiedTime);
            }
            title = body.substring(0, 8);
            StringBuilder ret = new StringBuilder();
            // set instrument(currency pair)
            ret.append(title.substring(0,3));
            ret.append("_");
            ret.append(title.substring(4, 7));
            ret.append("|");
            INSTRUMENT = ret.substring(0, ret.length()-1).toString();
            // set indentified_time
            ret.append(DateTimeUtils.parseForeSignalTime(fromTimeString));
            ret.append("|");
            ret.append(DateTimeUtils.getCurrentTimeStringinUTC());
            ret.append("|");
            int sellIdx = body.indexOf("Sell ");
            int buyIdx = body.indexOf("Buy ");
            int tpIdx = body.indexOf("Take profit");
            int slIdx = body.indexOf("Stop loss");
            String tillTimeString;
            char Direction = 'L';
            if(buyIdx < 0) Direction = 'S';
            if(Direction == 'L') {
                ret.append(body.substring(body.indexOf("Buy at") + 7, tpIdx-1));
                ret.append("|");
                tillTimeString = body.substring(tillIdx+5    , buyIdx-1);
            } else {
                ret.append(body.substring(body.indexOf("Sell at") + 8, tpIdx-1));
                ret.append("|");
                tillTimeString = body.substring(tillIdx+5    , sellIdx-1);
            }
            ret.append(body.substring(tpIdx+16, slIdx-1));
            ret.append("|");
            ret.append(body.substring(slIdx+13, body.indexOf("Instructions Pending")-1));
            ret.append("|75|");
            long holdSeconds = DateTimeUtils.diffForeSignalFromTillInSec(fromTimeString, tillTimeString);
            ret.append(String.valueOf(holdSeconds));
            ret.append("|");
            ret.append(String.valueOf(holdSeconds));
            ret.append("|");
            canSend = true;
            return ret.toString();
        }
    }


    public ForeSignal_0_0_1(EventPublisher publisher, String topic) throws Exception {
        this.lastSignalDict.put("eurusd", 0L);
        this.lastSignalDict.put("usdchf", 0L);
        this.lastSignalDict.put("gbpusd", 0L);
        this.lastSignalDict.put("usdjpy", 0L);
        this.lastSignalDict.put("usdcad", 0L);
        this.lastSignalDict.put("audusd", 0L);
        this.lastSignalDict.put("eurjpy", 0L);
        this.lastSignalDict.put("nzdusd", 0L);
        this.lastSignalDict.put("gbpchf", 0L);

        this.auth.authenticate();
        this.TOPIC = topic;
        if(topic.charAt(topic.length()-1) == '*') {
            this.TOPIC = topic.replace("*","");
        }
        this.publisher = publisher;
    }


    public void run() throws Exception {
        while(true) {
            queryNewSignals();
            sleep(60000); // query every 1 min
        }
    }

    private void queryNewSignals() throws Exception {
        // to do
        for(Map.Entry<String, Long> lastSignal : this.lastSignalDict.entrySet()) {
            String result = Parser.Parse(this.baseUrl + lastSignal.getKey(), this.auth, lastSignal, lastSignalDict);
            if (Parser.canSend) {
                logger.info("Sending signal to kafka:\nTOPIC: " + TOPIC + INSTRUMENT + "\nMESSAGE: " + result);
                publisher.publish(TOPIC + INSTRUMENT, result);
                Parser.canSend = false;
            }
        }
    }

}

