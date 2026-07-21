package uk.gov.hmcts.cp.audit.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.audit.model.AuditPayload;
import uk.gov.hmcts.cp.audit.service.AuditSenderService;
import uk.gov.hmcts.cp.audit.service.ClockService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(MockitoExtension.class)
class AuditFilterIntegrationTest {

    private static final UUID CORRELATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired MockMvc mockMvc;
    @MockitoBean AuditSenderService auditSenderService;
    @MockitoBean ClockService clockService;
    @Captor ArgumentCaptor<AuditPayload> payloadCaptor;

    @BeforeEach
    void setUp() {
        when(clockService.now()).thenReturn(NOW);
    }

    @Test
    void calling_audited_endpoint_should_send_request_and_response_audit_events() throws Exception {
        mockMvc.perform(get("/audited").header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isOk());

        verify(auditSenderService, times(2)).send(payloadCaptor.capture());
        final List<AuditPayload> payloads = payloadCaptor.getAllValues();

        JSONAssert.assertEquals(expectedRequest(), MAPPER.writeValueAsString(payloads.get(0)), JSONCompareMode.LENIENT);
        JSONAssert.assertEquals(expectedResponse(), MAPPER.writeValueAsString(payloads.get(1)), JSONCompareMode.LENIENT);
    }

    @Test
    void calling_audited_endpoint_without_correlation_id_should_return_403() throws Exception {
        mockMvc.perform(get("/audited"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Failed to find correlationId in header or MDC"));
    }

    @Test
    void calling_unannotated_endpoint_should_return_403() throws Exception {
        mockMvc.perform(get("/unannotated").header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().string("No @AuditDetail annotation on handler"));
    }

    @Test
    void calling_excluded_endpoint_should_return_200_without_auditing() throws Exception {
        mockMvc.perform(get("/excluded"))
                .andExpect(status().isOk());

        verify(auditSenderService, times(0)).send(any());
    }

    @Test
    void audit_sender_failure_should_return_403() throws Exception {
        doThrow(new IllegalStateException("broker down")).when(auditSenderService).send(any());

        mockMvc.perform(get("/audited").header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Audit failure"));
    }

    @Test
    void artemis_unavailable_should_return_403() throws Exception {
        doThrow(new RuntimeException("Artemis unavailable")).when(auditSenderService).send(any());

        mockMvc.perform(get("/audited").header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Audit failure"));
    }

    private String expectedRequest() {
        return """
                {
                  "_metadata": {
                    "origin":    "hearing-results-document",
                    "component": "QUERY_API",
                    "eventName": "test.audited",
                    "timestamp": "2026-01-01T00:00:00Z"
                  },
                  "eventType":      "REQUEST",
                  "action":         "View",
                  "correlationId":  "00000000-0000-0000-0000-000000000001",
                  "responseStatus": null,
                  "materialId":     null,
                  "caseId":         null,
                  "hearingId":      null,
                  "courtDocumentId": null,
                  "pathParams":     {}
                }
                """;
    }

    private String expectedResponse() {
        return """
                {
                  "_metadata": {
                    "origin":    "hearing-results-document",
                    "component": "QUERY_API",
                    "eventName": "test.audited",
                    "timestamp": "2026-01-01T00:00:00Z"
                  },
                  "eventType":      "RESPONSE",
                  "action":         "View",
                  "correlationId":  "00000000-0000-0000-0000-000000000001",
                  "responseStatus": 200,
                  "materialId":     null,
                  "caseId":         null,
                  "hearingId":      null,
                  "courtDocumentId": null,
                  "pathParams":     {}
                }
                """;
    }
}
