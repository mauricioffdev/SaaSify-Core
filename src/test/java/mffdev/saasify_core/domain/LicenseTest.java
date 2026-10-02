package mffdev.saasify_core.domain;

import mffdev.saasify_core.infrastructure.persistence.LicenseEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseTest {

    @Test
    @DisplayName("createNew gera uma licença completa a partir do tenant e da validade")
    void createNewBuildsCompleteLicense() {
        UUID tenantId = UUID.randomUUID();

        License license = License.createNew(tenantId, 30, "cliente@empresa.com");

        assertThat(license.id()).isNotNull();
        assertThat(license.tenantId()).isEqualTo(tenantId);
        assertThat(license.customerEmail()).isEqualTo("cliente@empresa.com");
        assertThat(license.status()).isEqualTo(License.LicenseStatus.ACTIVE);
    }

    @Test
    @DisplayName("createNew gera uma chave no formato SAAS-XXXXXXXX")
    void createNewGeneratesKeyWithExpectedFormat() {
        License license = License.createNew(UUID.randomUUID(), 30, "cliente@empresa.com");

        assertThat(license.licenseKey())
                .startsWith("SAAS-")
                .matches("SAAS-[0-9A-F]{8}");
    }

    @Test
    @DisplayName("createNew define expiresAt como createdAt somado aos dias de validade")
    void createNewSetsExpiryAfterCreation() {
        int validityDays = 30;
        LocalDateTime before = LocalDateTime.now();

        License license = License.createNew(UUID.randomUUID(), validityDays, "cliente@empresa.com");

        LocalDateTime after = LocalDateTime.now();

        assertThat(license.createdAt()).isBetween(before, after);
        assertThat(license.expiresAt()).isAfter(license.createdAt());
        assertThat(ChronoUnit.DAYS.between(license.createdAt(), license.expiresAt())).isEqualTo(validityDays);
    }

    @Test
    @DisplayName("createNew nunca gera createdAt no futuro nem expiresAt no passado")
    void createNewKeepsChronologyConsistent() {
        License license = License.createNew(UUID.randomUUID(), 1, "cliente@empresa.com");

        assertThat(license.createdAt()).isBeforeOrEqualTo(license.expiresAt());
    }

    @Test
    @DisplayName("createNew gera chaves distintas a cada chamada")
    void createNewGeneratesUniqueKeys() {
        UUID tenantId = UUID.randomUUID();

        String first = License.createNew(tenantId, 30, "a@empresa.com").licenseKey();
        String second = License.createNew(tenantId, 30, "a@empresa.com").licenseKey();

        assertThat(first).isNotEqualTo(second);
    }

    @ParameterizedTest
    @EnumSource(License.LicenseStatus.class)
    @DisplayName("todo status do domínio é mapeável por nome para o enum de persistência")
    void everyDomainStatusIsMappableByName(License.LicenseStatus status) {
        assertThat(LicenseEntity.LicenseStatus.valueOf(status.name()))
                .isNotNull()
                .hasToString(status.name());
    }
}
