package freshdesk_connector.razorpay_assignment.tool;

import java.util.List;
import java.util.Map;

public class ToolSchemas {

    public static ToolDefinition searchTicketsDefinition() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "Keywords matched client-side against subject and tags (single-word stems like 'payment' or 'fail' work best)"),
                        "status", Map.of("type", "string", "enum", List.of("open", "pending", "resolved", "closed", "unresolved")),
                        "priority", Map.of("description", "Single priority or list of priorities", "oneOf", List.of(
                                Map.of("type", "string", "enum", List.of("low", "medium", "high", "urgent")),
                                Map.of("type", "array", "items", Map.of("type", "string", "enum", List.of("low", "medium", "high", "urgent")))
                        )),
                        "tags", Map.of("type", "string", "description", "Freshdesk tag filter"),
                        "type", Map.of("type", "string", "description", "Freshdesk ticket type"),
                        "created_after", Map.of("type", "string", "description", "Date YYYY-MM-DD"),
                        "limit", Map.of("type", "integer", "default", 20, "maximum", 100)
                )
        );

        return new ToolDefinition(
                "search_tickets",
                "Discovery tool with explicit limits. Searches Freshdesk tickets with server-side filters and client-side keyword matching. Upstream candidate pool hard-capped at 300.",
                inputSchema,
                Map.of("type", "object")
        );
    }

    public static ToolDefinition listTicketsDefinition() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "status", Map.of("type", "string", "enum", List.of("open", "pending", "resolved", "closed", "unresolved")),
                        "limit", Map.of("type", "integer", "default", 25, "maximum", 100),
                        "cursor", Map.of("type", "string", "description", "Opaque pagination cursor from previous call"),
                        "updated_since", Map.of("type", "string", "description", "ISO-8601 timestamp. Note: without this, Freshdesk list only returns tickets created in the last 30 days.")
                )
        );

        return new ToolDefinition(
                "list_tickets",
                "Deterministic paginated ticket retrieval. Status filtering scans upstream pages (bounded to 5 pages/500 tickets per call).",
                inputSchema,
                Map.of("type", "object")
        );
    }

    public static ToolDefinition getTicketDefinition() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "ticket_id", Map.of("type", "integer", "description", "Ticket ID")
                ),
                "required", List.of("ticket_id")
        );

        return new ToolDefinition(
                "get_ticket",
                "Retrieve full details of a single ticket (description, tags, custom fields). Never includes conversations to protect private notes.",
                inputSchema,
                Map.of("type", "object")
        );
    }

    public static ToolDefinition getTicketConversationsDefinition() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "ticket_id", Map.of("type", "integer", "description", "Ticket ID"),
                        "include_private_notes", Map.of("type", "boolean", "default", false, "description", "Opt-in to private notes. Requires operator gate FRESHDESK_ALLOW_PRIVATE_NOTES=true.")
                ),
                "required", List.of("ticket_id")
        );

        return new ToolDefinition(
                "get_ticket_conversations",
                "Retrieve customer-visible conversation thread for a ticket. Private notes excluded by default.",
                inputSchema,
                Map.of("type", "object")
        );
    }
}