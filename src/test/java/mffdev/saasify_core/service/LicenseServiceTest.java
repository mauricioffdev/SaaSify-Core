package mffdev.saasify_core.service;

import mffdev.saasify_core.domain.License;
import mffdev.saasify_core.infrastructure.persistence.LicenseEntity;
import mffdev.saasify_core.infrastructure.persistence.LicenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {

    @Mock
    private LicenseRepository licenseRepository;

    private LicenseService licenseService;

    @BeforeEach
    void setUp() {
        licenseService = new LicenseService(licenseRepository);
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
    @DisplayName("generateLicense persiste a entidade uma única vez")
    void generateLicensePersistsExactlyOnce() {
        licenseService.generateLicense(UUID.randomUUID(), 30, "cliente@empresa.com");

        verify(licenseRepository).save(any(LicenseEntity.class));
        verifyNoMoreInteractions(licenseRepository);
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

    private LicenseEntity captureSavedEntity() {
        ArgumentCaptor<LicenseEntity> captor = ArgumentCaptor.forClass(LicenseEntity.class);
        verify(licenseRepository).save(captor.capture());
        return captor.getValue();
    }
}
