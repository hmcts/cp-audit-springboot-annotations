package uk.gov.hmcts.cp.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import uk.gov.hmcts.cp.audit.model.AuditContext;
import uk.gov.hmcts.cp.audit.model.AuditEventType;
import uk.gov.hmcts.cp.audit.model.AuditMessage;
import uk.gov.hmcts.cp.audit.model.AuditMetadata;
import uk.gov.hmcts.cp.audit.model.AuditPayload;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    void sending_an_audit_message_should_publish_json_to_the_audit_topic() throws Exception {
        final AuditMessage message = AuditMessage.builder()
                .metadata(AuditMetadata.builder()
                        .id(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                        .name(AuditMessage.AUDIT_EVENT_NAME)
                        .createdAt("2026-01-01T00:00:00Z")
                        .context(new AuditContext(null))
                        .build())
                .origin("test-service")
                .component("API")
                .timestamp("2026-01-01T00:00:00Z")
                .content(AuditPayload.builder()
                        .eventName("test.event")
                        .eventType(AuditEventType.REQUEST)
                        .action("View")
                        .correlationId(UUID.fromString("00000000-0000-0000-0000-000000000123"))
                        .build())
                .build();

        service.send(message);

        final ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(jmsTemplate).convertAndSend(eq("jms.topic.auditing.event"), jsonCaptor.capture(), processorCaptor.capture());
        assertThat(jsonCaptor.getValue())
                .contains("test.event")
                .contains("00000000-0000-0000-0000-000000000123")
                .contains("_metadata");

        final Message jmsMessage = mock(Message.class);
        processorCaptor.getValue().postProcessMessage(jmsMessage);
        verify(jmsMessage).setStringProperty("CPPNAME", "audit.events.audit-recorded");
    }
}
