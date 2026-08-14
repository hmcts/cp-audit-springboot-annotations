package uk.gov.hmcts.cp.audit.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * The audit message as it appears on the wire.
 *
 * <p>Shape is fixed by the CPP framework and by the audit2dls consumer:
 *  The top-level fields form the envelope
 * payload and are validated against {@code audit.events.audit-recorded.json}, which requires
 * {@code timestamp} (string, date-time), {@code origin} and {@code content}.
 */
@Getter
@Builder
public class AuditMessage {

    /** Envelope event name. Always Fixed. Consumers subscribe and dispatch on this exact value. */
    public static final String AUDIT_EVENT_NAME = "audit.events.audit-recorded";

    @JsonProperty("_metadata")
    private final AuditMetadata metadata;

    private final String origin;
    private final String component;

    /** ISO-8601 instant, e.g. {@code 2026-08-13T12:58:50.352Z}. Must be a string, never numeric. */
    private final String timestamp;

    private final AuditPayload content;
}
