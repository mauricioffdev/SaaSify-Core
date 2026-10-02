package mffdev.saasify_core.infrastructure.persistence;

import mffdev.saasify_core.domain.License;
import org.springframework.stereotype.Component;

@Component
public class LicenseMapper {

    public LicenseEntity toEntity(License license) {
        LicenseEntity entity = new LicenseEntity();
        entity.setId(license.id());
        entity.setTenantId(license.tenantId());
        entity.setLicenseKey(license.licenseKey());
        entity.setCustomerEmail(license.customerEmail());
        entity.setStatus(LicenseEntity.LicenseStatus.valueOf(license.status().name()));
        entity.setCreatedAt(license.createdAt());
        entity.setExpiresAt(license.expiresAt());
        entity.markNew();
        return entity;
    }

    public License toDomain(LicenseEntity entity) {
        return new License(
                entity.getId(),
                entity.getTenantId(),
                entity.getLicenseKey(),
                entity.getCustomerEmail(),
                License.LicenseStatus.valueOf(entity.getStatus().name()),
                entity.getCreatedAt(),
                entity.getExpiresAt()
        );
    }
}