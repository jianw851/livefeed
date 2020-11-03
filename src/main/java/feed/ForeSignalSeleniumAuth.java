package feed;


import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.IOException;

import static java.lang.Thread.sleep;


public class ForeSignalSeleniumAuth {

    private final static Logger logger = Logger.getLogger(ForeSignalSeleniumAuth.class);
    private WebDriver driver = null;
    private final String landingPageUrl = "https://foresignal.com/en/login/index";


    public ForeSignalSeleniumAuth() throws IOException {
        System.setProperty("webdriver.chrome.driver", "/home/jwang/livefeed/chromedriver");
        driver = new ChromeDriver();
        logger.info("Initialize Selenium WebDriver done ...");
    }

    public String authenticate(final String targetUrl, int depth) throws Exception {
        // 1. click login
        // WebElement loginHref = driver.findElement(By.cssSelector("btn.btn-default.btn-block"));
        WebElement loginHref = driver.findElement(By.linkText("Login"));
        loginHref.click();
        // 2. put username and password
        sleep(3000);
        WebElement username = driver.findElement(By.id("user_name"));
        username.sendKeys("jianw851");
        sleep(3000);
        WebElement password = driver.findElement(By.id("user_password"));
        password.sendKeys("Lover2!!");
        // 3. checkbox
        sleep(1000);
        WebElement remember = driver.findElement(By.name("set_remember_me_cookie"));
        remember.click();
        // 4. submit form
        sleep(3000);
        WebElement submit = driver.findElement(By.xpath("//*[@type='submit']"));
        submit.click();
        return stateMachine(targetUrl, depth);
    }

    private String stateMachine(final String targetUrl, int depth) throws Exception {
        if (depth > 6) {
            throw new Exception("stateMachine recursion depth > 6, something wrong with Web Crawler");
        }
        // System.out.println(page.asText());
        String webContent = driver.getPageSource();
        if(webContent.contains("We're sorry")) {
            // throw new Exception("being recognized as robot! fuck!");
            return kickReCaptchaAss(targetUrl, depth+1);
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


    public String kickReCaptchaAss(final String targetUrl, int depth) throws Exception {
        sleep(3000);
        WebElement frame = driver.findElement(By.xpath("//iframe[contains(@src, 'recaptcha')]"));
        driver.switchTo().frame(frame);
        WebElement reCaptchaAnchor = driver.findElement(By.xpath("//*[@id='recaptcha-anchor']"));
        reCaptchaAnchor.click();
        sleep(2000);
        // WebElement checkmark = driver.findElement(By.xpath("//*[@class='recaptcha-checkbox-checkmark']"));
        // checkmark.click();
        driver.switchTo().defaultContent();
        sleep(3000);
        WebElement submit = driver.findElement(By.xpath("//*[@type='submit']"));
        submit.click();
        return stateMachine(targetUrl, depth);
    }

    public String getTargetContent(final String targetUrl, int depth) throws Exception {
        driver.get(targetUrl);
        String webContent = driver.getPageSource();
        Document page = Jsoup.parse(webContent);
        Element body = page.body();
        if (!body.text().contains("My account")) {
            return stateMachine(targetUrl, depth);
        } else {
            // System.out.println(webContent);
            logger.info("WebClient parse webpage done, url: " + targetUrl);
            return webContent;
        }
    }
}
