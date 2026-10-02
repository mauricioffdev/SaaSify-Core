package mffdev.saasify_core.domain.exception;

public class InvalidLicenseDataException extends BusinessException {

    public InvalidLicenseDataException(String message) {
        super("invalid_license_data", message);
    }
}