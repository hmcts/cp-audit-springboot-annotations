package uk.gov.hmcts.cp.audit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditDetail {
    String origin() default "hearing-results-document";
    String component() default "QUERY_API";
    String eventName();
    String action() default "View";
    String[] pathParams() default {};
}
