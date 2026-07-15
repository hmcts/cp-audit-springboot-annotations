package uk.gov.hmcts.cp.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import uk.gov.hmcts.cp.audit.model.AuditEventType;
import uk.gov.hmcts.cp.audit.model.AuditMetadata;
import uk.gov.hmcts.cp.audit.model.AuditPayload;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditSenderServiceTest {

    @Mock private JmsTemplate jmsTemplate;

    private AuditSenderService service;

    @BeforeEach
    void setUp() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        service = new AuditSenderService(jmsTemplate, mapper);
    }

    @Test
    void sending_an_audit_payload_should_publish_json_to_the_audit_topic() {
        final AuditPayload payload = AuditPayload.builder()
                .metadata(AuditMetadata.builder()
                        .origin("test-service")
                        .component("API")
                        .eventName("test.event")
                        .timestamp(Instant.parse("2026-01-01T00:00:00Z"))
                        .build())
                .eventType(AuditEventType.REQUEST)
                .action("View")
                .correlationId(UUID.fromString("00000000-0000-0000-0000-000000000123"))
                .build();

        service.send(payload);

        final ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(jmsTemplate).convertAndSend(eq("jms.topic.auditing.event"), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue())
                .contains("test.event")
                .contains("00000000-0000-0000-0000-000000000123")
                .contains("_metadata");
    }
}
