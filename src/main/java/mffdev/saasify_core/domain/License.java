package mffdev.saasify_core.domain;

import mffdev.saasify_core.domain.exception.InvalidLicenseDataException;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

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

    private static final int MIN_VALIDITY_DAYS = 1;
    private static final int MAX_VALIDITY_DAYS = 3650;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    // Metodo de fábrica para criar uma licença ativa
    public static License createNew(UUID tenantId, int validityDays, String customerEmail) {
        return createNew(tenantId, generateLicenseKey(), validityDays, customerEmail);
    }

    // Mesma fábrica, porém com chave definida por fora (permite tratar colisão de chave)
    public static License createNew(UUID tenantId, String licenseKey, int validityDays, String customerEmail) {
        if (tenantId == null) {
            throw new InvalidLicenseDataException("O ID do tenant é obrigatório");
        }
        if (validityDays < MIN_VALIDITY_DAYS || validityDays > MAX_VALIDITY_DAYS) {
            throw new InvalidLicenseDataException(
                    "A validade deve estar entre " + MIN_VALIDITY_DAYS + " e " + MAX_VALIDITY_DAYS + " dias");
        }
        if (licenseKey == null || licenseKey.isBlank()) {
            throw new InvalidLicenseDataException("A chave da licença é obrigatória");
        }

        LocalDateTime now = LocalDateTime.now();

        return new License(
                UUID.randomUUID(),
                tenantId,
                licenseKey.trim(),
                normalizeEmail(customerEmail),
                LicenseStatus.ACTIVE,
                now,
                now.plusDays(validityDays)
        );
    }

    public static String generateLicenseKey() {
        return "SAAS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isSuspended() {
        return status == LicenseStatus.SUSPENDED;
    }

    public boolean isUsable(LocalDateTime now) {
        return status == LicenseStatus.ACTIVE && !isExpired(now);
    }

    private static String normalizeEmail(String customerEmail) {
        if (customerEmail == null || customerEmail.isBlank()) {
            throw new InvalidLicenseDataException("O e-mail do cliente é obrigatório");
        }

        String email = customerEmail.trim();
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidLicenseDataException("O formato do e-mail é inválido");
        }

        return email.toLowerCase(Locale.ROOT);
    }
}