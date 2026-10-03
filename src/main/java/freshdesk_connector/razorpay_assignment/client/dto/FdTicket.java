package freshdesk_connector.razorpay_assignment.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FdTicket(
        Long id,
        String subject,
        @JsonProperty("description_text") String descriptionText,
        String description,
        Integer status,
        Integer priority,
        @JsonProperty("requester_id") Long requesterId,
        @JsonProperty("responder_id") Long responderId,
        @JsonProperty("company_id") Long companyId,
        @JsonProperty("group_id") Long groupId,
        String type,
        List<String> tags,
        @JsonProperty("custom_fields") Map<String, Object> customFields,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt,
        @JsonProperty("due_by") Instant dueBy,
        @JsonProperty("fr_due_by") Instant frDueBy
) {}