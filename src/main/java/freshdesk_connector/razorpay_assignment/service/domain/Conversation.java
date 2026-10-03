// Conversation.java
package freshdesk_connector.razorpay_assignment.service.domain;


import java.time.Instant;

public record Conversation(
        Long id,
        Long userId,
        String body,
        boolean bodyTruncated,
        Boolean incoming,
        Instant createdAt
) {}