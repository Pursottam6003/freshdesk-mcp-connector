// TicketStatus.java
package freshdesk_connector.razorpay_assignment.service.domain;

public enum TicketStatus {
    OPEN(2),
    PENDING(3),
    RESOLVED(4),
    CLOSED(5),
    UNRESOLVED(-1);

    private final int code;
    TicketStatus(int code) { this.code = code; }
    public int getCode() { return code; }

    public static TicketStatus fromCode(Integer code) {
        if (code == null) return null;
        for (TicketStatus s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }

    public static TicketStatus fromString(String val) {
        if (val == null) return null;
        return valueOf(val.trim().toUpperCase());
    }
}