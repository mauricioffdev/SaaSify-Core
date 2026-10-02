package mffdev.saasify_core.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record License(
        UUID id,
        UUID tenantId,
        String licenseKey,
        String customerEmail,
        LicenseStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
    public enum LicenseStatus {
        ACTIVE,
        EXPIRED,
        SUSPENDED
    }

    // Metodo de fábrica para criar uma licença ativa
    public static License createNew(UUID tenantId, int validityDays, String customerEmail) {
        String generatedKey = "SAAS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        return new License(
                UUID.randomUUID(),
                tenantId,
                generatedKey,
                customerEmail,
                LicenseStatus.ACTIVE,
                now,
                now.plusDays(validityDays)
        );
    }
}