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

import static org.springframework.util.StringUtils.hasLength;

@Slf4j
@AutoConfiguration
@ConditionalOnClass(ActiveMQConnectionFactory.class)
@ConditionalOnProperty(prefix = "cp.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuditProperties.class)
public class ArtemisAuditAutoConfiguration {

    private static final String BEAN_CF  = "auditConnectionFactory";
    private static final String BEAN_JMS = "auditJmsTemplate";
    private static final String BEAN_OM  = "auditObjectMapper";

    @Bean(name = BEAN_CF)
    @ConditionalOnMissingBean(name = BEAN_CF)
    public ActiveMQConnectionFactory auditConnectionFactory(final AuditProperties properties) {
        validateProps(properties);
        final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(buildConnectionUrl(properties));
        factory.setUser(Objects.toString(properties.getUser(), ""));
        factory.setPassword(Objects.toString(properties.getPassword(), ""));
        log.info("Audit Artemis: hosts={} port={} ssl={} ha={}",
                properties.getHosts(), properties.getPort(), properties.isSslEnabled(), properties.isHighAvailability());
        return factory;
    }

    @Bean(name = BEAN_JMS)
    @ConditionalOnMissingBean(name = BEAN_JMS)
    public JmsTemplate auditJmsTemplate(
            @Qualifier(BEAN_CF) final ActiveMQConnectionFactory connectionFactory,
            final AuditProperties properties) {
        final CachingConnectionFactory caching = new CachingConnectionFactory(connectionFactory);
        final AuditProperties.JmsProperties jms = properties.getJms();
        caching.setSessionCacheSize(jms.getSessionCacheSize());
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
        if (p.getPort() <= 0) {
            throw new IllegalStateException("cp.audit.port must be a positive integer");
        }
        if (p.isSslEnabled()) {
            if (hasLength(p.getTruststore()) && p.getTruststorePassword() == null) {
                throw new IllegalStateException("cp.audit.truststore-password must be set when truststore is provided");
            }
            if (p.isClientAuthRequired()) {
                if (!hasLength(p.getKeystore())) {
                    throw new IllegalStateException("client-auth-required=true requires cp.audit.keystore");
                }
                if (p.getKeystorePassword() == null) {
                    throw new IllegalStateException("cp.audit.keystore-password must be set when keystore is provided");
                }
            }
        }
    }

    private static String buildConnectionUrl(final AuditProperties p) {
        final AuditProperties.JmsProperties jms = p.getJms();
        final String common = String.join("&",
                "ha=" + p.isHighAvailability(),
                "reconnectAttempts=" + jms.getReconnectAttempts(),
                "initialConnectAttempts=" + jms.getInitialConnectAttempts(),
                "retryInterval=" + jms.getRetryIntervalMs(),
                "retryIntervalMultiplier=" + jms.getRetryMultiplier(),
                "maxRetryInterval=" + jms.getMaxRetryIntervalMs(),
                "connectionTtl=" + jms.getConnectionTtlMs(),
                "callTimeout=" + jms.getCallTimeoutMs(),
                "failoverOnInitialConnection=" + p.isHighAvailability()
        );

        final StringBuilder ssl = new StringBuilder();
        if (p.isSslEnabled()) {
            ssl.append("sslEnabled=true&verifyHost=").append(p.isVerifyHost());
            final boolean hasTrust = hasLength(p.getTruststore());
            final boolean hasKey   = hasLength(p.getKeystore());
            if (hasTrust || hasKey) {
                final String trustPath = hasTrust ? p.getTruststore() : p.getKeystore();
                final String trustPass = hasTrust ? p.getTruststorePassword() : p.getKeystorePassword();
                if (hasLength(trustPath)) ssl.append("&trustStorePath=").append(trustPath);
                if (hasLength(trustPass)) ssl.append("&trustStorePassword=").append(trustPass);
            }
            if (p.isClientAuthRequired() && hasLength(p.getKeystore())) {
                ssl.append("&keyStorePath=").append(p.getKeystore());
                if (hasLength(p.getKeystorePassword())) ssl.append("&keyStorePassword=").append(p.getKeystorePassword());
            }
            ssl.append('&');
        }

        final StringJoiner urls = new StringJoiner(",");
        for (final String host : p.getHosts()) {
            urls.add("tcp://" + host + ':' + p.getPort() + '?' + ssl + common);
        }
        return urls.toString();
    }
}
