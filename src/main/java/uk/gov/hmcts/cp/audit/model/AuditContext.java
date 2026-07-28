package uk.gov.hmcts.cp.audit.model;

// TODO: wire to authenticated user — annotate controller or set cp.audit.user-id
public record AuditContext(String user) {}
