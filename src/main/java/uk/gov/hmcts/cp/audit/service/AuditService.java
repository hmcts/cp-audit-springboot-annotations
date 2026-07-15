package uk.gov.hmcts.cp.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.model.AuditEventType;
import uk.gov.hmcts.cp.audit.model.AuditPayload;

import java.util.UUID;

@RequiredArgsConstructor
public class AuditService {

    private final AuditPayloadGenerationService payloadService;
    private final AuditSenderService senderService;

    public void auditRequest(final HttpServletRequest request,
                             final AuditDetail annotation,
                             final UUID correlationId) {
        final AuditPayload payload = payloadService.build(request, annotation, correlationId, AuditEventType.REQUEST, null);
        senderService.send(payload);
    }

    public void auditResponse(final HttpServletRequest request,
                              final AuditDetail annotation,
                              final UUID correlationId,
                              final int responseStatus) {
        final AuditPayload payload = payloadService.build(request, annotation, correlationId, AuditEventType.RESPONSE, responseStatus);
        senderService.send(payload);
    }
}
