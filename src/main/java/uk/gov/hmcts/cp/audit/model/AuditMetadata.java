package uk.gov.hmcts.cp.audit.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AuditMetadata {
    private final String origin;
    private final String component;
    private final String eventName;
    private final Instant timestamp;
}
