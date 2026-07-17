package uk.gov.hmcts.cp.audit.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Slf4j
@AutoConfiguration
@ConditionalOnProperty(prefix = "cp.audit", name = "enabled", havingValue = "false")
@EnableConfigurationProperties(AuditProperties.class)
public class ArtemisAuditDisabledConfiguration {

    @Bean
    public String auditDisabledMarker(final AuditProperties properties) {
        log.warn("WARNING Audit is disabled: enabled={}, hosts={}", properties.isEnabled(), properties.getHosts());
        return "auditDisabled";
    }
}
