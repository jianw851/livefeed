package event;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.mortbay.log.Log;

import java.util.Properties;

public class EventPublisher {
    private static final EventPublisher INSTANCE = new EventPublisher();
    private static final Properties configProperties = new Properties();
    private static Producer producer = null;

    private EventPublisher() {
        INSTANCE.configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv("BOOTSTRAP_SERVERS_CONFIG"));
        INSTANCE.configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
        INSTANCE.configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        INSTANCE.producer = new KafkaProducer(INSTANCE.configProperties);
    }

    public static void publish(String topic, String message) {
        ProducerRecord<String, String> record = new ProducerRecord<String, String>(topic, message);
        try {
            INSTANCE.producer.send(record);
        } catch (Exception e) {
            e.printStackTrace();
            Log.debug(e.getStackTrace().toString());
        }
    }
}
