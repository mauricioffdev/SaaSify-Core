package mffdev.saasify_core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão da configuração: spring.threads.virtual.enabled estava aninhado dentro de
 * management.endpoint.health no application.yaml e não era aplicado.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("virtual threads ficam habilitados no lugar certo do YAML")
    void virtualThreadsAreEnabledAtRootLevel() {
        assertThat(environment.getProperty("spring.threads.virtual.enabled", Boolean.class)).isTrue();
        assertThat(environment.containsProperty("management.endpoint.health.spring")).isFalse();
    }

    @Test
    @DisplayName("propriedades de segurança do saasify são lidas do ambiente")
    void securityPropertiesAreBound() {
        assertThat(environment.getProperty("saasify.security.api-keys")).isEqualTo("test-api-key");
        assertThat(environment.getProperty("saasify.security.webhook-secret")).isEqualTo("test-webhook-secret");
        assertThat(environment.getProperty("saasify.security.webhook-tolerance", java.time.Duration.class))
                .isEqualTo(java.time.Duration.ofMinutes(5));
    }
}