package mffdev.saasify_core.domain.exception;

public class UnsupportedWebhookEventException extends BusinessException {

    public UnsupportedWebhookEventException(String eventType) {
        super("unsupported_webhook_event",
                "Evento de webhook não suportado: " + eventType);
    }
}