package uk.gov.hmcts.cp.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import uk.gov.hmcts.cp.audit.config.AuditProperties;
import uk.gov.hmcts.cp.audit.model.AuditDecision;
import uk.gov.hmcts.cp.audit.service.AuditDecisionService;
import uk.gov.hmcts.cp.audit.service.AuditService;

import java.io.IOException;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class AuditFilter extends OncePerRequestFilter {

    private final List<RequestMappingHandlerMapping> handlerMappings;
    private final AuditDecisionService decisionService;
    private final AuditService auditService;
    private final AuditProperties properties;

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        final HandlerMethod handler = resolveHandler(request);
        if (handler == null) {
            chain.doFilter(request, response);
            return;
        }

        final AuditDecision decision = decisionService.decide(handler, request);

        switch (decision) {
            case AuditDecision.Block block -> {
                log.error("Audit blocked request {} {}: {}", request.getMethod(), Encode.forJava(request.getRequestURI()), block.reason());
                sendForbidden(response, block.reason());
            }
            case AuditDecision.Exclude ignored -> chain.doFilter(request, response);
            case AuditDecision.Audit audit -> {
                try {
                    auditService.auditRequest(request, audit.annotation(), audit.correlationId());
                    chain.doFilter(request, response);
                    auditService.auditResponse(request, audit.annotation(), audit.correlationId(), response.getStatus());
                } catch (final Exception e) {
                    log.error("Audit failed for {} {}", audit.correlationId(), Encode.forJava(request.getRequestURI()), e);
                    if (properties.isBlockOnFailure()) {
                        sendForbidden(response, "Audit failure");
                    } else {
                        chain.doFilter(request, response);
                    }
                }
            }
        }
    }

    private void sendForbidden(final HttpServletResponse response, final String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/plain");
        response.getWriter().write(message);
    }

    private HandlerMethod resolveHandler(final HttpServletRequest request) {
        for (final RequestMappingHandlerMapping mapping : handlerMappings) {
            try {
                final HandlerExecutionChain chain = mapping.getHandler(request);
                if (chain != null && chain.getHandler() instanceof HandlerMethod hm) {
                    return hm;
                }
            } catch (final Exception e) {
                log.error("Could not resolve handler for {}", Encode.forJava(request.getRequestURI()), e);
            }
        }
        return null;
    }
}
