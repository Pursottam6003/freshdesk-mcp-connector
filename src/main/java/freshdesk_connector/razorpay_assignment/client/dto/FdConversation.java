package freshdesk_connector.razorpay_assignment.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record FdConversation(
        Long id,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("body_text") String bodyText,
        String body,
        @JsonProperty("incoming") Boolean incoming,
        @JsonProperty("private") Boolean isPrivate,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("support_email") String supportEmail,
        @JsonProperty("to_emails") List<String> toEmails
) {}