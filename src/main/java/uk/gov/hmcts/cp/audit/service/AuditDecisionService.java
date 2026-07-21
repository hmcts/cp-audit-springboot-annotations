package uk.gov.hmcts.cp.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.slf4j.MDC;
import org.springframework.web.method.HandlerMethod;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.annotation.AuditExclude;
import uk.gov.hmcts.cp.audit.model.AuditDecision;

import java.util.UUID;

@Slf4j
public class AuditDecisionService {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    public AuditDecision decide(final HandlerMethod handler, final HttpServletRequest request) {
        if (handler.hasMethodAnnotation(AuditExclude.class)
                || handler.getBeanType().isAnnotationPresent(AuditExclude.class)) {
            return new AuditDecision.Exclude();
        }

        AuditDetail detail = handler.getMethodAnnotation(AuditDetail.class);
        if (detail == null) {
            detail = handler.getBeanType().getAnnotation(AuditDetail.class);
        }

        if (detail == null) {
            return new AuditDecision.Block("No @AuditDetail annotation on handler");
        }

        final String correlationId = resolveCorrelationId(request);
        if (correlationId == null || correlationId.isBlank()) {
            return new AuditDecision.Block("Failed to find correlationId in header or MDC");
        }
        try {
            return new AuditDecision.Audit(detail, UUID.fromString(correlationId));
        } catch (final IllegalArgumentException e) {
            log.error("Failed to parse correlationId as UUID:{}", Encode.forJava(correlationId));
            return new AuditDecision.Block(CORRELATION_HEADER + " is not a valid UUID");
        }
    }

    private String resolveCorrelationId(final HttpServletRequest request) {
        if (request.getHeader(CORRELATION_HEADER) != null && !request.getHeader(CORRELATION_HEADER).isBlank()) {
            return request.getHeader(CORRELATION_HEADER);
        }
        if (MDC.get(CORRELATION_HEADER) != null && !MDC.get(CORRELATION_HEADER).isBlank()) {
            return MDC.get(CORRELATION_HEADER);
        }
        return null;
    }
}
