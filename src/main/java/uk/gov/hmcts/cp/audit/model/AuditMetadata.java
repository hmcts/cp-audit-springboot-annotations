package uk.gov.hmcts.cp.audit.model;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * Envelope metadata. Serialised as the top-level {@code _metadata} object of the audit message,
 * which the CPP framework requires in order to build a {@code JsonEnvelope}.
 *
 * <p>{@code name} is the <em>envelope</em> event name and must stay
 * {@link AuditMessage#AUDIT_EVENT_NAME} — consumers dispatch on it. The business event name
 * (e.g. {@code hrds.create-client-subscription}) belongs in {@link AuditPayload#getEventName()}.
 */
@Getter
@Builder
public class AuditMetadata {
    private final UUID id;
    private final String name;
    private final String createdAt;
    private final AuditContext context;
}
