package uk.gov.hmcts.cp.audit.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.annotation.AuditExclude;
import uk.gov.hmcts.cp.audit.model.AuditDecision;

import org.slf4j.MDC;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditDecisionServiceTest {

    @Mock private HandlerMethod handler;
    @Mock private HttpServletRequest request;

    private AuditDecisionService service;

    @BeforeEach
    void setUp() {
        service = new AuditDecisionService();
    }

    @Test
    void deciding_on_a_method_annotated_with_audit_exclude_should_return_exclude() {
        when(handler.hasMethodAnnotation(AuditExclude.class)).thenReturn(true);

        final AuditDecision decision = service.decide(handler, request);

        assertThat(decision).isInstanceOf(AuditDecision.Exclude.class);
    }

    @Test
    void deciding_on_a_class_annotated_with_audit_exclude_should_return_exclude() {
        when(handler.hasMethodAnnotation(AuditExclude.class)).thenReturn(false);
        when(handler.getBeanType()).thenAnswer(inv -> ExcludedController.class);

        final AuditDecision decision = service.decide(handler, request);

        assertThat(decision).isInstanceOf(AuditDecision.Exclude.class);
    }

    @Test
    void deciding_on_a_handler_with_no_annotation_should_return_block() {
        when(handler.hasMethodAnnotation(AuditExclude.class)).thenReturn(false);
        when(handler.getBeanType()).thenAnswer(inv -> UnannotatedController.class);
        when(handler.getMethodAnnotation(AuditDetail.class)).thenReturn(null);

        final AuditDecision decision = service.decide(handler, request);

        assertThat(decision).isInstanceOf(AuditDecision.Block.class);
    }

    @Test
    void deciding_on_an_audited_handler_without_correlation_id_should_return_block() {
        when(handler.hasMethodAnnotation(AuditExclude.class)).thenReturn(false);
        when(handler.getBeanType()).thenAnswer(inv -> UnannotatedController.class);
        when(handler.getMethodAnnotation(AuditDetail.class))
                .thenReturn(AuditedController.class.getAnnotation(AuditDetail.class));
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);

        final AuditDecision decision = service.decide(handler, request);

        assertThat(decision).isInstanceOf(AuditDecision.Block.class);
        assertThat(((AuditDecision.Block) decision).reason()).contains("correlationId");
    }

    @Test
    void deciding_on_an_audited_handler_with_correlation_id_should_return_audit() {
        when(handler.hasMethodAnnotation(AuditExclude.class)).thenReturn(false);
        when(handler.getBeanType()).thenAnswer(inv -> UnannotatedController.class);
        final AuditDetail annotation = AuditedController.class.getAnnotation(AuditDetail.class);
        when(handler.getMethodAnnotation(AuditDetail.class)).thenReturn(annotation);
        final UUID expectedId = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
        when(request.getHeader("X-Correlation-Id")).thenReturn(expectedId.toString());

        final AuditDecision decision = service.decide(handler, request);

        assertThat(decision).isInstanceOf(AuditDecision.Audit.class);
        assertThat(((AuditDecision.Audit) decision).correlationId()).isEqualTo(expectedId);
        assertThat(((AuditDecision.Audit) decision).annotation()).isSameAs(annotation);
    }

    @Test
    void deciding_on_an_audited_handler_with_non_uuid_correlation_id_should_return_block() {
        when(handler.hasMethodAnnotation(AuditExclude.class)).thenReturn(false);
        when(handler.getBeanType()).thenAnswer(inv -> UnannotatedController.class);
        when(handler.getMethodAnnotation(AuditDetail.class))
                .thenReturn(AuditedController.class.getAnnotation(AuditDetail.class));
        when(request.getHeader("X-Correlation-Id")).thenReturn("not-a-uuid");

        final AuditDecision decision = service.decide(handler, request);

        assertThat(decision).isInstanceOf(AuditDecision.Block.class);
    }

    @Test
    void deciding_on_an_audited_handler_with_correlation_id_in_mdc_should_return_audit() {
        when(handler.hasMethodAnnotation(AuditExclude.class)).thenReturn(false);
        when(handler.getBeanType()).thenAnswer(inv -> UnannotatedController.class);
        final AuditDetail annotation = AuditedController.class.getAnnotation(AuditDetail.class);
        when(handler.getMethodAnnotation(AuditDetail.class)).thenReturn(annotation);
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        final UUID expectedId = UUID.fromString("00000000-0000-0000-0000-0000000000cd");
        MDC.put("X-Correlation-Id", expectedId.toString());

        try {
            final AuditDecision decision = service.decide(handler, request);

            assertThat(decision).isInstanceOf(AuditDecision.Audit.class);
            assertThat(((AuditDecision.Audit) decision).correlationId()).isEqualTo(expectedId);
        } finally {
            MDC.remove("X-Correlation-Id");
        }
    }

    @AuditExclude
    private static final class ExcludedController {}

    @AuditDetail(eventName = "test.event")
    private static final class AuditedController {}

    private static final class UnannotatedController {}
}
