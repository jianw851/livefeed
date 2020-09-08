package feed;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;

import event.EventPublisher;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

public class OandaRestV20 implements Feed {
    // logger
    private final static Logger logger = Logger.getLogger(OandaRestV20.class);
    // account attributes
    private String prodEnvURL = "https://stream-fxtrade.oanda.com";
    private String testEnvURL = "https://stream-fxpractice.oanda.com";
    private String instrument = null;
    private String token = null;
    private String accountID = null;
    private double threshold = 0.00005;
    private String topic = null;
    private HttpClient httpClient = HttpClientBuilder.create().build();
    private EventPublisher publisher = null;
    HttpUriRequest httpGet =  null;


    public OandaRestV20(EventPublisher publisher, String topic, String env, String accountID, String instr, String token, double threshold) throws Exception {
        this.publisher = publisher;
        this.instrument = instr;
        this.token = token;
        this.threshold = threshold;
        this.accountID = accountID;
        this.topic = topic;
        if(env.equalsIgnoreCase("prod")) {
            httpGet = new HttpGet(prodEnvURL + "/v3/accounts/" + this.accountID + "/pricing/stream?instruments=" + this.instrument);
        } else {
            httpGet = new HttpGet(testEnvURL + "/v3/accounts/" + this.accountID + "/pricing/stream?instruments=" + this.instrument);
        }
        httpGet.setHeader(new BasicHeader("Authorization", "Bearer " + this.token));
    }

    @Override
    public void run() throws IOException, RuntimeException, GeneralSecurityException {
        try {
            System.out.println("Executing request: " + this.httpGet.getRequestLine());
            HttpResponse resp = this.httpClient.execute(this.httpGet);
            HttpEntity entity = resp.getEntity();
            double lastbid = 0.0, lastask = 0.0;
            if (resp.getStatusLine().getStatusCode() == 200 && entity != null) {
                InputStream stream = entity.getContent();
                String line;
                BufferedReader br = new BufferedReader(new InputStreamReader(stream));

                while ((line = br.readLine()) != null) {

                    Object obj = JSONValue.parse(line);
                    JSONObject tick = (JSONObject) obj;

                    // filter out heartbeats
                    if (tick.containsKey("instrument")) {
                        String time = (String)tick.get("time");
                        double bid = Double.parseDouble((String)((JSONObject)(((JSONArray) tick.get("bids")).get(0))).get("price"));
                        double ask = Double.parseDouble((String)((JSONObject)(((JSONArray) tick.get("asks")).get(0))).get("price"));
                        if(bid - lastbid > this.threshold || lastbid - bid > this.threshold ||
                                lastask - ask > this.threshold || ask - lastask > this.threshold) {
                            // TODO: write into kafka broker
                            System.out.println(time + "|" + bid + "|" + ask);
                            publisher.publish(topic, time + "|" + bid + "|" + ask);
                            lastbid = bid;
                            lastask = ask;
                        }
                    }
                }
            } else {
                String responseString = EntityUtils.toString(entity, "UTF-8");
                logger.error("http response not 200 or no response, response: " + responseString);
            }
        } catch (Exception e) {
            logger.error("Failed to get pricing stream from oanda rest v20");
        }
    }
}
