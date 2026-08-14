package uk.gov.hmcts.cp.audit.model;

public final class AuditMdcKeys {
    public static final String USER_ID           = "cp.audit.user-id";
    /**
     * Calling API client, emitted as {@code content.clientId}. Deliberately the plain
     * {@code clientId} key rather than a {@code cp.audit.*} one: auth filters already put the
     * resolved client there for log correlation, so the audit message reuses that entry instead
     * of requiring a second, duplicate MDC write.
     */
    public static final String CLIENT_ID         = "clientId";
    public static final String MATERIAL_ID       = "cp.audit.material-id";
    public static final String CASE_ID           = "cp.audit.case-id";
    public static final String HEARING_ID        = "cp.audit.hearing-id";
    public static final String COURT_DOCUMENT_ID = "cp.audit.court-document-id";

    private AuditMdcKeys() {}
}
