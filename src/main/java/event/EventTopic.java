package event;


public class EventTopic {
    private static final String DELIMITER = "-";
    public static String parseEventTopic(String ds) throws Exception {
        String[] array = ds.split(DELIMITER);
        if(array.length < 4) {
            // to do send to kafka
            throw new Exception();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.valueOf(parseBrokerID(array[0])));
        sb.append(DELIMITER);
        sb.append(String.valueOf(parseInstrumentTypeID(array[1])));
        sb.append(DELIMITER);
        sb.append(String.valueOf(parseEventTypeID(array[2])));
        sb.append(DELIMITER);
        sb.append(array[3]);
        return sb.toString();
    }

    static EventType parseEventTypeFromTopic(String topic) throws Exception {
        String[] array = topic.split(DELIMITER);
        return parseEventType(array[2]);
    }

    private static int parseBrokerID(String b) throws Exception {
        if ("OANDA".equals(b)) {
            return BrokerName.OANDA.id();
        } else if ("IB".equals(b)) {
            return BrokerName.IB.id();
        } else {
            // to do log in kafka
            throw new Exception();
        }
    }

    private static int parseInstrumentTypeID(String i) throws Exception {
        if ("CURRENCY".equals(i)) {
            return InstrumentType.CURRENCY.id();
        } else if ("STOCK".equals(i)) {
            return InstrumentType.STOCK.id();
        } else {
            // to do log in kafka
            throw new Exception();
        }
    }

    private static int parseEventTypeID(String st) throws Exception {
        if ("PRICING".equals(st)) {
            return EventType.PRICING.id();
        } else if ("SIGNAL".equals(st)) {
            return EventType.SIGNAL.id();
        } else {
            // todo log in kafka
            throw new Exception();
        }
    }

    private static EventType parseEventType(String st) throws Exception {
        if ("PRICING".equals(st)) {
            return EventType.PRICING;
        } else if ("SIGNAL".equals(st)) {
            return EventType.SIGNAL;
        } else {
            // todo log in kafka
            throw new Exception();
        }
    }

}
