package mffdev.saasify_core.controller.dto;

import mffdev.saasify_core.domain.License;

import java.time.LocalDateTime;
import java.util.UUID;

public record LicenseResponse(
        UUID id,
        UUID tenantId,
        String licenseKey,
        String customerEmail,
        License.LicenseStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
    public static LicenseResponse from(License license) {
        return new LicenseResponse(
                license.id(),
                license.tenantId(),
                license.licenseKey(),
                license.customerEmail(),
                license.status(),
                license.createdAt(),
                license.expiresAt()
        );
    }
}