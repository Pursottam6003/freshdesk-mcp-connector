package freshdesk_connector.razorpay_assignment.tool;

import java.util.Map;

public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema
) {}