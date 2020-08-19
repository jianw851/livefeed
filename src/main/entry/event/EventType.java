package event;

public enum EventType {
    SIGNAL(1),
    PRICING(2);
    EventType(int i) {
        this.id = i;
    }
    private int id;
    public int id() {
        return this.id;
    }
}
