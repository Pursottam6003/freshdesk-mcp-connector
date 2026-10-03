// ListingScan.java
package freshdesk_connector.razorpay_assignment.service.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ListingScan(
        @JsonProperty("upstream_pages_fetched") int upstreamPagesFetched,
        @JsonProperty("tickets_scanned") int ticketsScanned,
        @JsonProperty("max_pages_budget") int maxPagesBudget
) {}