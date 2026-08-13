package uk.gov.hmcts.cp.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerMapping;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.model.AuditContext;
import uk.gov.hmcts.cp.audit.model.AuditEventType;
import uk.gov.hmcts.cp.audit.model.AuditMdcKeys;
import uk.gov.hmcts.cp.audit.model.AuditMessage;
import uk.gov.hmcts.cp.audit.model.AuditMetadata;
import uk.gov.hmcts.cp.audit.model.AuditPayload;

import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AuditPayloadGenerationService {

    /**
     * Inbound header carrying the calling user's UUID. This is the CPP-wide convention and the same
     * source {@code cp-audit-filter-springboot} uses, so audit records from annotation-driven and
     * spec-driven services attribute the user identically. Servlet header lookup is
     * case-insensitive, so the header may arrive in any casing.
     */
    private static final String CJSCPPUID_HEADER = "CJSCPPUID";

    private final AuditClockService clockService;

    public AuditPayloadGenerationService(final AuditClockService clockService) {
        this.clockService = clockService;
    }

    public AuditMessage build(final HttpServletRequest request,
                              final AuditDetail annotation,
                              final UUID correlationId,
                              final UUID metadataId,
                              final AuditEventType eventType,
                              final Integer responseStatus) {
        final String timestamp = clockService.now().truncatedTo(ChronoUnit.MILLIS).toString();

        // Envelope metadata: name is the event the consumer dispatches on, never the business
        // event name — that goes in the content block below.
        final AuditMetadata metadata = AuditMetadata.builder()
                .id(metadataId)
                .name(AuditMessage.AUDIT_EVENT_NAME)
                .createdAt(timestamp)
                .context(new AuditContext(resolveUserId(request)))
                .build();

        final AuditPayload content = AuditPayload.builder()
                .eventName(annotation.eventName())
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

        return AuditMessage.builder()
                .metadata(metadata)
                .origin(annotation.origin())
                .component(annotation.component())
                .timestamp(timestamp)
                .content(content)
                .build();
    }

    /**
     * Resolves the audited user, preferring the inbound {@code CJSCPPUID} header and falling back
     * to the {@link AuditMdcKeys#USER_ID} MDC key for services that resolve the identity themselves
     * (e.g. from a JWT claim) rather than receiving it as a header. Null when neither is present.
     */
    private static String resolveUserId(final HttpServletRequest request) {
        final String header = request.getHeader(CJSCPPUID_HEADER);
        if (header != null && !header.isBlank()) {
            return header;
        }
        return MDC.get(AuditMdcKeys.USER_ID);
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
