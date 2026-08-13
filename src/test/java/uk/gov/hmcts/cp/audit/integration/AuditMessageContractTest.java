package uk.gov.hmcts.cp.audit.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.jms.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.config.ArtemisAuditAutoConfiguration;
import uk.gov.hmcts.cp.audit.model.AuditEventType;
import uk.gov.hmcts.cp.audit.model.AuditMessage;
import uk.gov.hmcts.cp.audit.service.AuditClockService;
import uk.gov.hmcts.cp.audit.service.AuditPayloadGenerationService;
import uk.gov.hmcts.cp.audit.service.AuditSenderService;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract test against the real audit2dls consumer.
 *
 * <p>The schema under {@code src/test/resources/schema} is a copy of
 * {@code cpp-context-audit2dls/audit2dls-event/audit2dls-event-processor/src/yaml/json/schema/audit.events.audit-recorded.json}.
 * It validates the envelope <em>payload</em> only; the framework's own requirement — a top-level
 * {@code _metadata} object, without which {@code DefaultJsonObjectEnvelopeConverter.asEnvelope}
 * throws and the message is dead-lettered — is asserted separately below.
 */
@ExtendWith(MockitoExtension.class)
class AuditMessageContractTest {

    private static final UUID METADATA_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CORRELATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-13T12:58:50.352Z");

    private final ObjectMapper mapper = new ArtemisAuditAutoConfiguration().auditObjectMapper();

    private JsonNode serialisedMessage;
    private String cppName;

    @BeforeEach
    void buildAndSendMessage() throws Exception {
        final AuditClockService clock = mock(AuditClockService.class);
        when(clock.now()).thenReturn(NOW);

        final AuditMessage message = new AuditPayloadGenerationService(clock).build(
                new org.springframework.mock.web.MockHttpServletRequest("POST", "/client-subscriptions"),
                auditDetail(),
                CORRELATION_ID,
                METADATA_ID,
                AuditEventType.RESPONSE,
                409);

        final JmsTemplate jmsTemplate = mock(JmsTemplate.class);
        new AuditSenderService(jmsTemplate, mapper).send(message);

        final ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(jmsTemplate).convertAndSend(anyString(), json.capture(), processor.capture());

        serialisedMessage = mapper.readTree(json.getValue());

        final Message jmsMessage = mock(Message.class);
        processor.getValue().postProcessMessage(jmsMessage);
        final ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(jmsMessage).setStringProperty(anyString(), name.capture());
        cppName = name.getValue();
    }

    @Test
    void serialised_message_should_satisfy_the_audit2dls_consumer_schema() {
        final Set<ValidationMessage> violations = schema().validate(serialisedMessage);

        assertThat(violations)
                .as("schema violations for %s", serialisedMessage.toPrettyString())
                .isEmpty();
    }

    @Test
    void serialised_message_should_carry_metadata_at_the_top_level() {
        final JsonNode metadata = serialisedMessage.get("_metadata");

        assertThat(metadata)
                .as("_metadata must be a top-level sibling of content, not nested inside it")
                .isNotNull();
        assertThat(metadata.get("id").asText()).isEqualTo(METADATA_ID.toString());
        assertThat(metadata.get("createdAt").isTextual()).isTrue();
        assertThat(serialisedMessage.get("content").has("_metadata"))
                .as("content must not carry its own _metadata")
                .isFalse();
    }

    @Test
    void envelope_name_should_be_the_event_consumers_dispatch_on_and_match_cppname() {
        assertThat(serialisedMessage.get("_metadata").get("name").asText())
                .isEqualTo("audit.events.audit-recorded");
        assertThat(cppName)
                .as("CPPNAME selector must match _metadata.name")
                .isEqualTo("audit.events.audit-recorded");
    }

    @Test
    void timestamp_should_be_an_iso_8601_string_not_a_number() {
        final JsonNode timestamp = serialisedMessage.get("timestamp");

        assertThat(timestamp.isTextual())
                .as("audit2dls calls jsonObject.getString(\"timestamp\"), which fails on a numeric value")
                .isTrue();
        assertThatCode(() -> OffsetDateTime.parse(timestamp.asText())).doesNotThrowAnyException();
        assertThat(timestamp.asText())
                .as("audit2dls derives the Data Lake path via timestamp.split(\"T\")[0]")
                .contains("T");
    }

    @Test
    void business_event_name_should_live_in_the_content_block() {
        assertThat(serialisedMessage.get("content").get("eventName").asText())
                .isEqualTo("hrds.create-client-subscription");
    }

    private JsonSchema schema() {
        try (InputStream in = getClass().getResourceAsStream("/schema/audit.events.audit-recorded.json")) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4).getSchema(in);
        } catch (final Exception e) {
            throw new IllegalStateException("Could not load consumer schema", e);
        }
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
