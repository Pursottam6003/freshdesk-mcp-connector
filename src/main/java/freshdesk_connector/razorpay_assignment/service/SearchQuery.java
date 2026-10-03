package freshdesk_connector.razorpay_assignment.service;

import freshdesk_connector.razorpay_assignment.service.domain.TicketPriority;
import freshdesk_connector.razorpay_assignment.service.domain.TicketStatus;

import java.util.List;

public record SearchQuery(
        String query,
        TicketStatus status,
        List<TicketPriority> priorities,
        String tag,
        String type,
        String createdAfter,
        int limit
) {}