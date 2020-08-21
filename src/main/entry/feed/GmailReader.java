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

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;

public class GmailReader {

    private static final String APPLICATION_NAME = "liveget";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Arrays.asList(GmailScopes.MAIL_GOOGLE_COM,
            GmailScopes.GMAIL_METADATA, GmailScopes.GMAIL_LABELS,
            GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_SEND,
            GmailScopes.GMAIL_COMPOSE, GmailScopes.GMAIL_INSERT);
    private static final List<String> URLSCOPES = Arrays.asList("https://mail.google.com/",
            "https://www.googleapis.com/auth/gmail.modify",
            "https://www.googleapis.com/auth/gmail.readonly",
            //"https://www.googleapis.com/auth/gmail.metadata",
            // "https://www.googleapis.com/auth/gmail.addons.current.message.metadata",
            "https://www.googleapis.com/auth/gmail.addons.current.message.readonly",
            "https://www.googleapis.com/auth/gmail.addons.current.message.action");

    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    private Gmail gmail = null;


    private final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

    public GmailReader() throws GeneralSecurityException, IOException {
        gmail = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = GmailReader.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, URLSCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("jianw851@gmail.com");
    }

    /**
     * Get Message with given ID.
     *
     * @param service Authorized Gmail API instance.
     * @param userId User's email address. The special value "me"
     * can be used to indicate the authenticated user.
     * @param messageId ID of Message to retrieve.
     * @return Message Retrieved Message.
     * @throws IOException
     */
    public static Message getMessage(Gmail service, String userId, String messageId)
            throws IOException {
        Message message = service.users().messages().get(userId, messageId).execute();
        //System.out.println(message.toPrettyString());
        // System.out.println(StringUtils.newStringUtf8(Base64.decodeBase64(message.getPayload().getBody().getData())));
        System.out.println("Message snippet: " + message.getSnippet());
        return message;
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
    public static MimeMessage getMimeMessage(Gmail service, String userId, String messageId)
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
     * List all Messages of the user's mailbox with labelIds applied.
     *
     * @param service Authorized Gmail API instance.
     * @param userId User's email address. The special value "me"
     * can be used to indicate the authenticated user.
     * @param labelIds Only return Messages with these labelIds applied.
     * @throws IOException
     */
    public static List<Message> listMessagesWithLabels(Gmail service, String userId,
                                                       List<String> labelIds) throws IOException {
        ListMessagesResponse response = service.users().messages().list(userId)
                .setLabelIds(labelIds).setMaxResults(100L).execute();

        List<Message> messages = new ArrayList<Message>();
        while (response.getMessages() != null) {
            messages.addAll(response.getMessages());
            if (response.getNextPageToken() != null) {
                String pageToken = response.getNextPageToken();
                response = service.users().messages().list(userId).setLabelIds(labelIds)
                        .setPageToken(pageToken).execute();
            } else {
                break;
            }
        }
        /*
        for (Message message : messages) {
            System.out.println(message.toPrettyString());
        }
        */
        return messages;
    }

    public static void main(String... args) throws IOException, GeneralSecurityException {
        // Build a new authorized API client service.
        // Print the labels in the user's account.
        GmailReader reader = new GmailReader();
        /*
        ListMessagesResponse msgs = reader.gmail.users().messages().list("jianw851@gmail.com").execute();
        String user = "jianw851@gmail.com";
        ListLabelsResponse listResponse = reader.gmail.users().labels().list(user).execute();
        List<Label> labels = listResponse.getLabels();
        if (labels.isEmpty()) {
            System.out.println("No labels found.");
        } else {
            System.out.println("Labels:");
            for (Label label : labels) {
                System.out.printf("%s - %s\n", label.getId(), label.getName());
            }
        }
        */
        List<Message> messageIds = GmailReader.listMessagesWithLabels(reader.gmail, "me", Arrays.asList("Label_7808765998247590589"));
        for(Message msg : messageIds) {
            System.out.println(msg.getId());
            GmailReader.getMessage(reader.gmail, "me", msg.getId());
        }
    }
}
