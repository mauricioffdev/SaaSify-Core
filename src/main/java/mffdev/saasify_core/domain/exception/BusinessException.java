package mffdev.saasify_core.domain.exception;

/**
 * Raiz das falhas de regra de negócio. Não carrega dependência de web:
 * o mapeamento para status HTTP fica no GlobalExceptionHandler.
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}