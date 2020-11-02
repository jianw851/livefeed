package feed;

import event.EventPublisher;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import util.DateTimeUtils;
import util.GmailService;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static java.lang.Thread.sleep;

class SignalKafkaMessage {
    String instrument;
    String message;
    SignalKafkaMessage(String inst, String msg) {
        instrument = inst;
        message = msg;
    }
}

public class ForeSignal implements Feed {

    // logger
    private final static Logger logger = Logger.getLogger(ForeSignal.class);
    private EventPublisher publisher = null;

    private String TOPIC = null;
    // app specific params
    private static ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of(DateTimeUtils.defaultTimeZone));

    // html parser
    static class Parser {
        private static Parser INSTANCE = null;
        private final static String baseUrl = "https://foresignal.com/en/";
        private static Map<String, Long> lastSignalDict = new HashMap<>();
        private static final String IDENTIFIER = "Filled ";
        private static Document doc = null;
        private static String title = null;
        private static String body = null;
        private static ForeSignalAuth auth = null;

        private Parser() throws Exception {
            lastSignalDict.put("EUR_USD", 0L);
            lastSignalDict.put("USD_CHF", 0L);
            lastSignalDict.put("GBP_USD", 0L);
            lastSignalDict.put("USD_JPY", 0L);
            lastSignalDict.put("USD_CAD", 0L);
            lastSignalDict.put("AUD_USD", 0L);
            lastSignalDict.put("EUR_JPY", 0L);
            lastSignalDict.put("NZD_USD", 0L);
            lastSignalDict.put("GBP_CHF", 0L);
            auth = new ForeSignalAuth();
            auth.authenticate();
        }

        public static Parser getInstance() throws Exception {
            if (INSTANCE == null) {
                INSTANCE = new Parser();
            }
            return INSTANCE;
        }

        /*
        CURRENCY|IDENTIFIED_TIME|DELIVER_TIME|BREAKOUT_PRICE|FORECAST_PRICE|STOPLOSS_PRICE|PROBABILITY|MIN_INTERVAL|MAX_INTERVAL|
         */
        static List<SignalKafkaMessage> Parse() throws Exception {
            doc = Jsoup.parse(auth.getTargetContent(baseUrl));
            /*
            String htmlAbsPath = "/home/jwang/livefeed/template/index.html";
            Path path = Paths.get(htmlAbsPath);
            List<String> all = Files.readAllLines(path);
            StringBuilder sb = new StringBuilder();
            for(String line : all) {
                sb.append(line);
            }
            doc = Jsoup.parse(sb.toString());
            */
            Element bodyElement = doc.body();
            Element container = bodyElement.select("div.container.container-shadow").get(0);
            Elements signalElements = container.select("div.row.row-eq-height");
            Elements targetSignals = new Elements();
            for(Element signalBar : signalElements) {
                targetSignals.addAll(signalBar.select("div.col-sm-4.col-xs-12.signal-cell"));
            }
            List<SignalKafkaMessage> ret = new ArrayList<>();
            for(Element element : targetSignals) {
                SignalKafkaMessage message =  ParseSignal(element.text());
                if(message != null) {
                    ret.add(message);
                }
            }
            return ret;
        }

        static SignalKafkaMessage ParseSignal(String content) {
            // check if signal expired
            if(content.indexOf(IDENTIFIER) > 0) {
                logger.debug("Signal expired :" + content);
                return null;
            }
            // check if signal has already been parsed
            int fromIdx = content.indexOf("From GMT");
            int tillIdx = content.indexOf("Till GMT");
            String fromTimeString = content.substring(fromIdx+5, tillIdx-1);
            long currSignalIdentifiedTime = DateTimeUtils.parseForeSignalTimeEpoch(fromTimeString);
            String instrument = content.substring(0, 7);
            instrument = instrument.replace("/", "_");
            if(lastSignalDict.get(instrument) >= currSignalIdentifiedTime) {
                logger.debug("Signal has already been parsed, ignore!");
                return null;
            } else {
                logger.debug("parser detected a new signal for " + instrument);
                lastSignalDict.put(instrument, currSignalIdentifiedTime);
            }
            // parse the signal
            StringBuilder ret = new StringBuilder();
            // set instrument(currency pair)
            ret.append(instrument);
            ret.append("|");

            // set indentified_time
            ret.append(DateTimeUtils.parseForeSignalTime(fromTimeString));
            ret.append("|");
            ret.append(DateTimeUtils.getCurrentTimeStringinUTC());
            ret.append("|");
            int sellIdx = content.indexOf("Sell ");
            int buyIdx = content.indexOf("Buy ");
            int tpIdx = content.indexOf("Take profit");
            int slIdx = content.indexOf("Stop loss");
            String tillTimeString;
            char Direction = 'L';
            if(buyIdx < 0) Direction = 'S';
            if(Direction == 'L') {
                ret.append(content.substring(content.indexOf("Buy at") + 7, tpIdx-1));
                ret.append("|");
                tillTimeString = content.substring(tillIdx+5    , buyIdx-1);
            } else {
                ret.append(content.substring(content.indexOf("Sell at") + 8, tpIdx-1));
                ret.append("|");
                tillTimeString = content.substring(tillIdx+5    , sellIdx-1);
            }
            ret.append(content.substring(tpIdx+16, slIdx-1));
            ret.append("|");
            ret.append(content.substring(slIdx+13, content.length()));
            ret.append("|75|");
            long holdSeconds = DateTimeUtils.diffForeSignalFromTillInSec(fromTimeString, tillTimeString);
            ret.append(String.valueOf(holdSeconds));
            ret.append("|");
            ret.append(String.valueOf(holdSeconds));
            ret.append("|");
            return new SignalKafkaMessage(instrument, ret.toString());
        }
    }


    public ForeSignal(EventPublisher publisher, String topic) throws Exception {
        this.TOPIC = topic;
        if(topic.charAt(topic.length()-1) == '*') {
            this.TOPIC = topic.replace("*","");
        }
        this.publisher = publisher;
    }


    public void run() throws Exception {
        boolean isNegative = false;
        int waitInSec = 60000;
        int offsetSec = 0;
        while(true) {
            queryNewSignals();
            logger.debug("wait " + (waitInSec + offsetSec) / 1000 + " seconds");
            sleep(waitInSec + offsetSec); // query about every 1 min
            offsetSec = (int)(Math.random() * 10000.0);
            if(isNegative) {
                offsetSec *= -1;
            }
            isNegative = !isNegative;
        }
    }

    private void queryNewSignals() throws Exception {
        List<SignalKafkaMessage> messages = Parser.getInstance().Parse();
        if (messages != null && messages.size() > 0) {
            for (SignalKafkaMessage msg : messages) {
                logger.info("Sending signal to kafka:\nTOPIC: " + this.TOPIC + msg.instrument + "\nMESSAGE: " + msg.message);
                publisher.publish(this.TOPIC + msg.instrument, msg.message);
                GmailService.getInstance().sendSignalEmail("Oanda Technical Analysis", "TOPIC: " + TOPIC + msg.instrument + "\nMESSAGE: " + msg.message);
            }
        }
    }

}

