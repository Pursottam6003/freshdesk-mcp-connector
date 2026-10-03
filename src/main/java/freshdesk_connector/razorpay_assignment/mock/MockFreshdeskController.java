package freshdesk_connector.razorpay_assignment.mock;

import freshdesk_connector.razorpay_assignment.client.dto.FdConversation;
import  freshdesk_connector.razorpay_assignment.client.dto.FdSearchResponse;
import  freshdesk_connector.razorpay_assignment.client.dto.FdTicket;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@Profile("mock")
public class MockFreshdeskController {
    private final MockDataStore dataStore;
    private final MockFaultManager faultManager;

    public MockFreshdeskController(MockDataStore dataStore, MockFaultManager faultManager) {
        this.dataStore = dataStore;
        this.faultManager = faultManager;
    }

    // --- Fault Injection Admin Endpoints ---
    @PostMapping("/mock-admin/faults")
    public ResponseEntity<Map<String, Object>> setFaults(@RequestBody Map<String, Object> body) {
        if (body.containsKey("reset") && Boolean.TRUE.equals(body.get("reset"))) {
            faultManager.reset();
            return ResponseEntity.ok(Map.of("status", "reset"));
        }
        if (body.containsKey("arm_429")) {
            int count = (int) body.getOrDefault("arm_429", 1);
            int retryAfter = (int) body.getOrDefault("retry_after", 2);
            faultManager.arm429(count, retryAfter);
        }
        if (body.containsKey("arm_500")) {
            int count = (int) body.getOrDefault("arm_500", 1);
            faultManager.arm500(count);
        }
        if (body.containsKey("arm_401")) {
            faultManager.arm401(Boolean.TRUE.equals(body.get("arm_401")));
        }
        if (body.containsKey("arm_delay_ms")) {
            faultManager.armDelay((int) body.get("arm_delay_ms"));
        }
        return ResponseEntity.ok(Map.of("status", "configured"));
    }

    // --- Mock Freshdesk API v2 Endpoints ---
    @GetMapping("/mock-freshdesk/api/v2/tickets/{id}")
    public ResponseEntity<?> getTicket(
            @RequestHeader HttpHeaders headers,
            @PathVariable("id") long id,
            @RequestParam(value = "include", required = false) String include) {
        ResponseEntity<?> fault = checkFaults(headers);
        if (fault != null) return fault;

        if (include != null && include.contains("conversations")) {
            // Guard enforcement check: our client should never request include=conversations
            return ResponseEntity.badRequest().body(Map.of("description", "include=conversations is prohibited by connector architecture"));
        }

        return dataStore.getTicket(id)
                .<ResponseEntity<?>>map(ticket -> ResponseEntity.ok()
                        .header("X-Mock-Data", "fictional")
                        .body(ticket))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("description", "Ticket not found")));
    }

    @GetMapping("/mock-freshdesk/api/v2/tickets")
    public ResponseEntity<?> listTickets(
            @RequestHeader HttpHeaders headers,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int per_page) {
        ResponseEntity<?> fault = checkFaults(headers);
        if (fault != null) return fault;

        List<FdTicket> all = dataStore.getAllTicketsSorted();
        int from = (page - 1) * per_page;
        if (from >= all.size()) {
            return ResponseEntity.ok().header("X-Mock-Data", "fictional").body(List.of());
        }
        int to = Math.min(from + per_page, all.size());
        List<FdTicket> pageItems = all.subList(from, to);

        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.add("X-Mock-Data", "fictional");
        if (to < all.size()) {
            respHeaders.add("Link", "</mock-freshdesk/api/v2/tickets?page=" + (page + 1) + "&per_page=" + per_page + ">; rel=\"next\"");
        }

        return new ResponseEntity<>(pageItems, respHeaders, HttpStatus.OK);
    }

    @GetMapping("/mock-freshdesk/api/v2/search/tickets")
    public ResponseEntity<?> searchTickets(
            @RequestHeader HttpHeaders headers,
            @RequestParam String query,
            @RequestParam(defaultValue = "1") int page) {
        ResponseEntity<?> fault = checkFaults(headers);
        if (fault != null) return fault;

        if (page > 10) {
            return ResponseEntity.badRequest().body(Map.of("description", "Page limit exceeded (max 10)"));
        }

        String decodedQuery = URLDecoder.decode(query, StandardCharsets.UTF_8);
        if (decodedQuery.startsWith("\"") && decodedQuery.endsWith("\"")) {
            decodedQuery = decodedQuery.substring(1, decodedQuery.length() - 1);
        }

        List<FdTicket> matched = new ArrayList<>();
        for (FdTicket t : dataStore.getAllTicketsSorted()) {
            if (evalMockQuery(t, decodedQuery)) {
                matched.add(t);
            }
        }

        int total = matched.size();
        int from = (page - 1) * 30;
        List<FdTicket> results = (from >= matched.size()) ? List.of() : matched.subList(from, Math.min(from + 30, matched.size()));

        return ResponseEntity.ok()
                .header("X-Mock-Data", "fictional")
                .body(new FdSearchResponse(total, results));
    }

    @GetMapping("/mock-freshdesk/api/v2/tickets/{id}/conversations")
    public ResponseEntity<?> listConversations(
            @RequestHeader HttpHeaders headers,
            @PathVariable("id") long id,
            @RequestParam(defaultValue = "1") int page) {
        ResponseEntity<?> fault = checkFaults(headers);
        if (fault != null) return fault;

        List<FdConversation> all = dataStore.getConversations(id);
        int perPage = 30;
        int from = (page - 1) * perPage;
        if (from >= all.size()) {
            return ResponseEntity.ok().header("X-Mock-Data", "fictional").body(List.of());
        }
        int to = Math.min(from + perPage, all.size());
        List<FdConversation> pageItems = all.subList(from, to);

        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.add("X-Mock-Data", "fictional");
        if (to < all.size()) {
            respHeaders.add("Link", "</mock-freshdesk/api/v2/tickets/" + id + "/conversations?page=" + (page + 1) + ">; rel=\"next\"");
        }

        return new ResponseEntity<>(pageItems, respHeaders, HttpStatus.OK);
    }

    private ResponseEntity<?> checkFaults(HttpHeaders headers) {
        String auth = headers.getFirst(HttpHeaders.AUTHORIZATION);
        if (faultManager.isInject401() || auth == null || !auth.startsWith("Basic ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
        }

        if (faultManager.consume429() > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", String.valueOf(faultManager.getRetryAfterSeconds()))
                    .body(Map.of("message", "Rate limit exceeded"));
        }

        if (faultManager.consume500() > 0) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Simulated internal server error"));
        }

        if (faultManager.getDelayMillis() > 0) {
            try {
                Thread.sleep(faultManager.getDelayMillis());
            } catch (InterruptedException ignored) {}
        }

        return null;
    }

    private boolean evalMockQuery(FdTicket t, String q) {
        if (q.isBlank()) return true;
        // Supports basic AND clauses for status, priority, tag
        String[] parts = q.split(" AND ");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("status:")) {
                int st = Integer.parseInt(part.substring("status:".length()));
                if (t.status() == null || t.status() != st) return false;
            } else if (part.startsWith("priority:")) {
                int pr = Integer.parseInt(part.substring("priority:".length()));
                if (t.priority() == null || t.priority() != pr) return false;
            } else if (part.contains("status:2 OR status:3 OR status:6 OR status:7")) {
                if (t.status() == null || (t.status() != 2 && t.status() != 3 && t.status() != 6 && t.status() != 7)) return false;
            } else if (part.startsWith("tag:'")) {
                String tag = part.substring(5, part.length() - 1);
                if (t.tags() == null || !t.tags().contains(tag)) return false;
            }
        }
        return true;
    }
}
