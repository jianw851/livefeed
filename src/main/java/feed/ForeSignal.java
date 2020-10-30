package feed;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.StringUtils;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.google.api.services.gmail.GmailScopes;
import event.EventPublisher;
import org.apache.log4j.Logger;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import util.DateTimeUtils;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import static java.lang.Thread.sleep;

public class ForeSignal implements Feed {

    // logger
    private final static Logger logger = Logger.getLogger(ForeSignal.class);

    // gmail
    private static final List<String> SCOPES = Arrays.asList(GmailScopes.MAIL_GOOGLE_COM,
            GmailScopes.GMAIL_METADATA, GmailScopes.GMAIL_LABELS,
            GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_SEND,
            GmailScopes.GMAIL_COMPOSE, GmailScopes.GMAIL_INSERT);
    private static final List<String> URLSCOPES = Arrays.asList("https://mail.google.com/",
            "https://www.googleapis.com/auth/gmail.modify",
            "https://www.googleapis.com/auth/gmail.readonly",
            "https://www.googleapis.com/auth/gmail.addons.current.message.readonly",
            "https://www.googleapis.com/auth/gmail.addons.current.message.action");
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private static Gmail gmail = null;
    private final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final int TIME_OFFSET = 5;
    private static String TOPIC = null;
    private static String INSTRUMENT = null;
    private EventPublisher publisher = null;


    // app specific params
    private static ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of(DateTimeUtils.defaultTimeZone));
    private static long lastMessageDeliverTimeInSec = -1L;
    private final static List<String> labelIDs = Arrays.asList("Label_586885049211149128");
    private final static String userID = "me";

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


    public ForeSignal(EventPublisher publisher, String topic) throws GeneralSecurityException, IOException {
        gmail = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(System.getenv("GCP_APPLICATION_NAME"))
                .build();
        init();
        this.TOPIC = topic;
        if(topic.charAt(topic.length()-1) == '*') {
            this.TOPIC = topic.replace("*","");
        }
        this.publisher = publisher;
    }

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets
        logger.info("Load client secrets");
        InputStream in = ForeSignal.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, URLSCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(System.getenv("GCP_TOKENS_DIRECTORY_PATH"))))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize(userID);
    }

    /**
     * Get Message with given ID.
     *
     * @param messageId ID of Message to retrieve.
     * @return Message Retrieved Message.
     * @throws IOException
     */
    private static String getMessage(String messageId)
            throws IOException {
        Message message = gmail.users().messages().get(userID, messageId).execute();
        long newMsgTime = message.getInternalDate() / 1000L + TIME_OFFSET;
        if(lastMessageDeliverTimeInSec < newMsgTime) {
            lastMessageDeliverTimeInSec = newMsgTime;
            logger.info("new message detected, lastMessageDeliverTimeInSec updated: " + lastMessageDeliverTimeInSec + " -> " +
                    DateTimeUtils.epochToDateTimeString(lastMessageDeliverTimeInSec));
        }
        // System.out.println("Message snippet: " + message.getSnippet());
        return StringUtils.newStringUtf8(Base64.decodeBase64(message.getPayload().getBody().getData()));
    }

    /**
     * Get a Message and use it to create a MimeMessage.
     *
     * @param service Authorized Gmail API instance.
     * @param userId User's email address. The special value "me"
     * can be used to indicate the authenticated user.
     * @param messageId ID of Message to retrieve.
     * @return MimeMessage MimeMessage populated from retrieved Message.
     * @throws IOException
     * @throws MessagingException
     */
    private static MimeMessage getMimeMessage(Gmail service, String userId, String messageId)
            throws IOException, MessagingException {
        Message message = service.users().messages().get(userId, messageId).setFormat("raw").execute();

        Base64 base64Url = new Base64(true);
        byte[] emailBytes = base64Url.decodeBase64(message.getRaw());

        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session, new ByteArrayInputStream(emailBytes));
        return email;
    }

    /**
     * List all Messages of the user's mailbox with labelIds and Query applied.
     * @param query To specify a accurate epoch time in seconds for PST timezone pass the query
     *        for example: after:1388552400 before:1391230800
     * @throws IOException
     */
    private static List<Message> listMessagesWithLabels(String query) throws IOException {
        ListMessagesResponse response = gmail.users().messages().list(userID)
                .setLabelIds(labelIDs).setQ(query).execute();
        List<Message> messages = new ArrayList<Message>();
        while (response.getMessages() != null) {
            messages.addAll(response.getMessages());
            if (response.getNextPageToken() != null) {
                String pageToken = response.getNextPageToken();
                response = gmail.users().messages().list(userID).setLabelIds(labelIDs)
                        .setPageToken(pageToken).execute();
            } else {
                break;
            }
        }
        return messages;
    }

    private void init() throws IOException {
        if (lastMessageDeliverTimeInSec == -1) {
            ListMessagesResponse response = gmail.users().messages().list(userID)
                    .setLabelIds(labelIDs).setQ("after:"+String.valueOf(dateTime.toEpochSecond()-DateTimeUtils.weekInSec)).execute();
            if(response.getMessages().size() > 0) {
                // for testing purpose, can modify get(#) to get the last # emails
                // however in production, the number should always to set to 0
                // otherwise an old signal will be sent
                String msgID = response.getMessages().get(0).getId();
                Message message = gmail.users().messages().get(userID, msgID).execute();
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
            sleep(10000); // query every 10 seconds
        }
    }

    private void queryNewEmails() throws Exception {
        List<Message> messageIds = ForeSignal.listMessagesWithLabels("after:"+String.valueOf(lastMessageDeliverTimeInSec));
        if(messageIds.size() > 0) {
            ForeSignalAuth auth = new ForeSignalAuth();
            auth.authenticate();
            for (Message msg : messageIds) {
                String htmlMessage = ForeSignal.getMessage(msg.getId());
                String result = Parser.Parse(htmlMessage, auth);
                if(Parser.canSend) {
                    logger.info("Sending signal to kafka:\nTOPIC: " + TOPIC + INSTRUMENT + "\nMESSAGE: " + result);
                    publisher.publish(TOPIC + INSTRUMENT, result);
                    Parser.canSend = false;
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ForeSignal signal = new ForeSignal(null, "FORESIGNAL");
        signal.queryNewEmails();
    }

}

