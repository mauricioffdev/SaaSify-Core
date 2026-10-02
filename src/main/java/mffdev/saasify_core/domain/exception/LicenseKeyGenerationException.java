package mffdev.saasify_core.domain.exception;

public class LicenseKeyGenerationException extends BusinessException {

    public LicenseKeyGenerationException() {
        super("license_key_generation_failed",
                "Não foi possível gerar uma chave de licença única após várias tentativas");
    }
}