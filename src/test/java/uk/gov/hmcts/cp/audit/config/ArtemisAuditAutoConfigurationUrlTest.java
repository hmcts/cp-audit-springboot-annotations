package uk.gov.hmcts.cp.audit.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ArtemisAuditAutoConfigurationUrlTest {

    @InjectMocks
    ArtemisAuditAutoConfiguration config;

    @Test
    void building_single_host_url_should_contain_ssl_and_snake_case_params() {
        final AuditProperties props = AuditProperties.builder()
                .enabled(true)
                .hosts(List.of("artemis-broker.internal"))
                .build();

        final String url = config.buildConnectionUrl(props);

        assertThat(url)
                .contains("tcp://artemis-broker.internal:61616")
                .contains("sslEnabled=true")
                .contains("verifyHost=false")
                .contains("ha=false")
                .contains("reconnect_attempts=")
                .contains("call_timeout=")
                .contains("connection_ttl=")
                .doesNotContain("reconnectAttempts")
                .doesNotContain("callTimeout");
    }

    @Test
    void building_two_host_url_should_enable_ha_and_produce_two_broker_entries() {
        final AuditProperties props = AuditProperties.builder()
                .enabled(true)
                .hosts(List.of("artemis-primary.internal", "artemis-secondary.internal"))
                .build();

        final String url = config.buildConnectionUrl(props);

        assertThat(url)
                .contains("tcp://artemis-primary.internal:61616")
                .contains("tcp://artemis-secondary.internal:61616")
                .contains("ha=true")
                .contains("sslEnabled=true")
                .contains("verifyHost=false");
    }
}
