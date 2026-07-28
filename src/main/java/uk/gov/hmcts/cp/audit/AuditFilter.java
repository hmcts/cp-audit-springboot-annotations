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
import org.slf4j.MDC;
import org.springframework.web.util.ContentCachingResponseWrapper;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.config.AuditProperties;
import uk.gov.hmcts.cp.audit.model.AuditDecision;
import uk.gov.hmcts.cp.audit.service.AuditDecisionService;
import uk.gov.hmcts.cp.audit.service.AuditService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
                final UUID metadataId = auditService.generateMetadataId();
                final ContentCachingResponseWrapper buffered = new ContentCachingResponseWrapper(response);
                try {
                    auditService.auditRequest(request, audit.annotation(), audit.correlationId(), metadataId);
                    chain.doFilter(request, buffered);
                    final String missingFields = missingMdcFields(audit.annotation());
                    if (!missingFields.isEmpty()) {
                        log.error("Audit blocked response {} {}: expectedMdcFields not set in MDC: {}",
                                request.getMethod(), Encode.forJava(request.getRequestURI()), missingFields);
                        sendForbidden(response, "Audit failure — missing MDC fields: " + missingFields);
                        return;
                    }
                    auditService.auditResponse(request, audit.annotation(), audit.correlationId(), metadataId, buffered.getStatus());
                    buffered.copyBodyToResponse();
                } catch (final Exception e) {
                    log.error("Audit failed for {} {}", audit.correlationId(), Encode.forJava(request.getRequestURI()), e);
                    if (properties.isBlockOnFailure()) {
                        sendForbidden(response, "Audit failure");
                    } else {
                        chain.doFilter(request, response);
                    }
                } finally {
                    clearMdcFields(audit.annotation());
                }
            }
        }
    }

    private void sendForbidden(final HttpServletResponse response, final String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("text/plain");
        response.getOutputStream().write(message.getBytes(StandardCharsets.UTF_8));
    }

    private static void clearMdcFields(final AuditDetail annotation) {
        Arrays.stream(annotation.expectedMdcFields()).forEach(MDC::remove);
    }

    private static String missingMdcFields(final AuditDetail annotation) {
        return Arrays.stream(annotation.expectedMdcFields())
                .filter(key -> {
                    final String value = MDC.get(key);
                    return value == null || value.isBlank();
                })
                .collect(Collectors.joining(", "));
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
