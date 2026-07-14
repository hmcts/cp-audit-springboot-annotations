package uk.gov.hmcts.cp.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditFilterTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;

    @InjectMocks
    private AuditFilter auditFilter;

    @Test
    void filtering_a_request_should_pass_through_to_chain() throws Exception {
        auditFilter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
