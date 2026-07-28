package uk.gov.hmcts.cp.audit.integration;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.audit.annotation.AuditDetail;
import uk.gov.hmcts.cp.audit.annotation.AuditExclude;
import uk.gov.hmcts.cp.audit.model.AuditMdcKeys;

@RestController
class TestController {

    @GetMapping("/audited")
    @AuditDetail(eventName = "test.audited")
    public ResponseEntity<String> audited() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/audited-with-mdc")
    @AuditDetail(eventName = "test.audited-with-mdc", expectedMdcFields = {AuditMdcKeys.MATERIAL_ID, AuditMdcKeys.USER_ID})
    public ResponseEntity<String> auditedWithMdc() {
        MDC.put(AuditMdcKeys.MATERIAL_ID, "00000000-0000-0000-0000-000000000010");
        MDC.put(AuditMdcKeys.USER_ID, "00000000-0000-0000-0000-000000000020");
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/audited-with-mdc-missing")
    @AuditDetail(eventName = "test.audited-with-mdc-missing", expectedMdcFields = {AuditMdcKeys.MATERIAL_ID, AuditMdcKeys.USER_ID})
    public ResponseEntity<String> auditedWithMdcMissing() {
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
