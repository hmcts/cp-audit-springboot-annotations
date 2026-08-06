package uk.gov.hmcts.cp.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import uk.gov.hmcts.cp.audit.model.AuditMessage;

@Slf4j
@RequiredArgsConstructor
public class AuditSenderService {

    private static final String AUDIT_TOPIC = "jms.topic.auditing.event";
    private static final String CPPNAME = "audit.events.audit-recorded";

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    public void send(final AuditMessage auditMessage) {
        try {
            final String json = objectMapper.writeValueAsString(auditMessage);
            jmsTemplate.convertAndSend(AUDIT_TOPIC, json, message -> {
                message.setStringProperty("CPPNAME", CPPNAME);
                return message;
            });
        } catch (final JsonProcessingException e) {
            log.error("Failed to serialize audit message for event={}", auditMessage.getContent().getMetadata().getName(), e);
            throw new IllegalStateException("Failed to serialize audit payload", e);
        } catch (final RuntimeException e) {
            log.error("Failed to send audit message to Artemis for event={}", auditMessage.getContent().getMetadata().getName(), e);
            throw new IllegalStateException("Failed to send audit payload to Artemis", e);
        }
    }
}
