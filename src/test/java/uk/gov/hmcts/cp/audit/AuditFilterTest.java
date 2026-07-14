package uk.gov.hmcts.cp.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.model.AuditDecision;
import uk.gov.hmcts.cp.audit.service.AuditDecisionService;
import uk.gov.hmcts.cp.audit.service.AuditService;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditFilterTest {

    @Mock
    private RequestMappingHandlerMapping handlerMapping;
    @Mock
    private AuditDecisionService decisionService;
    @Mock
    private AuditService auditService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;
    @Mock
    private HandlerMethod handlerMethod;
    @Mock
    private HandlerExecutionChain executionChain;

    @InjectMocks
    private AuditFilter auditFilter;

    @Test
    void filtering_a_request_with_no_handler_should_pass_through_to_chain() throws Exception {
        when(handlerMapping.getHandler(request)).thenReturn(null);

        auditFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(auditService, never()).auditRequest(any(), any(), any());
    }

    @Test
    void filtering_an_excluded_endpoint_should_pass_through_without_auditing() throws Exception {
        when(handlerMapping.getHandler(request)).thenReturn(executionChain);
        when(executionChain.getHandler()).thenReturn(handlerMethod);
        when(decisionService.decide(handlerMethod, request)).thenReturn(new AuditDecision.Exclude());

        auditFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(auditService, never()).auditRequest(any(), any(), any());
    }

    @Test
    void filtering_a_blocked_request_should_return_403_and_not_invoke_chain() throws Exception {
        when(handlerMapping.getHandler(request)).thenReturn(executionChain);
        when(executionChain.getHandler()).thenReturn(handlerMethod);
        when(decisionService.decide(handlerMethod, request)).thenReturn(new AuditDecision.Block("missing header"));

        auditFilter.doFilterInternal(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void filtering_an_audited_request_should_send_request_and_response_audit_events() throws Exception {
        final AuditDetail annotation = stubAuditDetail();
        when(handlerMapping.getHandler(request)).thenReturn(executionChain);
        when(executionChain.getHandler()).thenReturn(handlerMethod);
        when(decisionService.decide(handlerMethod, request)).thenReturn(new AuditDecision.Audit(annotation, "corr-123"));
        when(response.getStatus()).thenReturn(200);

        auditFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(auditService).auditRequest(eq(request), eq(annotation), eq("corr-123"));
        verify(auditService).auditResponse(eq(request), eq(annotation), eq("corr-123"), eq(200));
    }

    private AuditDetail stubAuditDetail() {
        return StubAuditDetail.class.getAnnotation(AuditDetail.class);
    }

    @AuditDetail(eventName = "test.event")
    private static final class StubAuditDetail {
    }
}
