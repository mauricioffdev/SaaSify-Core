package mffdev.saasify_core.service;

import mffdev.saasify_core.domain.License;
import mffdev.saasify_core.infrastructure.persistence.LicenseEntity;
import mffdev.saasify_core.infrastructure.persistence.LicenseRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LicenseService {

    private final LicenseRepository licenseRepository;

    public LicenseService(LicenseRepository licenseRepository) {
        this.licenseRepository = licenseRepository;
    }

    public License generateLicense(UUID tenantId, int validityDays, String customerEmail) {
        License domainLicense = License.createNew(tenantId, validityDays, customerEmail);

        LicenseEntity entity = new LicenseEntity();
        entity.setTenantId(domainLicense.tenantId());
        entity.setLicenseKey(domainLicense.licenseKey());
        entity.setCustomerEmail(customerEmail);
        entity.setStatus(LicenseEntity.LicenseStatus.valueOf(domainLicense.status().name()));
        entity.setExpiresAt(domainLicense.expiresAt());
        entity.setCreatedAt(domainLicense.createdAt());

        licenseRepository.save(entity);

        return domainLicense;
    }
}