package freshdesk_connector.razorpay_assignment.tool;

import freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;
import freshdesk_connector.razorpay_assignment.service.SearchQuery;
import freshdesk_connector.razorpay_assignment.service.TicketService;
import freshdesk_connector.razorpay_assignment.service.domain.TicketPriority;
import freshdesk_connector.razorpay_assignment.service.domain.TicketStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/mcp")
public class McpController {
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private final ToolRegistry toolRegistry;
    private final TicketService ticketService;
    private final ObjectMapper objectMapper;

    public McpController(ToolRegistry toolRegistry, TicketService ticketService, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.ticketService = ticketService;
        this.objectMapper = objectMapper;
    }

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<Void> handleGet() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .header(HttpHeaders.ALLOW, HttpMethod.POST.name())
                .build();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handleJsonRpc(@RequestBody JsonNode root) {
        if (!root.isObject()) {
            return ResponseEntity.ok(buildJsonRpcError(null, -32600, "Invalid Request: Expected JSON object"));
        }

        JsonNode idNode = root.get("id");
        Object id = idNode != null && !idNode.isNull() ? (idNode.isNumber() ? idNode.numberValue() : idNode.asText()) : null;
        String method = root.hasNonNull("method") ? root.get("method").asText() : "";

        // Notification handlers
        if (id == null) {
            if ("notifications/initialized".equals(method)) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).build();
            }
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }

        return switch (method) {
            case "initialize" -> ResponseEntity.ok(buildInitializeResponse(id));
            case "ping" -> ResponseEntity.ok(Map.of("jsonrpc", "2.0", "id", id, "result", Map.of()));
            case "tools/list" -> ResponseEntity.ok(buildToolsListResponse(id));
            case "tools/call" -> ResponseEntity.ok(handleToolsCall(id, root.get("params")));
            default -> ResponseEntity.ok(buildJsonRpcError(id, -32601, "Method not found: " + method));
        };
    }

    private Map<String, Object> buildInitializeResponse(Object id) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", Map.of(
                        "protocolVersion", PROTOCOL_VERSION,
                        "capabilities", Map.of("tools", Map.of("listChanged", false)),
                        "serverInfo", Map.of("name", "freshdesk-connector", "version", "1.0.0")
                )
        );
    }

    private Map<String, Object> buildToolsListResponse(Object id) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (ToolDefinition def : toolRegistry.listTools()) {
            tools.add(Map.of(
                    "name", def.name(),
                    "description", def.description(),
                    "inputSchema", def.inputSchema(),
                    "outputSchema", def.outputSchema()
            ));
        }
        return Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "result", Map.of("tools", tools)
        );
    }

    private Map<String, Object> handleToolsCall(Object id, JsonNode params) {
        if (params == null || !params.hasNonNull("name")) {
            return buildJsonRpcError(id, -32602, "Invalid params: 'name' is required");
        }
        String name = params.get("name").asText();
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : objectMapper.createObjectNode();

        try {
            Object resultData = executeTool(name, arguments);
            String jsonText = objectMapper.writeValueAsString(resultData);

            return Map.of(
                    "jsonrpc", "2.0",
                    "id", id,
                    "result", Map.of(
                            "content", List.of(Map.of("type", "text", "text", jsonText)),
                            "structuredContent", resultData,
                            "isError", false
                    )
            );
        } catch (Throwable t) {
            ToolErrorMapper.ToolErrorResult err = ToolErrorMapper.toToolError(t);
            try {
                String errJsonText = objectMapper.writeValueAsString(err.structuredError());
                return Map.of(
                        "jsonrpc", "2.0",
                        "id", id,
                        "result", Map.of(
                                "content", List.of(Map.of("type", "text", "text", errJsonText)),
                                "structuredContent", err.structuredError(),
                                "isError", true
                        )
                );
            } catch (Exception e) {
                return buildJsonRpcError(id, -32603, "Internal tool error serialization failure");
            }
        }
    }

    private Object executeTool(String name, JsonNode args) {
        return switch (name) {
            case "search_tickets" -> executeSearchTickets(args);
            case "list_tickets" -> executeListTickets(args);
            case "get_ticket" -> executeGetTicket(args);
            case "get_ticket_conversations" -> executeGetTicketConversations(args);
            default -> throw new FreshdeskException(ErrorCode.INVALID_REQUEST, "Unknown tool: " + name);
        };
    }

    private Object executeSearchTickets(JsonNode args) {
        String query = args.hasNonNull("query") ? args.get("query").asText() : null;
        TicketStatus status = args.hasNonNull("status") ? TicketStatus.fromString(args.get("status").asText()) : null;
        List<TicketPriority> priorities = new ArrayList<>();
        if (args.hasNonNull("priority")) {
            JsonNode pNode = args.get("priority");
            if (pNode.isArray()) {
                for (JsonNode n : pNode) {
                    priorities.add(TicketPriority.fromString(n.asText()));
                }
            } else {
                priorities.add(TicketPriority.fromString(pNode.asText()));
            }
        }
        String tag = args.hasNonNull("tags") ? args.get("tags").asText() : null;
        String type = args.hasNonNull("type") ? args.get("type").asText() : null;
        String createdAfter = args.hasNonNull("created_after") ? args.get("created_after").asText() : null;
        int limit = args.hasNonNull("limit") ? args.get("limit").asInt() : 20;

        SearchQuery sq = new SearchQuery(query, status, priorities, tag, type, createdAfter, limit);
        return ticketService.searchTickets(sq);
    }

    private Object executeListTickets(JsonNode args) {
        TicketStatus status = args.hasNonNull("status") ? TicketStatus.fromString(args.get("status").asText()) : null;
        Integer limit = args.hasNonNull("limit") ? args.get("limit").asInt() : null;
        String cursor = args.hasNonNull("cursor") ? args.get("cursor").asText() : null;
        String updatedSince = args.hasNonNull("updated_since") ? args.get("updated_since").asText() : null;

        return ticketService.listTickets(status, limit, cursor, updatedSince);
    }

    private Object executeGetTicket(JsonNode args) {
        if (!args.hasNonNull("ticket_id")) {
            throw new IllegalArgumentException("Field 'ticket_id' is required");
        }
        long ticketId = args.get("ticket_id").asLong();
        return ticketService.getTicket(ticketId);
    }

    private Object executeGetTicketConversations(JsonNode args) {
        if (!args.hasNonNull("ticket_id")) {
            throw new IllegalArgumentException("Field 'ticket_id' is required");
        }
        long ticketId = args.get("ticket_id").asLong();
        Boolean includePrivate = args.hasNonNull("include_private_notes") ? args.get("include_private_notes").asBoolean() : false;
        return ticketService.getTicketConversations(ticketId, includePrivate);
    }

    private Map<String, Object> buildJsonRpcError(Object id, int code, String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("jsonrpc", "2.0");
        err.put("id", id);
        err.put("error", Map.of("code", code, "message", message));
        return err;
    }
}