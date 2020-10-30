package feed;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.BrowserVersion.BrowserVersionBuilder;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.util.Cookie;
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
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
        this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        this.webClient.waitForBackgroundJavaScript(10000);
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
        textFieldUserName.type("jianw851");
        passwordInput.type("Lover2!!");
        final HtmlPage indexPage = button.click();
        Assert.assertTrue(indexPage.asText().contains("My account"));
    }

    public String getTargetContent(final String url) throws Exception {
        final HtmlPage page = this.webClient.getPage(url);
        Assert.assertTrue(page.asText().contains("My account"));
        return page.asText();
    }

} 
