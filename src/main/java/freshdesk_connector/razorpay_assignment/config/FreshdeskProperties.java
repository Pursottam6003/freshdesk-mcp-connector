package freshdesk_connector.razorpay_assignment.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.regex.Pattern;

@Validated
@ConfigurationProperties(prefix = "freshdesk")
public record FreshdeskProperties(
        @NotBlank String domain,
        @NotBlank String apiKey,
        String baseUrl,
        @Min(100) int connectTimeoutMs,
        @Min(100) int readTimeoutMs,
        @NotNull RetryConfig retry,
        @NotNull ListingConfig listing,
        @NotNull ConversationsConfig conversations
) {
    private static final Pattern DOMAIN_PATTERN =
            Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-]*(\\.freshdesk\\.com)?$");

    public record RetryConfig(
            @Min(0) @Max(10) int maxAttempts,
            @Min(50) long baseBackoffMs,
            @Min(100) long maxBackoffMs,
            @Min(1) int maxWaitSeconds
    ) {}

    public record ListingConfig(
            @Min(1) @Max(20) int maxPagesPerCall
    ) {}

    public record ConversationsConfig(
            boolean allowPrivateNotes,
            @Min(1) @Max(20) int maxPages,
            @Min(100) int maxBodyLength
    ) {}

    @PostConstruct
    public void validate() {
        if (!"mock".equalsIgnoreCase(domain) && !DOMAIN_PATTERN.matcher(domain).matches()) {
            throw new IllegalArgumentException("Invalid Freshdesk domain format: " + domain);
        }
    }

    public String resolveApiBaseUrl() {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        }
        String cleanDomain = domain.toLowerCase();
        if (!cleanDomain.endsWith(".freshdesk.com")) {
            cleanDomain = cleanDomain + ".freshdesk.com";
        }
        return "https://" + cleanDomain;
    }
}