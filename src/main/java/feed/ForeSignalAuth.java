package feed;

import com.gargoylesoftware.css.parser.CSSException;
import com.gargoylesoftware.css.parser.CSSErrorHandler;
import com.gargoylesoftware.css.parser.CSSParseException;
import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.BrowserVersion.BrowserVersionBuilder;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.html.parser.HTMLParserListener;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptErrorListener;
import com.gargoylesoftware.htmlunit.javascript.host.URL;
import com.gargoylesoftware.htmlunit.util.Cookie;
import org.apache.commons.logging.LogFactory;
import org.junit.Assert;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Set;
import java.util.logging.Level;


public class ForeSignalAuth {


    private WebClient webClient = null;
    private final String landingPageUrl = "https://foresignal.com/en/login/index";


    public ForeSignalAuth() throws IOException {
        final String applicationName = "Chrome";
        final String applicationVersion = "83.0.4103.97";
        final String userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.97 Safari/537.36";
        final int browserVersionNumeric = 83;
        final BrowserVersionBuilder builder = new BrowserVersionBuilder(BrowserVersion.CHROME);
        final BrowserVersion browser = builder.setApplicationName(applicationName).setApplicationVersion(applicationVersion).setUserAgent(userAgent).build();
        this.webClient = new WebClient(browser);
        this.webClient.addRequestHeader(HttpHeader.ACCEPT_LANGUAGE, "en-US,en;q=0.5");
        this.webClient.addRequestHeader(HttpHeader.USER_AGENT, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.97 Safari/537.36");
        this.webClient.addRequestHeader(HttpHeader.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        this.webClient.addRequestHeader(HttpHeader.ACCEPT_ENCODING, "gzip, deflate, br");
        this.webClient.addRequestHeader(HttpHeader.CACHE_CONTROL, "max-age=0");
        this.webClient.addRequestHeader(HttpHeader.SEC_FETCH_DEST, "document");
        this.webClient.addRequestHeader(HttpHeader.SEC_FETCH_SITE, "same-origin");
        this.webClient.addRequestHeader(HttpHeader.SEC_FETCH_MODE, "navigate");
        this.webClient.addRequestHeader(HttpHeader.SEC_FETCH_USER, "?1");
        this.webClient.addRequestHeader(HttpHeader.UPGRADE_INSECURE_REQUESTS, "1");
        this.webClient.getOptions().setJavaScriptEnabled(true);
        this.webClient.getOptions().setCssEnabled(false);
        //this.webClient.getOptions().setWebSocketEnabled(true);
        this.webClient.getOptions().setUseInsecureSSL(true);
        this.webClient.getCookieManager().setCookiesEnabled(true);
        this.webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        this.webClient.getOptions().setThrowExceptionOnScriptError(false);
        // avoid a bunch of logs
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);
        this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        this.webClient.getOptions().setThrowExceptionOnScriptError(false);
        this.webClient.waitForBackgroundJavaScript(10000);
        this.webClient.setIncorrectnessListener(new IncorrectnessListener() {

            @Override
            public void notify(String arg0, Object arg1) {
                // TODO Auto-generated method stub

            }
        });
        this.webClient.setCssErrorHandler(new CSSErrorHandler() {

            @Override
            public void warning(CSSParseException exception) throws CSSException {
                // TODO Auto-generated method stub

            }

            @Override
            public void fatalError(CSSParseException exception) throws CSSException {
                // TODO Auto-generated method stub

            }

            @Override
            public void error(CSSParseException exception) throws CSSException {
                // TODO Auto-generated method stub

            }
        });
        this.webClient.setJavaScriptErrorListener(new JavaScriptErrorListener() {

            @Override
            public void timeoutError(HtmlPage arg0, long arg1, long arg2) {
                // TODO Auto-generated method stub

            }

            @Override
            public void scriptException(HtmlPage arg0, ScriptException arg1) {
                // TODO Auto-generated method stub

            }

            @Override
            public void malformedScriptURL(HtmlPage arg0, String arg1, MalformedURLException arg2) {
                // TODO Auto-generated method stub

            }

            @Override
            public void loadScriptError(HtmlPage htmlPage, java.net.URL url, Exception e) {

            }

            @Override
            public void warn(String s, String s1, int i, String s2, int i1) {

            }

        });
        this.webClient.setHTMLParserListener(new HTMLParserListener() {

            @Override
            public void error(String s, java.net.URL url, String s1, int i, int i1, String s2) {

            }

            @Override
            public void warning(String s, java.net.URL url, String s1, int i, int i1, String s2) {

            }
        });

    }

    public void getCookies() {
        CookieManager manager = this.webClient.getCookieManager();
        Set<Cookie> cookies = manager.getCookies();
        for(Cookie cookie : cookies) {
            System.out.println(cookie);
        }
    }

    public void authenticate() throws Exception {
        final HtmlPage page = this.webClient.getPage(landingPageUrl);
        final HtmlForm form = page.getForms().get(0);
        HtmlElement button = form.getElementsByTagName("button").get(0);
        final HtmlTextInput textFieldUserName = form.getInputByName("user_name");
        final HtmlPasswordInput passwordInput = form.getInputByName("user_password");
        final HtmlCheckBoxInput checkbox = form.getInputByName("set_remember_me_cookie");
        textFieldUserName.type("jianw851");
        passwordInput.type("Lover2!!");
        checkbox.setChecked(true);
        final HtmlPage indexPage = button.click();
        Assert.assertTrue(indexPage.asText().contains("My account"));
    }

    public String getTargetContent(final String url) throws Exception {
        final HtmlPage page = this.webClient.getPage(url);
        Assert.assertTrue(page.asText().contains("My account"));
        return page.asText();
    }

} 
