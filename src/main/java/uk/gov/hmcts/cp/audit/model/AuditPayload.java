package uk.gov.hmcts.cp.audit.model;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

/**
 * The audit entry content. Serialised as the {@code content} object of the audit message, which the
 * consumer schema declares as a free-form object ({@code additionalProperties: true}), so fields may
 * be added here without a schema change.
 */
@Getter
@Builder
public class AuditPayload {
    /** Business event name from {@code @AuditDetail}, e.g. {@code hrds.create-client-subscription}. */
    private final String eventName;
    private final AuditEventType eventType;
    private final String action;
    /** Calling API client, from {@link AuditMdcKeys#CLIENT_ID}. */
    private final UUID clientId;
    private final UUID materialId;
    private final UUID caseId;
    private final UUID hearingId;
    private final UUID courtDocumentId;
    private final UUID correlationId;
    private final Integer responseStatus;
    private final Map<String, UUID> pathParams;
}
