package uk.gov.hmcts.cp.audit.model;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AuditMetadata {
    private final UUID id;
    private final String name;
    private final AuditContext context;
}
