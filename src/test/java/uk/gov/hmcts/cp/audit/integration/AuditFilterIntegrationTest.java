package uk.gov.hmcts.cp.audit.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import uk.gov.hmcts.cp.audit.config.ArtemisAuditAutoConfiguration;
import uk.gov.hmcts.cp.audit.model.AuditMessage;
import uk.gov.hmcts.cp.audit.service.AuditClockService;
import uk.gov.hmcts.cp.audit.service.AuditSenderService;
import uk.gov.hmcts.cp.audit.service.AuditUuidService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
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
    private static final UUID METADATA_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    /**
     * Deliberately the production mapper, not a locally-configured one as that hides the serialisation problems in runtime
     */
    private static final ObjectMapper MAPPER = new ArtemisAuditAutoConfiguration().auditObjectMapper();

    @Autowired MockMvc mockMvc;
    @MockitoBean AuditSenderService auditSenderService;
    @MockitoBean AuditClockService clockService;
    @MockitoBean AuditUuidService auditUuidService;
    @Captor ArgumentCaptor<AuditMessage> messageCaptor;

    @BeforeEach
    void setUp() {
        when(clockService.now()).thenReturn(NOW);
        when(auditUuidService.randomUUID()).thenReturn(METADATA_ID);
    }

    @Test
    void calling_audited_endpoint_should_send_request_and_response_audit_events() throws Exception {
        mockMvc.perform(get("/audited").header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isOk());

        verify(auditSenderService, times(2)).send(messageCaptor.capture());
        final List<AuditMessage> messages = messageCaptor.getAllValues();

        JSONAssert.assertEquals(expectedRequest(), MAPPER.writeValueAsString(messages.get(0)), JSONCompareMode.LENIENT);
        JSONAssert.assertEquals(expectedResponse(), MAPPER.writeValueAsString(messages.get(1)), JSONCompareMode.LENIENT);
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

    @Test
    void calling_endpoint_with_expected_mdc_fields_set_should_return_200_and_populate_user_from_mdc() throws Exception {
        mockMvc.perform(get("/audited-with-mdc").header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isOk());

        verify(auditSenderService, times(2)).send(messageCaptor.capture());
        final List<AuditMessage> messages = messageCaptor.getAllValues();

        JSONAssert.assertEquals(
                """
                { "_metadata": { "context": { "user": null } } }
                """,
                MAPPER.writeValueAsString(messages.get(0)), JSONCompareMode.LENIENT);

        JSONAssert.assertEquals(
                """
                { "_metadata": { "context": { "user": "00000000-0000-0000-0000-000000000020" } } }
                """,
                MAPPER.writeValueAsString(messages.get(1)), JSONCompareMode.LENIENT);
    }

    @Test
    void calling_endpoint_with_expected_mdc_fields_missing_should_return_403() throws Exception {
        mockMvc.perform(get("/audited-with-mdc-missing").header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("missing MDC fields")));
    }

    private String expectedRequest() {
        return """
                {
                  "_metadata": {
                    "id":        "00000000-0000-0000-0000-000000000002",
                    "name":      "audit.events.audit-recorded",
                    "createdAt": "2026-01-01T00:00:00Z",
                    "context":   { "user": null }
                  },
                  "origin":    "hearing-results-document",
                  "component": "QUERY_API",
                  "timestamp": "2026-01-01T00:00:00Z",
                  "content": {
                    "eventName":      "test.audited",
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
                }
                """;
    }

    private String expectedResponse() {
        return """
                {
                  "_metadata": {
                    "id":        "00000000-0000-0000-0000-000000000002",
                    "name":      "audit.events.audit-recorded",
                    "createdAt": "2026-01-01T00:00:00Z",
                    "context":   { "user": null }
                  },
                  "origin":    "hearing-results-document",
                  "component": "QUERY_API",
                  "timestamp": "2026-01-01T00:00:00Z",
                  "content": {
                    "eventName":      "test.audited",
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
                }
                """;
    }
}
