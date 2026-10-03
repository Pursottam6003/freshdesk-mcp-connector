package freshdesk_connector.razorpay_assignment.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolRegistry {
    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    public ToolRegistry() {
        register(ToolSchemas.searchTicketsDefinition());
        register(ToolSchemas.listTicketsDefinition());
        register(ToolSchemas.getTicketDefinition());
        register(ToolSchemas.getTicketConversationsDefinition());
    }

    public void register(ToolDefinition def) {
        tools.put(def.name(), def);
    }

    public Collection<ToolDefinition> listTools() {
        return tools.values();
    }

    public ToolDefinition get(String name) {
        return tools.get(name);
    }
}