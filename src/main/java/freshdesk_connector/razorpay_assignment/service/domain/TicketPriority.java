// TicketPriority.java
package freshdesk_connector.razorpay_assignment.service.domain;

public enum TicketPriority {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    URGENT(4);

    private final int code;
    TicketPriority(int code) { this.code = code; }
    public int getCode() { return code; }

    public static TicketPriority fromCode(Integer code) {
        if (code == null) return null;
        for (TicketPriority p : values()) {
            if (p.code == code) return p;
        }
        return null;
    }

    public static TicketPriority fromString(String val) {
        if (val == null) return null;
        return valueOf(val.trim().toUpperCase());
    }
}