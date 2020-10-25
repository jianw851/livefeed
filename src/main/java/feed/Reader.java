package feed;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.Details;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.gmail.model.*;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.common.collect.Lists;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

class Reader {
    private GoogleCredential credential = null;
    private Gmail gmail = null;
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

    Reader() throws GeneralSecurityException, IOException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        // Go to the Google API Console, open your application's
        // credentials page, and copy the client ID and client secret.
        // Then paste them into the following code.
        Details details = new Details();
        details.setClientId("605268858792-9bs34a1756bss53ckmp3bguo6hf0mjcc.apps.googleusercontent.com");
        details.setClientSecret("ZB5lGzSuvV07Q9LaWZF91TUU");

        // Or your redirect URL for web based applications.
        String redirectUrl = "urn:ietf:wg:oauth:2.0:oob";
        List<String> scopes = Arrays.asList("https://mail.google.com/",
        "https://www.googleapis.com/auth/gmail.modify",
        "https://www.googleapis.com/auth/gmail.readonly",
       //  "https://www.googleapis.com/auth/gmail.metadata",
        // "https://www.googleapis.com/auth/gmail.addons.current.message.metadata",
        "https://www.googleapis.com/auth/gmail.addons.current.message.readonly",
        "https://www.googleapis.com/auth/gmail.addons.current.message.action");

        GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
        clientSecrets.setInstalled(details);

        GoogleAuthorizationCodeFlow authorizationFlow = new GoogleAuthorizationCodeFlow
                .Builder(httpTransport, jsonFactory, clientSecrets, Lists.newArrayList(scopes))
                .setAccessType("offline").build();

        String authorizeUrl =
                authorizationFlow.newAuthorizationUrl().setRedirectUri(redirectUrl).build();
        System.out.println("Paste this url in your browser: \n" + authorizeUrl + '\n');

        // Wait for the authorization code.
        System.out.println("Type the code you received here: ");
        String authorizationCode = new BufferedReader(new InputStreamReader(System.in)).readLine();

        // Authorize the OAuth2 token.
        GoogleAuthorizationCodeTokenRequest tokenRequest =
                authorizationFlow.newTokenRequest(authorizationCode);
        tokenRequest.setRedirectUri(redirectUrl);
        GoogleTokenResponse tokenResponse = tokenRequest.execute();

        // Create the OAuth2 credential.
        credential = new GoogleCredential.Builder()
                .setTransport(new NetHttpTransport())
                .setJsonFactory(new JacksonFactory())
                .setClientSecrets(clientSecrets)
                .build();

        // Set authorized credentials.
        credential.setFromTokenResponse(tokenResponse);

        gmail = new Gmail
                .Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("liveget")
                .build();
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
        System.out.println(email.getContentMD5());
        System.out.println(email.getContent().toString());
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
                .setLabelIds(labelIds).execute();

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

    public static void main1(String[] args) throws Exception {
        Reader r = new Reader();
        String user = "jianw851@gmail.com";
        ListLabelsResponse listResponse = r.gmail.users().labels().list(user).execute();
        List<Label> labels = listResponse.getLabels();
        if (labels.isEmpty()) {
            System.out.println("No labels found.");
        } else {
            System.out.println("Labels:");
            for (Label label : labels) {
                System.out.printf("%s - %s\n", label.getId(), label.getName());
            }
        }
        /*
        List<Message> messageIds = Reader.listMessagesWithLabels(r.gmail, "me", Arrays.asList("Label_7808765998247590589"));
        for(Message msg : messageIds) {
            System.out.println(msg.getId());
            Reader.getMessage(r.gmail, "me", msg.getId());
        }
         */
    }

}