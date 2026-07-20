package uk.gov.hmcts.cp.audit.service;

import jakarta.jms.Connection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import uk.gov.hmcts.cp.audit.config.AuditProperties;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class ArtemisConnectivityCheckService {

    private static final int PORT = 61_616;
    private static final String CONNECTION_TIMEOUT_PARAMS =
            "sslEnabled=true&verifyHost=false&call_timeout=5000&connection_ttl=5000"
            + "&initial_connect_attempts=1&reconnect_attempts=0";

    private final AuditProperties properties;

    @Scheduled(cron = "0 0 * * * *")
    public void checkConnectivity() {
        final List<String> hosts = properties.getHosts();
        final boolean allOk = hosts.stream().allMatch(this::checkHost);
        if (allOk) {
            log.info("Artemis connectivity check successful hosts:{}", hosts);
        }
    }

    private boolean checkHost(final String host) {
        final String url = "tcp://" + host + ":" + PORT + "?" + CONNECTION_TIMEOUT_PARAMS;
        try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(url);
             Connection connection = factory.createConnection("", "")) {
            connection.start();
            log.info("Artemis connectivity check host:{} port:{} OK", host, PORT);
            return true;
        } catch (Exception e) {
            log.error("Artemis connectivity check host:{} port:{} FAILED", host, PORT, e);
            return false;
        }
    }
}
