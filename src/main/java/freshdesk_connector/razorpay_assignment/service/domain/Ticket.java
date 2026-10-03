// Ticket.java
package freshdesk_connector.razorpay_assignment.service.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record Ticket(
        Long id,
        String subject,
        String description,
        TicketStatus status,
        TicketPriority priority,
        Long requesterId,
        Long responderId,
        Long groupId,
        String type,
        List<String> tags,
        Map<String, Object> customFields,
        Instant createdAt,
        Instant updatedAt
) {}