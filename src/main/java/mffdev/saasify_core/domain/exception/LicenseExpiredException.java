package mffdev.saasify_core.domain.exception;

import java.time.LocalDateTime;

public class LicenseExpiredException extends BusinessException {

    public LicenseExpiredException(String licenseKey, LocalDateTime expiresAt) {
        super("license_expired", "Licença expirada em " + expiresAt + ": " + licenseKey);
    }
}