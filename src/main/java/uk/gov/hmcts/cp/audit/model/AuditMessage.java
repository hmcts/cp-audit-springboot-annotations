package uk.gov.hmcts.cp.audit.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AuditMessage {
    private final String origin;
    private final String component;
    private final Instant timestamp;
    private final AuditPayload content;
}
