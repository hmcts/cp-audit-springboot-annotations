package uk.gov.hmcts.cp.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.model.AuditEventType;
import uk.gov.hmcts.cp.audit.model.AuditMetadata;
import uk.gov.hmcts.cp.audit.model.AuditPayload;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditPayloadGenerationService payloadService;
    @Mock private AuditSenderService senderService;
    @Mock private HttpServletRequest request;

    @InjectMocks
    private AuditService service;

    @Test
    void auditing_a_request_should_build_request_event_and_send() {
        final AuditDetail annotation = StubController.class.getAnnotation(AuditDetail.class);
        final AuditPayload payload = stubPayload(AuditEventType.REQUEST);
        when(payloadService.build(request, annotation, "corr-1", AuditEventType.REQUEST, null)).thenReturn(payload);

        service.auditRequest(request, annotation, "corr-1");

        verify(payloadService).build(request, annotation, "corr-1", AuditEventType.REQUEST, null);
        verify(senderService).send(payload);
    }

    @Test
    void auditing_a_response_should_build_response_event_with_status_and_send() {
        final AuditDetail annotation = StubController.class.getAnnotation(AuditDetail.class);
        final AuditPayload payload = stubPayload(AuditEventType.RESPONSE);
        when(payloadService.build(eq(request), eq(annotation), eq("corr-1"), eq(AuditEventType.RESPONSE), eq(200)))
                .thenReturn(payload);

        service.auditResponse(request, annotation, "corr-1", 200);

        verify(payloadService).build(request, annotation, "corr-1", AuditEventType.RESPONSE, 200);
        verify(senderService).send(payload);
    }

    private static AuditPayload stubPayload(final AuditEventType type) {
        return AuditPayload.builder()
                .metadata(AuditMetadata.builder()
                        .origin("o").component("c").eventName("e").timestamp(Instant.now()).build())
                .eventType(type)
                .build();
    }

    @AuditDetail(eventName = "stub.event")
    private static final class StubController {}
}
