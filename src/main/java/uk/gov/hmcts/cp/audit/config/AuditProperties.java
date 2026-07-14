package uk.gov.hmcts.cp.audit.config;

import lombok.Builder;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Builder
@ConfigurationProperties(prefix = "cp.audit")
public class AuditProperties {

    private final List<String> hosts;
    private final int port;
    private final String user;
    private final String password;

    private final boolean highAvailability;

    private final boolean sslEnabled;
    private final boolean verifyHost;
    private final boolean clientAuthRequired;

    private final String keystore;
    private final String keystorePassword;

    private final String truststore;
    private final String truststorePassword;

    @Builder.Default
    private final JmsProperties jms = JmsProperties.builder().build();

    @Getter
    @Builder
    public static class JmsProperties {
        @Builder.Default private final int sessionCacheSize = 10;
        @Builder.Default private final int reconnectAttempts = -1;
        @Builder.Default private final int initialConnectAttempts = 10;
        @Builder.Default private final long retryIntervalMs = 2_000;
        @Builder.Default private final double retryMultiplier = 1.5;
        @Builder.Default private final long maxRetryIntervalMs = 30_000;
        @Builder.Default private final long connectionTtlMs = 60_000;
        @Builder.Default private final long callTimeoutMs = 15_000;
    }
}
