package event;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.mortbay.log.Log;

import java.util.Properties;

public class EventPublisher {
    private static EventPublisher INSTANCE = null;
    private static Properties configProperties = null;
    private static Producer producer = null;

    private EventPublisher() {
        configProperties= new Properties();
        configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv("BOOTSTRAP_SERVERS_CONFIG"));
        configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
        configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producer = new KafkaProducer(configProperties);
    }

    public static EventPublisher getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EventPublisher();
        }
        return INSTANCE;
    }

    public void publish(String topic, String message) {
        ProducerRecord<String, String> record = new ProducerRecord<String, String>(topic, message);
        try {
            INSTANCE.producer.send(record);
            System.out.println("sent");
        } catch (Exception e) {
            e.printStackTrace();
            Log.debug(e.getStackTrace().toString());
        }
    }
}
