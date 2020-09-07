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
import org.jsoup.nodes.Element;
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

public class OandaSignal implements Feed {

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
    private static final int TIME_OFFSET = 3;
    private static String TOPIC = null;
    private static String INSTRUMENT = null;


    // app specific params
    private static ZonedDateTime dateTime = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("US/Pacific"));
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
        CURRENCY|IDENTIFIED_TIME|DELIVER_TIME|BREAKOUT_PRICE|FORECAST_PRICE|PROBABILITY|MIN_INTERVAL|MAX_INTERVAL
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
                INSTRUMENT = instrument;
                ret.append(instrument.substring(0,3));
                ret.append("_");
                ret.append(instrument.substring(3, 6));
                ret.append("|");
                // parse IDENTIFIED_TIME|BREAKOUT_PRICE|FORECAST_PRICE|PROBABILITY
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
                ret.append(DateTimeUtils.dateTime.toString().substring(0,23));
                ret.append("|");
                ret.append(array[2]);
                ret.append("|");
                ret.append(array[3]);
                ret.append("|");
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
            } else {
                canSend = false;
            }
            return ret.toString();
        }
    }


    public OandaSignal(String topic) throws GeneralSecurityException, IOException {
        gmail = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(System.getenv("GCP_APPLICATION_NAME"))
                .build();
        init();
        this.TOPIC = topic;
    }

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets
        InputStream in = OandaSignal.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
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
                String msgID = response.getMessages().get(40).getId();
                Message message = gmail.users().messages().get(userID, msgID).execute();
                lastMessageDeliverTimeInSec = message.getInternalDate() / 1000L + TIME_OFFSET; // add time offset in case duplicate signal
                // every time when restart this app, update the timestamp to pull emails
                System.out.println("lastMessageDeliverTimeInSec set: " + this.lastMessageDeliverTimeInSec + " -> " +
                        DateTimeUtils.epochToDateTimeString(lastMessageDeliverTimeInSec));
            } else {
                lastMessageDeliverTimeInSec = dateTime.toEpochSecond();
            }
        }
    }

    public void run() throws IOException, RuntimeException, GeneralSecurityException, InterruptedException {
        while(true) {
            queryNewEmails();
            sleep(10000); // query every 10 sec
        }
    }

    private void queryNewEmails() throws IOException, RuntimeException, GeneralSecurityException {
        List<Message> messageIds = OandaSignal.listMessagesWithLabels("after:"+String.valueOf(lastMessageDeliverTimeInSec));
        if(messageIds.size() > 0) {
            for (Message msg : messageIds) {
                String htmlMessage = OandaSignal.getMessage(msg.getId());
                String result = Parser.Parse(htmlMessage);
                // TODO: write into kafka broker
                System.out.println(result);
                EventPublisher.publish(TOPIC+INSTRUMENT, result);
            }
        }
    }

}
