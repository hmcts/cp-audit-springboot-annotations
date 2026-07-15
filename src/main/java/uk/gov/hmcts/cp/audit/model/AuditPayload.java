package uk.gov.hmcts.cp.audit.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class AuditPayload {
    @JsonProperty("_metadata")
    private final AuditMetadata metadata;
    private final AuditEventType eventType;
    private final String action;
    private final UUID materialId;
    private final UUID caseId;
    private final UUID hearingId;
    private final UUID courtDocumentId;
    private final UUID correlationId;
    private final Integer responseStatus;
    private final Map<String, UUID> pathParams;
}
