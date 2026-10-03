package freshdesk_connector.razorpay_assignment.client.dto;

import java.util.List;

public record FdErrorResponse(
        String description,
        List<FdErrorDetail> errors
) {
    public record FdErrorDetail(
            String field,
            String message,
            String code
    ) {}
}