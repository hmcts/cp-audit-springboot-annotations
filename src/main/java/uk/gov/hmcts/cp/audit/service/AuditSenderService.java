package uk.gov.hmcts.cp.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import uk.gov.hmcts.cp.audit.model.AuditPayload;

@Slf4j
@RequiredArgsConstructor
public class AuditSenderService {

    private static final String AUDIT_TOPIC = "jms.topic.auditing.event";

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper objectMapper;

    public void send(final AuditPayload payload) {
        try {
            final String json = objectMapper.writeValueAsString(payload);
            jmsTemplate.convertAndSend(AUDIT_TOPIC, json);
        } catch (final JsonProcessingException e) {
            log.error("Failed to serialize audit payload for event={}", payload.getMetadata().getEventName(), e);
            throw new IllegalStateException("Failed to serialize audit payload", e);
        } catch (final RuntimeException e) {
            log.error("Failed to send audit payload to Artemis for event={}", payload.getMetadata().getEventName(), e);
            throw new IllegalStateException("Failed to send audit payload to Artemis", e);
        }
    }
}
