package uk.gov.hmcts.cp.audit.service;

import java.time.Instant;

public class ClockService {

    public Instant now() {
        return Instant.now();
    }
}
