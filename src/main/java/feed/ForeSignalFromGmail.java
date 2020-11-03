package feed;

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.StringUtils;
import com.google.api.services.gmail.model.*;
import event.EventPublisher;
import org.apache.log4j.Logger;
import org.jsoup.nodes.Element;
import util.DateTimeUtils;

import java.io.*;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import util.GmailService;

import static java.lang.Thread.sleep;

public class ForeSignalFromGmail implements Feed {

    // logger
    private final static Logger logger = Logger.getLogger(ForeSignalFromGmail.class);
    private static ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of(DateTimeUtils.defaultTimeZone));

    private static final int TIME_OFFSET = 10;
    private static String TOPIC = null;
    private static String INSTRUMENT = null;
    private EventPublisher publisher = null;

    // app specific params
    private static long lastMessageDeliverTimeInSec = -1L;
    private final static List<String> labelIDs = Arrays.asList("Label_586885049211149128");
    private final static String userID = "me";
    private static long currSignalIdentifiedTimeUTC = 0L;
    private static long tillSignalTimeUTC = 0L;

    private final ForeSignalSeleniumAuth auth = new ForeSignalSeleniumAuth();
    private static Map<String, Long> lastSignalDict = new HashMap<>();
    private final String signalGmailThreadId = "1758f199f9303f16";

    // html parser
    static class Parser {
        private static final String IDENTIFIER = "Filled ";
        private static Document doc = null;
        private static String title = null;
        private static String body = null;
        private static boolean canSend = false;

        static String parseEmailLink(String emailContent) throws RuntimeException {
            doc = Jsoup.parse(emailContent);
            Element bd = doc.body();
            String emailTitle = bd.select("div.signal").text();
            System.out.println(emailTitle);
            String link = bd.select("div.link").select("a.button").get(0).attributes().get("href");
            System.out.println(link);
            return link;
        }

        static String canProcess(String emailContent) throws RuntimeException {
            // get instrument
            doc = Jsoup.parse(emailContent);
            Element bd = doc.body();
            String emailTitle = bd.select("div.signal").text();
            int instEndIdx = emailTitle.indexOf("Forex signal");
            INSTRUMENT = emailTitle.substring(instEndIdx-8, instEndIdx-1).replace("/", "_");
            String signalTime = bd.select("div.time").select("div").get(2).text();
            int fromIdx = signalTime.indexOf("From");
            int tillIdx = signalTime.indexOf("Till");
            String fromStr = signalTime.substring(fromIdx+4, tillIdx-1).trim();
            String tillStr = signalTime.substring(tillIdx+4, signalTime.length()).trim();
            currSignalIdentifiedTimeUTC = DateTimeUtils.parseForeSignalEmailTimeEpochUTC(fromStr);
            tillSignalTimeUTC = DateTimeUtils.parseForeSignalEmailTimeEpochUTC(tillStr);
            long currTimeEpochinUTC = DateTimeUtils.getCurrentTimeEpochinUTC();
            if(lastSignalDict.get(INSTRUMENT) >= currSignalIdentifiedTimeUTC) {
                logger.info("Signal has already been parsed, ignore!");
                return null;
            } else if (Math.abs(currTimeEpochinUTC - currSignalIdentifiedTimeUTC) > 600) {
                logger.info("Signal might be too old, ignore!");
                return null;
            }
            String link = bd.select("div.link").select("a.button").get(0).attributes().get("href");
            System.out.println(link);
            return link;
        }

        /*
        CURRENCY|IDENTIFIED_TIME|DELIVER_TIME|BREAKOUT_PRICE|FORECAST_PRICE|STOPLOSS_PRICE|PROBABILITY|MIN_INTERVAL|MAX_INTERVAL|
         */
        static String Parse(String emailContent, ForeSignalSeleniumAuth auth) throws Exception {
            String signalLink = canProcess(emailContent);
            if( signalLink == null) {
                logger.info("Email is too old, ignore!");
                return null;
            }
            doc = Jsoup.parse(auth.getTargetContent(signalLink, 1));
            /*
            String htmlAbsPath = "/home/jwang/livefeed/template/new.html";
            Path path = Paths.get(htmlAbsPath);
            List<String> all = Files.readAllLines(path);
            StringBuilder sb = new StringBuilder();
            for(String line : all) {
                sb.append(line);
            }
            doc = Jsoup.parse(sb.toString());
            */
            Element bodyElement = doc.body();
            body = bodyElement.text();

            // check if signal expired
            if(body.indexOf(IDENTIFIER) > 0) {
                logger.info("Signal expired :" + body);
                return null;
            }
            int fromIdx = body.indexOf("From GMT");
            int tillIdx = body.indexOf("Till GMT");
            String fromTimeString = body.substring(fromIdx+5, tillIdx-1);
            // long currSignalIdentifiedTimeUTC = DateTimeUtils.parseForeSignalTimeEpochUTC(fromTimeString);
            // int instEndIdx = body.indexOf("Forex signal");
            // INSTRUMENT = body.substring(instEndIdx-8, instEndIdx-1).replace("/", "_");
            long currTimeEpochinUTC = DateTimeUtils.getCurrentTimeEpochinUTC();
            // check if signal has already been processed
            // or too old to process
            // or too early to process
            if(lastSignalDict.get(INSTRUMENT) >= currSignalIdentifiedTimeUTC) {
                logger.info("Signal has already been parsed, ignore!");
                return null;
            } else if (Math.abs(currTimeEpochinUTC - currSignalIdentifiedTimeUTC) > 660) {
                logger.info("Signal might be too old, ignore!");
                return null;
            } else {
                logger.info("parser detected a new signal for " + INSTRUMENT);
                lastSignalDict.put(INSTRUMENT, currSignalIdentifiedTimeUTC);
            }
            StringBuilder ret = new StringBuilder();
            // set instrument(currency pair)
            ret.append(INSTRUMENT);
            ret.append("|");
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
                ret.append(body.substring(body.indexOf("Buy at") + 7, tpIdx-1).trim());
                ret.append("|");
                tillTimeString = body.substring(tillIdx+5    , buyIdx-1);
            } else {
                ret.append(body.substring(body.indexOf("Sell at") + 8, tpIdx-1).trim());
                ret.append("|");
                tillTimeString = body.substring(tillIdx+5    , sellIdx-1);
            }
            ret.append(body.substring(tpIdx+16, slIdx-1).trim());
            ret.append("|");
            ret.append(body.substring(slIdx+13, body.indexOf("Instructions Pending")-1).trim());
            ret.append("|75|");
            long holdSeconds = tillSignalTimeUTC - currSignalIdentifiedTimeUTC;
            ret.append(String.valueOf(holdSeconds));
            ret.append("|");
            ret.append(String.valueOf(holdSeconds));
            ret.append("|");
            canSend = true;
            return ret.toString();
        }
    }

    public ForeSignalFromGmail(EventPublisher publisher, String topic) throws Exception {
        init();
        // this.auth.authenticate();
        this.TOPIC = topic;
        if(topic.charAt(topic.length()-1) == '*') {
            this.TOPIC = topic.replace("*","");
        }
        this.publisher = publisher;
    }

    /**
     * Get Message with given ID.
     *
     * @param messageId ID of Message to retrieve.
     * @return Message Retrieved Message.
     * @throws IOException
     */
    private static String getMessage(String messageId)
            throws IOException, GeneralSecurityException {
        Message message = GmailService.getService().users().messages().get(userID, messageId).execute();
        long newMsgTime = message.getInternalDate() / 1000L;
        if(lastMessageDeliverTimeInSec <= newMsgTime) {
            lastMessageDeliverTimeInSec = newMsgTime + TIME_OFFSET;
            logger.info("new message detected, lastMessageDeliverTimeInSec updated: " + lastMessageDeliverTimeInSec + " -> " +
                    DateTimeUtils.epochToDateTimeString(lastMessageDeliverTimeInSec));
        }
        // System.out.println("Message snippet: " + message.getSnippet());
        return StringUtils.newStringUtf8(Base64.decodeBase64(message.getPayload().getBody().getData()));
    }

    private void init() throws Exception {
        if (lastMessageDeliverTimeInSec == -1) {
            ListMessagesResponse response = GmailService.getService().users().messages().list(userID)
                    .setLabelIds(labelIDs).setQ("after:"+String.valueOf(dateTime.toEpochSecond()-DateTimeUtils.weekInSec)).execute();
            if(response.getMessages().size() > 0) {
                // for testing purpose, can modify get(#) to get the last # emails
                // however in production, the number should always to set to 0
                // otherwise an old signal will be sent
                String msgID = response.getMessages().get(1).getId();
                Message message = GmailService.getService().users().messages().get(userID, msgID).execute();
                lastMessageDeliverTimeInSec = message.getInternalDate() / 1000L + TIME_OFFSET; // add time offset in case duplicate signal
                // every time when restart this app, update the timestamp to pull emails

            } else {
                lastMessageDeliverTimeInSec = dateTime.toEpochSecond();
            }
        }
        logger.info("lastMessageDeliverTimeInSec set: " + lastMessageDeliverTimeInSec + " -> " +
                DateTimeUtils.epochToDateTimeString(lastMessageDeliverTimeInSec));
        lastSignalDict.put("EUR_USD", 0L);
        lastSignalDict.put("USD_CHF", 0L);
        lastSignalDict.put("GBP_USD", 0L);
        lastSignalDict.put("USD_JPY", 0L);
        lastSignalDict.put("USD_CAD", 0L);
        lastSignalDict.put("AUD_USD", 0L);
        lastSignalDict.put("EUR_JPY", 0L);
        lastSignalDict.put("NZD_USD", 0L);
        lastSignalDict.put("GBP_CHF", 0L);
    }

    public void run() throws Exception {
        while(true) {
            queryNewEmails();
            sleep(60000); // query every 60 seconds
            logger.info("query new emails ..." + DateTimeUtils.getCurrentTimeStringinUTC());
        }
    }

    private void queryNewEmails() throws Exception {
        List<Message> messageIds = GmailService.getInstance().listMessagesWithLabels("after:"+String.valueOf(lastMessageDeliverTimeInSec), labelIDs);
        logger.info("new email size = " + messageIds.size());
        if(messageIds.size() > 0) {
            for (Message msg : messageIds) {
                String htmlMessage = ForeSignalFromGmail.getMessage(msg.getId());
                String result = Parser.Parse(htmlMessage, this.auth);
                if(Parser.canSend) {
                    logger.info("Sending signal to kafka:\nTOPIC: " + TOPIC + INSTRUMENT + "\nMESSAGE: " + result);
                    publisher.publish(TOPIC + INSTRUMENT, result);
                    GmailService.getInstance().sendSignalEmail("Foresignal", "TOPIC: " + TOPIC + INSTRUMENT + "\nMESSAGE: " + result, signalGmailThreadId);
                    Parser.canSend = false;
                } else {
                    logger.info("cannot proceed, sleep 1 min ...");
                }
            }
        }
    }

}

