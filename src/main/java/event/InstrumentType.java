package event;

public enum InstrumentType {
    CURRENCY(1),
    STOCK(2),
    INDEX(3),
    BOND(4),
    FUTURE(5),
    OPTION(6),
    FORWARD(7),
    WARRANT(8),
    SWAP(9),
    CRYPTOCURRENCY(10);
    InstrumentType(int i) {
        this.id = i;
    }

    private int id;
    public int id() {
        return this.id;
    }
}
