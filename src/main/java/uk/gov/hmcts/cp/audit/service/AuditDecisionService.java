package uk.gov.hmcts.cp.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.web.method.HandlerMethod;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.annotation.AuditExclude;
import uk.gov.hmcts.cp.audit.model.AuditDecision;

import java.util.UUID;

@Slf4j
public class AuditDecisionService {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";

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

        final String raw = request.getHeader(CORRELATION_HEADER);
        if (raw == null || raw.isBlank()) {
            return new AuditDecision.Block("Missing " + CORRELATION_HEADER + " header");
        }
        try {
            return new AuditDecision.Audit(detail, UUID.fromString(raw));
        } catch (final IllegalArgumentException e) {
            log.error("{} value '{}' is not a valid UUID", CORRELATION_HEADER, Encode.forJava(raw));
            return new AuditDecision.Block(CORRELATION_HEADER + " is not a valid UUID");
        }
    }
}
