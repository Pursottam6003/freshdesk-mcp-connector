package freshdesk_connector.razorpay_assignment.client.dto;


import java.util.List;

public record FdSearchResponse(
        int total,
        List<FdTicket> results
) {}