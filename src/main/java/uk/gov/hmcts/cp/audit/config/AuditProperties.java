package uk.gov.hmcts.cp.audit.config;

import lombok.Builder;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Builder
@ConfigurationProperties(prefix = "cp.audit")
public class AuditProperties {

    private final boolean enabled;
    private final List<String> hosts;
}
