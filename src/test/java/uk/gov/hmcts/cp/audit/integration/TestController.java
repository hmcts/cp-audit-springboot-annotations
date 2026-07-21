package uk.gov.hmcts.cp.audit.integration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.annotation.AuditExclude;

@RestController
class TestController {

    @GetMapping("/audited")
    @AuditDetail(eventName = "test.audited")
    public ResponseEntity<String> audited() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/excluded")
    @AuditExclude
    public ResponseEntity<String> excluded() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/unannotated")
    public ResponseEntity<String> unannotated() {
        return ResponseEntity.ok("ok");
    }
}
