package uk.gov.hmcts.cp.audit.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.cp.audit.service.AuditSenderService;
import uk.gov.hmcts.cp.audit.service.ClockService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "cp.audit.block-on-failure=false")
@AutoConfigureMockMvc
@ExtendWith(MockitoExtension.class)
class AuditFilterNonBlockingIntegrationTest {

    private static final UUID CORRELATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired MockMvc mockMvc;
    @MockitoBean AuditSenderService auditSenderService;
    @MockitoBean ClockService clockService;

    @Test
    void audit_sender_failure_with_block_on_failure_false_should_pass_through_and_return_200() throws Exception {
        when(clockService.now()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        doThrow(new IllegalStateException("broker down")).when(auditSenderService).send(any());

        mockMvc.perform(get("/audited").header("X-Correlation-Id", CORRELATION_ID))
                .andExpect(status().isOk());
    }
}
