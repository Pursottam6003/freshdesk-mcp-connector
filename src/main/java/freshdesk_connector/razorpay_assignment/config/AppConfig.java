package freshdesk_connector.razorpay_assignment.config;

import freshdesk_connector.razorpay_assignment.client.FreshdeskClient;
import freshdesk_connector.razorpay_assignment.client.http.FreshdeskHttp;
import freshdesk_connector.razorpay_assignment.client.http.RetryExecutor;
import freshdesk_connector.razorpay_assignment.client.http.RetryPolicy;
import freshdesk_connector.razorpay_assignment.client.http.Sleeper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Bean
    public Sleeper sleeper() {
        return Thread::sleep;
    }

    @Bean
    public RetryPolicy retryPolicy(FreshdeskProperties properties) {
        FreshdeskProperties.RetryConfig rc = properties.retry();
        return new RetryPolicy(rc.maxAttempts(), rc.baseBackoffMs(), rc.maxBackoffMs(), rc.maxWaitSeconds());
    }

    @Bean
    public RetryExecutor retryExecutor(RetryPolicy policy, Sleeper sleeper) {
        return new RetryExecutor(policy, sleeper);
    }

    @Bean
    public FreshdeskHttp freshdeskHttp(FreshdeskProperties properties, RetryExecutor retryExecutor, ObjectMapper objectMapper) {
        return new FreshdeskHttp(properties, retryExecutor, objectMapper);
    }

    @Bean
    public FreshdeskClient freshdeskClient(FreshdeskHttp http, ObjectMapper objectMapper) {
        return new FreshdeskClient(http, objectMapper);
    }
}