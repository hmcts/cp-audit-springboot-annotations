package uk.gov.hmcts.cp.audit.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPropertiesTest {

    @Test
    void audit_properties_constructor_should_have_named_parameters_for_spring_binding() {
        final Constructor<?>[] constructors = AuditProperties.class.getDeclaredConstructors();
        assertThat(constructors)
                .as("AuditProperties must have at least one constructor")
                .isNotEmpty();

        final boolean anyHasNamedParams = Arrays.stream(constructors)
                .filter(c -> c.getParameterCount() > 0)
                .anyMatch(c -> Arrays.stream(c.getParameters()).allMatch(p -> p.isNamePresent()));

        assertThat(anyHasNamedParams)
                .as("AuditProperties constructor must retain parameter names (requires -parameters compiler flag) " +
                    "so Spring Boot can bind cp.audit.* properties via constructor binding")
                .isTrue();
    }
}
