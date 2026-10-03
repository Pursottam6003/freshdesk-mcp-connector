package freshdesk_connector.razorpay_assignment.tool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class McpControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetMcpReturns405() throws Exception {
        mockMvc.perform(get("/mcp"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void testInitialize() throws Exception {
        String req = """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "initialize",
                  "params": {}
                }
                """;

        mockMvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.protocolVersion").value("2024-11-05"))
                .andExpect(jsonPath("$.result.serverInfo.name").value("freshdesk-connector"));
    }

    @Test
    void testNotificationInitializedReturns202() throws Exception {
        String req = """
                {
                  "jsonrpc": "2.0",
                  "method": "notifications/initialized"
                }
                """;

        mockMvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isAccepted());
    }

    @Test
    void testToolsList() throws Exception {
        String req = """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "method": "tools/list"
                }
                """;

        mockMvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools[?(@.name=='search_tickets')]").exists())
                .andExpect(jsonPath("$.result.tools[?(@.name=='list_tickets')]").exists())
                .andExpect(jsonPath("$.result.tools[?(@.name=='get_ticket')]").exists())
                .andExpect(jsonPath("$.result.tools[?(@.name=='get_ticket_conversations')]").exists());
    }

    @Test
    void testToolsCallSearchTickets() throws Exception {
        String req = """
                {
                  "jsonrpc": "2.0",
                  "id": 3,
                  "method": "tools/call",
                  "params": {
                    "name": "search_tickets",
                    "arguments": {
                      "query": "payment",
                      "limit": 5
                    }
                  }
                }
                """;

        mockMvc.perform(post("/mcp").contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.structuredContent.search_scope").exists())
                .andExpect(jsonPath("$.result.structuredContent.search_scope.candidate_limit").value(300))
                .andExpect(jsonPath("$.result.structuredContent.meta.source").value("mock"));
    }
}