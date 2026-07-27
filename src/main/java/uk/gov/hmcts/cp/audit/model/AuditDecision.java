package uk.gov.hmcts.cp.audit.model;

import uk.gov.hmcts.cp.audit.annotation.AuditDetail;

public sealed interface AuditDecision permits AuditDecision.Audit, AuditDecision.Exclude, AuditDecision.Block {

    record Audit(AuditDetail annotation, String correlationId) implements AuditDecision {}

    record Exclude() implements AuditDecision {}

    record Block(String reason) implements AuditDecision {}
}
