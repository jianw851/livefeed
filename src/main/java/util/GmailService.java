package util;

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
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.google.api.services.gmail.GmailScopes;
import feed.ForeSignalFromGmail;
import org.apache.log4j.Logger;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;


public class GmailService {

    // logger
    private final static Logger logger = Logger.getLogger(GmailService.class);

    // instance
    private static GmailService INSTANCE = null;

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
    private final static String userID = "me";
    private final static String from = "livefeed-error@liveget.com";
    private final static String admin = "jianw851@gmail.com";
    private final static String signalRecipient = "jianw851@gmail.com,yixia.cai@gmail.com";


    public static GmailService getInstance() throws GeneralSecurityException, IOException {
        if(INSTANCE == null) {
            INSTANCE = new GmailService();
        }
        return INSTANCE;
    }

    public static Gmail getService() throws GeneralSecurityException, IOException {
        return getInstance().gmail;
    }

    private GmailService() throws GeneralSecurityException, IOException {
        gmail = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(System.getenv("GCP_APPLICATION_NAME"))
                .build();
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
        InputStream in = ForeSignalFromGmail.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
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
     * Create a MimeMessage using the parameters provided.
     *
     * @param to email address of the receiver
     * @param from email address of the sender, the mailbox account
     * @param subject subject of the email
     * @param bodyText body text of the email
     * @return the MimeMessage to be used to send email
     * @throws MessagingException
     */
    public static MimeMessage createEmail(String to,
                                          String from,
                                          String subject,
                                          String bodyText)
            throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        email.setFrom(new InternetAddress(from));
        if(to.indexOf(",") > 0) {
            String[] recipients = to.split(",");
            for (String recipient : recipients) {
                email.addRecipient(javax.mail.Message.RecipientType.TO,
                        new InternetAddress(recipient));
            }
        } else {
            email.addRecipient(javax.mail.Message.RecipientType.TO,
                    new InternetAddress(to));
        }
        email.setSubject(subject);
        email.setText(bodyText);
        return email;
    }

    /**
     * Create a message from an email.
     *
     * @param emailContent Email to be set to raw of message
     * @return a message containing a base64url encoded email
     * @throws IOException
     * @throws MessagingException
     */
    public static Message createMessageWithEmail(MimeMessage emailContent)
            throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    /**
     * Send an email from the user's mailbox to its recipient.
     *
     * can be used to indicate the authenticated user.
     * @param emailContent Email to be sent.
     * @return The sent message
     * @throws MessagingException
     * @throws IOException
     */
    public static Message sendMessage(MimeMessage emailContent)
            throws MessagingException, IOException {
        Message message = createMessageWithEmail(emailContent);
        message = gmail.users().messages().send(userID, message).execute();
        logger.info("Message id: " + message.getId());
        logger.info(message.toPrettyString());
        return message;
    }

    public static void sendWarningEmail(String stackTrace) throws MessagingException, IOException {
        String subject = "Livefeed Runtime Error Alert";
        MimeMessage email = createEmail(admin, from, subject, stackTrace);
        sendMessage(email);
    }

    public static void sendSignalEmail(String broker, String signal) throws MessagingException, IOException {
        String subject = String.format("A new signal received from %s", broker);
        MimeMessage email = createEmail(signalRecipient, from, subject, signal);
        sendMessage(email);
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
    public static List<Message> listMessagesWithLabels(String query, List<String> labels) throws IOException {
        ListMessagesResponse response = gmail.users().messages().list(userID)
                .setLabelIds(labels).setQ(query).execute();
        List<Message> messages = new ArrayList<Message>();
        while (response.getMessages() != null) {
            messages.addAll(response.getMessages());
            if (response.getNextPageToken() != null) {
                String pageToken = response.getNextPageToken();
                response = gmail.users().messages().list(userID).setLabelIds(labels)
                        .setPageToken(pageToken).execute();
            } else {
                break;
            }
        }
        return messages;
    }
}
