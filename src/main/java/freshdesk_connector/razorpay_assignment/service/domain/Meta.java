// Meta.java
package freshdesk_connector.razorpay_assignment.service.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Meta(
        String source,
        @JsonProperty("upstream_calls") int upstreamCalls,
        int retries
) {}