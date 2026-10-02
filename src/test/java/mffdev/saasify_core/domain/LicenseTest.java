package mffdev.saasify_core.domain;

import mffdev.saasify_core.domain.exception.InvalidLicenseDataException;
import mffdev.saasify_core.infrastructure.persistence.LicenseEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    @DisplayName("createNew aceita uma chave informada por fora")
    void createNewAcceptsProvidedKey() {
        License license = License.createNew(UUID.randomUUID(), "SAAS-DEADBEEF", 30, "cliente@empresa.com");

        assertThat(license.licenseKey()).isEqualTo("SAAS-DEADBEEF");
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
    @ValueSource(strings = {"", "   ", "sem-arroba", "cliente@", "@empresa.com", "cliente@empresa"})
    @DisplayName("createNew rejeita e-mail ausente ou malformado")
    void createNewRejectsInvalidEmail(String email) {
        assertThatThrownBy(() -> License.createNew(UUID.randomUUID(), 30, email))
                .isInstanceOf(InvalidLicenseDataException.class)
                .hasMessageContaining("e-mail");
    }

    @Test
    @DisplayName("createNew rejeita e-mail nulo")
    void createNewRejectsNullEmail() {
        assertThatThrownBy(() -> License.createNew(UUID.randomUUID(), 30, null))
                .isInstanceOf(InvalidLicenseDataException.class);
    }

    @Test
    @DisplayName("createNew normaliza o e-mail para minúsculas e sem espaços")
    void createNewNormalizesEmail() {
        License license = License.createNew(UUID.randomUUID(), 30, "  Cliente@Empresa.COM ");

        assertThat(license.customerEmail()).isEqualTo("cliente@empresa.com");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -30, 3651})
    @DisplayName("createNew rejeita validade fora da faixa permitida")
    void createNewRejectsInvalidValidity(int validityDays) {
        assertThatThrownBy(() -> License.createNew(UUID.randomUUID(), validityDays, "cliente@empresa.com"))
                .isInstanceOf(InvalidLicenseDataException.class)
                .hasMessageContaining("validade");
    }

    @Test
    @DisplayName("createNew rejeita tenant nulo")
    void createNewRejectsNullTenant() {
        assertThatThrownBy(() -> License.createNew(null, 30, "cliente@empresa.com"))
                .isInstanceOf(InvalidLicenseDataException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    @DisplayName("createNew rejeita chave em branco")
    void createNewRejectsBlankKey() {
        assertThatThrownBy(() -> License.createNew(UUID.randomUUID(), "  ", 30, "cliente@empresa.com"))
                .isInstanceOf(InvalidLicenseDataException.class);
    }

    @Test
    @DisplayName("isExpired considera expirada a licença no instante exato do vencimento")
    void isExpiredAtBoundary() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        License license = new License(UUID.randomUUID(), UUID.randomUUID(), "SAAS-AAAAAAAA",
                "cliente@empresa.com", License.LicenseStatus.ACTIVE, createdAt, createdAt.plusDays(30));

        assertThat(license.isExpired(createdAt.plusDays(30).minusNanos(1))).isFalse();
        assertThat(license.isExpired(createdAt.plusDays(30))).isTrue();
        assertThat(license.isExpired(createdAt.plusDays(31))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "ACTIVE,false",
            "EXPIRED,false",
            "SUSPENDED,false"
    })
    @DisplayName("isUsable só vale para licença ativa dentro da validade")
    void isUsableOnlyForActiveLicenseInsideValidity(License.LicenseStatus status, boolean expired) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime expiresAt = expired ? createdAt.minusDays(1) : createdAt.plusDays(30);
        License license = new License(UUID.randomUUID(), UUID.randomUUID(), "SAAS-AAAAAAAA",
                "cliente@empresa.com", status, createdAt, expiresAt);

        boolean usable = license.isUsable(createdAt.plusDays(1));

        assertThat(usable).isEqualTo(status == License.LicenseStatus.ACTIVE && !expired);
        assertThat(license.isSuspended()).isEqualTo(status == License.LicenseStatus.SUSPENDED);
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