package freshdesk_connector.razorpay_assignment.client.http;

import freshdesk_connector.razorpay_assignment.client.error.ErrorCode;
import freshdesk_connector.razorpay_assignment.client.error.FreshdeskException;
import freshdesk_connector.razorpay_assignment.config.FreshdeskProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FreshdeskHttp {
    private static final Pattern NEXT_LINK_PATTERN = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");
    private final RestClient restClient;
    private final RetryExecutor retryExecutor;
    private final ObjectMapper objectMapper;
    private final boolean isMock;

    public record HttpResponseHolder(
            int statusCode,
            HttpHeaders headers,
            byte[] body,
            boolean hasNextPage,
            String nextLink
    ) {}

    public FreshdeskHttp(FreshdeskProperties properties, RetryExecutor retryExecutor, ObjectMapper objectMapper) {
        this.retryExecutor = retryExecutor;
        this.objectMapper = objectMapper;
        this.isMock = "mock".equalsIgnoreCase(properties.domain());

        String credentials = properties.apiKey() + ":X";
        String basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        this.restClient = RestClient.builder()
                .baseUrl(properties.resolveApiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public HttpResponseHolder get(String uri, RetryExecutor.ExecutionTracker tracker) {
        return retryExecutor.execute(uri, () -> {
            try {
                return restClient.get()
                        .uri(uri)
                        .exchange((request, response) -> {
                            HttpStatusCode status = response.getStatusCode();
                            HttpHeaders headers = response.getHeaders();
                            byte[] body = extractBody(response);

                            if (status.is2xxSuccessful()) {
                                String linkHeader = headers.getFirst(HttpHeaders.LINK);
                                boolean hasNext = false;
                                String nextUrl = null;
                                if (linkHeader != null) {
                                    Matcher matcher = NEXT_LINK_PATTERN.matcher(linkHeader);
                                    if (matcher.find()) {
                                        hasNext = true;
                                        nextUrl = matcher.group(1);
                                    }
                                }
                                return new HttpResponseHolder(status.value(), headers, body, hasNext, nextUrl);
                            }

                            handleErrorResponse(status.value(), headers, body);
                            return null;
                        });
            } catch (ResourceAccessException rae) {
                throw new FreshdeskException(ErrorCode.UPSTREAM_UNAVAILABLE, "Network or timeout error contacting Freshdesk: " + rae.getMessage(), true, null, null, rae);
            }
        }, tracker);
    }

    private byte[] extractBody(ClientHttpResponse response) throws IOException {
        try (InputStream is = response.getBody()) {
            return is.readAllBytes();
        }
    }

    private void handleErrorResponse(int statusCode, HttpHeaders headers, byte[] body) {
        String bodyStr = new String(body, StandardCharsets.UTF_8);
        Integer retryAfter = parseRetryAfter(headers.getFirst(HttpHeaders.RETRY_AFTER));

        switch (statusCode) {
            case 400 -> throw new FreshdeskException(ErrorCode.INVALID_REQUEST, "Invalid request to Freshdesk: " + bodyStr, false, null, parseDetails(bodyStr), null);
            case 401 -> throw new FreshdeskException(ErrorCode.AUTHENTICATION_FAILED, "Freshdesk authentication failed (invalid API key)", false, null, null, null);
            case 403 -> throw new FreshdeskException(ErrorCode.ACCESS_DENIED, "Access forbidden to requested Freshdesk resource", false, null, null, null);
            case 404 -> throw new FreshdeskException(ErrorCode.TICKET_NOT_FOUND, "Resource not found on Freshdesk", false, null, null, null);
            case 429 -> throw new FreshdeskException(ErrorCode.RATE_LIMITED, "Freshdesk rate limit reached", true, retryAfter, null, null);
            case 502, 503, 504 -> throw new FreshdeskException(ErrorCode.UPSTREAM_UNAVAILABLE, "Freshdesk service unavailable (status: " + statusCode + ")", true, retryAfter, null, null);
            case 500 -> throw new FreshdeskException(ErrorCode.UNEXPECTED_RESPONSE, "Freshdesk server returned 500 Internal Server Error", false, null, null, null);
            default -> throw new FreshdeskException(ErrorCode.UNEXPECTED_RESPONSE, "Unexpected Freshdesk status " + statusCode + ": " + bodyStr, false, null, null, null);
        }
    }

    private Integer parseRetryAfter(String headerVal) {
        if (headerVal == null || headerVal.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(headerVal.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Object parseDetails(String bodyStr) {
        try {
            return objectMapper.readValue(bodyStr, Object.class);
        } catch (Exception e) {
            return bodyStr;
        }
    }

    public boolean isMock() {
        return isMock;
    }
}