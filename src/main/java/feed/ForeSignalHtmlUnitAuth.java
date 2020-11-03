package feed;

import com.gargoylesoftware.css.parser.CSSException;
import com.gargoylesoftware.css.parser.CSSErrorHandler;
import com.gargoylesoftware.css.parser.CSSParseException;
import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.BrowserVersion.BrowserVersionBuilder;
import com.gargoylesoftware.htmlunit.html.*;
import com.gargoylesoftware.htmlunit.html.parser.HTMLParserListener;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptEngine;
import com.gargoylesoftware.htmlunit.javascript.JavaScriptErrorListener;
import com.gargoylesoftware.htmlunit.util.Cookie;
import net.sourceforge.htmlunit.corejs.javascript.WrappedException;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import static java.lang.Thread.sleep;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Set;
import java.util.logging.Level;


public class ForeSignalHtmlUnitAuth {

    private final static Logger logger = Logger.getLogger(ForeSignalHtmlUnitAuth.class);
    private WebClient webClient = null;
    private final String landingPageUrl = "https://foresignal.com/en/login/index";


    public ForeSignalHtmlUnitAuth() throws IOException {
        final String applicationName = "Firefox";
        final String applicationVersion = "80";
        final String userAgent = "User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:80.0) Gecko/20100101 Firefox/80.0";
        final int browserVersionNumeric = 80;
        final BrowserVersionBuilder builder = new BrowserVersionBuilder(BrowserVersion.FIREFOX);
        final BrowserVersion browser = builder.setApplicationName(applicationName).setApplicationVersion(applicationVersion).setUserAgent(userAgent).build();
        this.webClient = new WebClient(browser);
        // this.webClient = new WebClient(BrowserVersion.FIREFOX, "165.22.36.75", 8888);
        // setupProxy();
        this.webClient.addRequestHeader(HttpHeader.ACCEPT_LANGUAGE, "en-US,en;q=0.5");
        // this.webClient.addRequestHeader(HttpHeader.USER_AGENT, "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/83.0.4103.97 Safari/537.36");
        this.webClient.addRequestHeader(HttpHeader.USER_AGENT, "User-Agent: Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:80.0) Gecko/20100101 Firefox/80.0");
        // this.webClient.addRequestHeader(HttpHeader.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
        this.webClient.addRequestHeader(HttpHeader.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");

        this.webClient.addRequestHeader(HttpHeader.ACCEPT_ENCODING, "gzip, deflate, br");
        this.webClient.addRequestHeader(HttpHeader.CACHE_CONTROL, "max-age=0");
        this.webClient.addRequestHeader(HttpHeader.SEC_FETCH_DEST, "document");
        this.webClient.addRequestHeader(HttpHeader.SEC_FETCH_SITE, "same-origin");
        this.webClient.addRequestHeader(HttpHeader.SEC_FETCH_MODE, "navigate");
        this.webClient.addRequestHeader(HttpHeader.SEC_FETCH_USER, "?1");
        this.webClient.addRequestHeader(HttpHeader.UPGRADE_INSECURE_REQUESTS, "1");
        this.webClient.addRequestHeader(HttpHeader.CONNECTION, "keep-alive");
        this.webClient.getOptions().setJavaScriptEnabled(true);
        this.webClient.getOptions().setCssEnabled(true);
        this.webClient.getOptions().setWebSocketEnabled(true);
        this.webClient.getOptions().setUseInsecureSSL(true);
        this.webClient.getCookieManager().setCookiesEnabled(true);
        this.webClient.setAjaxController(new NicelyResynchronizingAjaxController());
        //this.webClient.getOptions().setRedirectEnabled(false);

        this.webClient.getOptions().setThrowExceptionOnScriptError(false);
        // avoid a bunch of logs
        LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.apache.commons.httpclient").setLevel(Level.OFF);
        this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        this.webClient.getOptions().setThrowExceptionOnScriptError(false);
        this.webClient.getOptions().setTimeout(120000);
        this.webClient.waitForBackgroundJavaScript(120000);
        this.webClient.setJavaScriptTimeout(120000);
        webClient.setAlertHandler(new AlertHandler() {
            public void handleAlert(Page page, String string) {
                System.out.printf("alert: %s%n", string);
            }
        });
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
        logger.info("Initialize WebClient done ...");
    }

    public void setupProxy() {
        ProxyConfig pc = new ProxyConfig();

        // pc.setSocksProxy(true); //Set to false if it is a http server
        pc.setProxyHost("140.227.229.208"); //your proxy IP
        pc.setProxyPort(3128);


        this.webClient.getOptions().setProxyConfig(pc);
        // final DefaultCredentialsProvider credentialsProvider =
        //        (DefaultCredentialsProvider) webClient.getCredentialsProvider();
        // credentialsProvider.addCredentials("jianw851", "Lover2!!");
    }

    public void getCookies() {
        CookieManager manager = this.webClient.getCookieManager();
        Set<Cookie> cookies = manager.getCookies();
        for(Cookie cookie : cookies) {
            System.out.println(cookie);
        }
    }

    public String authenticate(final String targetUrl, int depth) throws Exception {
        final HtmlPage landingPage = loadPage(landingPageUrl);
        sleep(5000);
        final HtmlForm form = landingPage.getForms().get(0);
        HtmlElement button = form.getElementsByTagName("button").get(0);
        final HtmlTextInput textFieldUserName = form.getInputByName("user_name");
        final HtmlPasswordInput passwordInput = form.getInputByName("user_password");
        final HtmlCheckBoxInput checkbox = form.getInputByName("set_remember_me_cookie");
        textFieldUserName.type("jianw851");
        passwordInput.type("Lover2!!");
        checkbox.setChecked(true);
        final HtmlPage indexPage = button.click();
        return stateMachine(targetUrl, indexPage, depth);
    }

    private String stateMachine(final String targetUrl, final HtmlPage page, int depth) throws Exception {
        if (depth > 10) {
            throw new Exception("stateMachine recursion depth > 10, something wrong with Web Crawler");
        }
        // System.out.println(page.asText());
        String webContent = page.asText();
        if(webContent.contains("We're sorry")) {
            // throw new Exception("being recognized as robot! fuck!");
            return kickReCaptchaAss(targetUrl, page, depth+1);
        } else if (!webContent.contains("My account")) {
            sleep(5000);
            logger.info("not yet be authenticated, authenticating ...");
            return authenticate(targetUrl,depth+1);
        } else {
            sleep(5000);
            logger.info("authenticated, loading new target page ...");
            return getTargetContent(targetUrl, depth+1);
        }
    }

    private HtmlPage loadPage(final String url) throws IOException, InterruptedException {
        HtmlPage page = this.webClient.getPage(url);
        // /*
        webClient.waitForBackgroundJavaScript(20000);
        int waitForBackgroundJavaScript = webClient.waitForBackgroundJavaScript(2000);
        int loopCount = 0;
        while (waitForBackgroundJavaScript > 0 && loopCount < 10) {
            sleep(1000);
            ++loopCount;
            waitForBackgroundJavaScript = webClient.waitForBackgroundJavaScript(20000);
            if (waitForBackgroundJavaScript == 0) {
                logger.info("HtmlUnit exits background javascript at loop counter " + loopCount);
                break;
            }
        }
        JavaScriptEngine engine = (JavaScriptEngine) webClient.getJavaScriptEngine();
        engine.holdPosponedActions();
        if(engine.isScriptRunning()) {
            logger.info("js is still running");
        }
        // */
        return page;
    }

    public String kickReCaptchaAss(final String targetUrl, final HtmlPage page, int depth) throws Exception {
        //HtmlPage reCaptchaFrame;
        //final List<FrameWindow> frames = page.getFrames();
        //reCaptchaFrame = (HtmlPage) frames.get(0).getEnclosedPage();
        // initiating to enter the reCaptcha
        HtmlSpan reCaptchaAnchor =  null;
        HtmlElement checkbox = null;
        // while(reCaptchaAnchor == null || checkbox1 == null) {
        reCaptchaAnchor = page.getFirstByXPath(".//*[@id='recaptcha-anchor']");
        checkbox = page.getFirstByXPath(".//*[@id='recaptcha-anchor']/div[@class='recaptcha-checkbox-checkmark']");
        sleep(13000);
        //}
        if (checkbox == null) {
            throw new NullPointerException("Captcha not found");
        }
        HtmlPage validPage = null;
        try {
            // HtmlPage v1 = reCaptchaAnchor.click();
            HtmlPage v2 = checkbox.click(); // here I get the exception
            // System.out.println(v1.asXml());
            System.out.println(v2.asXml());
            // get submit button
            final HtmlForm form = v2.getForms().get(0);
            final HtmlInput submit = form.getInputByValue("Submit");
            validPage = submit.click();
            System.out.println(validPage.asXml());
            logger.info("KickReCaptchaAss done ...");
        } catch (WrappedException e) {
            logger.info("Found some stupid exception {}" + e.details());
        }
        return stateMachine(targetUrl, validPage, depth);
    }

    public String getTargetContent(final String targetUrl, int depth) throws Exception {
        final HtmlPage page = loadPage(targetUrl);
        String webContent = page.asXml();
        if (!webContent.contains("My account")) {
            return stateMachine(targetUrl, page, depth);
        } else {
            System.out.println(webContent);
            logger.info("WebClient parse webpage done, url: " + targetUrl);
            return webContent;
        }
    }

} 
