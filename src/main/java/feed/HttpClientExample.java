package feed;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.Certificate;
import java.io.*;

// import javax.net.ssl.HttpsURLConnection;
import java.net.URLConnection;

import javax.net.ssl.SSLPeerUnverifiedException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
// import javax.net.ssl.HttpsURLConnection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.HttpURLConnection;
import java.util.zip.GZIPInputStream;

public class HttpClientExample {

    private List<String> cookies;
    private HttpURLConnection conn;
    // private HttpURLConnection conn;
    private final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.97 Safari/537.36";
    private final String USER_AGENT1 = "Mozilla/5.0";
    private final String landingUrl = "https://foresignal.com/en/login/index";
    private final String authUrl = "https://foresignal.com/en/login/login";
    private final String signalUrl = "https://foresignal.com/en/signals/one/gbpusd/2020/10/23/1454?from=notification";
    private String params;
    private final String params1 = "user_name=jianw851&user_password=Lover2!!&set_remember_me_cookie=on&continue=&hash=";

    public static void main(String[] args) throws Exception {

        HttpClientExample example = new HttpClientExample();


        // 0 override https.proxyHost, https.proxyPort and http.agent
        example.forceAgentHeader("https.proxyHost");
        example.forceAgentHeader("https.proxyPort");
        example.forceAgentHeader("http.agent");

        // 1. make sure cookies is turn on and get necessary cookies from accessing landing page
        CookieHandler.setDefault(new CookieManager());
        example.setupCookies();
        // 2. make sure post params are correctly set (encoding)
        example.setupParams();

        // 3. Construct above post's content and then send a POST request for
        // authentication
        example.authentication();

        // 4. get target signal page content
        String content = example.getSignalPageContent();
    }

    protected void forceAgentHeader(final String header) throws Exception {
        final Class<?> clazz = Class
                .forName("sun.net.www.protocol.http.HttpURLConnection");
        final Field field = clazz.getField("userAgent");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, header);
    }

    private void setupParams() throws UnsupportedEncodingException {
        List<String> paramList = new ArrayList<String>();
        paramList.add("user_name" + "=" + URLEncoder.encode("jianw851", "UTF-8"));
        paramList.add("user_password" + "=" + URLEncoder.encode("Lover2!!", "UTF-8"));
        paramList.add("set_remember_me_cookie" + "=" + URLEncoder.encode("on", "UTF-8"));
        paramList.add("continue" + "=" + URLEncoder.encode("", "UTF-8"));
        paramList.add("hash" + "=" + URLEncoder.encode("", "UTF-8"));
        // build parameters list
        StringBuilder result = new StringBuilder();
        for (String param : paramList) {
            if (result.length() == 0) {
                result.append(param);
            } else {
                result.append("&" + param);
            }
        }
        this.params = result.toString();
        System.out.println(this.params);
    }

    private void setupCookies() throws IOException {
        URL obj = new URL(landingUrl);
        conn = (HttpURLConnection) obj.openConnection();

        // default is GET
        conn.setRequestMethod("GET");
        conn.setUseCaches(false);
        // act like a browser
        // conn.setRequestProperty("Connection", "keep-alive");
        conn.setRequestProperty("Authority", "foresignal.com");
        conn.setRequestProperty("Scheme", "https");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
        if (cookies != null) {
            for (String cookie : this.cookies) {
                conn.addRequestProperty("Cookie", cookie.split(";")[0]);
            }
        }
        conn.addRequestProperty("Cookie", "_ga=GA1.2.1554919586.1603411141");
        conn.addRequestProperty("Cookie", "__gads=ID=aeb35bfb840070bd-223a36be3cc400c3:T=1603411140:RT=1603411140:S=ALNI_MZCQkrK2rB_HUbJzayrdTG2rKCcag");
        conn.addRequestProperty("Cookie", "_gid=GA1.2.658040408.1603563016");
        conn.addRequestProperty("Cache-Control","max-age=0");
        conn.setRequestProperty("Path", "/en/login/index");
        conn.setRequestProperty("Referer", "https://foresignal.com/en/");
        conn.setRequestProperty("Sec-Fetch-Dest", "document");
        conn.setRequestProperty("Sec-Fetch-Site", "same-origin");
        conn.setRequestProperty("Sec-Fetch-Mode", "navigate");
        conn.setRequestProperty("Sec-Fetch-User", "?1");
        conn.setRequestProperty("Upgrade-Insecure-Requests", "1");
        int responseCode = conn.getResponseCode();
        System.out.println("\nSending 'GET' request to URL : " + landingUrl);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in =
                new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        // Get the response cookies
        this.cookies = conn.getHeaderFields().get("Set-Cookie");
        //String cookie = conn.getHeaderField( "Set-Cookie").split(";")[0];
        for(String cookie : this.cookies) {
            System.out.println(cookie);
            // System.out.println(cookie.split(";")[0]);
        }
        // System.out.println(response.toString());

    }

    private void authentication() throws Exception {

        URL obj = new URL(authUrl);
        conn = (HttpURLConnection) obj.openConnection();
        // Acts like a browser
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authority", "foresignal.com");
        conn.setRequestProperty("Scheme", "https");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        conn.setRequestProperty("Accept-language", "en-US,en;q=0.5");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
        for (String cookie : this.cookies) {
            conn.addRequestProperty("Cookie", cookie.split(";")[0]);
        }
        conn.addRequestProperty("Cookie", "_ga=GA1.2.1554919586.1603411141");
        conn.addRequestProperty("Cookie", "__gads=ID=aeb35bfb840070bd-223a36be3cc400c3:T=1603411140:RT=1603411140:S=ALNI_MZCQkrK2rB_HUbJzayrdTG2rKCcag");
        conn.addRequestProperty("Cookie", "_gid=GA1.2.658040408.1603563016");

        conn.setRequestProperty("Cache-Control","max-age=0");
        // conn.setRequestProperty("Connection", "keep-alive");
        conn.setRequestProperty("Path", "/en/login/login");
        conn.setRequestProperty("Origin", "https://foresignal.com");
        conn.setRequestProperty("Referer", "https://foresignal.com/en/login/index");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Content-Length", "" + this.params.getBytes().length);
        conn.setRequestProperty("Sec-Fetch-Dest", "document");
        conn.setRequestProperty("Sec-Fetch-Site", "same-origin");
        conn.setRequestProperty("Sec-Fetch-Mode", "navigate");
        conn.setRequestProperty("Sec-Fetch-User", "?1");
        conn.setRequestProperty("Upgrade-Insecure-Requests", "1");
        conn.setDoOutput(true);
        conn.setDoInput(true);

        // Send post request
        DataOutputStream wr = new DataOutputStream(conn.getOutputStream());
        wr.writeBytes(this.params);
        wr.flush();
        wr.close();

        int responseCode = conn.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + authUrl);
        System.out.println("Post parameters : " + this.params);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in =
                new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        System.out.println(response.toString());
    }

    public String getSignalPageContent() throws IOException {
        URL obj = new URL(this.signalUrl);
        conn = (HttpURLConnection) obj.openConnection();
        // Acts like a browser
        conn.setUseCaches(false);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authority", "foresignal.com");
        conn.setRequestProperty("Scheme", "https");
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        conn.setRequestProperty("Accept-language", "en-US,en;q=0.5");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
        for (String cookie : this.cookies) {
            conn.addRequestProperty("Cookie", cookie.split(";")[0]);
        }
        conn.addRequestProperty("Cookie", "_ga=GA1.2.1554919586.1603411141");
        conn.addRequestProperty("Cookie", "__gads=ID=aeb35bfb840070bd-223a36be3cc400c3:T=1603411140:RT=1603411140:S=ALNI_MZCQkrK2rB_HUbJzayrdTG2rKCcag");
        conn.addRequestProperty("Cookie", "_gid=GA1.2.658040408.1603563016");

        conn.setRequestProperty("Cache-Control","max-age=0");
        // conn.setRequestProperty("Connection", "keep-alive");
        conn.setRequestProperty("Sec-Fetch-Dest", "document");
        conn.setRequestProperty("Sec-Fetch-Site", "same-origin");
        conn.setRequestProperty("Sec-Fetch-Mode", "navigate");
        conn.setRequestProperty("Sec-Fetch-User", "?1");
        conn.setRequestProperty("Upgrade-Insecure-Requests", "1");
        conn.setDoOutput(true);
        conn.setDoInput(true);

        // Send get request
        int responseCode = conn.getResponseCode();
        System.out.println("\nSending 'GET' request to URL : " + signalUrl);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = null;
        if ("gzip".equals(conn.getContentEncoding())) {
            in = new BufferedReader(new InputStreamReader(new GZIPInputStream(conn.getInputStream())));
        } else {
            in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        }
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        String ret = response.toString();
        System.out.println(ret);
        return ret;
    }
    /*
    private void testIt(){
        String https_url = "https://www.google.com/";
        URL url;
        try {
            url = new URL(https_url);
            HttpsURLConnection con = (HttpsURLConnection)url.openConnection();
            //dumpl all cert info
            print_https_cert(con);
            //dump all the content
            print_content(con);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void print_https_cert(HttpsURLConnection con){
        if(con!=null){
            try {
                System.out.println("Response Code : " + con.getResponseCode());
                System.out.println("Cipher Suite : " + con.getCipherSuite());
                System.out.println("\n");
                Certificate[] certs = con.getServerCertificates();
                for(Certificate cert : certs){
                    System.out.println("Cert Type : " + cert.getType());
                    System.out.println("Cert Hash Code : " + cert.hashCode());
                    System.out.println("Cert Public Key Algorithm : "
                            + cert.getPublicKey().getAlgorithm());
                    System.out.println("Cert Public Key Format : "
                            + cert.getPublicKey().getFormat());
                    System.out.println("\n");
                }
            } catch (SSLPeerUnverifiedException e) {
                e.printStackTrace();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private void print_content(HttpsURLConnection con){
        if(con!=null){
            try {
                System.out.println("****** Content of the URL ********");
                BufferedReader br =
                        new BufferedReader(
                                new InputStreamReader(con.getInputStream()));
                String input;
                while ((input = br.readLine()) != null){
                    System.out.println(input);
                }
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

     */

}
