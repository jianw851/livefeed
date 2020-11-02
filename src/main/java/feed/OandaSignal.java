package feed;

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.StringUtils;
import com.google.api.services.gmail.Gmail;
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

import javax.mail.MessagingException;

import static java.lang.Thread.sleep;

public class OandaSignal implements Feed {

    // logger
    private final static Logger logger = Logger.getLogger(OandaSignal.class);

    private static final int TIME_OFFSET = 3;
    private static String TOPIC = null;
    private static String INSTRUMENT = null;
    private EventPublisher publisher = null;

    // app specific params
    private static ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of(DateTimeUtils.defaultTimeZone));
    private static long lastMessageDeliverTimeInSec = -1L;
    private final static List<String> labelIDs = Arrays.asList("Label_7808765998247590589");
    private final static String userID = "me";

    // html parser
    static class Parser {
        private static final String IDENTIFIER = "Correlating Alert: ";
        private static Document doc = null;
        private static String title = null;
        private static String body = null;
        private static boolean canSend = false;

        /*
        CURRENCY|IDENTIFIED_TIME|DELIVER_TIME|BREAKOUT_PRICE|FORECAST_PRICE|STOPLOSS_PRICE|PROBABILITY|MIN_INTERVAL|MAX_INTERVAL
         */
        static String Parse(String htmlContent) throws RuntimeException {
            doc = Jsoup.parse(htmlContent);
            title = doc.title();
            //System.out.printf("Title: %s%n", title);
            StringBuilder ret = new StringBuilder();
            if(title.contains(IDENTIFIER)) {
                body = doc.body().text();
                List<Element> tdList = doc.body().select("td");
                /*
                for(int i = 0; i < tdList.size() ; i++) {
                    System.out.print(i + " ");
                    System.out.println(tdList.get(i).text());
                }
                */
                // parse instrument
                String instrument = title.replace(IDENTIFIER, "");
                if(instrument.length() > 6) {
                    throw new RuntimeException("Oanda Signal html page update, instrument parsing Exception");
                }
                ret.append(instrument.substring(0,3));
                ret.append("_");
                ret.append(instrument.substring(3, 6));
                ret.append("|");
                INSTRUMENT = ret.substring(0, ret.length()-1).toString();
                // parse IDENTIFIED_TIME|BREAKOUT_PRICE|FORECAST_PRICE|STOPLOSS_PRICE|PROBABILITY
                String temp = body.substring(body.indexOf("Identified time"), body.indexOf("% Pattern"));
                // String temp = tdList.get(15).text();
                temp = temp.replace("Breakout price ", "");
                temp = temp.replace("Forecast price ", "");
                temp = temp.replace("Forecast pips ", "");
                temp = temp.replace("Probability ", "");
                String[] array = temp.split(" : ");
                //System.out.println(temp);
                ret.append(array[1]);
                ret.append("|");
                ret.append(DateTimeUtils.getCurrentTimeStringinUTC());
                ret.append("|");
                ret.append(array[2]);
                ret.append("|");
                ret.append(array[3]);
                ret.append("||"); // additional pipe to skip the stoploss_price
                ret.append(array[5].replace(" ",""));
                ret.append("|");
                // parse MIN_INTERVAL|MAX_INTERVAL
                temp = body.substring(body.indexOf("Interval"), body.indexOf("This service is subject to this"));
                temp = temp.replace("Interval", "");
                temp = temp.replace("Pattern ", "");
                array = temp.split(": ");
                int minInteval = Integer.MAX_VALUE, maxInterval = Integer.MIN_VALUE;
                for(String str : array) {
                    if(str.contains("Min")) {
                        int curr = Integer.valueOf(str.replace("Min","").replace(" ",""));
                        if(curr < minInteval)
                            minInteval = curr;
                        if(curr > maxInterval)
                            maxInterval = curr;
                    } else if(str.contains("Daily")) {
                        int curr = 1440;
                        if(curr < minInteval)
                            minInteval = curr;
                        if(curr > maxInterval)
                            maxInterval = curr;
                    }
                }
                ret.append(String.valueOf(minInteval*60));
                ret.append("|");
                ret.append(String.valueOf(maxInterval*60));
                //System.out.printf("Body: %s%n", body);
                //System.out.printf("%d, %d %n", minInteval, maxInterval);
                canSend = true;
            } else {
                canSend = false;
            }
            return ret.toString();
        }
    }


    public OandaSignal(EventPublisher publisher, String topic) throws GeneralSecurityException, IOException {
        init();
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
        long newMsgTime = message.getInternalDate() / 1000L + TIME_OFFSET;
        if(lastMessageDeliverTimeInSec < newMsgTime) {
            lastMessageDeliverTimeInSec = newMsgTime;
            logger.info("new message detected, lastMessageDeliverTimeInSec updated: " + lastMessageDeliverTimeInSec + " -> " +
                    DateTimeUtils.epochToDateTimeString(lastMessageDeliverTimeInSec));
        }
        // System.out.println("Message snippet: " + message.getSnippet());
        return StringUtils.newStringUtf8(Base64.decodeBase64(message.getPayload().getBody().getData()));
    }

    private void init() throws IOException, GeneralSecurityException {
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

    public void run() throws IOException, RuntimeException, GeneralSecurityException, InterruptedException, MessagingException {
        while(true) {
            queryNewEmails();
            sleep(60000); // query every 60 seconds
        }
    }

    private void queryNewEmails() throws IOException, RuntimeException, GeneralSecurityException, MessagingException {
        List<Message> messageIds = GmailService.getInstance().listMessagesWithLabels("after:"+String.valueOf(lastMessageDeliverTimeInSec), labelIDs);
        if(messageIds.size() > 0) {
            for (Message msg : messageIds) {
                String htmlMessage = OandaSignal.getMessage(msg.getId());
                String result = Parser.Parse(htmlMessage);
                if(Parser.canSend) {
                    logger.info("Sending signal to kafka:\nTOPIC: " + TOPIC + INSTRUMENT + "\nMESSAGE: " + result);
                    publisher.publish(TOPIC + INSTRUMENT, result);
                    GmailService.getInstance().sendSignalEmail("Oanda Technical Analysis", "TOPIC: " + TOPIC + INSTRUMENT + "\nMESSAGE: " + result);
                    Parser.canSend = false;
                }
            }
        }
    }

}
