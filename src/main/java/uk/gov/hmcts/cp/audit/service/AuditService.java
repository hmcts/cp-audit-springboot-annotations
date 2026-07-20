package uk.gov.hmcts.cp.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.model.AuditEventType;
import uk.gov.hmcts.cp.audit.model.AuditPayload;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditPayloadGenerationService payloadService;
    private final AuditSenderService senderService;

    public void auditRequest(final HttpServletRequest request,
                             final AuditDetail annotation,
                             final UUID correlationId) {
        final AuditPayload payload = payloadService.build(request, annotation, correlationId, AuditEventType.REQUEST, null);
        log.info("Sending audit REQUEST payload for correlationId:{}", correlationId);
        senderService.send(payload);
    }

    public void auditResponse(final HttpServletRequest request,
                              final AuditDetail annotation,
                              final UUID correlationId,
                              final int responseStatus) {
        final AuditPayload payload = payloadService.build(request, annotation, correlationId, AuditEventType.RESPONSE, responseStatus);
        log.info("Sending audit RESPONSE payload for correlationId:{}", correlationId);
        senderService.send(payload);
    }
}
