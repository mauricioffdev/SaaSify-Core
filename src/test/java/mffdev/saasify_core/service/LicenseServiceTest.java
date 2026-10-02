package mffdev.saasify_core.service;

import mffdev.saasify_core.domain.License;
import mffdev.saasify_core.domain.exception.InvalidLicenseDataException;
import mffdev.saasify_core.domain.exception.LicenseExpiredException;
import mffdev.saasify_core.domain.exception.LicenseKeyGenerationException;
import mffdev.saasify_core.domain.exception.LicenseNotFoundException;
import mffdev.saasify_core.domain.exception.LicenseSuspendedException;
import mffdev.saasify_core.infrastructure.persistence.LicenseEntity;
import mffdev.saasify_core.infrastructure.persistence.LicenseMapper;
import mffdev.saasify_core.infrastructure.persistence.LicenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {

    @Mock
    private LicenseRepository licenseRepository;

    private LicenseService licenseService;

    @BeforeEach
    void setUp() {
        licenseService = new LicenseService(licenseRepository, new LicenseMapper());
    }

    @Test
    @DisplayName("generateLicense devolve a licença de domínio criada")
    void generateLicenseReturnsDomainLicense() {
        UUID tenantId = UUID.randomUUID();

        License license = licenseService.generateLicense(tenantId, 30, "cliente@empresa.com");

        assertThat(license.tenantId()).isEqualTo(tenantId);
        assertThat(license.customerEmail()).isEqualTo("cliente@empresa.com");
        assertThat(license.status()).isEqualTo(License.LicenseStatus.ACTIVE);
    }

    @Test
    @DisplayName("generateLicense persiste a entidade exatamente uma vez")
    void generateLicensePersistsExactlyOnce() {
        licenseService.generateLicense(UUID.randomUUID(), 30, "cliente@empresa.com");

        verify(licenseRepository, times(1)).save(any(LicenseEntity.class));
    }

    @Test
    @DisplayName("generateLicense grava o mesmo id devolvido na resposta da API")
    void generateLicensePersistsDomainId() {
        License license = licenseService.generateLicense(UUID.randomUUID(), 30, "cliente@empresa.com");

        assertThat(captureSavedEntity().getId()).isEqualTo(license.id());
    }

    @Test
    @DisplayName("generateLicense mapeia todos os campos da licença de domínio para a entidade")
    void generateLicenseMapsEveryFieldToEntity() {
        License license = licenseService.generateLicense(UUID.randomUUID(), 30, "cliente@empresa.com");

        LicenseEntity saved = captureSavedEntity();

        assertThat(saved.getTenantId()).isEqualTo(license.tenantId());
        assertThat(saved.getLicenseKey()).isEqualTo(license.licenseKey());
        assertThat(saved.getCustomerEmail()).isEqualTo(license.customerEmail());
        assertThat(saved.getStatus()).isEqualTo(LicenseEntity.LicenseStatus.ACTIVE);
        assertThat(saved.getCreatedAt()).isEqualTo(license.createdAt());
        assertThat(saved.getExpiresAt()).isEqualTo(license.expiresAt());
    }

    @Test
    @DisplayName("generateLicense converte o status do domínio para o enum de persistência")
    void generateLicenseConvertsStatusEnum() {
        License license = licenseService.generateLicense(UUID.randomUUID(), 30, "cliente@empresa.com");

        LicenseEntity saved = captureSavedEntity();

        assertThat(saved.getStatus().name()).isEqualTo(license.status().name());
    }

    @Test
    @DisplayName("generateLicense preserva a validade ao mapear createdAt e expiresAt")
    void generateLicensePreservesValidityWindow() {
        License license = licenseService.generateLicense(UUID.randomUUID(), 45, "cliente@empresa.com");

        LicenseEntity saved = captureSavedEntity();

        assertThat(ChronoUnit.DAYS.between(saved.getCreatedAt(), saved.getExpiresAt())).isEqualTo(45);
        assertThat(saved.getExpiresAt()).isAfter(saved.getCreatedAt());
    }

    @Test
    @DisplayName("generateLicense tenta outra chave quando a gerada já existe no banco")
    void generateLicenseRetriesWhenKeyAlreadyExists() {
        when(licenseRepository.existsByLicenseKey(anyString()))
                .thenReturn(true, false);

        licenseService.generateLicense(UUID.randomUUID(), 30, "cliente@empresa.com");

        verify(licenseRepository, times(2)).existsByLicenseKey(anyString());
        verify(licenseRepository, times(1)).save(any(LicenseEntity.class));
    }

    @Test
    @DisplayName("generateLicense falha quando não consegue gerar chave única")
    void generateLicenseFailsWhenKeyIsAlwaysTaken() {
        when(licenseRepository.existsByLicenseKey(anyString())).thenReturn(true);

        assertThatThrownBy(() -> licenseService.generateLicense(UUID.randomUUID(), 30, "cliente@empresa.com"))
                .isInstanceOf(LicenseKeyGenerationException.class);

        verify(licenseRepository, never()).save(any(LicenseEntity.class));
    }

    @Test
    @DisplayName("generateLicense não grava nada quando os dados são inválidos")
    void generateLicenseDoesNotPersistInvalidData() {
        assertThatThrownBy(() -> licenseService.generateLicense(UUID.randomUUID(), 0, "cliente@empresa.com"))
                .isInstanceOf(InvalidLicenseDataException.class);

        verify(licenseRepository, never()).save(any(LicenseEntity.class));
    }

    @Test
    @DisplayName("validateLicense devolve a licença ativa dentro da validade")
    void validateLicenseReturnsActiveLicense() {
        String licenseKey = "SAAS-ABCD1234";
        UUID tenantId = UUID.randomUUID();
        when(licenseRepository.findByLicenseKey(licenseKey))
                .thenReturn(Optional.of(entity(licenseKey, tenantId, License.LicenseStatus.ACTIVE,
                        LocalDateTime.now().plusDays(30))));

        License license = licenseService.validateLicense(licenseKey);

        assertThat(license.licenseKey()).isEqualTo(licenseKey);
        assertThat(license.tenantId()).isEqualTo(tenantId);
        assertThat(license.status()).isEqualTo(License.LicenseStatus.ACTIVE);
    }

    @Test
    @DisplayName("validateLicense falha com LicenseNotFoundException quando a chave não existe")
    void validateLicenseFailsWhenNotFound() {
        when(licenseRepository.findByLicenseKey("SAAS-UNKNOWN00")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> licenseService.validateLicense("SAAS-UNKNOWN00"))
                .isInstanceOf(LicenseNotFoundException.class);
    }

    @Test
    @DisplayName("validateLicense falha com LicenseNotFoundException sem consultar o banco quando a chave é vazia")
    void validateLicenseFailsWithoutHittingDatabaseForBlankKey() {
        assertThatThrownBy(() -> licenseService.validateLicense("  "))
                .isInstanceOf(LicenseNotFoundException.class);

        verify(licenseRepository, never()).findByLicenseKey(anyString());
    }

    @Test
    @DisplayName("validateLicense falha com LicenseExpiredException quando a validade já passou")
    void validateLicenseFailsWhenExpired() {
        String licenseKey = "SAAS-EXPIRED1";
        when(licenseRepository.findByLicenseKey(licenseKey))
                .thenReturn(Optional.of(entity(licenseKey, UUID.randomUUID(), License.LicenseStatus.ACTIVE,
                        LocalDateTime.now().minusDays(1))));

        assertThatThrownBy(() -> licenseService.validateLicense(licenseKey))
                .isInstanceOf(LicenseExpiredException.class);
    }

    @Test
    @DisplayName("validateLicense falha com LicenseExpiredException quando o status já é EXPIRED")
    void validateLicenseFailsWhenStatusIsExpired() {
        String licenseKey = "SAAS-EXPIRED2";
        when(licenseRepository.findByLicenseKey(licenseKey))
                .thenReturn(Optional.of(entity(licenseKey, UUID.randomUUID(), License.LicenseStatus.EXPIRED,
                        LocalDateTime.now().plusDays(30))));

        assertThatThrownBy(() -> licenseService.validateLicense(licenseKey))
                .isInstanceOf(LicenseExpiredException.class);
    }

    @Test
    @DisplayName("validateLicense falha com LicenseSuspendedException quando a licença está suspensa")
    void validateLicenseFailsWhenSuspended() {
        String licenseKey = "SAAS-SUSPENDS";
        when(licenseRepository.findByLicenseKey(licenseKey))
                .thenReturn(Optional.of(entity(licenseKey, UUID.randomUUID(), License.LicenseStatus.SUSPENDED,
                        LocalDateTime.now().plusDays(30))));

        assertThatThrownBy(() -> licenseService.validateLicense(licenseKey))
                .isInstanceOf(LicenseSuspendedException.class);
    }

    private LicenseEntity entity(String licenseKey, UUID tenantId, License.LicenseStatus status,
                                 LocalDateTime expiresAt) {
        LicenseEntity entity = new LicenseEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setLicenseKey(licenseKey);
        entity.setCustomerEmail("cliente@empresa.com");
        entity.setStatus(LicenseEntity.LicenseStatus.valueOf(status.name()));
        entity.setCreatedAt(expiresAt.minusDays(30));
        entity.setExpiresAt(expiresAt);
        return entity;
    }

    private LicenseEntity captureSavedEntity() {
        ArgumentCaptor<LicenseEntity> captor = ArgumentCaptor.forClass(LicenseEntity.class);
        verify(licenseRepository).save(captor.capture());
        return captor.getValue();
    }
}