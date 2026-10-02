package mffdev.saasify_core.domain.exception;

public class LicenseSuspendedException extends BusinessException {

    public LicenseSuspendedException(String licenseKey) {
        super("license_suspended", "Licença suspensa: " + licenseKey);
    }
}