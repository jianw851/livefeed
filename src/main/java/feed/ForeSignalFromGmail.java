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
    private static final int TIME_OFFSET = 8;
    private static String TOPIC = null;
    private static String INSTRUMENT = null;
    private EventPublisher publisher = null;


    // app specific params
    private static ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of(DateTimeUtils.defaultTimeZone));
    private static long lastMessageDeliverTimeInSec = -1L;
    private final static List<String> labelIDs = Arrays.asList("Label_586885049211149128");
    private final static String userID = "me";

    private final ForeSignalAuth auth = new ForeSignalAuth();

    // html parser
    static class Parser {
        private static final String IDENTIFIER = "Forex signal";
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

        /*
        CURRENCY|IDENTIFIED_TIME|DELIVER_TIME|BREAKOUT_PRICE|FORECAST_PRICE|STOPLOSS_PRICE|PROBABILITY|MIN_INTERVAL|MAX_INTERVAL|
         */
        static String Parse(String emailContent, ForeSignalAuth auth) throws Exception {
            String signalLink = parseEmailLink(emailContent);
            doc = Jsoup.parse(auth.getTargetContent(signalLink));
            body = doc.body().text();
            title = body.substring(0, 8);
            StringBuilder ret = new StringBuilder();
            // set instrument(currency pair)
            ret.append(title.substring(0,3));
            ret.append("_");
            ret.append(title.substring(4, 7));
            ret.append("|");
            INSTRUMENT = ret.substring(0, ret.length()-1).toString();
            // set indentified_time
            int fromIdx = body.indexOf("From GMT");
            int tillIdx = body.indexOf("Till GMT");
            String fromTimeString = body.substring(fromIdx+5, tillIdx-1);
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


    public ForeSignalFromGmail(EventPublisher publisher, String topic) throws Exception {
        init();
        this.auth.authenticate();
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
                String msgID = response.getMessages().get(0).getId();
                Message message = GmailService.getService().users().messages().get(userID, msgID).execute();
                lastMessageDeliverTimeInSec = message.getInternalDate() / 1000L + TIME_OFFSET; // add time offset in case duplicate signal
                // every time when restart this app, update the timestamp to pull emails

            } else {
                lastMessageDeliverTimeInSec = dateTime.toEpochSecond();
            }
        }
        logger.info("lastMessageDeliverTimeInSec set: " + lastMessageDeliverTimeInSec + " -> " +
                DateTimeUtils.epochToDateTimeString(lastMessageDeliverTimeInSec));
    }

    public void run() throws Exception {
        while(true) {
            queryNewEmails();
            sleep(60000); // query every 10 seconds
        }
    }

    private void queryNewEmails() throws Exception {
        List<Message> messageIds = GmailService.getInstance().listMessagesWithLabels("is:unread after:"+String.valueOf(lastMessageDeliverTimeInSec), labelIDs);
        if(messageIds.size() > 0) {
            for (Message msg : messageIds) {
                String htmlMessage = ForeSignalFromGmail.getMessage(msg.getId());
                String result = Parser.Parse(htmlMessage, this.auth);
                if(Parser.canSend) {
                    logger.info("Sending signal to kafka:\nTOPIC: " + TOPIC + INSTRUMENT + "\nMESSAGE: " + result);
                    publisher.publish(TOPIC + INSTRUMENT, result);
                    Parser.canSend = false;
                }
            }
        }
    }

}

