package uk.gov.hmcts.cp.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.jms.DeliveryMode;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import uk.gov.hmcts.cp.audit.AuditFilter;
import uk.gov.hmcts.cp.audit.service.AuditDecisionService;
import uk.gov.hmcts.cp.audit.service.AuditPayloadGenerationService;
import uk.gov.hmcts.cp.audit.service.AuditSenderService;
import uk.gov.hmcts.cp.audit.service.AuditService;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(ActiveMQConnectionFactory.class)
@ConditionalOnProperty(prefix = "cp.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuditProperties.class)
public class ArtemisAuditAutoConfiguration {

    private static final String BEAN_CF  = "auditConnectionFactory";
    private static final String BEAN_JMS = "auditJmsTemplate";
    private static final String BEAN_OM  = "auditObjectMapper";

    // Fixed connection parameters — not configurable per environment
    private static final int    PORT                    = 61_616;
    private static final int    SESSION_CACHE_SIZE      = 10;
    private static final int    RECONNECT_ATTEMPTS      = -1;   // infinite
    private static final int    INITIAL_CONNECT_ATTEMPTS = 10;
    private static final long   RETRY_INTERVAL_MS       = 2_000;
    private static final double RETRY_MULTIPLIER        = 1.5;
    private static final long   MAX_RETRY_INTERVAL_MS   = 30_000;
    private static final long   CONNECTION_TTL_MS       = 60_000;
    private static final long   CALL_TIMEOUT_MS         = 15_000;

    @Bean(name = BEAN_CF)
    @ConditionalOnMissingBean(name = BEAN_CF)
    public ActiveMQConnectionFactory auditConnectionFactory(final AuditProperties properties) {
        validateProps(properties);
        final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(buildConnectionUrl(properties));
        factory.setUser(Objects.toString(properties.getUser(), ""));
        factory.setPassword(Objects.toString(properties.getPassword(), ""));
        log.info("Audit Artemis: hosts={}", properties.getHosts());
        return factory;
    }

    @Bean(name = BEAN_JMS)
    @ConditionalOnMissingBean(name = BEAN_JMS)
    public JmsTemplate auditJmsTemplate(
            @Qualifier(BEAN_CF) final ActiveMQConnectionFactory connectionFactory) {
        final CachingConnectionFactory caching = new CachingConnectionFactory(connectionFactory);
        caching.setSessionCacheSize(SESSION_CACHE_SIZE);
        caching.setCacheProducers(true);
        caching.setReconnectOnException(true);

        final JmsTemplate template = new JmsTemplate(caching);
        template.setPubSubDomain(true);
        template.setDeliveryMode(DeliveryMode.PERSISTENT);
        template.setReceiveTimeout(5_000L);
        return template;
    }

    @Bean(name = BEAN_OM)
    @ConditionalOnMissingBean(name = BEAN_OM)
    public ObjectMapper auditObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Bean
    public AuditDecisionService auditDecisionService() {
        return new AuditDecisionService();
    }

    @Bean
    public AuditPayloadGenerationService auditPayloadGenerationService() {
        return new AuditPayloadGenerationService();
    }

    @Bean
    public AuditSenderService auditSenderService(
            @Qualifier(BEAN_JMS) final JmsTemplate jmsTemplate,
            @Qualifier(BEAN_OM) final ObjectMapper objectMapper) {
        return new AuditSenderService(jmsTemplate, objectMapper);
    }

    @Bean
    public AuditService auditService(final AuditPayloadGenerationService payloadService,
                                     final AuditSenderService senderService) {
        return new AuditService(payloadService, senderService);
    }

    @Bean
    public FilterRegistrationBean<AuditFilter> auditFilterRegistration(
            @Qualifier("requestMappingHandlerMapping") final RequestMappingHandlerMapping handlerMapping,
            final AuditDecisionService decisionService,
            final AuditService auditService) {
        final AuditFilter filter = new AuditFilter(handlerMapping, decisionService, auditService);
        final FilterRegistrationBean<AuditFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
        reg.addUrlPatterns("/*");
        return reg;
    }

    private static void validateProps(final AuditProperties p) {
        final List<String> hosts = p.getHosts();
        if (hosts == null || hosts.isEmpty()) {
            throw new IllegalStateException("cp.audit.hosts must contain at least one broker host");
        }
    }

    private static String buildConnectionUrl(final AuditProperties p) {
        final boolean ha = p.getHosts().size() > 1;
        final String common = String.join("&",
                "ha=" + ha,
                "reconnectAttempts=" + RECONNECT_ATTEMPTS,
                "initialConnectAttempts=" + INITIAL_CONNECT_ATTEMPTS,
                "retryInterval=" + RETRY_INTERVAL_MS,
                "retryIntervalMultiplier=" + RETRY_MULTIPLIER,
                "maxRetryInterval=" + MAX_RETRY_INTERVAL_MS,
                "connectionTtl=" + CONNECTION_TTL_MS,
                "callTimeout=" + CALL_TIMEOUT_MS,
                "failoverOnInitialConnection=" + ha
        );

        final StringJoiner urls = new StringJoiner(",");
        for (final String host : p.getHosts()) {
            urls.add("tcp://" + host + ':' + PORT + '?' + common);
        }
        return urls.toString();
    }
}
