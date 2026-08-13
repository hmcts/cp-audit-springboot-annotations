package uk.gov.hmcts.cp.audit.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.model.AuditEventType;
import uk.gov.hmcts.cp.audit.model.AuditMdcKeys;
import uk.gov.hmcts.cp.audit.model.AuditMessage;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The audited user is resolved from the inbound {@code CJSCPPUID} header — the same source
 * {@code cp-audit-filter-springboot} uses — falling back to MDC for services that resolve the
 * identity themselves.
 */
class AuditPayloadGenerationServiceUserTest {

    private static final String HEADER_USER = "11111111-1111-1111-1111-111111111111";
    private static final String MDC_USER = "22222222-2222-2222-2222-222222222222";

    private final AuditPayloadGenerationService service = new AuditPayloadGenerationService(clock());

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void user_should_be_taken_from_the_cjscppuid_header() {
        final MockHttpServletRequest request = request();
        request.addHeader("CJSCPPUID", HEADER_USER);

        assertThat(user(request)).isEqualTo(HEADER_USER);
    }

    @Test
    void header_lookup_should_be_case_insensitive() {
        final MockHttpServletRequest request = request();
        request.addHeader("cjscppuid", HEADER_USER);

        assertThat(user(request)).isEqualTo(HEADER_USER);
    }

    @Test
    void header_should_win_over_the_mdc_fallback() {
        MDC.put(AuditMdcKeys.USER_ID, MDC_USER);
        final MockHttpServletRequest request = request();
        request.addHeader("CJSCPPUID", HEADER_USER);

        assertThat(user(request)).isEqualTo(HEADER_USER);
    }

    @Test
    void mdc_should_be_used_when_the_header_is_absent() {
        MDC.put(AuditMdcKeys.USER_ID, MDC_USER);

        assertThat(user(request())).isEqualTo(MDC_USER);
    }

    @Test
    void blank_header_should_fall_back_to_mdc() {
        MDC.put(AuditMdcKeys.USER_ID, MDC_USER);
        final MockHttpServletRequest request = request();
        request.addHeader("CJSCPPUID", "   ");

        assertThat(user(request)).isEqualTo(MDC_USER);
    }

    @Test
    void user_should_be_null_when_neither_header_nor_mdc_is_present() {
        assertThat(user(request())).isNull();
    }

    private String user(final MockHttpServletRequest request) {
        final AuditMessage message = service.build(
                request,
                auditDetail(),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                AuditEventType.REQUEST,
                null);
        return message.getMetadata().getContext().user();
    }

    private static MockHttpServletRequest request() {
        return new MockHttpServletRequest("POST", "/client-subscriptions");
    }

    private static AuditClockService clock() {
        final AuditClockService clock = mock(AuditClockService.class);
        when(clock.now()).thenReturn(Instant.parse("2026-08-13T12:58:50.352Z"));
        return clock;
    }

    private static AuditDetail auditDetail() {
        return new AuditDetail() {
            @Override public Class<? extends Annotation> annotationType() {
                return AuditDetail.class;
            }
            @Override public String eventName() {
                return "hrds.create-client-subscription";
            }
            @Override public String action() {
                return "Create";
            }
            @Override public String origin() {
                return "hearing-results-document";
            }
            @Override public String component() {
                return "QUERY_API";
            }
            @Override public String[] pathParams() {
                return new String[0];
            }
            @Override public String[] expectedMdcFields() {
                return new String[0];
            }
        };
    }
}
