package mffdev.saasify_core.domain.exception;

public class LicenseNotFoundException extends BusinessException {

    public LicenseNotFoundException(String licenseKey) {
        super("license_not_found", "Licença não encontrada: " + licenseKey);
    }
}