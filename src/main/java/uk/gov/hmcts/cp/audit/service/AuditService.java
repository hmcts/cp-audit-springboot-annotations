package uk.gov.hmcts.cp.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.model.AuditEventType;
import uk.gov.hmcts.cp.audit.model.AuditMessage;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditPayloadGenerationService payloadService;
    private final AuditSenderService senderService;
    private final AuditUuidService uuidService;

    public UUID generateMetadataId() {
        return uuidService.randomUUID();
    }

    public void auditRequest(final HttpServletRequest request,
                             final AuditDetail annotation,
                             final String correlationId,
                             final UUID metadataId) {
        final AuditMessage message = payloadService.build(request, annotation, correlationId, metadataId, AuditEventType.REQUEST, null);
        log.info("Sending audit REQUEST payload for correlationId:{}", correlationId);
        senderService.send(message);
    }

    public void auditResponse(final HttpServletRequest request,
                              final AuditDetail annotation,
                              final String correlationId,
                              final UUID metadataId,
                              final int responseStatus) {
        final AuditMessage message = payloadService.build(request, annotation, correlationId, metadataId, AuditEventType.RESPONSE, responseStatus);
        log.info("Sending audit RESPONSE payload for correlationId:{}", correlationId);
        senderService.send(message);
    }
}
