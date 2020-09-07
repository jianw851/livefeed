import event.EventPublisher;
import event.EventTopic;
import feed.Feed;
import feed.OandaRestV20;
import feed.OandaSignal;

public class FeedRunner {
    private static FeedRunner INSTANCE = null;
    private final String DELIMITER = "\\|";
    private String feedName = null;
    private String apiName = null;
    private String brokerName = null;
    private String eventType = null;
    private String instrumentType = null;
    private String instrument = null;
    private String topic = null;
    private Feed feed = null;
    private String threshold = null;
    private String accountID = null;
    private String token = null;
    private String brokerEnv = null;
    private EventPublisher publisher = null;

    private FeedRunner() throws Exception {
        this.feedName = System.getenv("FEED_NAME");
        String[] array = feedName.split(DELIMITER);
        this.brokerName = array[0];
        this.eventType = array[1];
        this.instrumentType = array[2];
        this.instrument = array[3];
        this.topic = EventTopic.parseEventTopic(this.feedName);
        // build broker
        this.publisher = EventPublisher.getInstance();
        this.accountID = System.getenv("ACCOUNT_ID");
        this.token = System.getenv("TOKEN");
        this.threshold = System.getenv("THRESHOLD");
        this.apiName = System.getenv("BROKER_API_NAME");
        this.brokerEnv = System.getenv("BROKER_ENV");
        if(brokerName.equalsIgnoreCase("OANDA") && apiName.equalsIgnoreCase("RestV20")) {
            this.feed = new OandaRestV20(publisher, brokerEnv, accountID, instrument, token, Double.valueOf(threshold));
        } else if (brokerName.equalsIgnoreCase("OANDA") && apiName.equalsIgnoreCase("SIGNAL")) {
            this.feed = new OandaSignal(publisher, topic);
        }
    }

    public static FeedRunner getInstance() throws Exception {
        if (INSTANCE == null) {
            INSTANCE = new FeedRunner();
        }
        return INSTANCE;
    }

    /*
    this is a compound feed, different parameter decide which feed to run
    so each kafka record will be send inside each feed
    only exception message will be send in main funtion
     */
    public static void main(String[] args) {
        try {
            FeedRunner feedRunner = FeedRunner.getInstance();
            feedRunner.feed.run();
        } catch (Exception e) {
            // TODO: log exceptions to kafka
            e.printStackTrace();
        }
    }

}
