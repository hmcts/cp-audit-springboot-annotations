package uk.gov.hmcts.cp.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerMapping;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.model.AuditEventType;
import uk.gov.hmcts.cp.audit.model.AuditMdcKeys;
import uk.gov.hmcts.cp.audit.model.AuditMetadata;
import uk.gov.hmcts.cp.audit.model.AuditPayload;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AuditPayloadGenerationService {

    public AuditPayload build(final HttpServletRequest request,
                              final AuditDetail annotation,
                              final UUID correlationId,
                              final AuditEventType eventType,
                              final Integer responseStatus) {
        return AuditPayload.builder()
                .metadata(AuditMetadata.builder()
                        .origin(annotation.origin())
                        .component(annotation.component())
                        .eventName(annotation.eventName())
                        .timestamp(Instant.now())
                        .build())
                .eventType(eventType)
                .action(annotation.action())
                .correlationId(correlationId)
                .responseStatus(responseStatus)
                .materialId(uuidFromMdc(AuditMdcKeys.MATERIAL_ID))
                .caseId(uuidFromMdc(AuditMdcKeys.CASE_ID))
                .hearingId(uuidFromMdc(AuditMdcKeys.HEARING_ID))
                .courtDocumentId(uuidFromMdc(AuditMdcKeys.COURT_DOCUMENT_ID))
                .pathParams(extractPathParams(request, annotation))
                .build();
    }

    private static UUID uuidFromMdc(final String key) {
        final String value = MDC.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, UUID> extractPathParams(final HttpServletRequest request, final AuditDetail annotation) {
        if (annotation.pathParams().length == 0) {
            return Map.of();
        }
        final Object attr = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attr instanceof Map<?, ?> rawVars)) {
            return Map.of();
        }
        final Map<String, String> uriVars = (Map<String, String>) rawVars;
        final Map<String, UUID> result = new LinkedHashMap<>();
        for (final String name : annotation.pathParams()) {
            final String raw = uriVars.get(name);
            if (raw != null) {
                try {
                    result.put(name, UUID.fromString(raw));
                } catch (final IllegalArgumentException ignored) {
                    // non-UUID path variable — skip
                }
            }
        }
        return Map.copyOf(result);
    }
}
